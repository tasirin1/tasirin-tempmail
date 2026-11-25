package id.tasirin.tempmail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

// Layar utama: alamat email sekali pakai (tempmail.plus) + kotak masuk
// yang dicek otomatis + deteksi kode verifikasi siap salin.
class MainActivity : AppCompatActivity() {

    private lateinit var tvEmail: TextView
    private lateinit var spinnerDomain: Spinner
    private lateinit var etNama: EditText
    private lateinit var llKode: LinearLayout
    private lateinit var tvKode: TextView
    private lateinit var tvStatus: TextView
    private lateinit var lvInbox: ListView
    private lateinit var tvKosong: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    // Executor khusus baca email: ketukan pengguna tidak mengantre di
    // belakang polling yang bisa makan 2x timeout (issue #8).
    private val executorDetail = Executors.newSingleThreadExecutor()
    private val sedangCek = AtomicBoolean(false)
    private val sedangBuka = AtomicBoolean(false)

    private var alamatSekarang: String = ""
    private var spinnerSiap = false
    private var polling = false

    // Id semua email yang isinya sudah tuntas diperiksa untuk kode
    // verifikasi. Semua email diperiksa, bukan cuma yang teratas (issue #2).
    // Himpunan ini disentuh thread UI dan thread latar — wajib sinkron,
    // dan penanda generasi memastikan hasil pengambilan untuk alamat lama
    // tidak mencemari keadaan alamat baru (issue #7).
    private val idSudahDicek = Collections.synchronizedSet(LinkedHashSet<String>())

    // Hitungan gagal ambil isi per email: sesudah 3x gagal, email dianggap
    // tuntas (menyerah) supaya tidak dicoba ulang selamanya (issue #26).
    private val gagalAmbilIsi = Collections.synchronizedMap(HashMap<String, Int>())

    @Volatile
    private var generasiAlamat = 0

    @Volatile
    private var inboxPernahDimuat = false

    private val dataInbox = ArrayList<HashMap<String, String>>()
    private var itemInbox: List<MailItem> = emptyList()
    private lateinit var adapterInbox: SimpleAdapter

    // Jeda polling berjalan: 8 detik saat sehat, mundur berlipat sampai
    // 60 detik saat gagal supaya tidak memukul server terus (issue #11).
    @Volatile
    private var jedaPollingMs = 8000L

    private val pollRunnable = object : Runnable {
        override fun run() {
            refreshInbox()
            if (polling) handler.postDelayed(this, jedaPollingMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvEmail = findViewById(R.id.tvEmail)
        spinnerDomain = findViewById(R.id.spinnerDomain)
        etNama = findViewById(R.id.etNama)
        llKode = findViewById(R.id.llKode)
        tvKode = findViewById(R.id.tvKode)
        tvStatus = findViewById(R.id.tvStatus)
        lvInbox = findViewById(R.id.lvInbox)
        tvKosong = findViewById(R.id.tvKosong)

        adapterInbox = SimpleAdapter(
            this,
            dataInbox,
            android.R.layout.simple_list_item_2,
            arrayOf("judul", "dari"),
            intArrayOf(android.R.id.text1, android.R.id.text2)
        )
        lvInbox.adapter = adapterInbox
        lvInbox.setOnItemClickListener { _, _, posisi, _ ->
            val item = itemInbox.getOrNull(posisi) ?: return@setOnItemClickListener
            bukaDetail(item)
        }

        val adapterDomain = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            AddressGenerator.DOMAINS
        )
        adapterDomain.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDomain.adapter = adapterDomain
        spinnerDomain.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, posisi: Int, id: Long) {
                if (!spinnerSiap || alamatSekarang.isBlank()) return
                val domain = AddressGenerator.DOMAINS[posisi]
                if (alamatSekarang.substringAfter("@", "") != domain) {
                    setEmail(alamatSekarang.substringBefore("@") + "@" + domain)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        findViewById<Button>(R.id.btnSalin).setOnClickListener {
            salin(alamatSekarang, "Email tersalin")
        }
        findViewById<Button>(R.id.btnGanti).setOnClickListener {
            val domain = AddressGenerator.DOMAINS[spinnerDomain.selectedItemPosition]
            val baru = AddressGenerator.randomLocalPart() + "@" + domain
            setEmail(baru)
            // Rotasi langsung menyalin alamat baru — tidak perlu tombol Salin lagi.
            salin(baru, "Email baru tersalin")
        }
        findViewById<Button>(R.id.btnPakai).setOnClickListener {
            val bersih = AddressGenerator.sanitizeLocalPart(etNama.text.toString())
            if (bersih.isEmpty()) {
                toast("Nama tidak sah")
            } else {
                val domain = AddressGenerator.DOMAINS[spinnerDomain.selectedItemPosition]
                etNama.text.clear()
                val baru = "$bersih@$domain"
                setEmail(baru)
                salin(baru, "Email tersalin")
            }
        }
        findViewById<Button>(R.id.btnSalinKode).setOnClickListener {
            salin(tvKode.text.toString(), "Kode tersalin")
        }
        findViewById<Button>(R.id.btnSegarkan).setOnClickListener { refreshInbox() }

        // Alamat tersimpan dari pemakaian sebelumnya, atau buat baru.
        val prefs = getSharedPreferences("tempmail", Context.MODE_PRIVATE)
        val tersimpan = prefs.getString("email", null)
        val awal = if (!tersimpan.isNullOrBlank()) tersimpan else AddressGenerator.randomAddress()
        val idxDomain = AddressGenerator.DOMAINS.indexOf(awal.substringAfter("@", ""))
        if (idxDomain >= 0) spinnerDomain.setSelection(idxDomain)
        spinnerSiap = true
        setEmail(awal)
    }

    override fun onResume() {
        super.onResume()
        polling = true
        // Cabut dulu supaya tidak pernah ada dua rantai polling (issue #31).
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
    }

    override fun onPause() {
        polling = false
        handler.removeCallbacks(pollRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        executorDetail.shutdownNow()
        super.onDestroy()
    }

    // Ganti alamat aktif: simpan, kosongkan tampilan, langsung cek inbox.
    private fun setEmail(alamat: String) {
        generasiAlamat++
        alamatSekarang = alamat
        tvEmail.text = alamat
        getSharedPreferences("tempmail", Context.MODE_PRIVATE)
            .edit().putString("email", alamat).apply()
        synchronized(idSudahDicek) { idSudahDicek.clear() }
        synchronized(gagalAmbilIsi) { gagalAmbilIsi.clear() }
        inboxPernahDimuat = false
        itemInbox = emptyList()
        dataInbox.clear()
        adapterInbox.notifyDataSetChanged()
        tvKosong.visibility = View.VISIBLE
        sembunyikanKode()
        refreshInbox()
    }

    // Ambil daftar inbox dari API di thread latar, lalu perbarui tampilan.
    private fun refreshInbox() {
        if (!sedangCek.compareAndSet(false, true)) return
        val email = alamatSekarang
        val gen = generasiAlamat
        try {
            executor.execute { jalankanRefresh(email, gen) }
        } catch (e: RejectedExecutionException) {
            // Activity sedang dihancurkan — jangan crash, bebaskan penanda.
            sedangCek.set(false)
        }
    }

    private fun jalankanRefresh(email: String, gen: Int) {
            try {
                val items = TempMailApi.fetchInbox(email)
                val pernahDimuat = inboxPernahDimuat
                // Fase 1: tampilkan daftar SEGERA — jangan menunggu pemindaian
                // isi email yang bisa lama (issue #25).
                runOnUiThread {
                    if (email != alamatSekarang) return@runOnUiThread
                    jedaPollingMs = 8000L
                    // Daftar hanya dibangun ulang bila isinya benar-benar
                    // berubah — mayoritas polling isinya sama (issue #20).
                    if (items != itemInbox) {
                        itemInbox = items
                        dataInbox.clear()
                        for (m in items) {
                            val baris = HashMap<String, String>()
                            baris["judul"] = m.subject.ifBlank { "(tanpa judul)" }
                            baris["dari"] = listOf(m.from, m.time).filter { it.isNotBlank() }.joinToString(" · ")
                            dataInbox.add(baris)
                        }
                        adapterInbox.notifyDataSetChanged()
                        tvKosong.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    }
                    val jam = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                    tvStatus.text = "Dicek terakhir $jam — ${items.size} email"
                    inboxPernahDimuat = true
                }
                // Fase 2: pindai kode dari email yang belum tuntas dicek
                // (issue #2), paling banyak 5 pengambilan isi per putaran —
                // sisanya putaran berikutnya (issue #25).
                var kodeBaru: String? = null
                var ambilIsi = 0
                for (m in items) {
                    if (gen != generasiAlamat) break
                    // Email tanpa id memakai kunci gabungan isi — kunci kosong
                    // bersama membuat email lain tak pernah dicek (issue #30).
                    val kunci = m.id.ifBlank { "tanpa-id:" + m.subject + "|" + m.from + "|" + m.time }
                    if (kunci in idSudahDicek) continue
                    var kode = OtpExtractor.extract(m.subject)
                    var tuntas = kode != null
                    if (kode == null) {
                        if (ambilIsi >= 5) continue
                        ambilIsi++
                        // Gagal ambil isi email tidak boleh menggagalkan daftar inbox,
                        // tapi emailnya juga BELUM tuntas dicek: jangan ditandai,
                        // supaya dicoba lagi pada polling berikutnya (issue #1).
                        try {
                            val detail = TempMailApi.fetchDetail(email, m.id)
                            kode = OtpExtractor.extract(m.subject + "\n" + detail.text)
                            tuntas = true
                            synchronized(gagalAmbilIsi) { gagalAmbilIsi.remove(kunci) }
                        } catch (e: Exception) {
                            kode = null
                            // Gagal sesaat dicoba lagi (issue #1), tapi dibatasi:
                            // 3x gagal berturut = menyerah, tandai tuntas (issue #26).
                            val kali = synchronized(gagalAmbilIsi) {
                                val n = (gagalAmbilIsi[kunci] ?: 0) + 1
                                gagalAmbilIsi[kunci] = n
                                n
                            }
                            if (kali >= 3) tuntas = true
                        }
                    }
                    // Alamat berganti di tengah pengambilan: hasil ini basi,
                    // jangan tandai apa pun untuk alamat baru (issue #7).
                    if (tuntas && gen == generasiAlamat) {
                        synchronized(idSudahDicek) {
                            idSudahDicek.add(kunci)
                            // Jaga ukuran penanda: simpan 50 id terakhir saja.
                            while (idSudahDicek.size > 50) {
                                idSudahDicek.remove(idSudahDicek.first())
                            }
                        }
                        if (kode != null && kodeBaru == null) kodeBaru = kode
                    }
                }
                val kodeFinal = kodeBaru
                runOnUiThread {
                    sedangCek.set(false)
                    if (email != alamatSekarang) return@runOnUiThread
                    if (kodeFinal != null) {
                        tampilkanKode(kodeFinal)
                        if (pernahDimuat) {
                            // Kode BARU tiba: langsung salin supaya tinggal tempel.
                            // (Hanya saat aplikasi terbuka — Android memblokir
                            // tulisan clipboard dari aplikasi latar.)
                            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("kode verifikasi", kodeFinal))
                            toast("Kode tiba & tersalin: $kodeFinal")
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    sedangCek.set(false)
                    if (email != alamatSekarang) return@runOnUiThread
                    jedaPollingMs = (jedaPollingMs * 2).coerceAtMost(60000L)
                    tvStatus.text = "Gagal cek inbox: ${e.message ?: "jaringan bermasalah"}"
                }
            }
    }

    // Ambil isi lengkap satu email lalu tampilkan sebagai dialog.
    private fun bukaDetail(item: MailItem) {
        // Satu bacaan pada satu waktu — ketukan beruntun tidak menumpuk
        // dialog (issue #28).
        if (!sedangBuka.compareAndSet(false, true)) return
        val email = alamatSekarang
        toast("Membuka email…")
        try {
            executorDetail.execute { jalankanBukaDetail(email, item) }
        } catch (e: RejectedExecutionException) {
            // Activity sedang dihancurkan — abaikan dengan aman (issue #24).
            sedangBuka.set(false)
        }
    }

    private fun jalankanBukaDetail(email: String, item: MailItem) {
            try {
                val detail = TempMailApi.fetchDetail(email, item.id)
                val kode = OtpExtractor.extract(detail.subject + "\n" + detail.text)
                runOnUiThread {
                    sedangBuka.set(false)
                    // Activity bisa sudah berakhir/berputar selama isi email
                    // diambil — dialog pada Activity mati = crash (issue #3).
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    val isi = StringBuilder()
                    isi.append("Dari: ").append(detail.from.ifBlank { item.from }).append("\n")
                    isi.append("Waktu: ").append(detail.date.ifBlank { item.time }).append("\n\n")
                    isi.append(detail.text.ifBlank { "(isi kosong)" })
                    val teks = if (isi.length > 6000) isi.substring(0, 6000) + "\n…(dipotong)" else isi.toString()
                    val builder = AlertDialog.Builder(this)
                        .setTitle(detail.subject.ifBlank { item.subject.ifBlank { "(tanpa judul)" } })
                        .setMessage(teks)
                        .setNegativeButton("Tutup", null)
                    if (kode != null) {
                        builder.setPositiveButton("Salin Kode $kode") { _, _ ->
                            salin(kode, "Kode tersalin")
                            tampilkanKode(kode)
                        }
                    }
                    builder.show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    sedangBuka.set(false)
                    toast("Gagal membuka: ${e.message ?: "jaringan bermasalah"}")
                }
            }
    }

    private fun tampilkanKode(kode: String) {
        tvKode.text = kode
        llKode.visibility = View.VISIBLE
    }

    private fun sembunyikanKode() {
        llKode.visibility = View.GONE
        tvKode.text = ""
    }

    private fun salin(teks: String, pesan: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("tempmail", teks))
        toast(pesan)
    }

    private fun toast(pesan: String) {
        Toast.makeText(this, pesan, Toast.LENGTH_SHORT).show()
    }
}

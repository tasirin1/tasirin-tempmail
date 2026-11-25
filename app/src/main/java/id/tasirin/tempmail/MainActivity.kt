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
    private lateinit var tvSubjudul: TextView
    private lateinit var spinnerLayanan: Spinner
    private lateinit var llNamaSendiri: LinearLayout
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

    // Executor khusus aksi pengguna (buat alamat + muat domain): tidak
    // boleh mengantre di belakang polling yang fase-2-nya bisa lama
    // (issue #48). Polling tetap di executor sendiri.
    private val executorAksi = Executors.newSingleThreadExecutor()
    private val sedangCek = AtomicBoolean(false)
    private val sedangBuka = AtomicBoolean(false)

    private var alamatSekarang: String = ""
    private var spinnerSiap = false
    private var polling = false

    // Layanan aktif + daftar domainnya (untuk pemilih domain).
    private var providerAktif: MailProvider = Layanan.semua[0]
    private var domainDaftar: List<String> = emptyList()
    private var layananSiap = false

    // Penanda generasi pembuatan alamat: hasil latar yang basi (layanan
    // atau permintaan sudah berganti) tidak boleh menimpa yang baru.
    @Volatile
    private var generasiBuat = 0

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

    // Salin otomatis hanya boleh untuk kode yang tiba SESUDAH sapuan awal
    // inbox lama tuntas seluruhnya — bukan sekadar daftar pernah dimuat,
    // karena sapuan awal bisa menyebar ke beberapa putaran (issue #33).
    @Volatile
    private var sapuAwalSelesai = false

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
        tvSubjudul = findViewById(R.id.tvSubjudul)
        spinnerLayanan = findViewById(R.id.spinnerLayanan)
        llNamaSendiri = findViewById(R.id.llNamaSendiri)
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

        spinnerDomain.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, posisi: Int, id: Long) {
                if (!spinnerSiap || alamatSekarang.isBlank()) return
                // Tukar domain di tempat hanya masuk akal untuk layanan
                // tanpa sesi (alamat bebas disusun ulang). Di layanan
                // berbasis sesi, domain hanya berlaku saat membuat
                // alamat baru.
                if (!providerAktif.tanpaSesi) return
                val domain = domainDaftar.getOrNull(posisi) ?: return
                if (alamatSekarang.substringAfter("@", "") != domain) {
                    setEmail(alamatSekarang.substringBefore("@") + "@" + domain)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Pemilih layanan: ganti provider = ganti alamat + muat ulang.
        val prefsAwal = getSharedPreferences("tempmail", Context.MODE_PRIVATE)
        providerAktif = Layanan.cari(prefsAwal.getString("layanan", "plus") ?: "plus")
        val adapterLayanan = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            Layanan.semua.map { it.nama }
        )
        adapterLayanan.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLayanan.adapter = adapterLayanan
        spinnerLayanan.setSelection(Layanan.semua.indexOf(providerAktif))
        spinnerLayanan.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, posisi: Int, id: Long) {
                if (!layananSiap) return
                val dipilih = Layanan.semua[posisi]
                if (dipilih !== providerAktif) gantiLayanan(dipilih)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        layananSiap = true

        findViewById<Button>(R.id.btnSalin).setOnClickListener {
            // Alamat kosong (pembuatan awal gagal) tidak boleh disalin
            // dengan toast sukses (issue #49).
            if (alamatSekarang.isBlank()) {
                toast("Alamat belum siap")
            } else {
                salin(alamatSekarang, "Email tersalin")
            }
        }
        findViewById<Button>(R.id.btnGanti).setOnClickListener {
            // Pembuatan alamat bisa lewat jaringan (layanan berbasis
            // sesi) — selalu lewat thread latar.
            buatAlamatBaru(acak = true, pesanSalin = "Email baru tersalin")
        }
        findViewById<Button>(R.id.btnPakai).setOnClickListener {
            val bersih = AddressGenerator.sanitizeLocalPart(etNama.text.toString())
            if (bersih.isEmpty()) {
                toast("Nama tidak sah")
            } else {
                etNama.text.clear()
                buatAlamatBaru(acak = false, namaBersih = bersih, pesanSalin = "Email tersalin")
            }
        }
        findViewById<Button>(R.id.btnSalinKode).setOnClickListener {
            salin(tvKode.text.toString(), "Kode tersalin")
        }
        findViewById<Button>(R.id.btnSegarkan).setOnClickListener { refreshInbox() }

        // Siapkan tampilan sesuai layanan aktif, lalu alamat awal:
        // sesi yang masih hidup dipakai lagi; alamat tersimpan hanya
        // untuk layanan tanpa sesi; selain itu buat alamat baru.
        terapkanLayananKeTampilan()
        val p = providerAktif
        val dariSesi = p.alamatAktif()
        val tersimpan = if (p.tanpaSesi) {
            val prefs = getSharedPreferences("tempmail", Context.MODE_PRIVATE)
            prefs.getString("email_" + p.id, null) ?: prefs.getString("email", null)
        } else null
        when {
            dariSesi != null -> {
                sesuaikanPilihanDomain(dariSesi)
                spinnerSiap = true
                setEmail(dariSesi)
            }
            !tersimpan.isNullOrBlank() -> {
                sesuaikanPilihanDomain(tersimpan)
                spinnerSiap = true
                setEmail(tersimpan)
            }
            p.tanpaSesi -> {
                val awal = p.alamatAcak(null)
                sesuaikanPilihanDomain(awal)
                spinnerSiap = true
                setEmail(awal)
            }
            else -> {
                spinnerSiap = true
                tvEmail.text = "Menyiapkan alamat…"
                buatAlamatBaru(acak = true, pesanSalin = null)
            }
        }
    }

    // Samakan subjudul + visibilitas blok nama sendiri dengan layanan
    // aktif, dan muat daftar domainnya (jaringan bila dinamis).
    private fun terapkanLayananKeTampilan() {
        val p = providerAktif
        tvSubjudul.text = "Email sekali pakai • ${p.nama}"
        llNamaSendiri.visibility = if (p.bisaCustom) View.VISIBLE else View.GONE
        if (!p.bisaCustom) return
        if (p.tanpaSesi) {
            muatDomain(p.domains())
        } else {
            // Domain dinamis (tempmail.is): ambil dari server di latar.
            try {
                executorAksi.execute {
                    try {
                        val d = p.domains()
                        runOnUiThread {
                            if (p === providerAktif) muatDomain(d)
                        }
                    } catch (e: Exception) {
                        // Domain gagal dimuat bukan keadaan fatal: tombol
                        // Pakai akan memuatnya lagi saat dipakai.
                    }
                }
            } catch (e: RejectedExecutionException) {
            }
        }
    }

    private fun muatDomain(daftar: List<String>) {
        domainDaftar = daftar
        val adapterDomain = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            daftar
        )
        adapterDomain.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        val siapSebelum = spinnerSiap
        spinnerSiap = false
        spinnerDomain.adapter = adapterDomain
        if (alamatSekarang.isNotBlank()) sesuaikanPilihanDomain(alamatSekarang)
        spinnerSiap = siapSebelum
    }

    private fun sesuaikanPilihanDomain(alamat: String) {
        val idx = domainDaftar.indexOf(alamat.substringAfter("@", ""))
        if (idx >= 0 && idx != spinnerDomain.selectedItemPosition) {
            spinnerDomain.setSelection(idx)
        }
    }

    // Ganti layanan aktif: simpan pilihan, sesuaikan tampilan, lalu
    // pakai alamat sesi yang masih hidup atau buat alamat baru.
    private fun gantiLayanan(p: MailProvider) {
        // Putuskan pasangan provider+alamat LAMA lebih dulu (issue #46):
        // naikkan generasi alamat (hasil refresh yang sedang terbang
        // gugur) dan kosongkan alamat aktif, supaya tidak ada polling
        // yang memasangkan provider baru dengan alamat lama — inbox dan
        // OTP layanan baru tidak boleh tampil/ tersalin di bawah alamat
        // lama.
        generasiAlamat++
        alamatSekarang = ""
        // Pembuatan alamat layanan lama yang masih terbang ditinggalkan:
        // penanda dilepas dan tokennya digugurkan (issue #64).
        tokenBuat++
        sedangBuat = false
        synchronized(idSudahDicek) { idSudahDicek.clear() }
        synchronized(gagalAmbilIsi) { gagalAmbilIsi.clear() }
        sapuAwalSelesai = false
        itemInbox = emptyList()
        dataInbox.clear()
        adapterInbox.notifyDataSetChanged()
        tvKosong.visibility = View.VISIBLE
        sembunyikanKode()
        providerAktif = p
        generasiBuat++
        getSharedPreferences("tempmail", Context.MODE_PRIVATE)
            .edit().putString("layanan", p.id).apply()
        terapkanLayananKeTampilan()
        val dariSesi = p.alamatAktif()
        val tersimpan = if (p.tanpaSesi) {
            getSharedPreferences("tempmail", Context.MODE_PRIVATE).getString("email_" + p.id, null)
        } else null
        when {
            dariSesi != null -> setEmail(dariSesi)
            !tersimpan.isNullOrBlank() -> setEmail(tersimpan)
            p.tanpaSesi -> setEmail(p.alamatAcak(null))
            else -> {
                tvEmail.text = "Menyiapkan alamat…"
                buatAlamatBaru(acak = true, pesanSalin = null)
            }
        }
    }

    // Penjaga pembuatan alamat yang sedang berjalan (issue #64):
    // ketukan beruntun tidak menumpuk panggilan jaringan; token
    // memastikan callback lama tidak melepaskan penanda milik
    // permintaan yang lebih baru.
    private var sedangBuat = false
    private var tokenBuat = 0

    // Buat alamat (acak/custom) di thread latar — layanan berbasis sesi
    // butuh jaringan. Hasil basi (sudah ada permintaan lebih baru atau
    // layanan berganti) dibuang.
    private fun buatAlamatBaru(acak: Boolean, namaBersih: String? = null, pesanSalin: String?) {
        if (sedangBuat) return
        sedangBuat = true
        val token = ++tokenBuat
        val p = providerAktif
        val gen = ++generasiBuat
        val domain = domainDaftar.getOrNull(spinnerDomain.selectedItemPosition)
        try {
            executorAksi.execute {
                try {
                    val baru = if (acak) {
                        p.alamatAcak(domain)
                    } else {
                        val d = domain ?: p.domains().firstOrNull()
                        ?: throw IllegalStateException("Daftar domain kosong")
                        p.alamatCustom(namaBersih ?: "", d)
                    }
                    runOnUiThread {
                        if (token == tokenBuat) sedangBuat = false
                        if (gen != generasiBuat || p !== providerAktif) return@runOnUiThread
                        setEmail(baru)
                        // Rotasi langsung menyalin alamat baru — tidak
                        // perlu tombol Salin lagi.
                        if (pesanSalin != null) salin(baru, pesanSalin)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        if (token == tokenBuat) sedangBuat = false
                        if (gen != generasiBuat || p !== providerAktif) return@runOnUiThread
                        if (alamatSekarang.isBlank()) tvEmail.text = "(gagal menyiapkan alamat)"
                        toast("Gagal membuat alamat: ${e.message ?: "jaringan bermasalah"}")
                    }
                }
            }
        } catch (e: RejectedExecutionException) {
            // Activity sedang dihancurkan — abaikan dengan aman.
            if (token == tokenBuat) sedangBuat = false
        }
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
        executorAksi.shutdownNow()
        super.onDestroy()
    }

    // Ganti alamat aktif: simpan, kosongkan tampilan, langsung cek inbox.
    private fun setEmail(alamat: String) {
        generasiAlamat++
        alamatSekarang = alamat
        tvEmail.text = alamat
        // Simpanan per layanan; kunci lama "email" tetap ditulis untuk
        // layanan asal supaya tidak ada yang kehilangan alamatnya.
        val editor = getSharedPreferences("tempmail", Context.MODE_PRIVATE).edit()
            .putString("email_" + providerAktif.id, alamat)
        if (providerAktif.tanpaSesi) editor.putString("email", alamat)
        editor.apply()
        synchronized(idSudahDicek) { idSudahDicek.clear() }
        synchronized(gagalAmbilIsi) { gagalAmbilIsi.clear() }
        sapuAwalSelesai = false
        itemInbox = emptyList()
        dataInbox.clear()
        adapterInbox.notifyDataSetChanged()
        tvKosong.visibility = View.VISIBLE
        sembunyikanKode()
        // Selaraskan spinner domain dengan alamat yang baru dipasang,
        // dengan penjaga yang sama seperti muatDomain — alur ganti
        // layanan dulu melewatkan ini (issue #63).
        val siapSebelum = spinnerSiap
        spinnerSiap = false
        sesuaikanPilihanDomain(alamat)
        spinnerSiap = siapSebelum
        refreshInbox()
    }

    // Ambil daftar inbox dari API di thread latar, lalu perbarui tampilan.
    private fun refreshInbox() {
        // Tanpa alamat aktif tidak ada yang dicek — memanggil provider
        // dengan alamat kosong hanya membuat sesi yatim di sisi
        // provider yang tidak pernah ditampilkan (issue #49).
        if (alamatSekarang.isBlank()) return
        if (!sedangCek.compareAndSet(false, true)) return
        val email = alamatSekarang
        val gen = generasiAlamat
        val p = providerAktif
        try {
            executor.execute { jalankanRefresh(email, gen, p) }
        } catch (e: RejectedExecutionException) {
            // Activity sedang dihancurkan — jangan crash, bebaskan penanda.
            sedangCek.set(false)
        }
    }

    private fun jalankanRefresh(email: String, gen: Int, p: MailProvider) {
            try {
                val items = p.fetchInbox(email)
                val bolehSalin = sapuAwalSelesai
                // Fase 1: tampilkan daftar SEGERA — jangan menunggu pemindaian
                // isi email yang bisa lama (issue #25).
                runOnUiThread {
                    if (email != alamatSekarang || p !== providerAktif) return@runOnUiThread
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
                }
                // Fase 2: pindai kode dari email yang belum tuntas dicek
                // (issue #2), paling banyak 5 pengambilan isi per putaran —
                // sisanya putaran berikutnya (issue #25).
                var kodeBaru: String? = null
                var ambilIsi = 0
                var sisaBelum = false
                // Kunci email tanpa id (gabungan isi, issue #30/#34) diberi
                // nomor urut yang dihitung dari EKOR daftar: email baru
                // menumpuk di kepala, jadi kunci email lama tidak bergeser
                // dan tidak diperiksa ulang (issue #35).
                val kunciPerIndeks = HashMap<Int, String>()
                val hitungEkor = HashMap<String, Int>()
                for (i in items.indices.reversed()) {
                    val m = items[i]
                    if (m.id.isBlank()) {
                        val dasar = "tanpa-id:" + m.subject + "|" + m.from + "|" + m.time
                        val urut = hitungEkor[dasar] ?: 0
                        hitungEkor[dasar] = urut + 1
                        kunciPerIndeks[i] = "$dasar#$urut"
                    }
                }
                for ((indeks, m) in items.withIndex()) {
                    if (gen != generasiAlamat) break
                    val kunci = if (m.id.isNotBlank()) m.id else kunciPerIndeks[indeks]!!
                    if (kunci in idSudahDicek) continue
                    var kode = OtpExtractor.extract(m.subject)
                    var tuntas = kode != null
                    if (kode == null) {
                        if (ambilIsi >= 5) {
                            sisaBelum = true
                            continue
                        }
                        ambilIsi++
                        // Gagal ambil isi email tidak boleh menggagalkan daftar inbox,
                        // tapi emailnya juga BELUM tuntas dicek: jangan ditandai,
                        // supaya dicoba lagi pada polling berikutnya (issue #1).
                        try {
                            val detail = p.fetchDetail(email, m)
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
                    if (!tuntas) sisaBelum = true
                }
                // Sapuan awal dinyatakan selesai hanya bila tak ada sisa.
                if (!sisaBelum && gen == generasiAlamat) sapuAwalSelesai = true
                val kodeFinal = kodeBaru
                runOnUiThread {
                    sedangCek.set(false)
                    if (email != alamatSekarang || p !== providerAktif) return@runOnUiThread
                    // Server melaporkan alamat lain (mis. alamat lama
                    // kedaluwarsa dan diputar server): ikuti server.
                    val alamatServer = p.alamatServer()
                    if (alamatServer != null && alamatServer != alamatSekarang) {
                        setEmail(alamatServer)
                        return@runOnUiThread
                    }
                    if (kodeFinal != null) {
                        tampilkanKode(kodeFinal)
                        if (bolehSalin) {
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
        val p = providerAktif
        toast("Membuka email…")
        try {
            executorDetail.execute { jalankanBukaDetail(email, item, p) }
        } catch (e: RejectedExecutionException) {
            // Activity sedang dihancurkan — abaikan dengan aman (issue #24).
            sedangBuka.set(false)
        }
    }

    private fun jalankanBukaDetail(email: String, item: MailItem, p: MailProvider) {
            try {
                val detail = p.fetchDetail(email, item)
                val kode = OtpExtractor.extract(detail.subject + "\n" + detail.text)
                runOnUiThread {
                    sedangBuka.set(false)
                    // Activity bisa sudah berakhir/berputar selama isi email
                    // diambil — dialog pada Activity mati = crash (issue #3).
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    // Alamat/layanan sudah berganti selama pengambilan:
                    // dialog email lama tidak boleh muncul di atas alamat
                    // baru (issue #47).
                    if (email != alamatSekarang || p !== providerAktif) return@runOnUiThread
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
                    if (email == alamatSekarang && p === providerAktif) {
                        toast("Gagal membuka: ${e.message ?: "jaringan bermasalah"}")
                    }
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

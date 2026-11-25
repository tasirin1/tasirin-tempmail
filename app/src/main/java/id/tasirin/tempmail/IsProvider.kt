package id.tasirin.tempmail

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// Layanan tempmail.is — API JSON di api.tempamail.com (terverifikasi
// langsung 2026-11-25). Berbasis "klien": satu uuid klien memiliki
// beberapa alamat; alamat dipilih lewat selected_email_id.
// Bentuk respons terverifikasi:
//   POST /webapp/client/create {app_uuid}      -> {client:{uuid}, email:{id, address}}
//   POST /webapp/domains {uuid}                -> {domains:[{id, name}]}
//   POST /webapp/email/random {uuid}           -> {email:{id, address}}
//   POST /webapp/email/custom {uuid, alias, domain_id} -> {email:{id, address}}
//   POST /webapp/messages {uuid, selected_email_id, known_message_id}
//     -> {messages:[{id, from:"{name,address}", subject, body, created_at}]}
// Isi email sudah ikut di daftar pesan — tidak ada panggilan detail.
class IsProvider : MailProvider {

    override val id = "is"
    override val nama = "tempmail.is"
    override val tanpaSesi = false
    override val bisaCustom = true

    private val http = HttpSesi()

    // Kunci seluruh keadaan sesi: provider adalah singleton yang disentuh
    // thread polling, thread detail, dan thread aksi bersamaan. Tanpa
    // kunci, dua thread bisa membuat dua klien berbeda dan alamat tampil
    // tidak cocok dengan inbox yang dicek (issue #36).
    private val kunciSesi = Any()

    @Volatile
    private var uuidKlien: String = ""
    @Volatile
    private var idEmailAktif: Long = 0
    @Volatile
    private var alamatAktif: String = ""
    private var daftarDomain: List<Pair<Long, String>> = emptyList()

    // Isi email terakhir dari fetchInbox, menurut id pesan — fetchDetail
    // membaca dari sini karena API tidak punya endpoint detail.
    private val isiTerakhir = KotakIsi()

    // Keadaan delta kotak masuk (hanya disentuh sambil memegang
    // kunciSesi): id pesan terbesar yang sudah diterima + daftar pesan
    // gabungan. Server menghormati known_message_id (terverifikasi
    // langsung: id terbesar → hanya pesan yang lebih baru yang
    // dikirim), jadi polling tidak lagi mengunduh ulang isi semua
    // email tiap putaran (issue #39).
    private var idTerbesar: Long = 0
    private val daftarPesan = ArrayList<MailItem>()
    // Himpunan id yang sudah ada di daftarPesan — dedupe O(1); pola
    // none{} lama membuat penggabungan makin lambat kuadratik, dan
    // daftar hanya tumbuh tanpa batas (issue #86).
    private val idDikenal = HashSet<String>()

    private fun post(rute: String, kolom: List<Pair<String, String>>): JSONObject {
        val hasil = http.postForm("$BASE$rute", kolom)
        if (hasil.kode !in 200..299) throw IllegalStateException("tempmail.is: HTTP ${hasil.kode}")
        // Respons non-JSON (halaman perantara/gerbang) jangan bocor
        // sebagai JSONException mentah ke pengguna (issue #55).
        val json = try {
            JSONObject(hasil.body)
        } catch (e: Exception) {
            throw IllegalStateException("tempmail.is: respons bukan JSON (mungkin halaman perantara)")
        }
        if (json.has("error")) throw IllegalStateException("tempmail.is menolak: ${json.optString("error")}")
        return json
    }

    // Buat klien bila belum ada; klien pertama langsung membawa satu alamat.
    // Wajib dipanggil sambil memegang kunciSesi.
    private fun pastikanKlien() {
        if (uuidKlien.isNotBlank()) return
        val json = post("/client/create", listOf("app_uuid" to APP_UUID))
        // Uraikan SEMUA kolom dulu, tetapkan keadaan sesudahnya: respons
        // sukses-sebagian tidak boleh meninggalkan uuid terisi dengan
        // id email 0 dan alamat kosong permanen (issue #38).
        // Kolom wajib yang hilang/rusak jangan bocor sebagai
        // JSONException mentah (issue #88).
        val uuidBaru: String
        val emailJson: JSONObject
        try {
            uuidBaru = json.getJSONObject("client").getString("uuid")
            emailJson = json.getJSONObject("email")
        } catch (e: Exception) {
            throw IllegalStateException("tempmail.is: respons tidak lengkap dari server")
        }
        catatEmail(emailJson)
        uuidKlien = uuidBaru
    }

    private fun catatEmail(email: JSONObject): String {
        val idBaru = try {
            email.getLong("id")
        } catch (e: Exception) {
            throw IllegalStateException("tempmail.is: respons tidak lengkap dari server")
        }
        val alamatBaru = try {
            email.getString("address")
        } catch (e: Exception) {
            throw IllegalStateException("tempmail.is: respons tidak lengkap dari server")
        }
        idEmailAktif = idBaru
        alamatAktif = alamatBaru
        // Alamat yang dicatat lewat jalur biasa (pembuatan/ganti oleh
        // pengguna atau pembuatan klien) membatalkan laporan pemulihan
        // lama: tanpa ini alamatDariServer basi menarik UI kembali ke
        // alamat hasil pemulihan setiap polling (issue #107).
        alamatDariServer = null
        // Alamat berganti = kotak masuk berganti: delta mulai dari nol.
        idTerbesar = 0
        daftarPesan.clear()
        idDikenal.clear()
        isiTerakhir.kosongkan()
        return alamatAktif
    }

    // Ambil objek email dari respons dengan pesan yang bisa dipahami —
    // sisa jalur JSONException mentah dari sapuan #88 (issue #110).
    private fun emailDari(json: JSONObject): JSONObject {
        return try {
            json.getJSONObject("email")
        } catch (e: Exception) {
            throw IllegalStateException("tempmail.is: respons tidak lengkap dari server")
        }
    }

    override fun domains(): List<String> = synchronized(kunciSesi) {
        pastikanKlien()
        if (daftarDomain.isEmpty()) {
            val json = post("/domains", listOf("uuid" to uuidKlien))
            val arr = json.optJSONArray("domains")
            val hasil = ArrayList<Pair<Long, String>>()
            if (arr != null) {
                try {
                    for (i in 0 until arr.length()) {
                        val d = arr.getJSONObject(i)
                        hasil.add(d.getLong("id") to d.getString("name"))
                    }
                } catch (e: Exception) {
                    throw IllegalStateException("tempmail.is: respons tidak lengkap dari server")
                }
            }
            daftarDomain = hasil
        }
        daftarDomain.map { it.second }
    }

    override fun alamatAcak(domain: String?): String = synchronized(kunciSesi) {
        // Domain acak ditentukan server; pilihan domain pengguna hanya
        // berlaku untuk alamat custom di layanan ini.
        if (uuidKlien.isBlank()) {
            pastikanKlien()
            alamatAktif
        } else {
            val json = post("/email/random", listOf("uuid" to uuidKlien))
            catatEmail(emailDari(json))
        }
    }

    override fun alamatCustom(namaBersih: String, domain: String): String = synchronized(kunciSesi) {
        pastikanKlien()
        if (daftarDomain.isEmpty()) domains()
        val domainId = daftarDomain.firstOrNull { it.second == domain }?.first
            ?: daftarDomain.firstOrNull()?.first
            ?: throw IllegalStateException("tempmail.is: daftar domain kosong")
        val json = post(
            "/email/custom",
            listOf("uuid" to uuidKlien, "alias" to namaBersih, "domain_id" to domainId.toString())
        )
        catatEmail(emailDari(json))
    }

    // Alamat hasil pemulihan klien (issue #82): dilaporkan ke UI lewat
    // alamatServer() supaya layar tidak terus menampilkan alamat mati.
    @Volatile
    private var alamatDariServer: String? = null

    // Buang seluruh keadaan klien supaya klien baru bisa dibuat. Tanpa
    // jalur ini, uuid yang ditolak server membuat layanan mati total
    // sampai proses aplikasi dibunuh (issue #82). Wajib dipanggil
    // sambil memegang kunciSesi.
    private fun resetKlien() {
        uuidKlien = ""
        idEmailAktif = 0
        alamatAktif = ""
        alamatDariServer = null
        idTerbesar = 0
        daftarPesan.clear()
        idDikenal.clear()
        isiTerakhir.kosongkan()
    }

    override fun fetchInbox(alamat: String): List<MailItem> = synchronized(kunciSesi) {
        // Parameter alamat DULU diabaikan total (issue #44): hasil selalu
        // inbox alamat aktif provider. Kini panggilan untuk alamat yang
        // sudah ditinggalkan dijawab kosong — pemanggil tidak mencampur
        // hasil alamat baru ke alamat lama. Alamat kosong (awal mulai)
        // dan keadaan belum ada alamat aktif tetap dilayani.
        if (alamat.isNotBlank() && alamatAktif.isNotBlank() && alamat != alamatAktif) {
            emptyList()
        } else {
            // Satu kali pemulihan klien: penolakan HTTP dari server bisa
            // berarti uuid klien tidak sah lagi → buang klien, buat baru,
            // coba sekali lagi, dan laporkan alamat barunya ke UI
            // (issue #82; pola yang sama dengan pemulihan #80).
            // Kegagalan jaringan (bukan IllegalStateException HTTP)
            // dilempar apa adanya tanpa merusak klien.
            try {
                ambilInboxSekali(alamat, dariPemulihan = false)
            } catch (e: IllegalStateException) {
                // Pemicu dipersempit seperti NowProvider (issue #108):
                // hanya penolakan sesi 401/403/404 yang berarti uuid
                // mati — 5xx/429 adalah server ngambek sesaat dan
                // TIDAK boleh membuang alamat yang sehat.
                val kodeHttp = Regex("HTTP (\\d+)").find(e.message ?: "")
                    ?.groupValues?.get(1)?.toIntOrNull()
                if (kodeHttp == 401 || kodeHttp == 403 || kodeHttp == 404) {
                    resetKlien()
                    try {
                        val hasilBaru = ambilInboxSekali(alamat, dariPemulihan = true)
                        if (alamatAktif.isNotBlank() && alamatAktif != alamat) {
                            alamatDariServer = alamatAktif
                        }
                        hasilBaru
                    } catch (e2: Exception) {
                        // Pemulihan ikut gagal (issue #106): klien baru
                        // mungkin sudah terbentuk — laporkan alamatnya
                        // sebelum melempar, sama seperti NowProvider.
                        if (alamatAktif.isNotBlank() && alamatAktif != alamat) {
                            alamatDariServer = alamatAktif
                        }
                        throw e2
                    }
                } else {
                    throw e
                }
            }
        }
    }

    // Wajib dipanggil sambil memegang kunciSesi.
    private fun ambilInboxSekali(alamatDiminta: String, dariPemulihan: Boolean): List<MailItem> {
        pastikanKlien()
        // Penjaga alamat basi, babak kedua (issue #91): penjaga di
        // fetchInbox lolos bila alamatAktif tadi masih kosong — sesi
        // baru yang baru saja dibuat bisa beralamat BERBEDA dari yang
        // diminta, dan inbox-nya tidak boleh diserahkan untuk label
        // alamat lama. Jalur pemulihan dikecualikan: alamat barunya
        // memang dilaporkan lewat alamatDariServer.
        if (!dariPemulihan && alamatDiminta.isNotBlank() && alamatAktif.isNotBlank() &&
            alamatAktif != alamatDiminta
        ) {
            return emptyList()
        }
        val json = post(
            "/messages",
            listOf(
                "uuid" to uuidKlien,
                "selected_email_id" to idEmailAktif.toString(),
                "known_message_id" to idTerbesar.toString()
            )
        )
        val arr = json.optJSONArray("messages")
        // Uraikan SELURUH batch ke penampung lokal dulu; keadaan bersama
        // (kursor delta, daftar, cache isi) hanya diubah sesudah seluruh
        // batch berhasil diuraikan. Pola lama memajukan kursor di tengah
        // loop: satu elemen rusak membuat pesan-pesan sebelumnya sudah
        // melewati kursor tapi tidak pernah masuk daftar — hilang
        // permanen (issue #83).
        val baru = ArrayList<MailItem>()
        val isiBaru = ArrayList<Pair<String, MailDetail>>()
        var kursorBaru = idTerbesar
        val dikenalBatch = HashSet(idDikenal)
        for (i in 0 until (arr?.length() ?: 0)) {
            val m = arr!!.getJSONObject(i)
            // Kolom from berbentuk string JSON: {"name":..., "address":...}
            var dari = m.optString("from")
            try {
                val f = JSONObject(dari)
                val nm = f.optString("name")
                val ad = f.optString("address")
                dari = if (nm.isNotBlank() && nm != "null") "$nm <$ad>" else ad
            } catch (e: Exception) {
                // Biarkan apa adanya bila bukan JSON.
            }
            val subjek = m.optString("subject")
            val waktu = waktuLokal(m.optString("created_at"))
            // Pesan tanpa id TIDAK boleh runtuh ke id "0" bersama
            // (dedupe + cache isi bertabrakan) — pakai kunci pengganti
            // dari sidik isi, pola #42 di NowProvider (issue #111).
            // Kursor delta hanya maju untuk id numerik sungguhan.
            val idNum = m.optLong("id")
            val idPesan = if (m.isNull("id")) {
                "tanpa-id-" + (dari + "|" + subjek + "|" + m.optString("body")).hashCode()
            } else {
                idNum.toString()
            }
            if (idNum > kursorBaru) kursorBaru = idNum
            if (dikenalBatch.add(idPesan)) {
                baru.add(MailItem(id = idPesan, from = dari, subject = subjek, time = waktu))
            }
            isiBaru.add(
                idPesan to MailDetail(
                    from = dari,
                    subject = subjek,
                    date = waktu,
                    text = m.optString("body")
                )
            )
        }
        // Batch utuh teruraikan — terapkan keadaan bersama sekarang.
        idTerbesar = kursorBaru
        idDikenal.clear()
        idDikenal.addAll(dikenalBatch)
        for ((idPesan, detail) in isiBaru) isiTerakhir.taruh(idPesan, detail)
        // Pesan baru dari server selalu lebih baru dari yang sudah ada.
        daftarPesan.addAll(0, baru)
        // Pangkas ekor (terlama): daftar delta tidak boleh tumbuh tanpa
        // batas selama alamat hidup (issue #86). Pesan terpangkas tidak
        // diminta ulang (kursor sudah lewat) — batas 100 jauh di atas
        // pemakaian email sekali pakai yang wajar.
        while (daftarPesan.size > BATAS_DAFTAR) {
            val terbuang = daftarPesan.removeAt(daftarPesan.lastIndex)
            idDikenal.remove(terbuang.id)
            isiTerakhir.buang(terbuang.id)
        }
        return ArrayList(daftarPesan)
    }

    override fun alamatServer(): String? = alamatDariServer

    override fun alamatAktif(): String? = alamatAktif.ifBlank { null }

    override fun fetchDetail(alamat: String, item: MailItem): MailDetail {
        if (alamat.isNotBlank() && alamatAktif.isNotBlank() && alamat != alamatAktif) {
            throw IllegalStateException("Alamat sudah berganti")
        }
        isiTerakhir[item.id]?.let { return it }
        // Cadangan: ambil ulang daftar lalu cari id-nya.
        fetchInbox(alamat)
        return isiTerakhir[item.id]
            ?: throw IllegalStateException("Email tidak ditemukan (mungkin sudah terhapus)")
    }

    // created_at dari server adalah UTC (akhiran Z) — tampilkan dalam
    // zona waktu perangkat pengguna, bukan UTC mentah (issue #54).
    // Gagal urai: kembalikan potongan mentah seperti perilaku lama.
    private fun waktuLokal(mentah: String): String {
        return try {
            val masuk = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            masuk.timeZone = TimeZone.getTimeZone("UTC")
            val tanggal = masuk.parse(mentah.take(19)) ?: return mentah.replace("T", " ").take(16)
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(tanggal)
        } catch (e: Exception) {
            mentah.replace("T", " ").take(16)
        }
    }

    companion object {
        // Batas panjang daftar gabungan delta (issue #86).
        private const val BATAS_DAFTAR = 100
        private const val BASE = "https://api.tempamail.com/webapp"
        // UUID aplikasi web tempmail.is — terbaca dari konfigurasi publik
        // situsnya sendiri, sama seperti yang dipakai peramban pengunjung.
        private const val APP_UUID = "b7m-kt9d-ze4p"
    }
}

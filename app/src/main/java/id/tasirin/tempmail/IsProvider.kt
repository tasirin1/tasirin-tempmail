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
        val uuidBaru = json.getJSONObject("client").getString("uuid")
        catatEmail(json.getJSONObject("email"))
        uuidKlien = uuidBaru
    }

    private fun catatEmail(email: JSONObject): String {
        val idBaru = email.getLong("id")
        val alamatBaru = email.getString("address")
        idEmailAktif = idBaru
        alamatAktif = alamatBaru
        // Alamat berganti = kotak masuk berganti: delta mulai dari nol.
        idTerbesar = 0
        daftarPesan.clear()
        isiTerakhir.kosongkan()
        return alamatAktif
    }

    override fun domains(): List<String> = synchronized(kunciSesi) {
        pastikanKlien()
        if (daftarDomain.isEmpty()) {
            val json = post("/domains", listOf("uuid" to uuidKlien))
            val arr = json.optJSONArray("domains")
            val hasil = ArrayList<Pair<Long, String>>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val d = arr.getJSONObject(i)
                    hasil.add(d.getLong("id") to d.getString("name"))
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
            catatEmail(json.getJSONObject("email"))
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
        catatEmail(json.getJSONObject("email"))
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
        idTerbesar = 0
        daftarPesan.clear()
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
                ambilInboxSekali()
            } catch (e: IllegalStateException) {
                if (e.message?.contains("HTTP ") == true) {
                    resetKlien()
                    val hasilBaru = ambilInboxSekali()
                    if (alamatAktif.isNotBlank() && alamatAktif != alamat) {
                        alamatDariServer = alamatAktif
                    }
                    hasilBaru
                } else {
                    throw e
                }
            }
        }
    }

    // Wajib dipanggil sambil memegang kunciSesi.
    private fun ambilInboxSekali(): List<MailItem> {
        pastikanKlien()
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
        for (i in 0 until (arr?.length() ?: 0)) {
            val m = arr!!.getJSONObject(i)
            val idPesan = m.optLong("id").toString()
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
            val idNum = m.optLong("id")
            if (idNum > kursorBaru) kursorBaru = idNum
            if (daftarPesan.none { it.id == idPesan } && baru.none { it.id == idPesan }) {
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
        for ((idPesan, detail) in isiBaru) isiTerakhir.taruh(idPesan, detail)
        // Pesan baru dari server selalu lebih baru dari yang sudah ada.
        daftarPesan.addAll(0, baru)
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
        private const val BASE = "https://api.tempamail.com/webapp"
        // UUID aplikasi web tempmail.is — terbaca dari konfigurasi publik
        // situsnya sendiri, sama seperti yang dipakai peramban pengunjung.
        private const val APP_UUID = "b7m-kt9d-ze4p"
    }
}

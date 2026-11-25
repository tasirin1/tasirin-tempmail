package id.tasirin.tempmail

import org.json.JSONObject

// Layanan tempmail.now — API JSON berbasis sesi kuki (terverifikasi
// langsung 2026-11-25):
//   GET  /api/temp_email  -> {email, csrf_token, duration_minutes, remaining_time}
//   GET  /fetch_emails    -> {emails:[{id, from_address, subject, body}], temp_email, remaining_time}
//   POST /change_email (header X-CSRF-Token) -> {new_email}
// Alamat berumur ±30 menit; sesudah kedaluwarsa server memutar alamat
// baru dan melaporkannya lewat kolom temp_email di /fetch_emails.
// Isi email ikut di daftar — tidak ada panggilan detail terpisah.
// Nama sendiri tidak didukung layanan ini.
class NowProvider : MailProvider {

    override val id = "now"
    override val nama = "tempmail.now"
    override val tanpaSesi = false
    override val bisaCustom = false

    private val http = HttpSesi()

    // Kunci keadaan sesi (singleton disentuh banyak thread).
    private val kunciSesi = Any()

    @Volatile
    private var csrf: String = ""
    @Volatile
    private var alamatAktif: String = ""
    // Ditulis thread latar, dibaca UI thread lewat alamatServer() —
    // wajib @Volatile seperti field sesi lain (issue #41).
    @Volatile
    private var alamatDariServer: String? = null
    // Cache isi bersama (KotakIsi): diganti atomik per ambil inbox,
    // bukan clear()+isi ulang (issue #43).
    private val isiTerakhir = KotakIsi()

    // Urai JSON dengan pesan yang bisa dipahami pengguna — respons
    // non-JSON (halaman perantara) tidak boleh bocor sebagai
    // JSONException mentah (issue #55).
    private fun uraiJson(body: String): JSONObject {
        return try {
            JSONObject(body)
        } catch (e: Exception) {
            throw IllegalStateException("tempmail.now: respons bukan JSON (mungkin halaman perantara)")
        }
    }

    // Wajib dipanggil sambil memegang kunciSesi.
    private fun pastikanSesi() {
        if (alamatAktif.isNotBlank()) return
        val hasil = http.get("$BASE/api/temp_email")
        if (hasil.kode !in 200..299) throw IllegalStateException("tempmail.now: HTTP ${hasil.kode}")
        val json = uraiJson(hasil.body)
        // Kolom wajib yang hilang jangan bocor sebagai JSONException
        // mentah (issue #88).
        alamatAktif = try {
            json.getString("email")
        } catch (e: Exception) {
            throw IllegalStateException("tempmail.now: respons tidak lengkap dari server")
        }
        csrf = json.optString("csrf_token")
    }

    // Buang sesi mati (kuki + keadaan) supaya sesi baru bisa dibuat.
    // Tanpa jalur pulih ini, sesi yang dibatalkan server membuat
    // provider macet permanen sampai aplikasi dipaksa berhenti
    // (issue #37). Wajib dipanggil sambil memegang kunciSesi.
    private fun resetSesi() {
        http.lupaKuki()
        alamatAktif = ""
        csrf = ""
        alamatDariServer = null
        isiTerakhir.kosongkan()
    }

    override fun domains(): List<String> = emptyList()

    override fun alamatAcak(domain: String?): String = synchronized(kunciSesi) {
        if (alamatAktif.isBlank()) {
            pastikanSesi()
            alamatAktif
        } else {
            // Sudah punya sesi: ganti alamat lewat endpoint resmi situsnya.
            var hasil = http.postForm(
                "$BASE/change_email",
                emptyList(),
                mapOf("X-CSRF-Token" to csrf)
            )
            if (hasil.kode == 403) {
                // Token CSRF mungkin sudah diputar server (issue #56):
                // segarkan dari endpoint sesi lalu coba sekali lagi.
                val segar = http.get("$BASE/api/temp_email")
                if (segar.kode in 200..299) {
                    val jsonSegar = uraiJson(segar.body)
                    csrf = jsonSegar.optString("csrf_token", csrf)
                    val emailSegar = jsonSegar.optString("email")
                    if (emailSegar.isNotBlank() && emailSegar != alamatAktif) {
                        // Sesi ternyata sudah berganti alamat di server.
                        alamatAktif = emailSegar
                        alamatDariServer = emailSegar
                        isiTerakhir.kosongkan()
                        return@synchronized alamatAktif
                    }
                    hasil = http.postForm(
                        "$BASE/change_email",
                        emptyList(),
                        mapOf("X-CSRF-Token" to csrf)
                    )
                }
            }
            if (hasil.kode in 200..299) {
                val json = uraiJson(hasil.body)
                alamatAktif = try {
                    json.getString("new_email")
                } catch (e: Exception) {
                    throw IllegalStateException("tempmail.now: respons tidak lengkap dari server")
                }
                alamatDariServer = null
                // Alamat berganti = cache isi alamat lama dibuang,
                // samakan dengan IsProvider.catatEmail — tanpa ini
                // fetchDetail bisa menyajikan isi alamat lama lewat id
                // yang bertabrakan di jendela sebelum polling (issue #92).
                isiTerakhir.kosongkan()
                alamatAktif
            } else {
                // Sesi kemungkinan mati di server: buang dan mulai sesi
                // baru — alamat sesi baru adalah alamat acak yang sah.
                resetSesi()
                pastikanSesi()
                alamatAktif
            }
        }
    }

    override fun alamatCustom(namaBersih: String, domain: String): String {
        throw IllegalStateException("tempmail.now tidak mendukung nama sendiri")
    }

    override fun fetchInbox(alamat: String): List<MailItem> = synchronized(kunciSesi) {
        // Panggilan untuk alamat yang sudah ditinggalkan dijawab kosong
        // (issue #44) — jangan sentuh sesi untuk alamat basi.
        if (alamat.isNotBlank() && alamatAktif.isNotBlank() && alamat != alamatAktif) {
            return@synchronized emptyList()
        }
        // Satu kali pemulihan sesi — HANYA untuk penolakan sesi oleh
        // server (HTTP 401/403/404). Perbaikan #37 semula menangkap
        // SEMUA kegagalan sebagai sesi mati, sehingga satu timeout
        // jaringan biasa membuang alamat yang sehat, dan alamat
        // penggantinya tidak pernah dilaporkan ke UI — inbox mati
        // permanen diam-diam (issue #80). Kegagalan jaringan, HTTP
        // lain, dan respons rusak dilempar apa adanya TANPA merusak
        // sesi: pemanggil menampilkan error dan mencoba lagi nanti.
        try {
            ambilInboxSekali(alamat, dariPemulihan = false)
        } catch (e: IllegalStateException) {
            val kode = Regex("HTTP (\\d+)").find(e.message ?: "")
                ?.groupValues?.get(1)?.toIntOrNull()
            if (kode == 401 || kode == 403 || kode == 404) {
                resetSesi()
                try {
                    val hasilBaru = ambilInboxSekali(alamat, dariPemulihan = true)
                    // Alamat sesi baru WAJIB dilaporkan: tanpa ini UI tetap
                    // menampilkan alamat lama yang mati dan polling
                    // berikutnya kena penjaga alamat basi selamanya (#80).
                    if (alamatAktif.isNotBlank() && alamatAktif != alamat) {
                        alamatDariServer = alamatAktif
                    }
                    hasilBaru
                } catch (e2: Exception) {
                    // Pemulihan ikut GAGAL (issue #106): sesi baru mungkin
                    // sudah terbentuk di tengah jalan (alamatAktif sudah
                    // alamat baru) — laporkan penyimpangannya sebelum
                    // melempar, supaya UI menyelaraskan alamat pada
                    // polling sukses berikutnya. Tanpa ini, penjaga
                    // alamat basi menjawab kosong-sukses selamanya.
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

    // Wajib dipanggil sambil memegang kunciSesi.
    private fun ambilInboxSekali(alamatDiminta: String, dariPemulihan: Boolean): List<MailItem> {
        pastikanSesi()
        // Penjaga alamat basi, babak kedua (issue #91): sama seperti di
        // IsProvider — sesi baru yang dibuat untuk panggilan berlabel
        // alamat lama tidak boleh menyerahkan inbox alamat barunya.
        if (!dariPemulihan && alamatDiminta.isNotBlank() && alamatAktif.isNotBlank() &&
            alamatAktif != alamatDiminta
        ) {
            return emptyList()
        }
        val hasil = http.get("$BASE/fetch_emails")
        if (hasil.kode !in 200..299) throw IllegalStateException("tempmail.now: HTTP ${hasil.kode}")
        val json = uraiJson(hasil.body)
        // Server adalah sumber kebenaran alamat (kedaluwarsa = diputar).
        val dilaporkan = json.optString("temp_email")
        if (dilaporkan.isNotBlank() && dilaporkan != alamatAktif) {
            alamatAktif = dilaporkan
            alamatDariServer = dilaporkan
        }
        val arr = json.optJSONArray("emails")
        val hasilList = ArrayList<MailItem>()
        val isiBaru = HashMap<String, MailDetail>()
        for (i in 0 until (arr?.length() ?: 0)) {
            val m = arr!!.getJSONObject(i)
            val dari = m.optString("from_address")
            val subjek = m.optString("subject")
            // Item tanpa id TIDAK boleh berbagi kunci cache yang sama
            // (isi email bisa tertukar) — pakai kunci pengganti dari
            // sidik isi email itu sendiri (issue #42).
            val idPesan = if (m.isNull("id")) {
                "tanpa-id-" + (dari + "|" + subjek + "|" + m.optString("body")).hashCode()
            } else {
                m.opt("id").toString()
            }
            val waktu = m.optString("date").ifBlank { m.optString("received_at") }
            hasilList.add(MailItem(id = idPesan, from = dari, subject = subjek, time = waktu))
            isiBaru[idPesan] = MailDetail(
                from = dari,
                subject = subjek,
                date = waktu,
                text = TempMailApi.htmlToText(m.optString("body"))
            )
        }
        isiTerakhir.gantiSemua(isiBaru)
        return hasilList
    }


    override fun fetchDetail(alamat: String, item: MailItem): MailDetail {
        if (alamat.isNotBlank() && alamatAktif.isNotBlank() && alamat != alamatAktif) {
            throw IllegalStateException("Alamat sudah berganti")
        }
        isiTerakhir[item.id]?.let { return it }
        fetchInbox(alamat)
        return isiTerakhir[item.id]
            ?: throw IllegalStateException("Email tidak ditemukan (mungkin sudah terhapus)")
    }

    override fun alamatServer(): String? = alamatDariServer

    override fun alamatAktif(): String? = alamatAktif.ifBlank { null }

    companion object {
        private const val BASE = "https://tempmail.now"
    }
}

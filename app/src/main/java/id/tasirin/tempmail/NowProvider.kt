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
    @Volatile
    private var csrf: String = ""
    @Volatile
    private var alamatAktif: String = ""
    private var alamatDariServer: String? = null
    // Disentuh thread polling dan thread detail — wajib tersinkron.
    private val isiTerakhir = java.util.Collections.synchronizedMap(HashMap<String, MailDetail>())

    private fun pastikanSesi() {
        if (alamatAktif.isNotBlank()) return
        val hasil = http.get("$BASE/api/temp_email")
        if (hasil.kode !in 200..299) throw IllegalStateException("tempmail.now: HTTP ${hasil.kode}")
        val json = JSONObject(hasil.body)
        alamatAktif = json.getString("email")
        csrf = json.optString("csrf_token")
    }

    override fun domains(): List<String> = emptyList()

    override fun alamatAcak(domain: String?): String {
        if (alamatAktif.isBlank()) {
            pastikanSesi()
            return alamatAktif
        }
        // Sudah punya sesi: ganti alamat lewat endpoint resmi situsnya.
        val hasil = http.postForm(
            "$BASE/change_email",
            emptyList(),
            mapOf("X-CSRF-Token" to csrf)
        )
        if (hasil.kode !in 200..299) throw IllegalStateException("tempmail.now: HTTP ${hasil.kode}")
        val json = JSONObject(hasil.body)
        alamatAktif = json.getString("new_email")
        alamatDariServer = null
        return alamatAktif
    }

    override fun alamatCustom(namaBersih: String, domain: String): String {
        throw IllegalStateException("tempmail.now tidak mendukung nama sendiri")
    }

    override fun fetchInbox(alamat: String): List<MailItem> {
        pastikanSesi()
        val hasil = http.get("$BASE/fetch_emails")
        if (hasil.kode !in 200..299) throw IllegalStateException("tempmail.now: HTTP ${hasil.kode}")
        val json = JSONObject(hasil.body)
        // Server adalah sumber kebenaran alamat (kedaluwarsa = diputar).
        val dilaporkan = json.optString("temp_email")
        if (dilaporkan.isNotBlank() && dilaporkan != alamatAktif) {
            alamatAktif = dilaporkan
            alamatDariServer = dilaporkan
        }
        val arr = json.optJSONArray("emails") ?: return emptyList()
        val hasilList = ArrayList<MailItem>()
        isiTerakhir.clear()
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            val idPesan = if (m.isNull("id")) "" else m.opt("id").toString()
            val dari = m.optString("from_address")
            val subjek = m.optString("subject")
            val waktu = m.optString("date").ifBlank { m.optString("received_at") }
            hasilList.add(MailItem(id = idPesan, from = dari, subject = subjek, time = waktu))
            isiTerakhir[idPesan] = MailDetail(
                from = dari,
                subject = subjek,
                date = waktu,
                text = TempMailApi.htmlToText(m.optString("body"))
            )
        }
        return hasilList
    }

    override fun fetchDetail(alamat: String, item: MailItem): MailDetail {
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

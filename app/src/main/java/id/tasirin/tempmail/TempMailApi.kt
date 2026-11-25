package id.tasirin.tempmail

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Satu email di daftar kotak masuk.
data class MailItem(val id: String, val from: String, val subject: String, val time: String)

// Isi lengkap satu email.
data class MailDetail(val from: String, val subject: String, val date: String, val text: String)

// Klien API tempmail.plus. Semua fungsi blocking — panggil dari thread latar.
// Bentuk respons (terverifikasi dari klien API yang lolos uji):
//   GET /api/mails?email=...        -> {result, mail_list:[{mail_id, from_mail, subject, time}]}
//   GET /api/mails/{id}?email=...   -> {mail_id, from_mail, subject, date, text, html}
object TempMailApi {

    private const val BASE = "https://tempmail.plus"

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun getJson(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "TasirinTempMail/0.1.0 (Android)")
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> bacaTerbatas(r) }
            } ?: ""
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return JSONObject(body)
        } finally {
            // Koneksi wajib dilepas juga di jalur gagal (issue #9).
            conn.disconnect()
        }
    }

    // Batas ukuran body respons yang diterima (issue #14): aplikasi hanya
    // butuh daftar 20 email dan satu isi email, bukan berkas raksasa.
    private const val BATAS_BODY = 2_000_000

    private fun bacaTerbatas(r: BufferedReader): String {
        val sb = StringBuilder()
        val buf = CharArray(8192)
        while (true) {
            val n = r.read(buf)
            if (n < 0) break
            sb.append(buf, 0, n)
            if (sb.length > BATAS_BODY) {
                throw IllegalStateException("Respons server terlalu besar")
            }
        }
        return sb.toString()
    }

    // mail_id bisa berupa angka atau teks di JSON — samakan jadi String.
    private fun idString(v: Any?): String {
        return if (v == null || v == JSONObject.NULL) "" else v.toString()
    }

    fun fetchInbox(email: String): List<MailItem> {
        val json = getJson("$BASE/api/mails?email=${enc(email)}&epin=&limit=20")
        // Terverifikasi dari API langsung (2026-11-25): inbox kosong yang sah
        // menjawab result=true dengan mail_list kosong. Jadi result=false
        // adalah penolakan layanan — laporkan sebagai gagal, jangan ditelan
        // jadi "0 email" (issue #10).
        if (!json.optBoolean("result", false)) {
            throw IllegalStateException("tempmail.plus menolak permintaan ini")
        }
        val list = json.optJSONArray("mail_list") ?: return emptyList()
        val hasil = ArrayList<MailItem>()
        for (i in 0 until list.length()) {
            val m = list.optJSONObject(i) ?: continue
            hasil.add(
                MailItem(
                    id = idString(m.opt("mail_id")),
                    from = m.optString("from_mail"),
                    subject = m.optString("subject"),
                    time = m.optString("time")
                )
            )
        }
        return hasil
    }

    fun fetchDetail(email: String, mailId: String): MailDetail {
        val json = getJson("$BASE/api/mails/${enc(mailId)}?email=${enc(email)}&epin=")
        var text = json.optString("text")
        if (text.isBlank()) {
            val html = json.optString("html")
            if (html.isNotBlank()) text = htmlToText(html)
        }
        val from = json.optString("from_mail").ifBlank { json.optString("from") }
        return MailDetail(
            from = from,
            subject = json.optString("subject"),
            date = json.optString("date"),
            text = text
        )
    }

    // Pola untuk htmlToText — dikompilasi sekali, bukan tiap panggilan.
    private val reScript = Regex("(?is)<script[^>]*>.*?</script>")
    private val reStyle = Regex("(?is)<style[^>]*>.*?</style>")
    private val reBr = Regex("(?i)<br\\s*/?>")
    private val reTutupP = Regex("(?i)</p>")
    private val reTag = Regex("<[^>]+>")
    private val reEntitasAngka = Regex("&#(\\d+);")
    private val reEntitasHex = Regex("(?i)&#x([0-9a-f]+);")
    private val reSpasi = Regex("[ \\t]+")
    private val reBaris3 = Regex("\n{3,}")

    // Ubah HTML sederhana jadi teks polos: buang blok script/style beserta
    // isinya (issue #13), buang tag, rapikan entitas umum + entitas numerik.
    fun htmlToText(html: String): String {
        var t = html.replace(reScript, "").replace(reStyle, "")
        t = t.replace(reBr, "\n")
        t = t.replace(reTutupP, "\n\n")
        t = t.replace(reTag, "")
        t = t.replace(reEntitasAngka) { it.groupValues[1].toInt().toChar().toString() }
        t = t.replace(reEntitasHex) { it.groupValues[1].toInt(16).toChar().toString() }
        t = t.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        return t.replace(reSpasi, " ")
            .replace(reBaris3, "\n\n")
            .trim()
    }
}

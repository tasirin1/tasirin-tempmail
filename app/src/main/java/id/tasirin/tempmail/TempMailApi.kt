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
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "TasirinTempMail/0.1.0 (Android)")
        conn.setRequestProperty("Accept", "application/json")
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.let {
            BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
        } ?: ""
        conn.disconnect()
        if (code !in 200..299) throw IllegalStateException("HTTP $code")
        return JSONObject(body)
    }

    // mail_id bisa berupa angka atau teks di JSON — samakan jadi String.
    private fun idString(v: Any?): String {
        return if (v == null || v == JSONObject.NULL) "" else v.toString()
    }

    fun fetchInbox(email: String): List<MailItem> {
        val json = getJson("$BASE/api/mails?email=${enc(email)}&epin=&limit=20")
        if (!json.optBoolean("result", false)) return emptyList()
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

    // Ubah HTML sederhana jadi teks polos: buang tag, rapikan entitas umum.
    fun htmlToText(html: String): String {
        var t = html.replace(Regex("(?i)<br\\s*/?>"), "\n")
        t = t.replace(Regex("(?i)</p>"), "\n\n")
        t = t.replace(Regex("<[^>]+>"), "")
        t = t.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        return t.replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}

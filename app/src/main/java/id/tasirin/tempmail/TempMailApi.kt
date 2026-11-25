package id.tasirin.tempmail

import org.json.JSONObject
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

    // Encoder segmen path URL: URLEncoder adalah encoder query (spasi
    // jadi "+"), sedangkan di path "+" berarti plus harfiah — id
    // berkarakter khusus akan terkirim sebagai id berbeda (issue #74).
    private fun encPath(s: String): String = enc(s).replace("+", "%20")

    // SATU tumpukan HTTP untuk semua layanan (issue #52): dulu kelas ini
    // punya klien sendiri dengan UA identitas aplikasi polos dan batas
    // body terpisah. Kini lewat HttpSesi — kebijakan timeout, batas body
    // (2 MB, issue #14), dan tampang permintaan seragam di satu tempat.
    // Instance dibuat PER PANGGILAN: tempmail.plus tanpa sesi/kuki, jadi
    // tidak ada yang perlu dibagi — instance bersama membuat polling dan
    // detail saling menunggu di kunci permintaan HttpSesi sampai
    // timeout (issue #87). Tumpukan (kelas) tetap satu.
    private fun getJson(url: String): JSONObject {
        val hasil = HttpSesi().get(url)
        if (hasil.kode !in 200..299) throw IllegalStateException("HTTP ${hasil.kode}")
        return JSONObject(hasil.body)
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
        val json = getJson("$BASE/api/mails/${encPath(mailId)}?email=${enc(email)}&epin=")
        // Konsisten dengan fetchInbox (issue #10): penolakan layanan adalah
        // kegagalan yang jelas, bukan email kosong (issue #29).
        if (json.has("result") && !json.optBoolean("result", true)) {
            throw IllegalStateException("Email tidak tersedia (mungkin sudah terhapus)")
        }
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

    private fun titikKode(cp: Int?, asal: String): String {
        if (cp == null || cp < 0 || cp > 0x10FFFF || cp in 0xD800..0xDFFF) return asal
        return String(Character.toChars(cp))
    }

    // Ubah HTML sederhana jadi teks polos: buang blok script/style beserta
    // isinya (issue #13), buang tag, rapikan entitas umum + entitas numerik.
    fun htmlToText(html: String): String {
        var t = html.replace(reScript, "").replace(reStyle, "")
        t = t.replace(reBr, "\n")
        t = t.replace(reTutupP, "\n\n")
        t = t.replace(reTag, "")
        // Entitas numerik diterjemahkan per titik kode penuh (emoji utuh),
        // dan nilai yang mustahil dibiarkan apa adanya — tidak melempar,
        // tidak terpotong 16 bit (issue #27).
        t = t.replace(reEntitasAngka) { m -> titikKode(m.groupValues[1].toIntOrNull(), m.value) }
        t = t.replace(reEntitasHex) { m -> titikKode(m.groupValues[1].toIntOrNull(16), m.value) }
        // &amp; diterjemahkan TERAKHIR (issue #73): teks yang sengaja
        // dikodekan dua kali seperti &amp;lt; harus tampil sebagai
        // teks "&lt;" sebagaimana ditulis pengirim, bukan "<".
        t = t.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
        return t.replace(reSpasi, " ")
            .replace(reBaris3, "\n\n")
            .trim()
    }
}

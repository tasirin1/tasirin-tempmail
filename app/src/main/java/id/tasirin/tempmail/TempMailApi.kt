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
    // internal (bukan private) supaya terkunci tes unit (issue #104).
    internal fun encPath(s: String): String = enc(s).replace("+", "%20")

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
        // Body kosong/HTML berstatus 200 tidak boleh bocor sebagai
        // JSONException mentah ke pengguna (issue #88).
        return try {
            JSONObject(hasil.body)
        } catch (e: Exception) {
            throw IllegalStateException("tempmail.plus: respons bukan JSON (mungkin halaman perantara)")
        }
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
            val dari = m.optString("from_mail")
            val subjek = m.optString("subject")
            val waktu = m.optString("time")
            // Item tanpa mail_id TIDAK boleh berbagi id "" bersama
            // (detail membuka email yang salah, penanda UI bertabrakan)
            // — pakai kunci sidik isi, pola #42/#111 (issue #120).
            // Id sintetis = sidik isi SAJA (issue #139): pembeda
            // posisi/peringkat kembar sudah dicoba tiga kali dan
            // selalu bergeser pada mutasi daftar tertentu (#125,
            // #129, #135) — dua email bertripel identik memang tidak
            // bisa dibedakan secara stabil, dan tidak perlu: kode
            // untuk item tanpa-id hanya berasal dari SUBJEK (bagian
            // dari sidik itu sendiri), dan isinya memang tidak bisa
            // diambil tanpa id (#125). Kembar identik berbagi satu
            // penanda tanpa kehilangan apa pun; id kini stabil
            // terhadap SEMUA mutasi daftar.
            // Sidiknya GANDA (issue #143): dua hash dari susunan
            // kolom berbeda + panjang tripel — satu hashCode 32-bit
            // saja bisa bertabrakan antar tripel BERBEDA (subjek
            // "Aa" vs "BB") dan item kedua lalu tak pernah dipindai
            // fase-2; tabrakan kini butuh kedua hash sekaligus.
            val idMentah = idString(m.opt("mail_id"))
            val idPesan = if (idMentah.isBlank()) {
                val tripel = dari + "|" + subjek + "|" + waktu
                val tripelBalik = waktu + "|" + subjek + "|" + dari
                "tanpa-id-" + tripel.hashCode() + "-" + tripelBalik.hashCode() +
                    "-" + tripel.length
            } else {
                idMentah
            }
            hasil.add(MailItem(id = idPesan, from = dari, subject = subjek, time = waktu))
        }
        return hasil
    }

    fun fetchDetail(email: String, mailId: String): MailDetail {
        // Id sintetis (email tanpa mail_id dari server, #120) BUKAN id
        // sungguhan — jangan kirim ke server lalu melaporkan pesan
        // menyesatkan "sudah terhapus"; emailnya ada, hanya isinya
        // memang tidak bisa diambil tanpa id (issue #125).
        if (mailId.startsWith("tanpa-id-")) {
            throw IllegalStateException(
                "tempmail.plus tidak memberi id untuk email ini — isinya tidak tersedia"
            )
        }
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
    // SATU pola untuk semua entitas (numerik, hex, bernama): decode
    // satu lintasan — teks hasil decode tidak pernah dipindai ulang,
    // jadi "&lt;" bentukan dari &#38; tetap teks (issue #73/#102)
    // TANPA skema penanda yang tertukar di karakter langka (#123).
    private val reEntitas = Regex("&(#\\d+|#[xX][0-9a-fA-F]+|lt|gt|quot|nbsp|amp);")
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
        // Decode entitas SATU lintasan (issue #123): hasil decode tidak
        // dipindai ulang, jadi teks yang sengaja dikodekan dua kali
        // (&#38;lt;, &amp;lt;) tampil sebagai teks "&lt;" sebagaimana
        // ditulis pengirim (issue #73/#102), dan karakter langka milik
        // pengirim (U+E000/U+FFFE, atau entitas numeriknya) lolos utuh
        // — skema penanda #102/#118 yang tertukar di jalur decode
        // dibuang total. Entitas numerik diterjemahkan per titik kode
        // penuh (emoji utuh); nilai mustahil dibiarkan apa adanya
        // (issue #27).
        t = t.replace(reEntitas) { m ->
            val isi = m.groupValues[1]
            when {
                isi.startsWith("#x") || isi.startsWith("#X") ->
                    titikKode(isi.substring(2).toIntOrNull(16), m.value)
                isi.startsWith("#") ->
                    titikKode(isi.substring(1).toIntOrNull(), m.value)
                isi == "lt" -> "<"
                isi == "gt" -> ">"
                isi == "quot" -> "\""
                isi == "nbsp" -> " "
                else -> "&" // amp
            }
        }
        return t.replace(reSpasi, " ")
            .replace(reBaris3, "\n\n")
            .trim()
    }
}

package id.tasirin.tempmail

import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Layanan Catchmail (catchmail.io) — API JSON tanpa sesi dan tanpa
// kunci (terverifikasi langsung 2026-11-28, termasuk email uji yang
// tiba dan terbaca isinya). Bentuk respons terverifikasi:
//   GET api.catchmail.io/api/v1/mailbox?address=<alamat>
//     -> {address, messages:[{id, mailbox, from, subject,
//          date (ISO 8601), size}], count, total}
//     "from" berbentuk "<pengirim@contoh.com>" (kurung sudut saja,
//     tanpa nama tampil) — dibersihkan saat diurai.
//   GET api.catchmail.io/api/v1/message/<id>?mailbox=<alamat>
//     -> {id, from, to, subject, date, body:{text, html}, ...}
// Alamat = nama apa pun di domain layanan; siapa pun yang tahu
// alamatnya bisa membaca inbox-nya — model yang sama dengan
// tempmail.plus, jadi tanpaSesi=true.
class CatchmailProvider : MailProvider {

    override val id = "catchmail"
    override val nama = "catchmail.io"
    override val tanpaSesi = true
    override val bisaCustom = true

    override fun domains(): List<String> = DOMAINS

    override fun alamatAcak(domain: String?): String {
        val d = domain?.takeIf { it in DOMAINS } ?: DOMAINS[0]
        return AddressGenerator.randomLocalPart() + "@" + d
    }

    override fun alamatCustom(namaBersih: String, domain: String): String {
        // Konsisten dengan PlusProvider: domain di luar layanan
        // ditolak, bukan dirangkai buta (pola issue #62).
        if (domain !in DOMAINS) {
            throw IllegalStateException("Domain tidak didukung Catchmail: $domain")
        }
        return "$namaBersih@$domain"
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    // Header asal situs: API menjawab 200 dengan header ini pada
    // verifikasi langsung; tanpa keduanya belum teruji, jadi
    // disertakan persis seperti jalur yang terbukti.
    private val headerAsal = mapOf(
        "Origin" to "https://catchmail.io",
        "Referer" to "https://catchmail.io/"
    )

    override fun fetchInbox(alamat: String): List<MailItem> {
        val hasil = HttpSesi().get("$BASE/mailbox?address=${enc(alamat)}", headerAsal)
        if (hasil.kode !in 200..299) {
            throw IllegalStateException("Catchmail: HTTP ${hasil.kode}")
        }
        // Respons non-JSON (halaman perantara) jangan bocor sebagai
        // JSONException mentah (pola issue #55/#88).
        val root = try {
            JSONObject(hasil.body)
        } catch (e: Exception) {
            throw IllegalStateException("Catchmail: respons bukan JSON (mungkin halaman perantara)")
        }
        val arr = root.optJSONArray("messages") ?: return emptyList()
        val hasilAkhir = ArrayList<MailItem>()
        for (i in 0 until arr.length()) {
            // Satu elemen rusak dilewati saja, jangan menggagalkan
            // seluruh pengambilan (pola issue #109/#119).
            val m = arr.optJSONObject(i) ?: continue
            val idPesan = m.optString("id")
            if (idPesan.isBlank()) continue
            hasilAkhir.add(
                MailItem(
                    id = idPesan,
                    from = bersihkanPengirim(m.optString("from")),
                    subject = m.optString("subject"),
                    time = waktuLokal(m.optString("date"))
                )
            )
        }
        return hasilAkhir
    }

    override fun fetchDetail(alamat: String, item: MailItem): MailDetail {
        val hasil = HttpSesi().get(
            "$BASE/message/${TempMailApi.encPath(item.id)}?mailbox=${enc(alamat)}",
            headerAsal
        )
        if (hasil.kode !in 200..299) {
            throw IllegalStateException("Catchmail: HTTP ${hasil.kode}")
        }
        val root = try {
            JSONObject(hasil.body)
        } catch (e: Exception) {
            throw IllegalStateException("Catchmail: respons bukan JSON (mungkin halaman perantara)")
        }
        val body = root.optJSONObject("body")
        val teks = body?.optString("text")?.takeIf { it.isNotBlank() }
            ?: body?.optString("html")?.takeIf { it.isNotBlank() }?.let { TempMailApi.htmlToText(it) }
            ?: ""
        return MailDetail(
            from = bersihkanPengirim(root.optString("from")),
            subject = root.optString("subject"),
            date = waktuLokal(root.optString("date")),
            text = teks
        )
    }

    companion object {
        private val DOMAINS = listOf("catchmail.io", "mailistry.com", "zeppost.com")
        private const val BASE = "https://api.catchmail.io/api/v1"

        // "from" API berbentuk "<a@b.com>" atau "Nama <a@b.com>" —
        // ambil alamat di dalam kurung sudut bila ada, selain itu
        // pakai teks apa adanya. internal supaya terkunci tes unit.
        internal fun bersihkanPengirim(mentah: String): String {
            val t = mentah.trim()
            val awal = t.indexOf('<')
            val akhir = t.indexOf('>', awal + 1)
            return if (awal >= 0 && akhir > awal) t.substring(awal + 1, akhir).trim() else t
        }

        // "2026-11-28T14:03:49.753000+00:00" (ISO 8601 dari API) ->
        // "yyyy-MM-dd HH:mm" zona waktu perangkat. internal supaya
        // terkunci tes unit. Nilai tidak sah menghasilkan teks kosong.
        internal fun waktuLokal(iso: String): String {
            val t = iso.trim()
            if (t.isEmpty()) return ""
            for (pola in arrayOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
            )) {
                try {
                    val masuk = SimpleDateFormat(pola, Locale.US)
                    masuk.isLenient = false
                    if (pola.endsWith("'Z'")) {
                        masuk.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    }
                    val parsed = masuk.parse(t) ?: continue
                    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(parsed)
                } catch (e: Exception) {
                    // Coba format berikutnya.
                }
            }
            return ""
        }
    }
}

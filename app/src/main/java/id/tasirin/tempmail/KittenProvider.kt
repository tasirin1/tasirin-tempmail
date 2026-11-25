package id.tasirin.tempmail

import org.json.JSONArray
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Layanan InboxKitten (inboxkitten.com) — API JSON tanpa sesi dan
// tanpa kunci (terverifikasi langsung 2026-11-25, termasuk email uji
// yang tiba dan terbaca isinya). Bentuk respons terverifikasi:
//   GET /api/v1/mail/list?recipient=<nama lokal>
//     -> [ {id, timestamp (epoch detik), recipient,
//           message:{headers:{from, subject, to}},
//           storage:{key, region}} ]
//     Daftar TIDAK membawa isi — hanya kepala Mailgun.
//   GET /api/v1/mail/getHtml?region=<region>&key=<key>
//     -> isi email (teks polos atau HTML; server menyisipkan skrip
//        kecil di ekor yang dibuang htmlToText).
// Alamat = nama lokal apa pun @inboxkitten.com; siapa pun yang tahu
// nama lokalnya bisa membaca inbox-nya — model yang sama dengan
// tempmail.plus, jadi tanpaSesi=true.
class KittenProvider : MailProvider {

    override val id = "kitten"
    override val nama = "inboxkitten.com"
    override val tanpaSesi = true
    override val bisaCustom = true

    override fun domains(): List<String> = listOf(DOMAIN)

    override fun alamatAcak(domain: String?): String {
        // Satu-satunya domain layanan ini inboxkitten.com; pilihan
        // domain lain (sisa layanan sebelumnya) jatuh ke domain itu.
        return AddressGenerator.randomLocalPart() + "@" + DOMAIN
    }

    override fun alamatCustom(namaBersih: String, domain: String): String {
        // Konsisten dengan PlusProvider: domain di luar layanan
        // ditolak, bukan dirangkai buta (pola issue #62).
        if (domain != DOMAIN) {
            throw IllegalStateException("Domain tidak didukung InboxKitten: $domain")
        }
        return "$namaBersih@$DOMAIN"
    }

    // Peta id pesan (kunci storage Mailgun) -> wilayah, diisi ulang
    // utuh tiap fetchInbox (tukar referensi @Volatile, pola KotakIsi)
    // karena fetchDetail butuh wilayah untuk menyusun URL isi.
    @Volatile
    private var refWilayah: Map<String, String> = emptyMap()

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    override fun fetchInbox(alamat: String): List<MailItem> {
        val lokal = alamat.substringBefore("@").trim()
        if (lokal.isEmpty()) return emptyList()
        // Instance HttpSesi per panggilan seperti tempmail.plus
        // (layanan tanpa sesi/kuki — issue #87).
        val hasil = HttpSesi().get("$BASE/list?recipient=${enc(lokal)}")
        if (hasil.kode !in 200..299) {
            throw IllegalStateException("InboxKitten: HTTP ${hasil.kode}")
        }
        // Respons non-JSON (halaman perantara) jangan bocor sebagai
        // JSONException mentah (pola issue #55/#88).
        val arr = try {
            JSONArray(hasil.body)
        } catch (e: Exception) {
            throw IllegalStateException("InboxKitten: respons bukan JSON (mungkin halaman perantara)")
        }
        val mentah = ArrayList<Triple<Double, MailItem, String>>()
        val wilayah = HashMap<String, String>()
        for (i in 0 until arr.length()) {
            // Satu elemen rusak dilewati saja, jangan menggagalkan
            // seluruh pengambilan (pola issue #109/#119).
            val m = arr.optJSONObject(i) ?: continue
            val headers = m.optJSONObject("message")?.optJSONObject("headers") ?: continue
            val storage = m.optJSONObject("storage") ?: continue
            val kunci = storage.optString("key")
            val region = storage.optString("region")
            if (kunci.isBlank() || region.isBlank()) continue
            val ts = m.optDouble("timestamp", 0.0)
            mentah.add(
                Triple(
                    ts,
                    MailItem(
                        // Id pesan = kunci storage: identitas pesan
                        // itu sendiri, stabil antar putaran polling
                        // (id peristiwa Mailgun berbeda per peristiwa
                        // untuk pesan yang sama).
                        id = kunci,
                        from = headers.optString("from"),
                        subject = headers.optString("subject"),
                        time = waktuLokal(ts)
                    ),
                    region
                )
            )
        }
        // Terbaru dulu; pesan yang sama bisa muncul sebagai beberapa
        // peristiwa — dedupe menurut kunci storage, simpan yang
        // terbaru.
        mentah.sortByDescending { it.first }
        val hasilAkhir = ArrayList<MailItem>()
        val sudah = HashSet<String>()
        for ((_, item, region) in mentah) {
            if (sudah.add(item.id)) {
                hasilAkhir.add(item)
                wilayah[item.id] = region
            }
        }
        refWilayah = wilayah
        return hasilAkhir
    }

    override fun fetchDetail(alamat: String, item: MailItem): MailDetail {
        var region = refWilayah[item.id]
        if (region == null) {
            // Cadangan pola IsProvider: segarkan daftar (mengisi ulang
            // peta wilayah) lalu cari lagi.
            fetchInbox(alamat)
            region = refWilayah[item.id]
                ?: throw IllegalStateException("Email tidak ditemukan (mungkin sudah terhapus)")
        }
        val hasil = HttpSesi().get("$BASE/getHtml?region=${enc(region)}&key=${enc(item.id)}")
        if (hasil.kode !in 200..299) {
            throw IllegalStateException("InboxKitten: HTTP ${hasil.kode}")
        }
        // getHtml mengembalikan isi mentah (teks/HTML + skrip sisipan
        // server di ekor) — htmlToText membuang skrip dan tag, sama
        // seperti jalur HTML tempmail.plus.
        return MailDetail(
            from = item.from,
            subject = item.subject,
            date = item.time,
            text = TempMailApi.htmlToText(hasil.body)
        )
    }

    companion object {
        private const val DOMAIN = "inboxkitten.com"
        private const val BASE = "https://inboxkitten.com/api/v1/mail"

        // Epoch detik (bisa pecahan) -> "yyyy-MM-dd HH:mm" zona waktu
        // perangkat. internal supaya terkunci tes unit. Nilai tidak
        // sah (<= 0) menghasilkan teks kosong.
        internal fun waktuLokal(epochDetik: Double): String {
            if (epochDetik <= 0.0) return ""
            return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                .format(Date((epochDetik * 1000).toLong()))
        }
    }
}

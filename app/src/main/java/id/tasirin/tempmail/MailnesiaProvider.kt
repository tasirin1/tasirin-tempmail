package id.tasirin.tempmail

import java.text.SimpleDateFormat
import java.util.Locale

// Layanan Mailnesia (mailnesia.com) — kotak masuk publik menurut
// nama, dibaca lewat feed RSS (terverifikasi langsung 2026-11-25,
// termasuk email uji yang tiba dan terbaca isinya):
//   GET /rss/<nama lokal> -> RSS 2.0; tiap <item>:
//     <title>Subjek – pengirim</title> (pemisah " – " en-dash)
//     <link>https://mailnesia.com/mailbox/<nama>/<id pesan></link>
//     <description>isi teks email (CDATA)</description>
//     <pubDate>RFC 822</pubDate>
// Isi email ikut di feed — tidak ada panggilan detail terpisah.
// Alamat = nama apa pun @mailnesia.com; siapa pun yang tahu nama
// lokalnya bisa membaca inbox-nya — model yang sama dengan
// tempmail.plus, jadi tanpaSesi=true.
class MailnesiaProvider : MailProvider {

    override val id = "nesia"
    override val nama = "mailnesia.com"
    override val tanpaSesi = true
    override val bisaCustom = true

    override fun domains(): List<String> = listOf(DOMAIN)

    override fun alamatAcak(domain: String?): String {
        // Satu-satunya domain layanan ini mailnesia.com; pilihan
        // domain lain (sisa layanan sebelumnya) jatuh ke domain itu.
        return AddressGenerator.randomLocalPart() + "@" + DOMAIN
    }

    override fun alamatCustom(namaBersih: String, domain: String): String {
        // Konsisten dengan PlusProvider: domain di luar layanan
        // ditolak, bukan dirangkai buta (pola issue #62).
        if (domain != DOMAIN) {
            throw IllegalStateException("Domain tidak didukung Mailnesia: $domain")
        }
        return "$namaBersih@$DOMAIN"
    }

    // Cache isi dari feed terakhir (KotakIsi): diganti atomik per
    // ambil inbox, bukan clear()+isi ulang.
    private val isiTerakhir = KotakIsi()

    override fun fetchInbox(alamat: String): List<MailItem> {
        val lokal = alamat.substringBefore("@").trim()
        if (lokal.isEmpty()) return emptyList()
        // Instance HttpSesi per panggilan seperti tempmail.plus
        // (layanan tanpa sesi/kuki — issue #87). Nama lokal masuk
        // segmen path: pakai encPath, bukan encoder query (issue #74).
        val hasil = HttpSesi().get("$BASE/rss/${TempMailApi.encPath(lokal)}")
        if (hasil.kode !in 200..299) {
            throw IllegalStateException("Mailnesia: HTTP ${hasil.kode}")
        }
        // Respons non-RSS (halaman perantara) jangan diurai buta jadi
        // inbox kosong (pola issue #55/#88).
        if (!hasil.body.contains("<rss") && !hasil.body.contains("<channel")) {
            throw IllegalStateException("Mailnesia: respons bukan RSS (mungkin halaman perantara)")
        }
        val pasangan = uraiRss(hasil.body)
        isiTerakhir.gantiSemua(pasangan.associate { it.first.id to it.second })
        return pasangan.map { it.first }
    }

    override fun fetchDetail(alamat: String, item: MailItem): MailDetail {
        isiTerakhir[item.id]?.let { return it }
        // Cadangan pola IsProvider/Kitten: segarkan daftar (mengisi
        // ulang cache isi) lalu cari lagi.
        fetchInbox(alamat)
        return isiTerakhir[item.id]
            ?: throw IllegalStateException("Email tidak ditemukan (mungkin sudah terhapus)")
    }

    companion object {
        private const val DOMAIN = "mailnesia.com"
        private const val BASE = "https://mailnesia.com"

        // Pemisah judul RSS: "Subjek – pengirim" (en-dash U+2013
        // diapit spasi) — ditulis sebagai escape supaya tidak ada
        // karakter tak terlihat di sumber.
        private const val PEMISAH = " \u2013 "

        // Urai RSS -> pasangan (item daftar, detail isi). Murni tanpa
        // jaringan — terkunci tes unit dengan contoh feed sungguhan.
        // Urutan feed dipertahankan (Mailnesia menyusun terbaru dulu).
        internal fun uraiRss(body: String): List<Pair<MailItem, MailDetail>> {
            val hasil = ArrayList<Pair<MailItem, MailDetail>>()
            var dari = 0
            while (true) {
                val awal = body.indexOf("<item>", dari)
                if (awal < 0) break
                val akhir = body.indexOf("</item>", awal)
                if (akhir < 0) break
                val blok = body.substring(awal, akhir)
                dari = akhir + "</item>".length
                val judul = tagXml(blok, "title")
                val tautan = tagXml(blok, "link").trim()
                val isi = tagXml(blok, "description").trim()
                val tanggal = tagXml(blok, "pubDate").trim()
                // Pisah di pemisah TERAKHIR supaya subjek yang memuat
                // " – " tetap utuh sebagai subjek.
                val pisah = judul.lastIndexOf(PEMISAH)
                val subjek = if (pisah >= 0) judul.substring(0, pisah) else judul
                val pengirim = if (pisah >= 0) judul.substring(pisah + PEMISAH.length) else ""
                val waktu = waktuLokal(tanggal)
                val idTautan = tautan.substringAfterLast("/").trim()
                val id = if (idTautan.isNotBlank() && idTautan.all { it.isDigit() }) {
                    idTautan
                } else {
                    // Item tanpa id tautan tetap bisa dibuka (isinya
                    // ada di feed) — pakai id sintetis sidik isi:
                    // hashCode + FNV-1a + panjang tripel (pola
                    // issue #42/#143/#148).
                    val tripel = pengirim + "|" + subjek + "|" + tanggal
                    "tanpa-id-" + tripel.hashCode() + "-" + fnv1a(tripel) +
                        "-" + tripel.length
                }
                val item = MailItem(id = id, from = pengirim, subject = subjek, time = waktu)
                hasil.add(
                    item to MailDetail(
                        from = pengirim, subject = subjek, date = waktu, text = isi
                    )
                )
            }
            return hasil
        }

        // Isi satu tag XML dari blok item. Bungkus CDATA dibuka apa
        // adanya; teks non-CDATA didekode entitasnya SATU lintasan
        // (pola issue #73/#123 — hasil decode tidak dipindai ulang).
        private fun tagXml(blok: String, tag: String): String {
            val awal = blok.indexOf("<$tag>")
            if (awal < 0) return ""
            val mulai = awal + tag.length + 2
            val akhir = blok.indexOf("</$tag>", mulai)
            if (akhir < 0) return ""
            val mentah = blok.substring(mulai, akhir)
            return if (mentah.startsWith("<![CDATA[") && mentah.endsWith("]]>")) {
                mentah.substring("<![CDATA[".length, mentah.length - "]]>".length)
            } else {
                decodeEntitas(mentah)
            }
        }

        private val reEntitasXml = Regex("&(#\\d+|#[xX][0-9a-fA-F]+|lt|gt|quot|apos|amp);")

        private fun titikKode(cp: Int?, asal: String): String {
            if (cp == null || cp < 0 || cp > 0x10FFFF || cp in 0xD800..0xDFFF) return asal
            return String(Character.toChars(cp))
        }

        private fun decodeEntitas(teks: String): String = teks.replace(reEntitasXml) { m ->
            val isi = m.groupValues[1]
            when {
                isi.startsWith("#x") || isi.startsWith("#X") ->
                    titikKode(isi.substring(2).toIntOrNull(16), m.value)
                isi.startsWith("#") ->
                    titikKode(isi.substring(1).toIntOrNull(), m.value)
                isi == "lt" -> "<"
                isi == "gt" -> ">"
                isi == "quot" -> "\""
                isi == "apos" -> "'"
                else -> "&" // amp
            }
        }

        // FNV-1a 32-bit atas teks UTF-8 — komponen sidik kedua yang
        // strukturnya berbeda dari hashCode polinomial (issue #148);
        // salinan setempat supaya berkas ini berdiri sendiri seperti
        // provider lain (aslinya di TempMailApi bersifat private).
        private fun fnv1a(teks: String): Int {
            var h = -2128831035 // 0x811C9DC5 sebagai Int bertanda (issue #153)
            for (b in teks.toByteArray(Charsets.UTF_8)) {
                h = h xor (b.toInt() and 0xFF)
                h *= 16777619 // 0x01000193; luapan Int memang sifat FNV
            }
            return h
        }

        // "Wed, 25 Nov 2026 03:47:01 -0800" (RFC 822 dari RSS) ->
        // "yyyy-MM-dd HH:mm" zona waktu perangkat. internal supaya
        // terkunci tes unit. Nilai tidak sah menghasilkan teks kosong.
        internal fun waktuLokal(pubDate: String): String {
            val t = pubDate.trim()
            if (t.isEmpty()) return ""
            return try {
                val masuk = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
                masuk.isLenient = false
                val parsed = masuk.parse(t) ?: return ""
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(parsed)
            } catch (e: Exception) {
                ""
            }
        }
    }
}

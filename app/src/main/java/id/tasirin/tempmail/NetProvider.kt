package id.tasirin.tempmail

// Keluarga layanan tempmail.net + tempail.com — satu perangkat lunak
// yang sama di dua situs (endpoint identik, terverifikasi 2026-11-25).
// Berbasis sesi kuki; jawaban API berbentuk potongan HTML, bukan JSON:
//   GET  /en/                     -> halaman berisi alamat + token sesi
//   POST /en/api/kontrol/ {oturum, tarih, geri_don} -> daftar inbox (HTML)
//   GET  /en/mail_<id>/           -> halaman detail; isinya di iframe
//   GET  /en/api/icerik/?oturum=..&mail_no=<id>     -> isi email (HTML)
//   POST /en/api/yoket/ {oturum}  -> hancurkan sesi (ganti alamat)
// Judul email TIDAK ditampilkan keluarga layanan ini (terbukti kosong
// pada email uji) — deteksi kode berjalan dari isi email.
// Pengirim tersandi Cloudflare (data-cfemail) — dibongkar lokal.
class NetProvider(
    override val id: String,
    override val nama: String,
    private val base: String
) : MailProvider {

    override val tanpaSesi = false
    override val bisaCustom = false

    private val http = HttpSesi()
    @Volatile
    private var oturum: String = ""
    @Volatile
    private var alamatAktif: String = ""
    @Volatile
    private var daftarTerakhir: List<MailItem> = emptyList()

    private fun pastikanSesi() {
        if (oturum.isNotBlank() && alamatAktif.isNotBlank()) return
        val hasil = http.get("$base/en/")
        if (hasil.kode !in 200..299) throw IllegalStateException("$nama: HTTP ${hasil.kode}")
        val alamat = reAlamat.find(hasil.body)?.groupValues?.get(1)
            ?: reAlamatCadangan.find(hasil.body)?.groupValues?.get(1)
            ?: throw IllegalStateException("$nama: alamat tidak ditemukan di halaman")
        oturum = reOturum.find(hasil.body)?.groupValues?.get(1)
            ?: throw IllegalStateException("$nama: token sesi tidak ditemukan")
        alamatAktif = alamat
    }

    override fun alamatAktif(): String? = alamatAktif.ifBlank { null }

    override fun domains(): List<String> = emptyList()

    override fun alamatAcak(domain: String?): String {
        if (alamatAktif.isBlank()) {
            pastikanSesi()
            return alamatAktif
        }
        // Hancurkan sesi lama, mulai sesi baru = alamat baru.
        try {
            http.postForm("$base/en/api/yoket/", listOf("oturum" to oturum))
        } catch (e: Exception) {
            // Sesi lama gagal dihancurkan pun, sesi baru tetap dibuat.
        }
        http.lupaKuki()
        oturum = ""
        alamatAktif = ""
        daftarTerakhir = emptyList()
        pastikanSesi()
        return alamatAktif
    }

    override fun alamatCustom(namaBersih: String, domain: String): String {
        throw IllegalStateException("$nama tidak mendukung nama sendiri")
    }

    override fun fetchInbox(alamat: String): List<MailItem> {
        pastikanSesi()
        // tarih=0 memaksa daftar penuh; tarih terkini bisa dijawab
        // 304 kosong oleh tempmail.net (terverifikasi) yang akan
        // disalahartikan sebagai inbox kosong.
        val hasil = http.postForm(
            "$base/en/api/kontrol/",
            listOf(
                "oturum" to oturum,
                "tarih" to "0",
                "geri_don" to "$base/en/"
            )
        )
        if (hasil.kode !in 200..299) throw IllegalStateException("$nama: HTTP ${hasil.kode}")
        if (!hasil.body.contains("<li")) {
            // Jawaban kosong/tidak wajar: jangan hapus daftar yang ada.
            return daftarTerakhir
        }
        val hasilList = ArrayList<MailItem>()
        for (m in reItem.findAll(hasil.body)) {
            val blok = m.groupValues[2]
            val dari = reCfEmail.find(blok)?.let { bongkarCfEmail(it.groupValues[1]) }
                ?: teksDiv(blok, "gonderen")
            val subjek = teksDiv(blok, "baslik")
            val waktu = teksDiv(blok, "zaman")
            hasilList.add(MailItem(id = m.groupValues[1], from = dari, subject = subjek, time = waktu))
        }
        daftarTerakhir = hasilList
        return hasilList
    }

    override fun fetchDetail(alamat: String, item: MailItem): MailDetail {
        pastikanSesi()
        val halaman = http.get("$base/en/mail_${item.id}/")
        if (halaman.kode !in 200..299) throw IllegalStateException("$nama: HTTP ${halaman.kode}")
        val dari = reCfEmail.find(halaman.body)?.let { bongkarCfEmail(it.groupValues[1]) }
            ?: item.from
        val subjek = reKuat.find(halaman.body)?.groupValues?.get(1)?.trim() ?: item.subject
        val urlIsi = reIframeIsi.find(halaman.body)?.groupValues?.get(1)?.replace("&amp;", "&")
            ?: throw IllegalStateException("$nama: isi email tidak ditemukan")
        val isi = http.get(urlIsi)
        if (isi.kode !in 200..299) throw IllegalStateException("$nama: HTTP ${isi.kode}")
        return MailDetail(
            from = dari,
            subject = subjek,
            date = item.time,
            text = TempMailApi.htmlToText(isi.body)
        )
    }

    // Teks polos satu <div class="..."> di dalam blok HTML.
    private fun teksDiv(blok: String, kelas: String): String {
        val re = Regex("<div class=\"$kelas\">(.*?)</div>", RegexOption.DOT_MATCHES_ALL)
        val mentah = re.find(blok)?.groupValues?.get(1) ?: return ""
        return TempMailApi.htmlToText(mentah).trim()
    }

    companion object {
        private val reAlamat = Regex("""id="eposta_adres"[^>]*value="([^"]+)"""")
        private val reAlamatCadangan = Regex("""value="([^"]+@[a-z0-9.-]+)"""")
        private val reOturum = Regex("""oturum="([^"]+)"""")
        private val reItem = Regex("""<li class="mail[^"]*" id="mail_(\d+)">(.*?)</li>""", RegexOption.DOT_MATCHES_ALL)
        private val reCfEmail = Regex("""data-cfemail="([0-9a-fA-F]+)"""")
        private val reKuat = Regex("""mail-oku-gonderen">.*?<strong>(.*?)</strong>""", RegexOption.DOT_MATCHES_ALL)
        private val reIframeIsi = Regex("""<iframe src="([^"]*icerik[^"]*)"""")

        // Sandi email Cloudflare: byte pertama = kunci XOR untuk sisanya.
        // Terbukti dari contoh asli: a4ce... -> juliustasirin@gmail.com.
        fun bongkarCfEmail(hex: String): String {
            if (hex.length < 4 || hex.length % 2 != 0) return ""
            val b = ByteArray(hex.length / 2)
            for (i in b.indices) {
                b[i] = hex.substring(i * 2, i * 2 + 2).toIntOrNull(16)?.toByte() ?: return ""
            }
            val kunci = b[0].toInt()
            val sb = StringBuilder()
            for (i in 1 until b.size) sb.append((b[i].toInt() xor kunci).toChar())
            return sb.toString()
        }
    }
}

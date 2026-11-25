package id.tasirin.tempmail

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

// Layanan DropMail (dropmail.me) — API GraphQL berbasis token + sesi
// (terverifikasi langsung 2026-11-25, termasuk email uji yang tiba
// dan terbaca isinya):
//   POST /api/token/generate {type:"af", lifetime:"1d"} -> {token}
//     Token gratis tanpa akun dan bisa dibuat berulang. Umur panjang
//     (mis. 90 hari) ditolak gerbang captcha/credits (HTTP 402), jadi
//     umur 1 hari yang dipakai — token mati tinggal dibuat ulang.
//   POST /api/graphql/<token> mutation introduceSession
//     -> {id, addresses[{address}]}; nama lokal ditentukan server,
//        jadi nama sendiri tidak didukung.
//   POST /api/graphql/<token> query session(id) { mails {...} }
//     -> daftar email SEKALIAN isi teks polosnya (kolom text).
// Sesi berumur ±10 menit dan diperpanjang tiap akses — polling
// aplikasi (tiap 8 detik selama terbuka) menjaganya tetap hidup.
// Sesi mati dijawab data.session = null + errors SESSION_NOT_FOUND;
// provider memulihkan diri dengan sesi baru dan melaporkannya lewat
// alamatServer() (pola pemulihan NowProvider issue #80/#106).
class DropMailProvider : MailProvider {

    override val id = "drop"
    override val nama = "dropmail.me"
    override val tanpaSesi = false
    override val bisaCustom = false

    private val http = HttpSesi()

    // Kunci keadaan sesi (singleton disentuh banyak thread).
    private val kunciSesi = Any()

    @Volatile
    private var token: String = ""
    @Volatile
    private var sesiId: String = ""
    @Volatile
    private var alamatAktif: String = ""
    // Ditulis thread latar, dibaca UI thread lewat alamatServer().
    @Volatile
    private var alamatDariServer: String? = null
    // Cache isi bersama (KotakIsi): diganti atomik per ambil inbox.
    private val isiTerakhir = KotakIsi()

    // Urai JSON dengan pesan yang bisa dipahami pengguna — respons
    // non-JSON (halaman perantara) tidak boleh bocor sebagai
    // JSONException mentah (pola issue #55/#88).
    private fun uraiJson(body: String): JSONObject {
        return try {
            JSONObject(body)
        } catch (e: Exception) {
            throw IllegalStateException("DropMail: respons bukan JSON (mungkin halaman perantara)")
        }
    }

    // Satu panggilan GraphQL. Kode HTTP di luar 2xx dilempar apa
    // adanya — pesannya memuat "HTTP <kode>" supaya fetchInbox bisa
    // mengenali penolakan token (401/403), pola NowProvider.
    // Wajib dipanggil sambil memegang kunciSesi.
    private fun graphql(query: String): JSONObject {
        val badan = JSONObject().put("query", query).toString()
        val hasil = http.postJson("$BASE/api/graphql/$token", badan)
        if (hasil.kode !in 200..299) throw IllegalStateException("DropMail: HTTP ${hasil.kode}")
        return uraiJson(hasil.body)
    }

    // Wajib dipanggil sambil memegang kunciSesi.
    private fun pastikanToken() {
        if (token.isNotBlank()) return
        val hasil = http.postJson(
            "$BASE/api/token/generate",
            "{\"type\":\"af\",\"lifetime\":\"1d\"}"
        )
        if (hasil.kode !in 200..299) throw IllegalStateException("DropMail: HTTP ${hasil.kode}")
        val json = uraiJson(hasil.body)
        token = try {
            json.getString("token")
        } catch (e: Exception) {
            throw IllegalStateException("DropMail: respons tidak lengkap dari server")
        }
    }

    // Wajib dipanggil sambil memegang kunciSesi.
    private fun pastikanSesi() {
        pastikanToken()
        if (sesiId.isNotBlank()) return
        val json = graphql("mutation { introduceSession { id, addresses { address } } }")
        val sesi = json.optJSONObject("data")?.optJSONObject("introduceSession")
            ?: throw IllegalStateException("DropMail: respons tidak lengkap dari server")
        val alamatBaru = sesi.optJSONArray("addresses")
            ?.optJSONObject(0)?.optString("address").orEmpty()
        if (sesi.optString("id").isBlank() || alamatBaru.isBlank()) {
            throw IllegalStateException("DropMail: respons tidak lengkap dari server")
        }
        sesiId = sesi.optString("id")
        alamatAktif = alamatBaru
    }

    // Buang sesi mati supaya sesi baru bisa dibuat. Token ikut dibuang
    // hanya bila dialah yang ditolak server (401/403). Wajib dipanggil
    // sambil memegang kunciSesi.
    private fun resetSesi(buangToken: Boolean) {
        if (buangToken) token = ""
        sesiId = ""
        alamatAktif = ""
        alamatDariServer = null
        isiTerakhir.kosongkan()
    }

    override fun domains(): List<String> = emptyList()

    override fun alamatAcak(domain: String?): String = synchronized(kunciSesi) {
        if (alamatAktif.isNotBlank()) {
            // Alamat baru = sesi baru (sesi lama ditinggalkan; alamat
            // lamanya mati sendiri mengikuti umur sesi). Token tetap.
            resetSesi(buangToken = false)
        }
        pastikanSesi()
        alamatDariServer = null
        alamatAktif
    }

    override fun alamatCustom(namaBersih: String, domain: String): String {
        throw IllegalStateException("dropmail.me tidak mendukung nama sendiri")
    }

    override fun fetchInbox(alamat: String): List<MailItem> = synchronized(kunciSesi) {
        // Panggilan untuk alamat yang sudah ditinggalkan dijawab
        // kosong (pola issue #44) — jangan sentuh sesi untuk alamat basi.
        if (alamat.isNotBlank() && alamatAktif.isNotBlank() && alamat != alamatAktif) {
            return@synchronized emptyList()
        }
        // Satu kali pemulihan — HANYA untuk penolakan sesi/token oleh
        // server (HTTP 401/403 atau SESSION_NOT_FOUND). Kegagalan
        // jaringan dan HTTP lain dilempar apa adanya TANPA merusak
        // sesi (pola issue #80).
        try {
            ambilInboxSekali(alamat, dariPemulihan = false)
        } catch (e: IllegalStateException) {
            val pesan = e.message ?: ""
            val kode = Regex("HTTP (\\d+)").find(pesan)
                ?.groupValues?.get(1)?.toIntOrNull()
            val tokenDitolak = kode == 401 || kode == 403
            val sesiMati = tokenDitolak || pesan.contains("SESSION_NOT_FOUND")
            if (!sesiMati) throw e
            resetSesi(buangToken = tokenDitolak)
            try {
                val hasilBaru = ambilInboxSekali(alamat, dariPemulihan = true)
                // Alamat sesi baru WAJIB dilaporkan supaya UI
                // menyelaraskan alamat (pola issue #80).
                if (alamatAktif.isNotBlank() && alamatAktif != alamat) {
                    alamatDariServer = alamatAktif
                }
                hasilBaru
            } catch (e2: Exception) {
                // Pemulihan ikut gagal: laporkan penyimpangan alamat
                // sebelum melempar (pola issue #106).
                if (alamatAktif.isNotBlank() && alamatAktif != alamat) {
                    alamatDariServer = alamatAktif
                }
                throw e2
            }
        }
    }

    // Wajib dipanggil sambil memegang kunciSesi.
    private fun ambilInboxSekali(alamatDiminta: String, dariPemulihan: Boolean): List<MailItem> {
        pastikanSesi()
        // Penjaga alamat basi babak kedua (pola issue #91): sesi baru
        // yang dibuat untuk panggilan berlabel alamat lama tidak boleh
        // menyerahkan inbox alamat barunya.
        if (!dariPemulihan && alamatDiminta.isNotBlank() && alamatAktif.isNotBlank() &&
            alamatAktif != alamatDiminta
        ) {
            return emptyList()
        }
        val json = graphql(
            "query { session(id: \"$sesiId\") { mails { id, fromAddr, headerSubject, text, receivedAt } } }"
        )
        // Sesi kedaluwarsa: data.session null + errors SESSION_NOT_FOUND
        // (bentuk terdokumentasi) — terjemahkan jadi sinyal pemulihan.
        val sesi = json.optJSONObject("data")?.optJSONObject("session")
            ?: throw IllegalStateException("DropMail: sesi tidak ditemukan (SESSION_NOT_FOUND)")
        val arr = sesi.optJSONArray("mails")
        val mentah = ArrayList<Triple<String, MailItem, MailDetail>>()
        for (i in 0 until (arr?.length() ?: 0)) {
            // Satu elemen rusak dilewati saja (pola issue #109).
            val m = arr?.optJSONObject(i) ?: continue
            val idPesan = m.optString("id")
            if (idPesan.isBlank()) continue
            val dari = m.optString("fromAddr")
            val subjek = m.optString("headerSubject")
            val mentahWaktu = m.optString("receivedAt")
            val waktu = waktuLokal(mentahWaktu)
            val item = MailItem(id = idPesan, from = dari, subject = subjek, time = waktu)
            mentah.add(
                Triple(
                    mentahWaktu, item,
                    MailDetail(from = dari, subject = subjek, date = waktu, text = m.optString("text"))
                )
            )
        }
        // Terbaru dulu: receivedAt ISO 8601 UTC dari server sebanding
        // secara leksikografis.
        mentah.sortByDescending { it.first }
        isiTerakhir.gantiSemua(mentah.associate { it.second.id to it.third })
        return mentah.map { it.second }
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
        private const val BASE = "https://dropmail.me"

        // "2026-11-25T11:43:07+00:00" (ISO 8601 dari GraphQL) ->
        // "yyyy-MM-dd HH:mm" zona waktu perangkat. internal supaya
        // terkunci tes unit. Nilai tidak sah menghasilkan teks kosong.
        internal fun waktuLokal(iso: String): String {
            val t = iso.trim()
            if (t.isEmpty()) return ""
            return try {
                val masuk = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                masuk.isLenient = false
                val parsed = masuk.parse(t) ?: return ""
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(parsed)
            } catch (e: Exception) {
                ""
            }
        }
    }
}

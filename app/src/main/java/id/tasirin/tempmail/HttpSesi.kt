package id.tasirin.tempmail

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// Hasil satu permintaan HTTP: kode status + isi body teks.
data class HasilHttp(val kode: Int, val body: String)

// Klien HTTP sederhana dengan toples kuki sendiri. Dipakai semua
// layanan (issue #52): TempMailApi (tempmail.plus), IsProvider
// (tempmail.is), dan NowProvider (tempmail.now) — yang berbasis sesi
// mengenali kotak masuk dari kuki, bukan dari alamat email saja.
// Semua fungsi blocking — panggil dari thread latar.
class HttpSesi {

    private val kuki = LinkedHashMap<String, String>()

    fun lupaKuki() {
        synchronized(kuki) { kuki.clear() }
    }

    fun get(url: String, headerTambahan: Map<String, String> = emptyMap()): HasilHttp {
        return kirim("GET", url, null, null, headerTambahan)
    }

    // POST dengan body form-urlencoded (pasangan nama=nilai).
    fun postForm(url: String, kolom: List<Pair<String, String>>, headerTambahan: Map<String, String> = emptyMap()): HasilHttp {
        val body = kolom.joinToString("&") { (n, v) -> enc(n) + "=" + enc(v) }
        return kirim("POST", url, body, "application/x-www-form-urlencoded", headerTambahan)
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    // Satu permintaan pada satu waktu per sesi: baca-kuki dan
    // simpan-kuki menjadi satu rangkaian atomik, jadi dua permintaan
    // paralel tidak bisa membuat toples kuki mundur versi (issue #58).
    private val kunciPermintaan = Any()

    private fun kirim(metode: String, url: String, body: String?, tipeIsi: String?, headerTambahan: Map<String, String>): HasilHttp {
        return synchronized(kunciPermintaan) { kirimSekali(metode, url, body, tipeIsi, headerTambahan) }
    }

    private fun kirimSekali(metode: String, url: String, body: String?, tipeIsi: String?, headerTambahan: Map<String, String>, sisaAlihan: Int = 3): HasilHttp {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = metode
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            // Alihan TIDAK diikuti otomatis — ditangani manual di bawah
            // supaya kuki sesi tidak diteruskan ke host lain (issue #61).
            conn.instanceFollowRedirects = false
            // Header dibuat menyerupai peramban Android beneran: gerbang
            // bot di beberapa layanan menolak (HTTP 403) permintaan yang
            // tampak seperti klien non-peramban — terbukti di perangkat
            // 2026-11-25 dengan UA aplikasi polos.
            conn.setRequestProperty("User-Agent", UA_PERAMBAN)
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
            conn.setRequestProperty("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
            synchronized(kuki) {
                if (kuki.isNotEmpty()) {
                    conn.setRequestProperty("Cookie", kuki.entries.joinToString("; ") { "${it.key}=${it.value}" })
                }
            }
            for ((n, v) in headerTambahan) conn.setRequestProperty(n, v)
            if (body != null) {
                conn.doOutput = true
                if (tipeIsi != null) conn.setRequestProperty("Content-Type", tipeIsi)
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val kode = conn.responseCode
            // Simpan kuki baru dari server (Set-Cookie bisa banyak baris).
            val ladang = conn.headerFields
            synchronized(kuki) {
                for ((nama, nilai) in ladang) {
                    if (nama != null && nama.equals("Set-Cookie", ignoreCase = true)) {
                        for (mentah in nilai) {
                            val bagian = mentah.split(";")
                            val pasangan = bagian[0].trim()
                            val eq = pasangan.indexOf('=')
                            if (eq > 0) {
                                val namaKuki = pasangan.substring(0, eq).trim()
                                val isiKuki = pasangan.substring(eq + 1).trim()
                                // Hormati penghapusan dari server: nilai
                                // kosong, Max-Age <= 0, atau Expires di
                                // masa lalu (epoch) = kuki dibuang, bukan
                                // disimpan dan dikirim selamanya (issue #45).
                                val attrs = bagian.drop(1).joinToString(";").lowercase()
                                val maxAge = reMaxAge.find(attrs)?.groupValues?.get(1)?.toLongOrNull()
                                // Expires diuraikan sungguhan (dua format
                                // umum) — pola lama hanya mengenali satu
                                // string epoch yang persis, format lampau
                                // lain lolos dan kuki basi disimpan terus
                                // (issue #89).
                                val expires = reExpires.find(attrs)?.groupValues?.get(1)?.trim()
                                val mati = isiKuki.isEmpty() ||
                                    (maxAge != null && maxAge <= 0) ||
                                    (expires != null && expiresSudahLewat(expires))
                                if (mati) kuki.remove(namaKuki) else kuki[namaKuki] = isiKuki
                            }
                        }
                    }
                }
            }
            // Alihan manual (issue #61): hanya GET, hanya ke host yang
            // sama, maksimal 3 kali. Kuki dari respons alihan sudah
            // disimpan di atas sebelum mengikuti. Alihan lintas host
            // atau POST dikembalikan apa adanya ke pemanggil.
            if (kode in intArrayOf(301, 302, 303, 307, 308) && metode == "GET" && sisaAlihan > 0) {
                val lokasi = conn.getHeaderField("Location")
                if (!lokasi.isNullOrBlank()) {
                    val asal = URL(url)
                    val tujuan = URL(asal, lokasi)
                    if (tujuan.host == asal.host) {
                        return kirimSekali("GET", tujuan.toString(), null, null, emptyMap(), sisaAlihan - 1)
                    }
                }
            }
            val stream = if (kode in 200..299) conn.inputStream else conn.errorStream
            val teks = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> bacaTerbatas(r) }
            } ?: ""
            return HasilHttp(kode, teks)
        } finally {
            conn.disconnect()
        }
    }

    private fun bacaTerbatas(r: BufferedReader): String {
        val sb = StringBuilder()
        val buf = CharArray(8192)
        while (true) {
            val n = r.read(buf)
            if (n < 0) break
            sb.append(buf, 0, n)
            if (sb.length > BATAS_BODY) throw IllegalStateException("Respons server terlalu besar")
        }
        return sb.toString()
    }

    // Benarkah nilai Expires (sudah lowercase dari attrs) ada di masa
    // lalu? Dua format umum: RFC 1123 "thu, 01 jan 1970 00:00:00 gmt"
    // dan Netscape bertanda hubung "thu, 01-jan-1970 00:00:00 gmt".
    // Tidak teruraikan = anggap belum lewat (perilaku aman lama).
    private fun expiresSudahLewat(nilai: String): Boolean {
        for (pola in arrayOf("EEE, dd MMM yyyy HH:mm:ss 'gmt'", "EEE, dd-MMM-yyyy HH:mm:ss 'gmt'")) {
            try {
                val fmt = SimpleDateFormat(pola, Locale.US)
                fmt.timeZone = TimeZone.getTimeZone("GMT")
                fmt.isLenient = false
                val t = fmt.parse(nilai) ?: continue
                return t.time < System.currentTimeMillis()
            } catch (e: Exception) {
                // Coba format berikutnya.
            }
        }
        return false
    }

    companion object {
        private val reMaxAge = Regex("""max-age\s*=\s*(-?\d+)""")
        private val reExpires = Regex("""expires\s*=\s*([^;]+)""")
        private const val BATAS_BODY = 2_000_000
        private const val UA_PERAMBAN =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}

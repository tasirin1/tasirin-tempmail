package id.tasirin.tempmail

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Hasil satu permintaan HTTP: kode status + isi body teks.
data class HasilHttp(val kode: Int, val body: String)

// Klien HTTP sederhana dengan toples kuki sendiri — dibutuhkan layanan
// berbasis sesi (tempmail.now, tempmail.net, tempail.com) yang mengenali
// kotak masuk dari kuki, bukan dari alamat email saja.
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

    private fun kirim(metode: String, url: String, body: String?, tipeIsi: String?, headerTambahan: Map<String, String>): HasilHttp {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = metode
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.instanceFollowRedirects = true
            // Header dibuat menyerupai peramban Android beneran: gerbang
            // bot Cloudflare di tempail.com menolak (HTTP 403) permintaan
            // yang tampak seperti klien non-peramban — terbukti di
            // perangkat 2026-11-25 dengan UA aplikasi polos.
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
                            val pasangan = mentah.substringBefore(";").trim()
                            val eq = pasangan.indexOf('=')
                            if (eq > 0) {
                                kuki[pasangan.substring(0, eq).trim()] = pasangan.substring(eq + 1).trim()
                            }
                        }
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

    companion object {
        private const val BATAS_BODY = 2_000_000
        private const val UA_PERAMBAN =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}

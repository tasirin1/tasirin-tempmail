package id.tasirin.tempmail

import kotlin.random.Random

// Pembuat alamat email untuk domain-domain tempmail.plus.
// Logika murni (tanpa kelas Android) supaya bisa diuji JVM langsung.
object AddressGenerator {

    // Daftar domain dari halaman resmi tempmail.plus (dicek 2026-11-25).
    val DOMAINS = listOf(
        "mailto.plus",
        "fexpost.com",
        "fexbox.org",
        "fextemp.com",
        "merepost.com",
        "rover.info",
        "any.pink"
    )

    private const val CAMPURAN = "abcdefghijklmnopqrstuvwxyz0123456789"

    // Pola titik ganda — dikompilasi sekali, bukan tiap panggilan
    // sanitizeLocalPart (issue #79).
    private val reTitikGanda = Regex("\\.+")

    // Nama lokal acak: diawali huruf, sisanya huruf kecil/angka.
    fun randomLocalPart(panjang: Int = 12, random: Random = Random.Default): String {
        require(panjang >= 4) { "panjang minimal 4" }
        val sb = StringBuilder()
        sb.append(('a'..'z').random(random))
        repeat(panjang - 1) { sb.append(CAMPURAN.random(random)) }
        return sb.toString()
    }

    // Bersihkan nama ketikan pengguna jadi nama lokal yang sah:
    // huruf kecil, hanya a-z 0-9 . _ -, titik ganda dirapatkan, tanpa
    // pemisah di ujung, dan maksimal 64 karakter (batas bagian lokal
    // alamat email) — issue #21.
    fun sanitizeLocalPart(mentah: String): String {
        return mentah.lowercase()
            .filter { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' || it == '-' }
            .replace(reTitikGanda, ".")
            .trim('.', '-', '_')
            .take(64)
            .trim('.', '-', '_')
    }
}

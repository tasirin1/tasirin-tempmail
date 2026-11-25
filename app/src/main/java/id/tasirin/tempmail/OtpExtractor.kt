package id.tasirin.tempmail

// Pencari kode verifikasi (OTP) di judul/isi email.
// Logika murni (tanpa kelas Android) supaya bisa diuji JVM langsung.
object OtpExtractor {

    // 1) Pola berkode konteks: kata pemicu, lalu angka 4-8 digit tak jauh sesudahnya.
    private val polaKonteks = Regex(
        """(?i)\b(?:kode|code|otp|verifikasi|verification|passcode|pin)\b[^0-9]{0,40}?(\d{4,8})"""
    )

    // 2) Kode tertulis terpencar: "123 456" atau "123-456".
    private val polaTerpencar = Regex("""\b(\d{3})[\s-](\d{3})\b""")

    // 3) Angka 6 digit berdiri sendiri — bentuk OTP paling umum.
    private val pola6 = Regex("""\b(\d{6})\b""")

    // Angka 4 digit berdiri sendiri SENGAJA tidak diterima: terlalu sering
    // berupa nomor pesanan/jumlah uang/tahun, dan kode 4 digit asli hampir
    // selalu datang bersama kata pemicu (tertangkap pola konteks) — issue #4.
    // Kembalikan kode pertama yang ketemu menurut urutan prioritas, atau null.
    fun extract(teks: String): String? {
        if (teks.isBlank()) return null
        polaKonteks.find(teks)?.let { return it.groupValues[1] }
        polaTerpencar.find(teks)?.let { return it.groupValues[1] + it.groupValues[2] }
        pola6.find(teks)?.let { return it.groupValues[1] }
        return null
    }
}

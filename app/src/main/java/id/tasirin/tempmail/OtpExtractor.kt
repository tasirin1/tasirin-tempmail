package id.tasirin.tempmail

// Pencari kode verifikasi (OTP) di judul/isi email.
// Logika murni (tanpa kelas Android) supaya bisa diuji JVM langsung.
object OtpExtractor {

    // 1) Pola berkode konteks: kata pemicu, lalu angka 4-8 digit tak jauh sesudahnya.
    private val polaKonteks = Regex(
        """(?i)\b(?:kode|code|otp|verifikasi|verification|passcode|pin)\b[^0-9]{0,40}?(\d{4,8})(?!\d)"""
    )

    // 2) Kode tertulis terpencar: "123 456" atau "123-456".
    // Penjaga tepi (?<![\d-]) / (?![\d-]): potongan nomor telepon
    // seperti "0812-345-678" TIDAK boleh cocok di tengahnya (issue #71).
    private val polaTerpencar = Regex("""(?<![\d-])(\d{3})[\s-](\d{3})(?![\d-])""")

    // 3) Angka 6 digit berdiri sendiri — bentuk OTP paling umum.
    private val pola6 = Regex("""\b(\d{6})\b""")

    // Angka 4 digit berdiri sendiri SENGAJA tidak diterima: terlalu sering
    // berupa nomor pesanan/jumlah uang/tahun, dan kode 4 digit asli hampir
    // selalu datang bersama kata pemicu (tertangkap pola konteks) — issue #4.
    // Kembalikan kode pertama yang ketemu menurut urutan prioritas, atau null.
    // Frasa alamat surat, BUKAN kode verifikasi: "Kode pos: 40286"
    // tertangkap pola konteks karena kata "kode" + angka (issue #51).
    // TANPA jangkar awal: nilai cocok pola konteks selalu diawali kata
    // pemicu PERTAMA, jadi "Kode verifikasi dan kode pos: 40286" lolos
    // dari penjaga berjangkar ^ — frasa kode pos dicari di seluruh
    // jendela konteks sebelum angkanya (issue #85).
    private val polaKodePos = Regex("""(?i)kode\s*pos\b""")
    private val polaPemicu = Regex("""(?i)\b(?:kode|code|otp|verifikasi|verification|passcode|pin)\b""")

    // Benarkah angka pada kecocokan konteks ini milik frasa "kode pos"?
    // Syaratnya: ada frasa kode pos di jendela sebelum angka DAN tidak
    // ada kata pemicu lain sesudah frasa itu — bila ada ("Kode pos
    // dicatat. Kode verifikasi: 112233"), angkanya milik pemicu lain.
    private fun kodePosUntukAngkaIni(nilaiCocok: String, angka: String): Boolean {
        val awalan = nilaiCocok.dropLast(angka.length)
        val cocokPos = polaKodePos.findAll(awalan).lastOrNull() ?: return false
        val sesudah = awalan.substring(cocokPos.range.last + 1)
        return polaPemicu.find(sesudah) == null
    }

    fun extract(teks: String): String? {
        if (teks.isBlank()) return null
        for (m in polaKonteks.findAll(teks)) {
            // Lewati frasa "kode pos" — angka sesudahnya kode pos.
            if (kodePosUntukAngkaIni(m.value, m.groupValues[1])) continue
            val v = m.groupValues[1]
            // Tahun sesudah kata pemicu bukan kode — lewati, cari berikutnya.
            if (v.length == 4 && v.toInt() in 1900..2099) continue
            return v
        }
        polaTerpencar.find(teks)?.let { return it.groupValues[1] + it.groupValues[2] }
        pola6.find(teks)?.let { return it.groupValues[1] }
        return null
    }
}

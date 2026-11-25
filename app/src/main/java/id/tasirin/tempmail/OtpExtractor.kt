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
    // Penjaga tambahan (issue #101): format spasi "0812 345 678" —
    // segmen tengahnya didahului kelompok 4 digit + pemisah, dan
    // kelompok yang diikuti kelompok angka lain juga ditolak.
    private val polaTerpencar = Regex("""(?<![\d-])(?<!\d{4}[\s-])(?<!\d{3}[\s-])(\d{3})[\s-](\d{3})(?![\d-])(?![\s-]\d)""")

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
    // [\s-]* juga menelan varian bertanda hubung "Kode-pos" (issue #99).
    // Sufiks -nya ikut dikenali ("kode posnya 40115" adalah frasa
    // alamat yang wajar) — \b polos sesudah "pos" gagal cocok di sana
    // (issue #116).
    private val polaKodePos = Regex("""(?i)kode[\s-]*pos(?:nya)?\b""")
    // Padanan Inggrisnya: "ZIP code" / "postal code" (issue #100).
    private val polaKodePosInggris = Regex("""(?i)\b(?:zip|postal)[\s-]*code\b""")
    // \b di depan wajib: tanpa itu kata berakhiran "zip" (unzip,
    // gzip) tepat sebelum pemicu ikut menolak kode yang sah
    // (issue #117).
    private val polaEkorInggris = Regex("""(?i)\b(?:zip|postal)[\s-]*$""")
    private val polaPemicu = Regex("""(?i)\b(?:kode|code|otp|verifikasi|verification|passcode|pin)\b""")

    // Benarkah angka pada kecocokan konteks ini milik frasa kode pos?
    // Syaratnya: ada frasa kode pos di jendela sebelum angka DAN tidak
    // ada kata pemicu lain sesudah frasa itu — bila ada ("Kode pos
    // dicatat. Kode verifikasi: 112233"), angkanya milik pemicu lain.
    private fun kodePosUntukAngkaIni(teks: String, m: MatchResult): Boolean {
        val awalan = m.value.dropLast(m.groupValues[1].length)
        val cocokId = polaKodePos.findAll(awalan).lastOrNull()
        val cocokEn = polaKodePosInggris.findAll(awalan).lastOrNull()
        val cocokPos = listOfNotNull(cocokId, cocokEn).maxByOrNull { it.range.last }
        if (cocokPos != null) {
            val sesudah = awalan.substring(cocokPos.range.last + 1)
            return polaPemicu.find(sesudah) == null
        }
        // "code" yang merupakan ekor "ZIP code"/"postal code": kata
        // pertamanya berada di LUAR jendela cocok (tepat sebelum kata
        // pemicu), jadi periksa juga teks persis sebelum kecocokan.
        // Cabang ini kemarin mengembalikan true langsung — menelan
        // kode sah bila ada pemicu LAIN di jendela sesudah "code"
        // milik zip ("Your zip code and verification code is 4455").
        // Kini: kode pos hanya bila tidak ada pemicu lain sesudah
        // pemicu pertama (issue #122).
        val sebelum = teks.substring(maxOf(0, m.range.first - 12), m.range.first)
        if (polaEkorInggris.containsMatchIn(sebelum)) {
            val pertama = polaPemicu.find(awalan) ?: return true
            val sesudahPertama = awalan.substring(pertama.range.last + 1)
            return polaPemicu.find(sesudahPertama) == null
        }
        return false
    }

    fun extract(teks: String): String? {
        if (teks.isBlank()) return null
        for (m in polaKonteks.findAll(teks)) {
            // Lewati frasa "kode pos" — angka sesudahnya kode pos.
            if (kodePosUntukAngkaIni(teks, m)) continue
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

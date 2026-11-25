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
    // Sufiks posesif ikut dikenali ("kode posnya/posku/posmu/
    // poskau/posanda 40115" adalah frasa alamat yang wajar) — \b
    // polos sesudah "pos" gagal cocok di sana (issue #116, #124,
    // #127). "-na" = padanan Sunda dari "-nya" (issue #134).
    private val polaKodePos = Regex("""(?i)kode[\s-]*pos(?:nya|ku|mu|kau|anda|na)?\b""")
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
        // Jalur cadangan ikut dijaga label kode pos (issue #128):
        // kemarin penjaga hanya berlaku di jalur konteks, jadi
        // "Kode pos: 123456" dan "Kode pos: 123 456" lolos sebagai OTP.
        // Kandidat ditelusuri SEMUA (findAll, seperti jalur konteks):
        // kandidat pertama yang ditolak penjaga tidak boleh membunuh
        // kandidat sah berikutnya (issue #131).
        for (m in polaTerpencar.findAll(teks)) {
            if (!berlabelKodePos(teks, m.range.first)) {
                return m.groupValues[1] + m.groupValues[2]
            }
        }
        for (m in pola6.findAll(teks)) {
            if (!berlabelKodePos(teks, m.range.first)) {
                return m.groupValues[1]
            }
        }
        return null
    }

    // Label telanjang Inggris wajib titik dua ("ZIP: 123456",
    // "Postal: 123456") — kata "zip" biasa ("file zip 123456")
    // bukan label (issue #133).
    private val polaLabelTelanjang = Regex("""(?i)\b(?:zip|postal)[\s]*:""")

    // Angka ini milik frasa kode pos? Cari frasa kode pos terakhir
    // di jendela pandang-balik (label boleh berjarak, mis. "kode
    // pos anda adalah 123456", issue #132), lalu angka ini milik
    // frasa itu HANYA bila di antaranya tidak ada angka lain (angka
    // lain = labelnya milik angka itu) dan tidak ada kata pemicu
    // lain (pemicu lain = angkanya milik pemicu itu). Logika yang
    // sama dengan penjaga jalur konteks. Jendela 120 karakter
    // (issue #137): 40 kemarin memotong frasa yang penghubungnya
    // panjang ("Kode pos tercatat di dalam sistem kami ya: …").
    private fun berlabelKodePos(teks: String, awalAngka: Int): Boolean {
        val sebelum = teks.substring(maxOf(0, awalAngka - 120), awalAngka)
        val cocok = listOfNotNull(
            polaKodePos.findAll(sebelum).lastOrNull(),
            polaKodePosInggris.findAll(sebelum).lastOrNull(),
            polaLabelTelanjang.findAll(sebelum).lastOrNull()
        ).maxByOrNull { it.range.last } ?: return false
        val antara = sebelum.substring(cocok.range.last + 1)
        // Batas kalimat juga memutus ikatan label (issue #138):
        // frasa pos di kalimat SEBELUMNYA tidak memiliki angka di
        // kalimat ini — tanpa pemutus ini OTP tanpa pemicu sesudah
        // kalimat kode pos tertelan penjaga.
        return !antara.any { it.isDigit() } &&
            polaPemicu.find(antara) == null &&
            !adaPemutusIkatan(antara)
    }

    // Singkatan baku bertitik — titiknya BUKAN batas kalimat
    // (issue #140: "Jl."/"hal."/"No." di antara frasa dan angka
    // kemarin memutus ikatan label dan kode pos lolos sebagai OTP).
    private val singkatanTitik = setOf(
        "jl", "no", "hal", "dr", "drs", "h", "hj", "sdr", "sdri",
        "prof", "ir", "ust"
    )

    // Ada batas kalimat sungguhan di teks ini? '!' dan '?' selalu
    // batas; '.' hanya bila kata sebelumnya bukan singkatan baku
    // dan bukan inisial satu huruf.
    private fun adaBatasKalimat(teks: String): Boolean {
        for (i in teks.indices) {
            when (teks[i]) {
                '!', '?' -> return true
                '.' -> {
                    val sb = StringBuilder()
                    var j = i - 1
                    while (j >= 0 && teks[j].isLetter()) {
                        sb.append(teks[j])
                        j--
                    }
                    val kata = sb.reverse().toString().lowercase()
                    if (kata.isEmpty()) return true
                    if (kata.length > 1 && kata !in singkatanTitik) return true
                }
            }
        }
        return false
    }

    // Pemutus ikatan label di jalur cadangan: batas kalimat, atau
    // baris baru yang didahului kata nyata di baris yang sama.
    // Baris baru tepat sesudah label ("Postal code:\n123456")
    // adalah pemisah label-nilai, BUKAN pemutus (issue #141).
    private fun adaPemutusIkatan(antara: String): Boolean {
        if (adaBatasKalimat(antara)) return true
        val idx = antara.indexOf('\n')
        return idx >= 0 && antara.substring(0, idx).any { it.isLetter() }
    }
}

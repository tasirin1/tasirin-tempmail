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
            // Batas kalimat sesudah frasa juga memutus ikatan
            // (issue #142): frasa pos di kalimat sebelumnya tidak
            // memiliki angka di kalimat ini — kemarin penjaga
            // konteks menelan OTP 4 digit tanpa pemicu yang jatuh
            // sesudah kalimat kode pos. Pemutus yang dipakai sama
            // dengan jalur cadangan termasuk aturan baris barunya
            // (issue #146).
            return polaPemicu.find(sesudah) == null && !adaPemutusIkatan(sesudah)
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
            // Pemutus ikatan yang sama dengan cabang frasa
            // (issue #142, #146).
            return polaPemicu.find(sesudahPertama) == null &&
                !adaPemutusIkatan(sesudahPertama)
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
        "prof", "ir", "ust", "dsb", "dll", "tsb", "tgl", "vol",
        "pt", "an", "up", "dst", "sda", "etc",
        // Singkatan alamat/administrasi yang terbukti realistis
        // di blok alamat Indonesia (issue #151: "prov." >=4 huruf
        // lolos dari aturan struktural).
        "prov", "kec", "kel", "kab", "yth", "alm"
    )

    // Ada batas kalimat sungguhan di teks ini? '!' dan '?' selalu
    // batas. '.' hanya batas bila memenuhi SEMUA syarat struktural
    // (issue #144 — daftar singkatan tertutup sendirian selalu
    // menyisakan singkatan di luar daftar seperti dsb./tgl./PT.):
    // kata sebelumnya minimal 3 huruf dan bukan singkatan, DAN
    // karakter berikutnya (sesudah spasi) huruf kapital, angka,
    // baris baru, atau akhir teks. Ambang 3 huruf (issue #149):
    // kata penutup kalimat yang sangat umum seperti "ini"/"itu"
    // ikut tertelan pada ambang 4 — singkatan 3 huruf ditanggung
    // daftar singkatan. Kalimat yang berakhir dengan kata 2 huruf
    // adalah sisa yang diterima.
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
                    // Kata kosong = titik menempel langsung sesudah
                    // frasa/tanda lain ("Kode pos. 40286", elipsis):
                    // tidak ada kalimat yang diakhiri di sana —
                    // BUKAN batas (issue #145).
                    if (kata.length < 3 || kata in singkatanTitik) continue
                    var k = i + 1
                    while (k < teks.length && (teks[k] == ' ' || teks[k] == '\t')) k++
                    if (k >= teks.length) return true
                    val berikut = teks[k]
                    if (berikut == '\n' || berikut.isUpperCase() || berikut.isDigit()) {
                        return true
                    }
                    // Kalimat baru berhuruf kecil (gaya email
                    // informal) ikut dihitung bila kata sebelumnya
                    // panjang (>=5 huruf): singkatan sepanjang itu
                    // yang diikuti kata kecil jauh lebih jarang
                    // daripada kalimat informal (issue #150).
                    if (berikut.isLowerCase() && kata.length >= 5) return true
                }
            }
        }
        return false
    }

    // Kata penghubung pembuka baris nilai — kelanjutan label,
    // bukan kalimat baru (issue #152).
    private val penghubungBaris = setOf(
        "adalah", "is", "ialah", "yaitu", "yakni", "namely"
    )

    // Pemutus ikatan label: batas kalimat, atau baris baru yang
    // dikelilingi kata nyata — huruf sebelum baris baru pertama
    // (kalimat berakhir tanpa titik) ATAU huruf sesudahnya
    // (kalimat baru dimulai; issue #147). Baris baru yang hanya
    // dikelilingi tanda/label dan angka ("Postal code:\n123456")
    // adalah pemisah label-nilai, BUKAN pemutus (issue #141);
    // kata penghubung tunggal sesudah baris baru ("Kode pos:\n
    // adalah 123456") juga kelanjutan label (issue #152).
    private fun adaPemutusIkatan(antara: String): Boolean {
        if (adaBatasKalimat(antara)) return true
        val idx = antara.indexOf('\n')
        if (idx < 0) return false
        if (antara.substring(0, idx).any { it.isLetter() }) return true
        var sesudah = antara.substring(idx + 1).trim()
        val kataPertama = sesudah.takeWhile { it.isLetter() }.lowercase()
        if (kataPertama in penghubungBaris) {
            sesudah = sesudah.substring(kataPertama.length).trim()
        }
        return sesudah.any { it.isLetter() }
    }
}

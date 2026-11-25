package id.tasirin.tempmail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Uji logika murni OtpExtractor — nilai harapan ditulis dari bentuk
// email verifikasi yang umum, bukan dari keluaran fungsi sendiri.
class OtpExtractorTest {

    @Test
    fun kodeSesudahKataPemicu() {
        assertEquals(
            "123456",
            OtpExtractor.extract("Kode verifikasi Anda adalah 123456. Jangan bagikan ke siapa pun.")
        )
    }

    @Test
    fun otpBahasaInggris() {
        assertEquals("987654", OtpExtractor.extract("Your OTP is 987654, valid for 5 minutes."))
    }

    @Test
    fun pinEmpatDigit() {
        assertEquals("4321", OtpExtractor.extract("PIN Anda 4321. Rahasiakan."))
    }

    @Test
    fun kodeTerpencarSpasi() {
        assertEquals("123456", OtpExtractor.extract("Masukkan kode 123 456 di aplikasi."))
    }

    @Test
    fun enamDigitBerdiriSendiri() {
        assertEquals("445566", OtpExtractor.extract("Gunakan 445566 untuk masuk ke akun Anda."))
    }

    @Test
    fun tahunSesudahKataPemicuBukanKode() {
        assertNull(OtpExtractor.extract("Verification needed. Copyright 2026 PT X"))
        assertEquals(
            "778899",
            OtpExtractor.extract("Kode berlaku sampai 2026. Kode kamu 778899 ya.")
        )
    }

    @Test
    fun kodePanjangTidakTerpotong() {
        // Angka 10 digit bukan kode 8 digit yang terpotong (issue #5).
        assertNull(OtpExtractor.extract("Your verification code is 1234567890"))
    }

    @Test
    fun angkaEmpatDigitBukanKode() {
        // Nomor pesanan dan jumlah uang bukan kode verifikasi (issue #4).
        assertNull(OtpExtractor.extract("Order 1234 sudah dikirim, total Rp 5000"))
        assertNull(OtpExtractor.extract("Gunakan 4321 untuk masuk."))
    }

    @Test
    fun tahunBukanKode() {
        assertNull(OtpExtractor.extract("Copyright 2026 PT Contoh. All rights reserved."))
    }

    @Test
    fun teksTanpaKode() {
        assertNull(OtpExtractor.extract("Halo, terima kasih sudah mendaftar. Selamat datang!"))
    }

    @Test
    fun teksKosong() {
        assertNull(OtpExtractor.extract(""))
    }

    @Test
    fun konteksMenangAtasAngkaLain() {
        // Angka 6 digit lain muncul lebih dulu, tapi yang berkode konteks yang dipilih.
        assertEquals(
            "555777",
            OtpExtractor.extract("Order 998877 confirmed. Your verification code: 555777.")
        )
    }

    @Test
    fun nomorTeleponBukanKodeTerpencar() {
        // Issue #71: potongan nomor telepon berformat 08xx-xxx-xxx
        // tidak boleh dibaca sebagai kode terpencar.
        assertNull(OtpExtractor.extract("Hubungi kami di 0812-345-678 untuk bantuan"))
        // Issue #101: format spasi berkelompok 4-3-3 juga nomor telepon.
        assertNull(OtpExtractor.extract("Hubungi 0812 345 678 untuk bantuan"))
        // Kode terpencar sungguhan tetap ketemu.
        assertEquals("123456", OtpExtractor.extract("Kode kamu: 123 456"))
        assertEquals("123456", OtpExtractor.extract("kode: 123-456"))
    }

    @Test
    fun kodePosBukanKodeVerifikasi() {
        // Issue #51: frasa "Kode pos" adalah alamat surat, bukan pemicu OTP.
        assertNull(OtpExtractor.extract("Kode pos: 40286"))
        assertNull(OtpExtractor.extract("Alamat: Jl. Merdeka, Kode pos 40115, Bandung"))
        // Kode pos dilewati, kode verifikasi sungguhan tetap ketemu.
        assertEquals(
            "731204",
            OtpExtractor.extract("Kode pos: 40286. Kode verifikasi kamu 731204")
        )
    }

    @Test
    fun kodePosBertandaHubungDilewati() {
        // Issue #99: penulisan "Kode-pos" juga frasa alamat.
        assertNull(OtpExtractor.extract("Kode-pos: 40286"))
        assertNull(OtpExtractor.extract("Kode-pos 40286"))
    }

    @Test
    fun kodePosInggrisDilewati() {
        // Issue #100: "ZIP code"/"postal code" juga frasa alamat.
        assertNull(OtpExtractor.extract("ZIP code: 90210"))
        assertNull(OtpExtractor.extract("Postal code 40115 Indonesia"))
        // Kode sungguhan berbahasa Inggris tetap ketemu.
        assertEquals("112233", OtpExtractor.extract("Your verification code is 112233"))
    }

    @Test
    fun zipCodeSebelumPemicuLainTidakMenelanKode() {
        // Issue #122: "code" milik "zip code" berpasangan dengan zip
        // di luar jendela — angka milik pemicu berikutnya tetap
        // terdeteksi; zip code yang berdiri sendiri tetap dilewati.
        assertEquals(
            "4455",
            OtpExtractor.extract("Your zip code and verification code is 4455")
        )
        assertEquals("4455", OtpExtractor.extract("ZIP code: none. Your code: 4455"))
        assertNull(OtpExtractor.extract("Your ZIP code is 90210"))
    }

    @Test
    fun kataBerakhiranZipBukanKodePos() {
        // Issue #117: "unzip" tepat sebelum kata "code" bukan penanda
        // kode pos — kode konteksnya tetap terdeteksi.
        assertEquals("4455", OtpExtractor.extract("Unzip code: 4455"))
    }

    @Test
    fun kandidatSahSesudahKodePosTetapKetemu() {
        // Issue #131: kandidat pertama berlabel kode pos tidak boleh
        // membunuh kandidat sah berikutnya di jalur cadangan.
        assertEquals(
            "654321",
            OtpExtractor.extract("Kode pos: 123456. Gunakan 654321 untuk masuk.")
        )
        assertEquals(
            "789012",
            OtpExtractor.extract("Kode pos: 123 456. Kode kamu 789 012")
        )
    }

    @Test
    fun frasaPosBarisSebelumnyaTidakMenelanOtpKonteks() {
        // Issue #146: pemutus baris baru berlaku juga di jalur
        // konteks — OTP 4 digit di baris berikut tetap ketemu.
        assertEquals(
            "4455",
            OtpExtractor.extract("Kode pos dicatat\nGunakan 4455 untuk masuk")
        )
    }

    @Test
    fun frasaPosKalimatSebelumnyaTidakMenelanOtpKonteks() {
        // Issue #142: penjaga konteks kini juga memutus ikatan pada
        // batas kalimat — OTP 4 digit tanpa pemicu sesudah kalimat
        // kode pos tetap ketemu.
        assertEquals(
            "4455",
            OtpExtractor.extract("Kode pos tersimpan. Gunakan 4455 untuk masuk.")
        )
        assertEquals(
            "4455",
            OtpExtractor.extract("Your zip code is stored. Use 4455 for login")
        )
    }

    @Test
    fun barisBaruSesudahLabelBukanPemutus() {
        // Issue #141: label di satu baris, nilainya di baris
        // berikut — ikatan label tetap berlaku.
        assertNull(OtpExtractor.extract("Postal code:\n123456"))
        assertNull(OtpExtractor.extract("Kode pos:\n123 456"))
        // Kalimat yang berakhir di ujung baris tetap pemutus.
        assertEquals(
            "445566",
            OtpExtractor.extract("Kode pos dicatat\nGunakan 445566 untuk masuk")
        )
        // Issue #147: label menutup barisnya sendiri, kalimat OTP
        // di baris berikut — huruf sesudah baris baru ikut memutus.
        assertEquals(
            "445566",
            OtpExtractor.extract("Kode pos:\nGunakan 445566 untuk masuk")
        )
    }

    @Test
    fun kataPenutupTigaHurufUmumAdalahBatas() {
        // Issue #149: kalimat berakhir "ini"/"itu" adalah batas
        // kalimat sungguhan — OTP kalimat berikut tidak tertelan.
        assertEquals(
            "445566",
            OtpExtractor.extract(
                "Kode pos sudah kami catat untuk alamat ini. Gunakan 445566 untuk masuk."
            )
        )
        assertEquals(
            "4455",
            OtpExtractor.extract("Kode pos untuk alamat itu. Gunakan 4455 untuk masuk.")
        )
    }

    @Test
    fun singkatanDiLuarDaftarUmumBukanBatas() {
        // Issue #144: aturan struktural menangkap singkatan yang
        // tidak terdaftar satu per satu.
        assertNull(OtpExtractor.extract("Kode pos dsb. 40286"))
        assertNull(OtpExtractor.extract("Kode pos dll. tercatat 123456"))
        assertNull(OtpExtractor.extract("Kode pos PT. Maju adalah 123456"))
    }

    @Test
    fun titikMenempelSesudahFrasaBukanBatas() {
        // Issue #145: "Kode pos." / elipsis tidak memutus ikatan.
        assertNull(OtpExtractor.extract("Kode pos. 40286"))
        assertNull(OtpExtractor.extract("Kode pos... 123456"))
    }

    @Test
    fun titikSingkatanBukanBatasKalimat() {
        // Issue #140: titik singkatan di antara frasa dan angka
        // tidak memutus ikatan label.
        assertNull(OtpExtractor.extract("Kode pos untuk Jl. Merdeka adalah 123456"))
        assertNull(OtpExtractor.extract("Kode pos tertulis di hal. 123456"))
        assertNull(OtpExtractor.extract("kode pos sesuai alamat No. 123 456"))
    }

    @Test
    fun batasKalimatMemutusIkatanLabel() {
        // Issue #138: frasa kode pos di kalimat sebelumnya tidak
        // menelan OTP tanpa pemicu di kalimat ini.
        assertEquals(
            "445566",
            OtpExtractor.extract("Kode pos dicatat. Gunakan 445566 untuk masuk.")
        )
        assertEquals(
            "445566",
            OtpExtractor.extract("Kode pos dicatat.\nGunakan 445566 untuk masuk.")
        )
    }

    @Test
    fun kodePosPenghubungPanjangDilewati() {
        // Issue #137: frasa dengan penghubung panjang (>32 karakter)
        // tetap dikenali penjaga jalur cadangan.
        assertNull(
            OtpExtractor.extract("Kode pos tercatat di dalam sistem kami ya: 123456")
        )
        assertNull(
            OtpExtractor.extract("ZIP code associated with your billing address: 123456")
        )
    }

    @Test
    fun kodePosBerjarakDilewatiDiJalurCadangan() {
        // Issue #132: label berjarak kata penghubung ikut dikenali
        // di jalur cadangan, selaras jalur konteks.
        assertNull(OtpExtractor.extract("kode pos anda adalah 123456"))
        assertNull(OtpExtractor.extract("kode pos anda adalah 123 456"))
        // Label milik angka LAIN tidak menelan kode berikutnya.
        assertEquals(
            "654321",
            OtpExtractor.extract("Kode pos: 40286. Gunakan 654321")
        )
    }

    @Test
    fun kataZipBukanLabelTidakMenelanKode() {
        // Issue #133: "zip" di dalam kata lain atau sebagai kata
        // biasa bukan label kode pos — kode sah tetap ketemu.
        assertEquals("123456", OtpExtractor.extract("unzip 123456"))
        assertEquals("123456", OtpExtractor.extract("file zip 123456"))
        // Label telanjang dengan titik dua tetap penjaga.
        assertNull(OtpExtractor.extract("Postal: 123456"))
    }

    @Test
    fun kodePosEnamDigitDanTerpencarDilewati() {
        // Issue #128: penjaga kode pos juga berlaku di jalur cadangan
        // (pola 6 digit dan pola terpencar), bukan hanya jalur konteks.
        assertNull(OtpExtractor.extract("Kode pos: 123456"))
        assertNull(OtpExtractor.extract("Kode pos: 123 456"))
        assertNull(OtpExtractor.extract("ZIP code: 123 456"))
    }

    @Test
    fun kodePosPosesifKuMuDilewati() {
        // Issue #124: sufiks posesif menempel -ku/-mu juga frasa
        // alamat — jangan tampil sebagai kode verifikasi.
        assertNull(OtpExtractor.extract("Kode posku: 40115"))
        assertNull(OtpExtractor.extract("kode posmu 40115"))
        // Issue #127: -kau/-anda menempel juga.
        assertNull(OtpExtractor.extract("kode poskau 40115"))
        assertNull(OtpExtractor.extract("kode posanda 40115"))
        // Issue #134: -na (Sunda) menempel juga.
        assertNull(OtpExtractor.extract("kode posna 40115"))
    }

    @Test
    fun kodePosnyaDenganSufiksNyaDilewati() {
        // Issue #116: frasa alamat "kode posnya …" jangan tampil
        // sebagai kode verifikasi.
        assertNull(OtpExtractor.extract("Kode Posnya: 40286"))
        assertNull(OtpExtractor.extract("Jl. Merdeka No. 5, kode posnya 40115"))
    }

    @Test
    fun kodePosSesudahPemicuLainTetapDilewati() {
        // Issue #85: kata pemicu lain mendahului "kode pos" — penjaga
        // berjangkar awal lolos; angka tetap kode pos, bukan OTP.
        assertNull(OtpExtractor.extract("Kode verifikasi dan kode pos: 40286"))
        assertNull(OtpExtractor.extract("PIN dan kode pos: 40286"))
        // Tapi kode milik pemicu yang datang SESUDAH penyebutan kode
        // pos (tanpa angka di antaranya) tidak boleh ikut tertelan.
        assertEquals(
            "112233",
            OtpExtractor.extract("Kode pos dicatat. Kode verifikasi: 112233")
        )
    }
}

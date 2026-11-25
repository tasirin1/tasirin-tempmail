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

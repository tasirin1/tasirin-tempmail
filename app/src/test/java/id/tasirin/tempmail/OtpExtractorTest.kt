package id.tasirin.tempmail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertTrue(true)
    }
}

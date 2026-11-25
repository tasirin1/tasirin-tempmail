package id.tasirin.tempmail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// Uji bagian murni DropMail — komposisi alamat dan format waktu,
// pola KittenProviderTest. Jalur jaringan (token/sesi GraphQL)
// tidak diuji di JVM, sama seperti provider sesi lain.
class DropMailProviderTest {

    private val p = DropMailProvider()

    @Test
    fun daftarDomainKosong() {
        // Domain DropMail berganti-ganti dan ditentukan server —
        // pemilih domain disembunyikan (daftar kosong).
        assertEquals(emptyList<String>(), p.domains())
    }

    @Test
    fun alamatCustomDitolak() {
        try {
            p.alamatCustom("budi", "dropmail.me")
            fail("nama sendiri harus ditolak")
        } catch (e: IllegalStateException) {
            // Sesuai harapan: nama lokal ditentukan server.
        }
    }

    @Test
    fun waktuLokalDariIsoGraphql() {
        // Stempel email uji sungguhan (2026-11-25 11:43 UTC).
        val teks = DropMailProvider.waktuLokal("2026-11-25T11:43:07+00:00")
        assertTrue(teks.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")))
        assertTrue(teks.startsWith("2026-11-25"))
    }

    @Test
    fun waktuLokalTidakSahKosong() {
        assertEquals("", DropMailProvider.waktuLokal(""))
        assertEquals("", DropMailProvider.waktuLokal("besok"))
        assertEquals("", DropMailProvider.waktuLokal("2026-13-45T99:99:99+00:00"))
    }
}

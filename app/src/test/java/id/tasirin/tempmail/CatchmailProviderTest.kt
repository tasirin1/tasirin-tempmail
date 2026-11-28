package id.tasirin.tempmail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// Uji komposisi alamat + pembersih pengirim + format waktu
// Catchmail — bagian murni yang tidak menyentuh jaringan/org.json,
// pola KittenProviderTest.
class CatchmailProviderTest {

    private val p = CatchmailProvider()

    @Test
    fun daftarDomainCatchmail() {
        assertEquals(
            listOf("catchmail.io", "mailistry.com", "zeppost.com"),
            p.domains()
        )
    }

    @Test
    fun alamatAcakMengikutiDomainPilihan() {
        repeat(25) {
            val alamat = p.alamatAcak(null)
            assertEquals("catchmail.io", alamat.substringAfter("@"))
            assertTrue(alamat.substringBefore("@").isNotEmpty())
        }
        assertEquals("zeppost.com", p.alamatAcak("zeppost.com").substringAfter("@"))
        // Pilihan domain sisa layanan lain jatuh ke domain utama.
        assertEquals("catchmail.io", p.alamatAcak("mailto.plus").substringAfter("@"))
    }

    @Test
    fun alamatCustomMerangkaiNamaDanDomain() {
        assertEquals("budi@mailistry.com", p.alamatCustom("budi", "mailistry.com"))
    }

    @Test
    fun alamatCustomDomainAsingDitolak() {
        try {
            p.alamatCustom("budi", "mailto.plus")
            fail("domain asing harus ditolak")
        } catch (e: IllegalStateException) {
            // Sesuai harapan (pola issue #62).
        }
    }

    @Test
    fun pengirimKurungSudutDibersihkan() {
        // Bentuk persis dari email uji sungguhan 2026-11-28.
        assertEquals(
            "juliustasirin@gmail.com",
            CatchmailProvider.bersihkanPengirim("<juliustasirin@gmail.com>")
        )
        assertEquals(
            "a@b.com",
            CatchmailProvider.bersihkanPengirim("Nama Tampil <a@b.com>")
        )
        assertEquals("a@b.com", CatchmailProvider.bersihkanPengirim("a@b.com"))
        assertEquals("", CatchmailProvider.bersihkanPengirim(""))
    }

    @Test
    fun waktuLokalDariIsoBerpecahanMikro() {
        // Stempel persis email uji sungguhan (14:03 UTC = 21:03 WIB);
        // formatnya tanggal + jam:menit, harinya tetap 2026-11-28.
        val teks = CatchmailProvider.waktuLokal("2026-11-28T14:03:49.753000+00:00")
        assertTrue(teks.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")))
        assertTrue(teks.startsWith("2026-11-28"))
    }

    @Test
    fun waktuLokalTidakSahKosong() {
        assertEquals("", CatchmailProvider.waktuLokal(""))
        assertEquals("", CatchmailProvider.waktuLokal("bukan tanggal"))
    }
}

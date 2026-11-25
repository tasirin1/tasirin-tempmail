package id.tasirin.tempmail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// Uji komposisi alamat + format waktu InboxKitten — bagian murni
// yang tidak menyentuh jaringan/org.json, pola PlusProviderTest.
class KittenProviderTest {

    private val p = KittenProvider()

    @Test
    fun daftarDomainHanyaInboxKitten() {
        assertEquals(listOf("inboxkitten.com"), p.domains())
    }

    @Test
    fun alamatAcakSelaluDomainInboxKitten() {
        repeat(25) {
            val alamat = p.alamatAcak(null)
            assertEquals("inboxkitten.com", alamat.substringAfter("@"))
            assertTrue(alamat.substringBefore("@").isNotEmpty())
        }
        // Pilihan domain sisa layanan lain jatuh ke domain ini juga.
        assertEquals("inboxkitten.com", p.alamatAcak("mailto.plus").substringAfter("@"))
    }

    @Test
    fun alamatCustomMerangkaiNamaDanDomain() {
        assertEquals("budi@inboxkitten.com", p.alamatCustom("budi", "inboxkitten.com"))
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
    fun waktuLokalDariEpochDetik() {
        // 1795605767.492991 adalah stempel email uji sungguhan
        // (2026-11-25 sore WIB) — formatnya tanggal + jam:menit.
        val teks = KittenProvider.waktuLokal(1795605767.492991)
        assertTrue(teks.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")))
        assertTrue(teks.startsWith("2026-11-25"))
    }

    @Test
    fun waktuLokalTidakSahKosong() {
        assertEquals("", KittenProvider.waktuLokal(0.0))
        assertEquals("", KittenProvider.waktuLokal(-5.0))
    }
}

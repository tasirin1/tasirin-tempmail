package id.tasirin.tempmail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// Uji komposisi SUNGGUHAN yang dipakai produksi (PlusProvider) —
// menggantikan tes tautologi lama yang menyusun alamat di dalam tes
// lalu menegaskan daftar yang sama (issue #103).
class PlusProviderTest {

    private val p = PlusProvider()

    @Test
    fun alamatAcakMemakaiDomainResmi() {
        repeat(25) {
            val alamat = p.alamatAcak(null)
            assertTrue(alamat.substringAfter("@") in AddressGenerator.DOMAINS)
            assertTrue(alamat.substringBefore("@").isNotEmpty())
        }
    }

    @Test
    fun alamatAcakMenghormatiDomainPilihan() {
        val d = AddressGenerator.DOMAINS.first()
        assertEquals(d, p.alamatAcak(d).substringAfter("@"))
    }

    @Test
    fun alamatAcakDomainAsingJatuhKeDomainResmi() {
        val alamat = p.alamatAcak("domain-asing.example")
        assertTrue(alamat.substringAfter("@") in AddressGenerator.DOMAINS)
    }

    @Test
    fun alamatCustomMerangkaiNamaDanDomain() {
        val d = AddressGenerator.DOMAINS.first()
        assertEquals("budi@$d", p.alamatCustom("budi", d))
    }

    @Test
    fun alamatCustomDomainAsingDitolak() {
        try {
            p.alamatCustom("budi", "domain-asing.example")
            fail("domain asing harus ditolak")
        } catch (e: IllegalStateException) {
            // Sesuai harapan (issue #62).
        }
    }
}

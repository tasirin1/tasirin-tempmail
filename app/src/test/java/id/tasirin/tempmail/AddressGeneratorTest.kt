package id.tasirin.tempmail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

// Uji logika murni AddressGenerator.
class AddressGeneratorTest {

    @Test
    fun namaAcakPanjangDanKarakterSah() {
        val nama = AddressGenerator.randomLocalPart(12, Random(42))
        assertEquals(12, nama.length)
        assertTrue(nama.first() in 'a'..'z')
        assertTrue(nama.all { it in 'a'..'z' || it in '0'..'9' })
    }

    // Tes alamat acak dipindah ke PlusProviderTest: komposisi produksi
    // diuji langsung, bukan disusun ulang di dalam tes (issue #103).

    @Test
    fun sanitizeMembersihkanNama() {
        assertEquals("budi.santoso", AddressGenerator.sanitizeLocalPart("Budi.Santoso!"))
        assertEquals("tasirin_01", AddressGenerator.sanitizeLocalPart("  Tasirin_01 "))
        assertEquals("", AddressGenerator.sanitizeLocalPart("..."))
        assertEquals("", AddressGenerator.sanitizeLocalPart("!!!"))
    }

    @Test
    fun sanitizeMerapatkanTitikGandaDanMembatasiPanjang() {
        assertEquals("budi.santoso", AddressGenerator.sanitizeLocalPart("budi..santoso"))
        assertEquals(64, AddressGenerator.sanitizeLocalPart("a".repeat(80)).length)
    }
}

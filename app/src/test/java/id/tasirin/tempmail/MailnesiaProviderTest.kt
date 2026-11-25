package id.tasirin.tempmail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// Uji komposisi alamat, format waktu, dan pengurai RSS Mailnesia —
// bagian murni yang tidak menyentuh jaringan, pola KittenProviderTest.
// Contoh feed di bawah adalah salinan persis feed email uji
// sungguhan (2026-11-25, id pesan 1305549607).
class MailnesiaProviderTest {

    private val p = MailnesiaProvider()

    @Test
    fun daftarDomainHanyaMailnesia() {
        assertEquals(listOf("mailnesia.com"), p.domains())
    }

    @Test
    fun alamatAcakSelaluDomainMailnesia() {
        repeat(25) {
            val alamat = p.alamatAcak(null)
            assertEquals("mailnesia.com", alamat.substringAfter("@"))
            assertTrue(alamat.substringBefore("@").isNotEmpty())
        }
        assertEquals("mailnesia.com", p.alamatAcak("mailto.plus").substringAfter("@"))
    }

    @Test
    fun alamatCustomMerangkaiNamaDanDomain() {
        assertEquals("budi@mailnesia.com", p.alamatCustom("budi", "mailnesia.com"))
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
    fun waktuLokalDariPubDateRss() {
        val teks = MailnesiaProvider.waktuLokal("Wed, 25 Nov 2026 03:47:01 -0800")
        assertTrue(teks.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")))
        assertTrue(teks.startsWith("2026-11-25"))
    }

    @Test
    fun waktuLokalTidakSahKosong() {
        assertEquals("", MailnesiaProvider.waktuLokal(""))
        assertEquals("", MailnesiaProvider.waktuLokal("besok"))
    }

    @Test
    fun uraiRssEmailUjiSungguhan() {
        val feed = """
            <rss version="2.0">
              <channel>
                <link>https://mailnesia.com</link>
                <title>aliveuji25 @ Mailnesia</title>
                <description>Anonymous Email in Seconds</description>
                <item>
                  <title>Uji kode 654321 – juliustasirin@gmail.com</title>
                  <link>https://mailnesia.com/mailbox/aliveuji25/1305549607</link>
                  <guid>https://mailnesia.com/mailbox/aliveuji25/1305549607</guid>
                  <description><![CDATA[ Halo, ini email uji buat Mailnesia. Kode verifikasi kamu 654321.
            Abaikan email ini. ]]></description>
                  <pubDate>Wed, 25 Nov 2026 03:47:01 -0800</pubDate>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val hasil = MailnesiaProvider.uraiRss(feed)
        assertEquals(1, hasil.size)
        val (item, detail) = hasil[0]
        assertEquals("1305549607", item.id)
        assertEquals("Uji kode 654321", item.subject)
        assertEquals("juliustasirin@gmail.com", item.from)
        assertTrue(item.time.startsWith("2026-11-25"))
        assertTrue(detail.text.startsWith("Halo, ini email uji buat Mailnesia."))
        assertTrue(detail.text.contains("Kode verifikasi kamu 654321."))
        assertEquals(item.from, detail.from)
        assertEquals(item.subject, detail.subject)
    }

    @Test
    fun uraiRssFeedKosong() {
        val feed = """
            <rss version="2.0">
              <channel>
                <link>https://mailnesia.com</link>
                <title>aliveuji25 @ Mailnesia</title>
                <description>Anonymous Email in Seconds</description>
              </channel>
            </rss>
        """.trimIndent()
        assertTrue(MailnesiaProvider.uraiRss(feed).isEmpty())
    }

    @Test
    fun uraiRssSubjekMemuatPemisahDanIdBukanAngka() {
        // Subjek yang sendiri memuat " – " harus utuh (pisah di
        // pemisah terakhir); tautan tanpa id angka -> id sintetis.
        val feed = """
            <rss version="2.0">
              <channel>
                <item>
                  <title>Kode – Penting – a@b.c</title>
                  <link>https://mailnesia.com/mailbox/uji/abc</link>
                  <description><![CDATA[isi saja]]></description>
                  <pubDate>Wed, 25 Nov 2026 03:47:01 -0800</pubDate>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val hasil = MailnesiaProvider.uraiRss(feed)
        assertEquals(1, hasil.size)
        assertEquals("Kode – Penting", hasil[0].first.subject)
        assertEquals("a@b.c", hasil[0].first.from)
        assertTrue(hasil[0].first.id.startsWith("tanpa-id-"))
        assertEquals("isi saja", hasil[0].second.text)
    }
}

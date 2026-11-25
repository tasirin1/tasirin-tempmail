package id.tasirin.tempmail

import org.junit.Assert.assertEquals
import org.junit.Test

// Uji logika murni TempMailApi.htmlToText — nilai harapan ditulis dari
// arti HTML-nya, bukan dari keluaran fungsi sendiri.
class TempMailApiTest {

    @Test
    fun scriptDanStyleDibuangBersamaIsinya() {
        assertEquals(
            "Halo\nkode 123 selesai",
            TempMailApi.htmlToText(
                "<style>.a{color:red}</style>" +
                    "<p>Halo<br>kode &#49;&#50;&#51; selesai</p>" +
                    "<script>var x=123456;</script>"
            )
        )
    }

    @Test
    fun entitasEmojiUtuhDanEntitasMustahilAman() {
        assertEquals("\uD83D\uDE00", TempMailApi.htmlToText("&#128512;"))
        assertEquals("\uD83D\uDE00", TempMailApi.htmlToText("&#x1F600;"))
        // Tidak melempar; dibiarkan apa adanya (issue #27).
        assertEquals("&#999999999999;", TempMailApi.htmlToText("&#999999999999;"))
    }

    @Test
    fun entitasUmumDanHexDiterjemahkan() {
        assertEquals(
            "\"Halo\" & AB",
            TempMailApi.htmlToText("&quot;Halo&quot; &amp; &#x41;&#66;")
        )
    }

    @Test
    fun entitasBerkodeGandaTidakDiterjemahkanDuaKali() {
        // Issue #73: &amp;lt; adalah teks "&lt;" yang ditulis pengirim
        // secara sengaja (mis. contoh kode HTML di email) — tampil apa
        // adanya, bukan berubah jadi "<".
        assertEquals(
            "&lt;div&gt;",
            TempMailApi.htmlToText("&amp;lt;div&amp;gt;")
        )
        assertEquals("<div>", TempMailApi.htmlToText("&lt;div&gt;"))
    }

    @Test
    fun encPathMengenkodeSegmenPath() {
        // Issue #104: perilaku encPath dikunci tes — spasi jadi %20
        // (bukan '+'), plus asli jadi %2B, id numerik apa adanya.
        assertEquals("a%20b", TempMailApi.encPath("a b"))
        assertEquals("a%2Bb", TempMailApi.encPath("a+b"))
        assertEquals("42582210", TempMailApi.encPath("42582210"))
    }

    @Test
    fun ampersandNumerikTidakDecodeDuaKali() {
        // Issue #102: &#38; / &#x26; adalah '&' numerik — rangkaian
        // "&lt;" yang terbentuk darinya adalah teks, bukan entitas.
        assertEquals("&lt;", TempMailApi.htmlToText("&#38;lt;"))
        assertEquals("&lt;", TempMailApi.htmlToText("&#x26;lt;"))
        // Ampersand numerik yang berdiri sendiri tetap jadi '&'.
        assertEquals("A & B", TempMailApi.htmlToText("A &#38; B"))
        assertEquals("&#49;", TempMailApi.htmlToText("&#38;#49;"))
    }

    @Test
    fun karakterPenandaAsliPengirimTidakBerubah() {
        // Issue #118: U+E000 harfiah di isi email adalah milik
        // pengirim — tidak boleh tampil sebagai '&'.
        assertEquals("teks \uE000 polos", TempMailApi.htmlToText("teks \uE000 polos"))
        assertEquals(
            "campur \uE000 dan &",
            TempMailApi.htmlToText("campur \uE000 dan &#38;")
        )
    }
}

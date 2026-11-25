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
}

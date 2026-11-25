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
    fun entitasUmumDanHexDiterjemahkan() {
        assertEquals(
            "\"Halo\" & AB",
            TempMailApi.htmlToText("&quot;Halo&quot; &amp; &#x41;&#66;")
        )
    }
}

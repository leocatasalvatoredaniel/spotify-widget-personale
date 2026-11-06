package it.personale.spotifywidget

import it.personale.spotifywidget.widget.SystemFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemFontTest {
    private val aosp = """
        <familyset version="23">
            <family name="sans-serif">
                <font weight="400" style="normal">Roboto-Regular.ttf
                  <axis tag="wght" stylevalue="400" />
                </font>
            </family>
            <alias name="sans-serif-medium" to="sans-serif" weight="500" />
            <family lang="und-Arab" variant="elegant">
                <font weight="400" style="normal">NotoNaskhArabic-Regular.ttf</font>
            </family>
        </familyset>
    """.trimIndent()

    @Test
    fun androidOnlyHasNoSamsungFont() {
        assertNull(SystemFont.parse(listOf(aosp)))
    }

    @Test
    fun samsungFamilyAndItsMediumWeight() {
        val samsung = """
            <familyset>
                <family name="sec">
                    <font weight="400" style="normal">SamsungOneUI-Regular.ttf</font>
                    <font weight="600" style="normal">SamsungOneUI-SemiBold.ttf</font>
                </family>
                <alias name="sec-600" to="sec" weight="600" />
                <alias name="sec-500" to="sec" weight="500" />
                <alias name="sec-700" to="sec" weight="700" />
            </familyset>
        """.trimIndent()
        val font = SystemFont.parse(listOf(aosp, samsung))!!
        assertEquals("sec", font.family)
        assertEquals("sec-500", font.medium)
        assertEquals("SamsungOneUI-Regular.ttf", font.file)
    }

    @Test
    fun oneUiSansWinsOverOlderSamsungFonts() {
        val samsung = """
            <family name="sec-roboto-light"><font weight="300" style="normal">SECRobotoLight-Regular.ttf</font></family>
            <family name="one-ui-sans"><font weight="400" style="normal">OneUISans-VF.ttf</font></family>
        """.trimIndent()
        val font = SystemFont.parse(listOf(samsung))!!
        assertEquals("one-ui-sans", font.family)
        assertNull(font.medium)
    }
}

package com.butaca13.puzzles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The policy lives in two places and makes promises about the app: keep all of that honest. */
class PrivacyPolicyTest {

    private val strings = File("src/main/res/values/strings.xml").readText()
    private val page = File("../docs/privacidad.html").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    private fun appPolicy(): List<String> =
        Regex("""<string name="privacy_[a-z_]+">(.*?)</string>""").findAll(strings)
            .map { it.groupValues[1] }
            .toList()

    private fun webText(): String =
        page.substringAfter("<main>").substringBefore("</main>").replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ")

    @Test
    fun `the web page says everything the in-app policy says`() {
        val web = webText()

        for (text in appPolicy()) {
            if (text == "Política de privacidad") continue
            assertTrue("missing on the web page: $text", web.contains(text.replace(Regex("\\s+"), " ")))
        }
    }

    @Test
    fun `the app connects to the internet, and the policy says what for and how to stop it`() {
        assertTrue(manifest.contains("android.permission.INTERNET"))
        assertTrue(appPolicy().any { it.contains("envía tu jugador al servidor") })
        assertTrue(appPolicy().any { it.contains("modo sin conexión") })
    }

    @Test
    fun `release builds send nothing over plain HTTP`() {
        val config = File("src/main/res/xml/network_security_config.xml").readText()

        assertTrue(manifest.contains("@xml/network_security_config"))
        assertTrue(config.contains("""cleartextTrafficPermitted="false""""))
        assertFalse(config.contains("""cleartextTrafficPermitted="true""""))
    }

    @Test
    fun `the app really can be backed up, as the policy warns`() {
        assertEquals(true, manifest.contains("""android:allowBackup="true""""))
        assertTrue(appPolicy().any { it.contains("copia de seguridad de Android") })
    }
}

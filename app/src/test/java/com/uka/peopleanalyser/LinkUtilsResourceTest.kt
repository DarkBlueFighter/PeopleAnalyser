package com.uka.peopleanalyser

import org.junit.Assert
import org.junit.Test
import java.io.File

class LinkUtilsResourceTest {

    private fun findFile(vararg candidates: String): File? {
        val cwd = System.getProperty("user.dir") ?: ""
        // Try candidates relative to current working dir and also one level up (project root)
        for (base in listOf(cwd, File(cwd).parent ?: "")) {
            for (cand in candidates) {
                val f = File(base, cand)
                if (f.exists()) return f
            }
        }
        // last resort: try absolute candidates
        for (cand in candidates) {
            val f = File(cand)
            if (f.exists()) return f
        }
        return null
    }

    @Test
    fun copiedMessageStringExists() {
        val resFile = findFile("src/main/res/values/strings.xml", "app/src/main/res/values/strings.xml")
        Assert.assertNotNull("strings.xml not found in expected locations", resFile)
        val content = resFile!!.readText()
        Assert.assertTrue("copied_message not defined in strings.xml", content.contains("copied_message"))
    }

    @Test
    fun linkUtilsUsesCopiedMessageResource() {
        val ktFile = findFile("src/main/java/com/uka/peopleanalyser/LinkUtils.kt", "app/src/main/java/com/uka/peopleanalyser/LinkUtils.kt")
        Assert.assertNotNull("LinkUtils.kt not found in expected locations", ktFile)
        val content = ktFile!!.readText()
        Assert.assertTrue("LinkUtils.kt should reference R.string.copied_message", content.contains("R.string.copied_message"))
    }
}

package net.transgressoft.commons.music.m3u

import com.google.common.jimfs.Configuration
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/**
 * Verifies M3U parsing on a Windows-style filesystem: backslash separators, drive-letter
 * absolute paths, and case-insensitive nested playlist detection.
 *
 * Uses Jimfs with [Configuration.windows] so the test is portable on Unix CI runners.
 */
@DisplayName("M3uParser on Windows filesystem")
internal class M3uWindowsPathTest : StringSpec({

    data class WindowsPathCase(
        val label: String,
        val fileName: String,
        val content: String,
        val expectedEntries: List<String>,
        val absoluteFirst: Boolean = false
    )

    withData(
        nameFn = { it.label },
        WindowsPathCase(
            label = "resolves backslash-separated relative paths against base directory",
            fileName = "Mix.m3u",
            content =
                """
                #EXTM3U
                ..\Library\Alien\Interterrestial\04 Hold It Now.flac
                ..\Library\BUNTek\Hardamuffin\04 Bongo instrumental.flac
                """.trimIndent(),
            expectedEntries =
                listOf(
                    "C:\\Music\\Library\\Alien\\Interterrestial\\04 Hold It Now.flac",
                    "C:\\Music\\Library\\BUNTek\\Hardamuffin\\04 Bongo instrumental.flac"
                )
        ),
        WindowsPathCase(
            label = "accepts drive-letter absolute paths from the playlist",
            fileName = "Absolute.m3u",
            content =
                """
                #EXTM3U
                C:\Library\NeoKaoss\Naciones Hundidas\12 WeAreMutties.flac
                """.trimIndent(),
            expectedEntries = listOf("C:\\Library\\NeoKaoss\\Naciones Hundidas\\12 WeAreMutties.flac"),
            absoluteFirst = true
        ),
        WindowsPathCase(
            label = "tolerates mixed forward and back slashes in relative paths",
            // Real-world .m3u files often use forward slashes even on Windows
            fileName = "Mixed.m3u",
            content = "../Library/Alien/Interterrestial/04 Hold It Now.flac\n",
            expectedEntries = listOf("C:\\Music\\Library\\Alien\\Interterrestial\\04 Hold It Now.flac")
        )
    ) { case ->
        parseM3uOnFs(Configuration.windows(), "C:\\Music\\Playlists", case.fileName, case.content) { fs, _, result ->
            result.entries shouldHaveSize case.expectedEntries.size
            result.entries.map { it.resolvedPath } shouldBe case.expectedEntries.map { fs.getPath(it) }
            if (case.absoluteFirst) {
                result.entries[0].resolvedPath.isAbsolute shouldBe true
            }
        }
    }

    "classifies nested .m3u and .M3U8 entries case-insensitively on Windows" {
        parseM3uOnFs(
            Configuration.windows(),
            "C:\\Music\\Playlists",
            "Parent.m3u",
            """
            #EXTM3U
            Raver\Core.M3U
            Raver\CoreTunel.m3u8
            """.trimIndent()
        ) { _, _, result ->
            result.entries shouldHaveSize 0
            result.nestedPlaylists shouldHaveSize 2
            result.nestedPlaylists.map { it.fileName.toString() } shouldBe
                listOf("Core.M3U", "CoreTunel.m3u8")
        }
    }
})
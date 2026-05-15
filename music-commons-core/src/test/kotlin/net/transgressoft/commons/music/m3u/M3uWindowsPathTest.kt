package net.transgressoft.commons.music.m3u

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.nio.file.Files

/**
 * Verifies M3U parsing on a Windows-style filesystem: backslash separators, drive-letter
 * absolute paths, and case-insensitive nested playlist detection.
 *
 * Uses Jimfs with [Configuration.windows] so the test is portable on Unix CI runners.
 */
@DisplayName("M3uParser on Windows filesystem")
internal class M3uWindowsPathTest : StringSpec({

    "resolves backslash-separated relative paths against base directory" {
        Jimfs.newFileSystem(Configuration.windows()).use { fs ->
            val baseDir = fs.getPath("C:\\Music\\Playlists")
            Files.createDirectories(baseDir)
            val m3uPath = baseDir.resolve("Mix.m3u")
            Files.writeString(
                m3uPath,
                """
                #EXTM3U
                ..\Library\Alien\Interterrestial\04 Hold It Now.flac
                ..\Library\BUNTek\Hardamuffin\04 Bongo instrumental.flac
                """.trimIndent()
            )

            val result = M3uParser(baseDir).parse(m3uPath)

            result.entries shouldHaveSize 2
            result.entries[0].resolvedPath shouldBe
                fs.getPath("C:\\Music\\Library\\Alien\\Interterrestial\\04 Hold It Now.flac")
            result.entries[1].resolvedPath shouldBe
                fs.getPath("C:\\Music\\Library\\BUNTek\\Hardamuffin\\04 Bongo instrumental.flac")
        }
    }

    "accepts drive-letter absolute paths from the playlist" {
        Jimfs.newFileSystem(Configuration.windows()).use { fs ->
            val baseDir = fs.getPath("C:\\Music\\Playlists")
            Files.createDirectories(baseDir)
            val m3uPath = baseDir.resolve("Absolute.m3u")
            Files.writeString(
                m3uPath,
                """
                #EXTM3U
                C:\Library\NeoKaoss\Naciones Hundidas\12 WeAreMutties.flac
                """.trimIndent()
            )

            val result = M3uParser(baseDir).parse(m3uPath)

            result.entries shouldHaveSize 1
            result.entries[0].resolvedPath shouldBe
                fs.getPath("C:\\Library\\NeoKaoss\\Naciones Hundidas\\12 WeAreMutties.flac")
            result.entries[0].resolvedPath.isAbsolute shouldBe true
        }
    }

    "classifies nested .m3u and .M3U8 entries case-insensitively on Windows" {
        Jimfs.newFileSystem(Configuration.windows()).use { fs ->
            val baseDir = fs.getPath("C:\\Music\\Playlists")
            Files.createDirectories(baseDir)
            val m3uPath = baseDir.resolve("Parent.m3u")
            Files.writeString(
                m3uPath,
                """
                #EXTM3U
                Raver\Core.M3U
                Raver\CoreTunel.m3u8
                """.trimIndent()
            )

            val result = M3uParser(baseDir).parse(m3uPath)

            result.entries shouldHaveSize 0
            result.nestedPlaylists shouldHaveSize 2
            result.nestedPlaylists.map { it.fileName.toString() } shouldBe
                listOf("Core.M3U", "CoreTunel.m3u8")
        }
    }

    "tolerates mixed forward and back slashes in relative paths" {
        Jimfs.newFileSystem(Configuration.windows()).use { fs ->
            val baseDir = fs.getPath("C:\\Music\\Playlists")
            Files.createDirectories(baseDir)
            val m3uPath = baseDir.resolve("Mixed.m3u")
            // Real-world .m3u files often use forward slashes even on Windows
            Files.writeString(m3uPath, "../Library/Alien/Interterrestial/04 Hold It Now.flac\n")

            val result = M3uParser(baseDir).parse(m3uPath)

            result.entries shouldHaveSize 1
            result.entries[0].resolvedPath shouldBe
                fs.getPath("C:\\Music\\Library\\Alien\\Interterrestial\\04 Hold It Now.flac")
        }
    }
})
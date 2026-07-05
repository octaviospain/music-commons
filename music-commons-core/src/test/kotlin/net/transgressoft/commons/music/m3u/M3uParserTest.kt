package net.transgressoft.commons.music.m3u

import net.transgressoft.commons.music.shouldHaveMessageContaining
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files

/**
 * Parser-level tests exercising line classification, encoding, and path resolution on
 * an in-memory filesystem. The static fixtures under `m3u/` resources back the import
 * and round-trip tests; this suite focuses on parser semantics in isolation.
 */
@DisplayName("M3uParser")
internal class M3uParserTest : StringSpec({

    "parses tracks and discards directive metadata" {
        parseM3uOnFs(
            Configuration.unix(),
            "/music",
            "playlist.m3u",
            """
            #EXTM3U
            #EXTINF:180,Track One
            track1.mp3
            #EXTINF:240,Track Two
            track2.flac
            """.trimIndent()
        ) { _, baseDir, result ->
            result.entries shouldHaveSize 2
            result.nestedPlaylists.shouldBeEmpty()
            result.entries.map { it.resolvedPath } shouldBe
                listOf(baseDir.resolve("track1.mp3"), baseDir.resolve("track2.flac"))
        }
    }

    withData<Triple<String, Pair<String, String>, Int>>(
        nameFn = { (label, _, _) -> label },
        Triple("strips BOM from the first line", "bom.m3u" to "\uFEFF#EXTM3U\ntrack1.mp3\n", 1),
        Triple("tolerates CRLF line endings", "crlf.m3u" to "#EXTM3U\r\n#EXTINF:90,X\r\ntrack1.mp3\r\n", 1),
        Triple("tolerates missing #EXTM3U header", "noheader.m3u" to "track1.mp3\n", 1),
        Triple(
            "skips blank lines and comments",
            "comments.m3u" to
                """
                #EXTM3U

                # comment
                #EXTINF:180,Valid Track
                track1.mp3

                # trailing comment
                """.trimIndent(),
            1
        )
    ) { (_, fileAndContent, expectedEntryCount) ->
        val (fileName, m3uContent) = fileAndContent
        parseM3uOnFs(Configuration.unix(), "/music", fileName, m3uContent) { _, _, result ->
            result.entries shouldHaveSize expectedEntryCount
        }
    }

    "resolves relative and absolute paths" {
        parseM3uOnFs(Configuration.unix(), "/music", "mixed.m3u", "album/track1.mp3\n/other/music/track2.mp3\n") { fs, baseDir, result ->
            result.entries.map { it.resolvedPath } shouldBe
                listOf(
                    baseDir.resolve("album/track1.mp3"),
                    fs.getPath("/other/music/track2.mp3")
                )
        }
    }

    "classifies m3u and m3u8 entries as nested playlists" {
        parseM3uOnFs(Configuration.unix(), "/music", "parent.m3u", "child.m3u\nchild.M3U8\n") { _, _, result ->
            result.entries.shouldBeEmpty()
            result.nestedPlaylists shouldHaveSize 2
        }
    }

    "skips remote URLs with a warning" {
        parseM3uOnFs(
            Configuration.unix(),
            "/music",
            "urls.m3u",
            """
            http://example.com/track.mp3
            https://example.com/track.mp3
            file:///music/local.mp3
            local.mp3
            """.trimIndent()
        ) { _, baseDir, result ->
            result.entries shouldHaveSize 1
            result.entries[0].resolvedPath shouldBe baseDir.resolve("local.mp3")
        }
    }

    "throws M3uParseException for non-existent file" {
        Jimfs.newFileSystem(Configuration.unix()).use { fs ->
            val baseDir = fs.getPath("/music")
            Files.createDirectories(baseDir)
            val missing = baseDir.resolve("missing.m3u")
            val ex = shouldThrow<M3uParseException> { M3uParser(baseDir).parse(missing) }
            ex shouldHaveMessageContaining "does not exist"
        }
    }

    "preserves paths with spaces" {
        parseM3uOnFs(Configuration.unix(), "/music", "spaces.m3u", "My Music/track with spaces.mp3\n") { _, baseDir, result ->
            result.entries[0].resolvedPath shouldBe baseDir.resolve("My Music/track with spaces.mp3")
        }
    }

    "decodes .m3u8 strictly as UTF-8" {
        Jimfs.newFileSystem(Configuration.unix()).use { fs ->
            val baseDir = fs.getPath("/music")
            Files.createDirectories(baseDir)
            val m3u = baseDir.resolve("strict.m3u8")
            // Latin-1 byte for 'é' inside a .m3u8 file must fail decoding.
            Files.write(m3u, "#EXTM3U\ntrack-é.flac\n".toByteArray(Charsets.ISO_8859_1))

            val ex = shouldThrow<M3uParseException> { M3uParser(baseDir).parse(m3u) }
            ex shouldHaveMessageContaining "UTF-8"
        }
    }

    "decodes .m3u with Windows-1252 fallback when UTF-8 fails" {
        Jimfs.newFileSystem(Configuration.unix()).use { fs ->
            val baseDir = fs.getPath("/music")
            Files.createDirectories(baseDir)
            val m3u = baseDir.resolve("legacy.m3u")
            // 0xE9 is 'é' in Windows-1252 and an invalid UTF-8 lead byte.
            Files.write(m3u, "#EXTM3U\nDisco Sucré.flac\n".toByteArray(Charsets.ISO_8859_1))

            val result = M3uParser(baseDir).parse(m3u)

            result.entries shouldHaveSize 1
            result.entries[0].resolvedPath.fileName.toString() shouldContain "Sucré"
        }
    }
})
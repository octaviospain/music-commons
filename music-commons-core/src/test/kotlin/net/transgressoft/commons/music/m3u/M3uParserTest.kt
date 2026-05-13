package net.transgressoft.commons.music.m3u

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
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
        Jimfs.newFileSystem(Configuration.unix()).use { fs ->
            val baseDir = fs.getPath("/music")
            Files.createDirectories(baseDir)
            val m3u = baseDir.resolve("playlist.m3u")
            Files.writeString(
                m3u,
                """
                #EXTM3U
                #EXTINF:180,Track One
                track1.mp3
                #EXTINF:240,Track Two
                track2.flac
                """.trimIndent()
            )

            val result = M3uParser(baseDir).parse(m3u)

            result.entries shouldHaveSize 2
            result.nestedPlaylists.shouldBeEmpty()
            result.entries.map { it.resolvedPath } shouldBe
                listOf(baseDir.resolve("track1.mp3"), baseDir.resolve("track2.flac"))
        }
    }

    "strips BOM from the first line" {
        Jimfs.newFileSystem(Configuration.unix()).use { fs ->
            val baseDir = fs.getPath("/music")
            Files.createDirectories(baseDir)
            val m3u = baseDir.resolve("bom.m3u")
            Files.writeString(m3u, "#EXTM3U\ntrack1.mp3\n")

            val result = M3uParser(baseDir).parse(m3u)

            result.entries shouldHaveSize 1
        }
    }

    "tolerates CRLF line endings" {
        Jimfs.newFileSystem(Configuration.unix()).use { fs ->
            val baseDir = fs.getPath("/music")
            Files.createDirectories(baseDir)
            val m3u = baseDir.resolve("crlf.m3u")
            Files.writeString(m3u, "#EXTM3U\r\n#EXTINF:90,X\r\ntrack1.mp3\r\n")

            M3uParser(baseDir).parse(m3u).entries shouldHaveSize 1
        }
    }

    "tolerates missing #EXTM3U header" {
        Jimfs.newFileSystem(Configuration.unix()).use { fs ->
            val baseDir = fs.getPath("/music")
            Files.createDirectories(baseDir)
            val m3u = baseDir.resolve("noheader.m3u")
            Files.writeString(m3u, "track1.mp3\n")

            M3uParser(baseDir).parse(m3u).entries shouldHaveSize 1
        }
    }

    "skips blank lines and comments" {
        Jimfs.newFileSystem(Configuration.unix()).use { fs ->
            val baseDir = fs.getPath("/music")
            Files.createDirectories(baseDir)
            val m3u = baseDir.resolve("comments.m3u")
            Files.writeString(
                m3u,
                """
                #EXTM3U

                # comment
                #EXTINF:180,Valid Track
                track1.mp3

                # trailing comment
                """.trimIndent()
            )

            M3uParser(baseDir).parse(m3u).entries shouldHaveSize 1
        }
    }

    "resolves relative and absolute paths" {
        Jimfs.newFileSystem(Configuration.unix()).use { fs ->
            val baseDir = fs.getPath("/music")
            Files.createDirectories(baseDir)
            val m3u = baseDir.resolve("mixed.m3u")
            Files.writeString(m3u, "album/track1.mp3\n/other/music/track2.mp3\n")

            val result = M3uParser(baseDir).parse(m3u)

            result.entries.map { it.resolvedPath } shouldBe
                listOf(
                    baseDir.resolve("album/track1.mp3"),
                    fs.getPath("/other/music/track2.mp3")
                )
        }
    }

    "classifies m3u and m3u8 entries as nested playlists" {
        Jimfs.newFileSystem(Configuration.unix()).use { fs ->
            val baseDir = fs.getPath("/music")
            Files.createDirectories(baseDir)
            val m3u = baseDir.resolve("parent.m3u")
            Files.writeString(m3u, "child.m3u\nchild.M3U8\n")

            val result = M3uParser(baseDir).parse(m3u)

            result.entries.shouldBeEmpty()
            result.nestedPlaylists shouldHaveSize 2
        }
    }

    "skips remote URLs with a warning" {
        Jimfs.newFileSystem(Configuration.unix()).use { fs ->
            val baseDir = fs.getPath("/music")
            Files.createDirectories(baseDir)
            val m3u = baseDir.resolve("urls.m3u")
            Files.writeString(
                m3u,
                """
                http://example.com/track.mp3
                https://example.com/track.mp3
                file:///music/local.mp3
                local.mp3
                """.trimIndent()
            )

            val result = M3uParser(baseDir).parse(m3u)

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
            ex.message!! shouldContain "does not exist"
        }
    }

    "preserves paths with spaces" {
        Jimfs.newFileSystem(Configuration.unix()).use { fs ->
            val baseDir = fs.getPath("/music")
            Files.createDirectories(baseDir)
            val m3u = baseDir.resolve("spaces.m3u")
            Files.writeString(m3u, "My Music/track with spaces.mp3\n")

            M3uParser(baseDir).parse(m3u).entries[0].resolvedPath shouldBe
                baseDir.resolve("My Music/track with spaces.mp3")
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
            ex.message!! shouldContain "UTF-8"
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
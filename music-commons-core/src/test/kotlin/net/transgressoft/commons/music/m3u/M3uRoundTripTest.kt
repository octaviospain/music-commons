package net.transgressoft.commons.music.m3u

import net.transgressoft.commons.music.CoreMusicLibrary
import net.transgressoft.commons.music.audio.ArbitraryAudioFile
import net.transgressoft.commons.music.testing.reactiveScope
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Integration tests verifying M3U import/export round-trip parity.
 *
 * Uses real audio fixtures on a real filesystem because [AudioPlaylist.exportToM3uFile]
 * writes through `PrintWriter` and requires `path.toFile()` to be supported.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("M3U Round-trip")
internal class M3uRoundTripTest : StringSpec({

    val reactive = reactiveScope()

    val mp3Source = ArbitraryAudioFile.getResourceAsFile("/testfiles/testeable.mp3").toPath()
    val flacSource = ArbitraryAudioFile.getResourceAsFile("/testfiles/testeable.flac").toPath()

    val mp3Title = "Ruff Message..."
    val mp3Duration = 17L
    val flacTitle = "Heaviest Fucking Acid Trip In The Universe"
    val flacDuration = 28L

    fun writePlaylist(path: Path, content: String): Path {
        Files.writeString(path, content)
        return path
    }

    "flat playlist round-trip preserves track order and titles" {
        val tmpDir = tempdir().toPath()
        val mp3File = tmpDir.resolve("testeable.mp3")
        val flacFile = tmpDir.resolve("testeable.flac")
        Files.copy(mp3Source, mp3File, StandardCopyOption.REPLACE_EXISTING)
        Files.copy(flacSource, flacFile, StandardCopyOption.REPLACE_EXISTING)

        val sourcePlaylist =
            writePlaylist(
                tmpDir.resolve("RoundTrip.m3u"),
                """
                #EXTM3U
                #EXTINF:$mp3Duration,$mp3Title
                ${mp3File.toAbsolutePath()}
                #EXTINF:$flacDuration,$flacTitle
                ${flacFile.toAbsolutePath()}
                """.trimIndent()
            )

        CoreMusicLibrary.builder().build().use { lib1 ->
            M3uImportService(lib1).use { svc ->
                val first = svc.import(sourcePlaylist)
                val exportDir = tmpDir.resolve("export").also { Files.createDirectory(it) }
                val exportedPath = exportDir.resolve("${first.name}.m3u")
                first.exportToM3uFile(exportedPath)

                CoreMusicLibrary.builder().build().use { lib2 ->
                    M3uImportService(lib2).use { svc2 ->
                        val second = svc2.import(exportedPath)

                        first.name shouldBe second.name
                        first.audioItems.map { it.title } shouldBe second.audioItems.map { it.title }
                        first.audioItems.map { it.duration.seconds } shouldBe second.audioItems.map { it.duration.seconds }
                    }
                }
            }
        }
    }

    "nested playlist round-trip preserves directory structure and child playlists" {
        val tmpDir = tempdir().toPath()
        val mp3File = tmpDir.resolve("testeable.mp3")
        Files.copy(mp3Source, mp3File, StandardCopyOption.REPLACE_EXISTING)

        writePlaylist(
            tmpDir.resolve("child1.m3u"),
            """
            #EXTM3U
            #EXTINF:$mp3Duration,$mp3Title
            ${mp3File.toAbsolutePath()}
            """.trimIndent()
        )
        writePlaylist(
            tmpDir.resolve("child2.m3u"),
            """
            #EXTM3U
            #EXTINF:$flacDuration,$flacTitle
            ${mp3File.toAbsolutePath()}
            """.trimIndent()
        )
        val parent =
            writePlaylist(
                tmpDir.resolve("parent.m3u"),
                """
                #EXTM3U
                child1.m3u
                child2.m3u
                """.trimIndent()
            )

        CoreMusicLibrary.builder().build().use { lib1 ->
            M3uImportService(lib1).use { svc ->
                val first = svc.import(parent)
                first.isDirectory.shouldBeTrue()
                first.playlists shouldHaveSize 2

                val exportDir = tmpDir.resolve("export").also { Files.createDirectory(it) }
                val exportedPath = exportDir.resolve("${first.name}.m3u")
                first.exportToM3uFile(exportedPath)

                CoreMusicLibrary.builder().build().use { lib2 ->
                    M3uImportService(lib2).use { svc2 ->
                        val second = svc2.import(exportedPath)
                        second.isDirectory.shouldBeTrue()
                        second.playlists shouldHaveSize 2
                        first.audioItemsRecursive.size shouldBe second.audioItemsRecursive.size
                    }
                }
            }
        }
    }

    "round-trip tolerant of CRLF line endings and BOM" {
        val tmpDir = tempdir().toPath()
        val mp3File = tmpDir.resolve("testeable.mp3")
        Files.copy(mp3Source, mp3File, StandardCopyOption.REPLACE_EXISTING)

        val playlistPath = tmpDir.resolve("BomCrlf.m3u")
        Files.writeString(
            playlistPath,
            "\uFEFF#EXTM3U\r\n#EXTINF:$mp3Duration,$mp3Title\r\n${mp3File.toAbsolutePath()}\r\n"
        )

        CoreMusicLibrary.builder().build().use { lib1 ->
            M3uImportService(lib1).use { svc ->
                val first = svc.import(playlistPath)
                val exportedPath = tmpDir.resolve("exported.m3u")
                first.exportToM3uFile(exportedPath)

                CoreMusicLibrary.builder().build().use { lib2 ->
                    M3uImportService(lib2).use { svc2 ->
                        val second = svc2.import(exportedPath)
                        first.audioItems shouldHaveSize 1
                        second.audioItems shouldHaveSize 1
                        first.audioItems.first().title shouldBe second.audioItems.first().title
                    }
                }
            }
        }
    }
})
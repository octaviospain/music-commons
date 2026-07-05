package net.transgressoft.commons.music.itunes

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.json.shouldContainJsonKeyValue
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Path

@DisplayName("SyntheticItunesLibraryGenerator")
internal class SyntheticItunesLibraryGeneratorTest : StringSpec({

    "SyntheticItunesLibraryGenerator writes deterministic XML and expectations for nested audio files" {
        val sourceRoot = Files.createTempDirectory("itunes-source-")
        val outputDir = Files.createTempDirectory("itunes-output-")
        writeAudioFile(sourceRoot.resolve("root song.mp3"))
        writeAudioFile(sourceRoot.resolve("Album A").resolve("alpha.flac"))
        writeAudioFile(sourceRoot.resolve("Album A").resolve("beta.m4a"))
        writeAudioFile(sourceRoot.resolve("Album B").resolve("gamma.wav"))
        Files.writeString(sourceRoot.resolve("ignore.txt"), "not audio")

        SyntheticItunesLibraryGenerator.generate(sourceRoot, outputDir)

        val xml = Files.readString(outputDir.resolve(SyntheticItunesLibraryGenerator.DEFAULT_LIBRARY_FILE))
        val expectations = Files.readString(outputDir.resolve(SyntheticItunesLibraryGenerator.DEFAULT_EXPECTATIONS_FILE))

        assertSoftly(xml) {
            shouldContain("Synthetic Title Sentinel")
            shouldContain("Synthetic Primary Artist")
            shouldContain("Synthetic Album Sentinel")
            shouldContain("root%20song.mp3")
            shouldContain("Album A Tracks")
            shouldContain("Album B Tracks")
        }
        expectations.shouldContainJsonKeyValue("trackCount", 4)
        expectations.shouldContainJsonKeyValue("playlistCount", 6)
    }

    "SyntheticItunesLibraryGenerator writes custom output names" {
        val sourceRoot = Files.createTempDirectory("itunes-source-custom-")
        val outputDir = Files.createTempDirectory("itunes-output-custom-")
        writeAudioFile(sourceRoot.resolve("song.mp3"))

        SyntheticItunesLibraryGenerator.generate(sourceRoot, outputDir, "library.xml", "expectations.json")

        Files.list(outputDir).use { files ->
            files.map { it.fileName.toString() }.toList() shouldContainAll listOf("library.xml", "expectations.json")
        }
    }
})

private fun writeAudioFile(path: Path) {
    Files.createDirectories(path.parent)
    Files.write(path, byteArrayOf(0))
}
package net.transgressoft.commons.music.itunes

import com.dd.plist.NSDictionary
import com.dd.plist.PropertyListParser
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files

@DisplayName("ItunesLibraryTestFixture")
internal class ItunesLibraryTestFixtureTest : StringSpec({

    "ItunesLibraryTestFixture materializes runtime audio locations" {
        val tempDir = Files.createTempDirectory("itunes-fixture-")

        val prepared =
            ItunesLibraryTestFixture.prepare(
                tempDir,
                ItunesLibraryTestFixtureTest::class.java,
                "/testfiles/itunes-library.xml"
            )

        val root = PropertyListParser.parse(prepared.xmlPath().toFile()) as NSDictionary
        val tracks = root.objectForKey("Tracks") as NSDictionary
        val firstTrack = tracks.objectForKey("100") as NSDictionary
        val location = firstTrack.objectForKey("Location").toString()

        Files.exists(prepared.xmlPath()) shouldBe true
        location shouldContain tempDir.resolve("audio").toUri().toString()
        Files.list(tempDir.resolve("audio")).use { files ->
            files.map { it.fileName.toString() }.toList() shouldContain "track-100.mp3"
        }
    }
})
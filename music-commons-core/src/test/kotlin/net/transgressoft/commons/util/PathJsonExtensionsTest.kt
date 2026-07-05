package net.transgressoft.commons.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.nio.file.Path
import kotlinx.serialization.SerializationException

internal class PathJsonExtensionsTest : StringSpec({

    "Path.toJsonUri produces file:// URI on absolute path" {
        val path = Path.of("/home/u/song.mp3")
        val uri = path.toJsonUri()
        uri shouldStartWith "file://"
        uri shouldContain "/home/u/song.mp3"
    }

    "Path.toJsonUri percent-encodes spaces and special characters" {
        val path = Path.of("/home/u/my song.mp3")
        val uri = path.toJsonUri()
        uri shouldStartWith "file://"
        uri shouldContain "my%20song.mp3"
    }

    "Path.toJsonUri calls toAbsolutePath on relative input" {
        val path = Path.of("relative.mp3")
        val uri = path.toJsonUri()
        uri shouldStartWith "file://"
        uri shouldContain "relative.mp3"
    }

    "String.toPathFromJsonUri round-trips a unix path via file:// URI" {
        val original = Path.of("/home/u/song.mp3")
        val uri = original.toJsonUri()
        val recovered = uri.toPathFromJsonUri()
        recovered.toString() shouldBe original.toAbsolutePath().toString()
    }

    "String.toPathFromJsonUri rejects raw path string with no scheme, hard-breaking on legacy formats" {
        val ex = shouldThrow<SerializationException> { "/home/u/song.mp3".toPathFromJsonUri() }
        ex.message!! shouldContain "file://"
        ex.message!! shouldContain "pre-25.1"
    }

    withData(
        mapOf(
            "String.toPathFromJsonUri rejects http URI (only file:// accepted)" to "http://example.com/x.mp3",
            "String.toPathFromJsonUri rejects empty string (only file:// accepted)" to "",
            "String.toPathFromJsonUri rejects raw Windows-style path (only file:// accepted)" to "C:\\Users\\u\\song.mp3"
        )
    ) { input ->
        shouldThrow<SerializationException> { input.toPathFromJsonUri() }
    }

    "file:// URI encoding is stable for ASCII paths (golden fixture)" {
        val p = Path.of("/golden/song.mp3")
        val uri = p.toJsonUri()
        uri shouldStartWith "file:///"
        uri shouldContain "/golden/song.mp3"
        uri.toPathFromJsonUri() shouldBe p.toAbsolutePath()
    }
})
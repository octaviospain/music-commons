package net.transgressoft.commons.music.common

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.nio.file.Path
import kotlinx.serialization.SerializationException

class PathJsonExtensionsTest : StringSpec({

    "Path.toJsonUri produces file:// URI on absolute path" {
        val path = Path.of("/home/u/song.mp3")
        val uri = path.toJsonUri()
        uri shouldStartWith "file://"
        uri shouldContain "/home/u/song.mp3"
    }

    "Path.toJsonUri produces file:// URI for an absolute path on the default filesystem" {
        val path = Path.of("/tmp/test/song.mp3")
        val uri = path.toJsonUri()
        uri shouldStartWith "file://"
        uri shouldContain "song.mp3"
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

    "String.toPathFromJsonUri rejects raw path string with no scheme (D-02 hard-break)" {
        val ex = shouldThrow<SerializationException> { "/home/u/song.mp3".toPathFromJsonUri() }
        ex.message!! shouldContain "file://"
        ex.message!! shouldContain "pre-25.1"
    }

    "String.toPathFromJsonUri rejects http URI (only file:// accepted)" {
        shouldThrow<SerializationException> { "http://example.com/x.mp3".toPathFromJsonUri() }
    }

    "String.toPathFromJsonUri rejects empty string" {
        shouldThrow<SerializationException> { "".toPathFromJsonUri() }
    }

    "String.toPathFromJsonUri rejects raw Windows-style path string" {
        shouldThrow<SerializationException> { "C:\\Users\\u\\song.mp3".toPathFromJsonUri() }
    }

    "file:// URI encoding is stable for ASCII paths (golden fixture)" {
        val p = Path.of("/golden/song.mp3")
        val uri = p.toJsonUri()
        uri shouldStartWith "file:///"
        uri shouldContain "/golden/song.mp3"
        uri.toPathFromJsonUri() shouldBe p.toAbsolutePath()
    }
})
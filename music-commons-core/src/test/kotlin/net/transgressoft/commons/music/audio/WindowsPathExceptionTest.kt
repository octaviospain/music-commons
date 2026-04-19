package net.transgressoft.commons.music.audio

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

class WindowsPathExceptionTest : StringSpec({

    "WindowsPathException is catchable as InvalidAudioFilePathException" {
        val ex = WindowsPathException("bad|name.mp3", WindowsViolation.ForbiddenChar('|'))
        ex.shouldBeInstanceOf<InvalidAudioFilePathException>()
    }

    "WindowsPathException is catchable as AudioItemManipulationException (consumer catch-superclass path)" {
        val ex = WindowsPathException("NUL", WindowsViolation.ReservedName("NUL"))
        ex.shouldBeInstanceOf<AudioItemManipulationException>()
    }

    "WindowsPathException message includes offending name and violation phrase" {
        val ex = WindowsPathException("bad|name.mp3", WindowsViolation.ForbiddenChar('|'))
        ex.message!! shouldContain "bad|name.mp3"
        ex.message!! shouldContain "forbidden character '|'"
    }

    "WindowsViolation.ForbiddenChar formats with the char in single quotes" {
        WindowsViolation.ForbiddenChar('*').toString() shouldBe "forbidden character '*'"
    }

    "WindowsViolation.ReservedName formats with the name in single quotes" {
        WindowsViolation.ReservedName("NUL").toString() shouldBe "reserved name 'NUL'"
    }

    "WindowsViolation.TrailingDotOrSpace formats with fixed phrase" {
        WindowsViolation.TrailingDotOrSpace.toString() shouldBe "trailing dot or space"
    }

    "WindowsViolation.ExceedsMaxPath formats with MAX_PATH constant" {
        WindowsViolation.ExceedsMaxPath.toString() shouldBe "exceeds Windows MAX_PATH (260 characters)"
    }

    "InvalidAudioFilePathException(message) delegates to two-arg constructor with null cause" {
        val ex = InvalidAudioFilePathException("File not found")
        ex.message shouldBe "File not found"
        ex.cause shouldBe null
    }
})
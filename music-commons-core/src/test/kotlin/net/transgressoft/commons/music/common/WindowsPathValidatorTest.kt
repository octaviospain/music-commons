package net.transgressoft.commons.music.common

import net.transgressoft.commons.music.audio.WindowsPathException
import net.transgressoft.commons.music.audio.WindowsViolation
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class WindowsPathValidatorTest : StringSpec({
    isolationMode = IsolationMode.SingleInstance

    "WindowsPathValidator.validatePath is a no-op on Linux (isWindows=false)" {
        OsDetector.withOverriddenIsWindows(false) {
            Jimfs.newFileSystem(Configuration.unix()).use { fs ->
                WindowsPathValidator.validatePath(fs.getPath("/any/path/<>|?*"))
            }
        }
    }

    "WindowsPathValidator.validateName is a no-op on Linux (isWindows=false)" {
        OsDetector.withOverriddenIsWindows(false) {
            WindowsPathValidator.validateName("NUL")
            WindowsPathValidator.validateName("bad|name")
        }
    }

    listOf('<', '>', ':', '"', '/', '\\', '|', '?', '*').forEach { ch ->
        "WindowsPathValidator.validateName rejects forbidden character '$ch' on Windows" {
            OsDetector.withOverriddenIsWindows(true) {
                val ex =
                    shouldThrow<WindowsPathException> {
                        WindowsPathValidator.validateName("bad${ch}name.mp3")
                    }
                ex.violation.shouldBeInstanceOf<WindowsViolation.ForbiddenChar>()
                (ex.violation as WindowsViolation.ForbiddenChar).char shouldBe ch
            }
        }
    }

    listOf(
        "CON", "PRN", "AUX", "NUL",
        "COM0", "COM1", "COM9",
        "LPT0", "LPT1", "LPT9"
    ).forEach { reserved ->
        "WindowsPathValidator.validateName rejects reserved name '$reserved' on Windows (case-insensitive)" {
            OsDetector.withOverriddenIsWindows(true) {
                val ex =
                    shouldThrow<WindowsPathException> {
                        WindowsPathValidator.validateName(reserved)
                    }
                ex.violation.shouldBeInstanceOf<WindowsViolation.ReservedName>()
            }
        }

        "WindowsPathValidator.validateName rejects lowercase '$reserved' on Windows" {
            OsDetector.withOverriddenIsWindows(true) {
                shouldThrow<WindowsPathException> {
                    WindowsPathValidator.validateName(reserved.lowercase())
                }
            }
        }

        "WindowsPathValidator.validateName rejects '$reserved.txt' on Windows (reserved with extension)" {
            OsDetector.withOverriddenIsWindows(true) {
                shouldThrow<WindowsPathException> {
                    WindowsPathValidator.validateName("$reserved.txt")
                }
            }
        }

        "WindowsPathValidator.validateName rejects '$reserved.tar.gz' on Windows (reserved with multi-extension)" {
            OsDetector.withOverriddenIsWindows(true) {
                val ex =
                    shouldThrow<WindowsPathException> {
                        WindowsPathValidator.validateName("$reserved.tar.gz")
                    }
                ex.violation.shouldBeInstanceOf<WindowsViolation.ReservedName>()
            }
        }
    }

    "WindowsPathValidator.validateName rejects names ending with dot on Windows" {
        OsDetector.withOverriddenIsWindows(true) {
            val ex =
                shouldThrow<WindowsPathException> {
                    WindowsPathValidator.validateName("file.")
                }
            ex.violation shouldBe WindowsViolation.TrailingDotOrSpace
        }
    }

    "WindowsPathValidator.validateName rejects names ending with space on Windows" {
        OsDetector.withOverriddenIsWindows(true) {
            shouldThrow<WindowsPathException> { WindowsPathValidator.validateName("file ") }
        }
    }

    "WindowsPathValidator.validateName accepts a valid name on Windows" {
        OsDetector.withOverriddenIsWindows(true) {
            WindowsPathValidator.validateName("valid-song.mp3")
        }
    }

    "WindowsPathValidator.validatePath rejects absolute path > 260 chars on Windows" {
        OsDetector.withOverriddenIsWindows(true) {
            Jimfs.newFileSystem(Configuration.windows()).use { fs ->
                val longSegment = "a".repeat(100)
                val path = fs.getPath("C:\\", longSegment, longSegment, longSegment, "file.mp3")
                val ex = shouldThrow<WindowsPathException> { WindowsPathValidator.validatePath(path) }
                ex.violation shouldBe WindowsViolation.ExceedsMaxPath
            }
        }
    }

    "WindowsPathValidator.validatePath accepts short path on Windows" {
        OsDetector.withOverriddenIsWindows(true) {
            Jimfs.newFileSystem(Configuration.windows()).use { fs ->
                val path = fs.getPath("C:\\", "music", "song.mp3")
                WindowsPathValidator.validatePath(path)
            }
        }
    }

    "WindowsPathValidator.sanitizeForTempFile strips forbidden chars" {
        WindowsPathValidator.sanitizeForTempFile("bad|name?.mp3") shouldBe "bad_name_.mp3"
    }

    "WindowsPathValidator.sanitizeForTempFile strips trailing dots and spaces" {
        WindowsPathValidator.sanitizeForTempFile("name.") shouldBe "name"
        WindowsPathValidator.sanitizeForTempFile("name  ") shouldBe "name"
        WindowsPathValidator.sanitizeForTempFile("name. ") shouldBe "name"
    }

    "WindowsPathValidator.sanitizeForTempFile replaces each forbidden char with underscore" {
        WindowsPathValidator.sanitizeForTempFile("****") shouldBe "____"
    }

    "WindowsPathValidator.sanitizeForTempFile returns '_' when result sanitizes to empty" {
        WindowsPathValidator.sanitizeForTempFile("...") shouldBe "_"
        WindowsPathValidator.sanitizeForTempFile("   ") shouldBe "_"
    }

    "WindowsPathValidator.sanitizeForTempFile never throws" {
        WindowsPathValidator.sanitizeForTempFile("<>|?*") shouldBe "_____"
    }
})
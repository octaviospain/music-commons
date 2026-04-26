package net.transgressoft.commons.music.common

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.io.File
import java.nio.file.Path

/**
 * Pure simulation tests for [WindowsLongPathSupport]. The [OsDetector] override seam is a no-op on a
 * real Windows JVM (since `OsDetector.isWindows` is already `true`), and the Windows path provider
 * canonicalizes `Path.of("\\\\?\\…")` differently from how Linux's path provider preserves it
 * verbatim — so these assertions are written against the Linux-JDK `Path.toString()` shape and only
 * run on the Linux + macOS legs. Real-Windows behavior of [WindowsLongPathSupport] (prefix survives
 * `File.absolutePath`, prefixed paths actually open files, UNC paths use `\\?\UNC\`) is verified by
 * [WindowsIntegrationTest] under the `windows-only` tag.
 */
@Tags("linux-only")
class WindowsLongPathSupportTest : StringSpec({
    isolationMode = IsolationMode.SingleInstance

    "WindowsLongPathSupport.toLongPathSafe(Path) passes through unchanged on Linux" {
        OsDetector.withOverriddenIsWindows(false) {
            val longName = "a".repeat(300)
            val long = Path.of("/$longName.mp3")
            val result = WindowsLongPathSupport.toLongPathSafe(long)
            result shouldBe long
        }
    }

    "WindowsLongPathSupport.toLongPathSafe(File) passes through unchanged on Linux" {
        OsDetector.withOverriddenIsWindows(false) {
            val longName = "a".repeat(300)
            val long = File("/$longName.mp3")
            val result = WindowsLongPathSupport.toLongPathSafe(long)
            result shouldBe long
        }
    }

    "WindowsLongPathSupport.toLongPathSafe(File) passes through short paths" {
        OsDetector.withOverriddenIsWindows(true) {
            val short = File("/short.mp3")
            val result = WindowsLongPathSupport.toLongPathSafe(short)
            result.path shouldBe short.path
        }
    }

    "WindowsLongPathSupport.toLongPathSafe(File) prefixes long File path" {
        OsDetector.withOverriddenIsWindows(true) {
            val longName = "a".repeat(300)
            val long = File("/long-dir/$longName.mp3")
            val result = WindowsLongPathSupport.toLongPathSafe(long)
            result.path shouldStartWith "\\\\?\\"
            result.path shouldContain longName
        }
    }

    "WindowsLongPathSupport.toLongPathSafe(Path) does not double-prefix already-prefixed paths" {
        OsDetector.withOverriddenIsWindows(true) {
            val longName = "a".repeat(300)
            val long = Path.of("/long-dir/$longName.mp3")
            val first = WindowsLongPathSupport.toLongPathSafe(long)
            // Round-trip the prefixed result through toLongPathSafe so the early-exit branch
            // (raw.startsWith(LONG_PATH_PREFIX)) actually runs. Because the implementation
            // consults the raw path string before absolutization, this works on non-Windows hosts.
            val second = WindowsLongPathSupport.toLongPathSafe(first)

            second.toString() shouldStartWith "\\\\?\\"
            val occurrences = second.toString().windowed(4).count { it == "\\\\?\\" }
            occurrences shouldBe 1
        }
    }

    "WindowsLongPathSupport.toLongPathSafe(Path) passes through short paths" {
        OsDetector.withOverriddenIsWindows(true) {
            val short = Path.of("/short.mp3")
            val result = WindowsLongPathSupport.toLongPathSafe(short)
            result.toString() shouldBe short.toString()
        }
    }

    "WindowsLongPathSupport.toLongPathSafe(Path) prefixes long path" {
        OsDetector.withOverriddenIsWindows(true) {
            val longName = "a".repeat(300)
            val long = Path.of("/long-dir/$longName.mp3")
            val result = WindowsLongPathSupport.toLongPathSafe(long)
            result.toString() shouldStartWith "\\\\?\\"
        }
    }

    "WindowsLongPathSupport.toLongPathSafe(File) uses \\\\?\\UNC\\ for UNC paths" {
        OsDetector.withOverriddenIsWindows(true) {
            val longName = "a".repeat(300)
            val unc = File("\\\\server\\share\\$longName.mp3")
            val result = WindowsLongPathSupport.toLongPathSafe(unc)
            result.path shouldStartWith "\\\\?\\UNC\\"
            // Plain \\?\ on a UNC path would produce \\?\\\server\share\... which Windows rejects.
            result.path.startsWith("\\\\?\\\\\\") shouldBe false
        }
    }
})
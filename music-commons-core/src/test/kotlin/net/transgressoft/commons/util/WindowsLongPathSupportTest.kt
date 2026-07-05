package net.transgressoft.commons.util

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.StringSpec
import io.kotest.datatest.withData
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
internal class WindowsLongPathSupportTest : StringSpec({
    isolationMode = IsolationMode.SingleInstance

    // Each overload's result is normalized to its raw path string: File via `.path`, Path via
    // `.toString()`. Dispatch stays explicit so a broken overload fails on its own row.
    val rawOf: (Overload, String) -> String = { overload, raw ->
        when (overload) {
            Overload.FILE -> WindowsLongPathSupport.toLongPathSafe(File(raw)).path
            Overload.PATH -> WindowsLongPathSupport.toLongPathSafe(Path.of(raw)).toString()
        }
    }

    withData(
        nameFn = { "WindowsLongPathSupport.toLongPathSafe($it) passes through unchanged on Linux" },
        Overload.FILE, Overload.PATH
    ) { overload ->
        OsDetector.withOverriddenIsWindows(false) {
            val raw = "/${"a".repeat(300)}.mp3"
            rawOf(overload, raw) shouldBe raw
        }
    }

    withData(
        nameFn = { "WindowsLongPathSupport.toLongPathSafe($it) passes through short paths" },
        Overload.FILE, Overload.PATH
    ) { overload ->
        OsDetector.withOverriddenIsWindows(true) {
            rawOf(overload, "/short.mp3") shouldBe "/short.mp3"
        }
    }

    withData(
        nameFn = { "WindowsLongPathSupport.toLongPathSafe($it) prefixes long path" },
        Overload.FILE, Overload.PATH
    ) { overload ->
        OsDetector.withOverriddenIsWindows(true) {
            val longName = "a".repeat(300)
            val result = rawOf(overload, "/long-dir/$longName.mp3")
            result shouldStartWith "\\\\?\\"
            result shouldContain longName
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
}) {
    /**
     * The two `toLongPathSafe` overloads under test: [FILE] takes a [java.io.File], [PATH] takes a
     * [java.nio.file.Path]. Used to drive the mirror-pair cases through one parametrized table.
     */
    private enum class Overload { FILE, PATH }
}
package net.transgressoft.commons.music.common

import net.transgressoft.commons.music.audio.WindowsPathException
import net.transgressoft.commons.music.audio.WindowsViolation
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.EnabledCondition
import io.kotest.core.annotation.EnabledIf
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import java.nio.file.Paths
import kotlin.reflect.KClass

/**
 * Real-JVM Windows integration tests. Opted in via `-PincludeWindowsOnly=true`
 *
 * These tests complement two simulation suites that run on every platform via the [OsDetector]
 * override seam: [WindowsPathValidatorTest] (validator behavior on Jimfs paths) and
 * [WindowsLongPathSupportTest] (string-construction logic of [WindowsLongPathSupport]).
 *
 * The integration tests here verify what those simulations cannot:
 * - The validator accepts inputs the real Windows NIO filesystem provider also accepts
 *   (reserved names, paths exceeding MAX_PATH) on an actual NTFS layout.
 * - `toLongPathSafe` produces prefixed paths that survive `Path.toAbsolutePath()` round-trips
 *   on the Windows path provider, so a prefixed input fed back into the helper does not
 *   double-prefix.
 * - UNC paths get the `\\?\UNC\` prefix when their absolute form crosses MAX_PATH on Windows.
 *
 * Inputs that the Windows JVM `Paths.get` itself rejects or silently rewrites (trailing
 * dots/spaces, forbidden chars) are covered only in the simulation suites.
 *
 * The [IsWindowsCondition] enabler skips this spec on non-Windows hosts even if the tag filter
 * lets it through, so a stray local invocation cannot surface false negatives.
 */
@Tags("windows-only")
@EnabledIf(IsWindowsCondition::class)
class WindowsIntegrationTest : StringSpec({

    "real Windows rejects reserved name at the OS level when we try to create NUL" {
        shouldThrow<WindowsPathException> {
            WindowsPathValidator.validateName("NUL")
        }
    }

    "real Windows accepts a normal filename without throwing" {
        WindowsPathValidator.validateName("valid-song.mp3")
    }

    "real Windows long-path support: toLongPathSafe emits prefix for > 260 char paths" {
        val longName = "a".repeat(300)
        val longFile = File("C:\\$longName.mp3")
        val result = WindowsLongPathSupport.toLongPathSafe(longFile)
        result.absolutePath shouldStartWith "\\\\?\\"
    }

    "real Windows long-path support: toLongPathSafe does not double-prefix already-prefixed input" {
        val longName = "a".repeat(300)
        val first = WindowsLongPathSupport.toLongPathSafe(File("C:\\$longName.mp3"))
        val second = WindowsLongPathSupport.toLongPathSafe(first)

        // Real Windows preserves the prefix through Path/File round-trips, so the raw-input
        // early-exit fires and the second call returns the input unchanged (one occurrence).
        val occurrences = second.path.windowed(4).count { it == "\\\\?\\" }
        occurrences shouldBe 1
    }

    "real Windows long-path support: toLongPathSafe uses \\\\?\\UNC\\ prefix for UNC paths" {
        val longName = "a".repeat(300)
        val unc = File("\\\\server\\share\\$longName.mp3")
        val result = WindowsLongPathSupport.toLongPathSafe(unc)
        // The plain \\?\ form on a UNC path would yield \\?\\\server\share\... — invalid on Windows.
        result.absolutePath shouldStartWith "\\\\?\\UNC\\"
    }

    "validatePath rejects reserved name 'CON.mp3' on a real Windows path" {
        val tempDir = System.getProperty("java.io.tmpdir")
        val ex =
            shouldThrow<WindowsPathException> {
                WindowsPathValidator.validatePath(Paths.get(tempDir, "CON.mp3"))
            }
        ex.violation.shouldBeInstanceOf<WindowsViolation.ReservedName>()
    }

    "validatePath rejects reserved name 'PRN.txt' on a real Windows path" {
        val tempDir = System.getProperty("java.io.tmpdir")
        val ex =
            shouldThrow<WindowsPathException> {
                WindowsPathValidator.validatePath(Paths.get(tempDir, "PRN.txt"))
            }
        ex.violation.shouldBeInstanceOf<WindowsViolation.ReservedName>()
    }

    "validatePath rejects reserved name in a directory segment on a real Windows path" {
        val tempDir = System.getProperty("java.io.tmpdir")
        val ex =
            shouldThrow<WindowsPathException> {
                WindowsPathValidator.validatePath(Paths.get(tempDir, "AUX", "song.mp3"))
            }
        ex.violation.shouldBeInstanceOf<WindowsViolation.ReservedName>()
    }

    "validatePath rejects a path exceeding MAX_PATH (260 chars) on a real Windows tempdir" {
        val tempDir = System.getProperty("java.io.tmpdir")
        val longSegment = "a".repeat(100)
        val ex =
            shouldThrow<WindowsPathException> {
                WindowsPathValidator.validatePath(
                    Paths.get(tempDir, longSegment, longSegment, longSegment, "song.mp3")
                )
            }
        ex.violation shouldBe WindowsViolation.ExceedsMaxPath
    }

    "validatePath accepts a short, well-formed real Windows path" {
        val tempDir = System.getProperty("java.io.tmpdir")
        WindowsPathValidator.validatePath(Paths.get(tempDir, "music", "song.mp3"))
    }

    // Trailing-dot and trailing-space inputs are intentionally NOT covered here at the real-Windows
    // level — Windows JVM's `Paths.get` either rejects them with `InvalidPathException` (trailing
    // space) or silently strips them (trailing dot) at parse time, before the validator runs. The
    // simulation suite `WindowsPathValidatorTest` exercises both via raw-string `validateName` calls
    // and Jimfs paths, providing equivalent coverage cross-platform.
})

/**
 * Kotest [io.kotest.core.annotation.EnabledIf] condition that enables specs only on Windows hosts.
 */
class IsWindowsCondition : EnabledCondition {
    override fun enabled(kclass: KClass<out Spec>): Boolean =
        System.getProperty("os.name").lowercase().contains("windows")
}
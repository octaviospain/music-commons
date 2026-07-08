/******************************************************************************
 * Copyright (C) 2025  Octavio Calleya Garcia                                 *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 * (at your option) any later version.                                        *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.     *
 ******************************************************************************/

package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.util.InvalidAudioFilePathException
import net.transgressoft.commons.util.OsDetector
import net.transgressoft.lirp.persistence.VolatileRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.Condition
import io.kotest.core.annotation.EnabledIf
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import org.opentest4j.TestAbortedException
import java.nio.file.Files
import kotlin.reflect.KClass

/**
 * Host-OS-dependent FXAudioItem tests that rely on POSIX file permission semantics
 * (e.g. `File.setReadable(false)` actually demoting `Files.isReadable` to `false`).
 *
 * Skipped on Windows, where `setReadable(false)` is a no-op for the file owner; the
 * `posix-only` tag also lets a Windows CI leg exclude the spec via
 * `-PexcludePosixOnly=true` for symmetry with the other platform-gating flags.
 */
@Tags("posix-only")
@EnabledIf(IsPosixCondition::class)
internal class FXAudioItemPosixTest : StringSpec({

    "FXAudioLibrary.createFromFile throws InvalidAudioFilePathException when file is not readable" {
        val tempFile = Files.createTempFile("unreadable-fx", ".mp3")
        try {
            // setReadable(false) is a no-op when running as root (POSIX permissions are bypassed),
            // and some filesystems silently refuse the change. Both conditions abort the test as
            // skipped so it does not produce confusing failures on root-in-container CI.
            val flipped = tempFile.toFile().setReadable(false)
            if (!flipped || Files.isReadable(tempFile)) {
                throw TestAbortedException("Cannot demote read permission on this filesystem (root or unsupported FS)")
            }
            FXAudioLibrary(VolatileRepository("FXAudioLibrary")).use { library ->
                val ex = shouldThrow<InvalidAudioFilePathException> { library.createFromFile(tempFile) }
                ex.message shouldContain "is not readable"
            }
        } finally {
            tempFile.toFile().setReadable(true)
            Files.deleteIfExists(tempFile)
        }
    }
})

internal class IsPosixCondition : Condition {
    override fun evaluate(kclass: KClass<out Spec>): Boolean = !OsDetector.isWindows
}
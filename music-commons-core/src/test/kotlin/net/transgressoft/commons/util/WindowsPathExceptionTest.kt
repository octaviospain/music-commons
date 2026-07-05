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

package net.transgressoft.commons.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

internal class WindowsPathExceptionTest : StringSpec({

    "WindowsPathException is catchable as InvalidAudioFilePathException" {
        val ex = WindowsPathException("bad|name.mp3", WindowsViolation.ForbiddenChar('|'))
        ex.shouldBeInstanceOf<InvalidAudioFilePathException>()
    }

    // Phase 40 moved `InvalidAudioFilePathException` into music-commons-api, decoupling it from the
    // core-resident `AudioItemManipulationException`. Path-validation failures are now caught via
    // `InvalidAudioFilePathException` (or its superclass `Exception`), not `AudioItemManipulationException`.
    "WindowsPathException remains catchable as InvalidAudioFilePathException for ReservedName violations" {
        val ex = WindowsPathException("NUL", WindowsViolation.ReservedName("NUL"))
        ex.shouldBeInstanceOf<InvalidAudioFilePathException>()
    }

    "WindowsPathException message includes offending name and violation phrase" {
        val ex = WindowsPathException("bad|name.mp3", WindowsViolation.ForbiddenChar('|'))
        ex.message!! shouldContain "bad|name.mp3"
        ex.message!! shouldContain "forbidden character '|'"
    }

    withData<Pair<WindowsViolation, String>>(
        nameFn = { (violation, expected) -> "${violation::class.simpleName} formats as \"$expected\"" },
        WindowsViolation.ForbiddenChar('*') to "forbidden character '*'",
        WindowsViolation.ReservedName("NUL") to "reserved name 'NUL'",
        WindowsViolation.TrailingDotOrSpace to "trailing dot or space",
        WindowsViolation.ExceedsMaxPath to "exceeds Windows MAX_PATH (260 characters)"
    ) { (violation, expected) ->
        violation.toString() shouldBe expected
    }

    "InvalidAudioFilePathException(message) delegates to two-arg constructor with null cause" {
        val ex = InvalidAudioFilePathException("File not found")
        ex.message shouldBe "File not found"
        ex.cause shouldBe null
    }
})
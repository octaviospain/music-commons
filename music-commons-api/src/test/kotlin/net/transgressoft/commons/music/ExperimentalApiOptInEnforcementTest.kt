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

package net.transgressoft.commons.music

import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldNotBe

/**
 * A probe function annotated with [MusicCommonsExperimentalApi] used exclusively to
 * verify that the compiler enforces opt-in when callers use experimental API without
 * explicit acknowledgment.
 *
 * Removing the [OptIn] annotation on the call site below causes `compileTestKotlin` to
 * fail with a `RequiresOptIn` error, proving the enforcement contract is active.
 */
@MusicCommonsExperimentalApi
internal fun experimentalProbe(): String = "experimental"

/**
 * Live opt-in usage example and enforcement witness for [MusicCommonsExperimentalApi].
 *
 * This test file serves two purposes: it retains a compilable, permanently-green
 * opt-in usage example so the module compiles cleanly, and it documents the
 * witnessed-failure contract — removing the [OptIn] annotation and running
 * `gradle :music-commons-api:compileTestKotlin` produces a BUILD FAILED containing
 * a RequiresOptIn error, proving ERROR-level enforcement is active.
 */
@DisplayName("ExperimentalApiOptInEnforcement")
internal class ExperimentalApiOptInEnforcementTest : StringSpec({

    @OptIn(MusicCommonsExperimentalApi::class)
    "ExperimentalApiOptInEnforcement opt-in call site compiles and returns expected value" {
        experimentalProbe() shouldNotBe null
    }
})
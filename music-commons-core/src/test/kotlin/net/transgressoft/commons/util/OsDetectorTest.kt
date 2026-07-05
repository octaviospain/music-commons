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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.StringSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

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
internal class OsDetectorTest : StringSpec({
    isolationMode = IsolationMode.SingleInstance

    "OsDetector.isWindows reflects real os.name when no override is active" {
        val expected = System.getProperty("os.name").lowercase().contains("windows")
        OsDetector.isWindows shouldBe expected
    }

    withData(
        nameFn = { "OsDetector.withOverriddenIsWindows flips isWindows to $it inside block" },
        first = true,
        second = false
    ) { override ->
        OsDetector.withOverriddenIsWindows(override) {
            OsDetector.isWindows shouldBe override
        }
    }

    "OsDetector.withOverriddenIsWindows restores state after normal block completion" {
        val before = OsDetector.isWindows
        OsDetector.withOverriddenIsWindows(!before) {
            OsDetector.isWindows shouldBe !before
        }
        OsDetector.isWindows shouldBe before
    }

    "OsDetector.withOverriddenIsWindows restores state after block throws" {
        val before = OsDetector.isWindows
        val ex =
            shouldThrow<RuntimeException> {
                OsDetector.withOverriddenIsWindows(!before) {
                    throw RuntimeException("test")
                }
            }
        ex.message shouldBe "test"
        OsDetector.isWindows shouldBe before
    }
})
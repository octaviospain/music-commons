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

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.StringSpec
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

    "OsDetector.withOverriddenIsWindows flips isWindows to true inside block" {
        OsDetector.withOverriddenIsWindows(true) {
            OsDetector.isWindows shouldBe true
        }
    }

    "OsDetector.withOverriddenIsWindows flips isWindows to false inside block" {
        OsDetector.withOverriddenIsWindows(false) {
            OsDetector.isWindows shouldBe false
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
            runCatching {
                OsDetector.withOverriddenIsWindows(!before) {
                    throw RuntimeException("test")
                }
            }.exceptionOrNull()
        ex?.message shouldBe "test"
        OsDetector.isWindows shouldBe before
    }
})
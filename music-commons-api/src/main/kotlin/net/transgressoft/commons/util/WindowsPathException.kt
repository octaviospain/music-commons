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

/**
 * Exception thrown when a path or name violates Windows filename constraints.
 *
 * Carries the [offendingName] and a specific [violation] describing the Windows rule
 * that was broken, enabling precise diagnostic messages. Only thrown when the JVM is
 * running on a Windows host; pass-through on Linux/macOS.
 * @since 1.0
 */
public class WindowsPathException(public val offendingName: String, public val violation: WindowsViolation) :
    InvalidAudioFilePathException("Windows filename violation for '$offendingName': $violation")

/**
 * Categorizes Windows filename constraint violations.
 * @since 1.0
 */
public sealed class WindowsViolation {
    public data class ForbiddenChar(public val char: Char) : WindowsViolation() {
        override fun toString(): String = "forbidden character '$char'"
    }

    public data class ReservedName(public val name: String) : WindowsViolation() {
        override fun toString(): String = "reserved name '$name'"
    }

    public data object TrailingDotOrSpace : WindowsViolation() {
        override fun toString(): String = "trailing dot or space"
    }

    public data object ExceedsMaxPath : WindowsViolation() {
        override fun toString(): String = "exceeds Windows MAX_PATH (260 characters)"
    }
}
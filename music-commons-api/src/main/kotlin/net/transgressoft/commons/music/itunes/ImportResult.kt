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

package net.transgressoft.commons.music.itunes

import net.transgressoft.commons.music.audio.ReactiveAudioItem
import java.nio.file.Path

/**
 * Result of an iTunes library import operation.
 *
 * @property imported Audio items successfully imported into the library.
 * @property unresolved Tracks that could not be imported, each carrying the path attempt and reason.
 * @property rejectedPlaylistNames Playlist names that were rejected before playlist creation due to
 *  filesystem-incompatible characters, reserved Windows names, or trailing dots or spaces.
 * @since 1.0
 */
public data class ImportResult(
    val imported: List<ReactiveAudioItem<*>>,
    val unresolved: List<UnresolvedTrack>,
    val rejectedPlaylistNames: List<RejectedPlaylistName>
)

/**
 * Describes a track that could not be imported from an iTunes library.
 *
 * @property path The resolved path that was attempted, or `null` if path resolution itself failed.
 * @property title The track title from iTunes metadata, for diagnostic messages.
 * @property reason Why the track was not imported.
 * @since 1.0
 */
public data class UnresolvedTrack(val path: Path?, val title: String, val reason: UnresolvedReason)

/**
 * Describes a playlist name that was rejected during import.
 *
 * @property name The original iTunes playlist name.
 * @property reason Why the name was rejected.
 * @since 1.0
 */
public data class RejectedPlaylistName(val name: String, val reason: RejectionReason)

/**
 * Reason a track could not be resolved during iTunes import.
 * @since 1.0
 */
public sealed class UnresolvedReason {

    /**
     * The resolved path does not point to an existing file.
     * @since 1.0
     */
    public data object FileNotFound : UnresolvedReason() {
        override fun toString(): String = "file not found"
    }

    /**
     * The file extension is not a supported audio type.
     * @since 1.0
     */
    public data class UnsupportedType(public val extension: String) : UnresolvedReason() {
        override fun toString(): String = "unsupported file type '$extension'"
    }

    /**
     * An unexpected error occurred during import; [message] describes what failed.
     * @since 1.0
     */
    public data class ImportError(public val message: String) : UnresolvedReason() {
        override fun toString(): String = "import error: $message"
    }
}

/**
 * Reason a playlist name was rejected during iTunes import.
 * @since 1.0
 */
public sealed class RejectionReason {

    /**
     * The name contained a character forbidden on Windows filesystems.
     * @since 1.0
     */
    public data class ForbiddenChar(public val char: Char) : RejectionReason() {
        override fun toString(): String = "forbidden character '$char'"
    }

    /**
     * The name matched a reserved Windows name (CON, NUL, etc., case-insensitive, with or without extension).
     * @since 1.0
     */
    public data object ReservedName : RejectionReason() {
        override fun toString(): String = "reserved Windows name"
    }

    /**
     * The name ended with a dot or space (silently stripped by Windows -- rejected to surface the discrepancy).
     * @since 1.0
     */
    public data object TrailingDotOrSpace : RejectionReason() {
        override fun toString(): String = "trailing dot or space"
    }

    /**
     * The name caused the constructed playlist path to exceed Windows MAX_PATH (260 characters).
     * @since 1.0
     */
    public data object ExceedsMaxPath : RejectionReason() {
        override fun toString(): String = "exceeds Windows MAX_PATH (260 characters)"
    }
}
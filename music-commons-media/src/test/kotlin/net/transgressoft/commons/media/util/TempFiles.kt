/******************************************************************************
 * Copyright (C) 2026  Octavio Calleya Garcia                                 *
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

package net.transgressoft.commons.media.util

import java.nio.file.Files
import java.nio.file.Path

/**
 * Deletes a decoded audio temp file, tolerating Windows file locks.
 *
 * Compressed-container SPI decoders (notably the JAAD MP4/AAC reader) can retain a native
 * file handle that the OS only releases once the decoder's finalizer runs. On Windows that
 * leaves the file locked, so an immediate [Files.deleteIfExists] raises a
 * `java.nio.file.FileSystemException`. Nudging a GC cycle runs the pending finalizers, after
 * which the handle is freed and the delete succeeds.
 */
internal fun deleteDecodedTempFile(path: Path) {
    repeat(5) {
        if (runCatching { Files.deleteIfExists(path) }.isSuccess) return
        System.gc()
        Thread.sleep(50)
    }
    runCatching { Files.deleteIfExists(path) }
}
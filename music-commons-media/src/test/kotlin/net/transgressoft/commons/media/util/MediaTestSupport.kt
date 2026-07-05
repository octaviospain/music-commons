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
import java.nio.file.StandardCopyOption
import javax.sound.sampled.AudioInputStream

/**
 * Copies a bundled `/testfiles` resource into a fresh temp file, preserving the original
 * extension so SPI decoders route on the file suffix. Callers dispose the file via
 * [deleteDecodedTempFile] to tolerate Windows decoder handle locks.
 */
fun resourceToTemp(name: String): Path {
    val stream =
        object {}.javaClass.getResourceAsStream("/testfiles/$name")
            ?: throw IllegalArgumentException("Resource not found: $name")
    return stream.use {
        val temp = Files.createTempFile("audio_", ".${name.substringAfterLast('.')}")
        Files.copy(it, temp, StandardCopyOption.REPLACE_EXISTING)
        temp
    }
}

/**
 * Fully drains this PCM stream in 8 KiB chunks and returns the total number of bytes read,
 * closing the stream afterwards. Used to assert that a decoder produced non-empty PCM output.
 */
fun AudioInputStream.drainAndCount(): Int {
    val buffer = ByteArray(8192)
    var total = 0
    var read = read(buffer)
    while (read != -1) {
        if (read > 0) total += read
        read = read(buffer)
    }
    close()
    return total
}
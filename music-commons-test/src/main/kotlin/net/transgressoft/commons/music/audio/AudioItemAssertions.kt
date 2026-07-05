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

package net.transgressoft.commons.music.audio

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.File

infix fun AudioItem.shouldMatch(attributes: AudioItemTestAttributes) {
    val metadata = attributes.metadata
    assertSoftly {
        id shouldBe attributes.id
        title shouldBe metadata.title
        album.name shouldBe metadata.album.name
        album.albumArtist.name shouldBe metadata.album.albumArtist.name
        album.albumArtist.countryCode shouldBe metadata.album.albumArtist.countryCode
        album.label.name shouldBe metadata.album.label.name
        album.label.countryCode shouldBe metadata.album.label.countryCode
        artist shouldBe metadata.artist
        bpm shouldBe metadata.bpm
        trackNumber shouldBe metadata.trackNumber
        discNumber shouldBe metadata.discNumber
        comments shouldBe metadata.comments
        genres shouldBe metadata.genres
        encoder shouldBe metadata.encoder
        uniqueId shouldBe
            buildString {
                append(path.fileName.toString().replace(' ', '_'))
                append('-')
                append(title)
                append('-')
                append(duration.toSeconds())
                append('-')
                append(bitRate)
            }
        artistsInvolved shouldContainExactly
            getArtistsNamesInvolved(
                title, artist.name, album.albumArtist.name
            ).map { Artist.of(it) }.toSet()
    }
}

infix fun File.shouldEqual(audioItemJson: String) {
    this.readText() shouldEqualJson audioItemJson
}

fun List<AudioItem>.shouldBeOrdered() {
    isSortedWith(audioItemTrackDiscNumberComparator()) shouldBe true
}
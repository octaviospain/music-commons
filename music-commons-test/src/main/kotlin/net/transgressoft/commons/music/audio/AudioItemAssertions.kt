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

import net.transgressoft.commons.music.AudioUtils
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.fail
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.File
import java.time.ZoneOffset
import kotlin.io.path.absolutePathString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

infix fun AudioItem.shouldMatch(attributes: AudioItemTestAttributes) {
    assertSoftly {
        id shouldBe attributes.id
        title shouldBe attributes.title
        album.name shouldBe attributes.album.name
        album.albumArtist.name shouldBe attributes.album.albumArtist.name
        album.albumArtist.countryCode shouldBe attributes.album.albumArtist.countryCode
        album.label.name shouldBe attributes.album.label.name
        album.label.countryCode shouldBe album.label.countryCode
        artist shouldBe attributes.artist
        bpm shouldBe attributes.bpm
        trackNumber shouldBe attributes.trackNumber
        discNumber shouldBe attributes.discNumber
        comments shouldBe attributes.comments
        genre shouldBe attributes.genre
        encoder shouldBe attributes.encoder
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
            AudioUtils.getArtistsNamesInvolved(
                title, artist.name, album.albumArtist.name
            ).map { ImmutableArtist.of(it) }.toSet()
    }
}

infix fun JsonObject.shouldContainAudioItem(audioItem: AudioItem) {
    val id = audioItem.id.toString()

    (id in this) shouldBe true

    val itemJson = this[id]?.jsonObject ?: fail("No JSON object found for ID: $id")

    itemJson["path"]?.jsonPrimitive?.content shouldBe audioItem.path.absolutePathString()
    itemJson["id"]?.jsonPrimitive?.int shouldBe audioItem.id
    itemJson["title"]?.jsonPrimitive?.content shouldBe audioItem.title
    itemJson["duration"]?.jsonPrimitive?.int shouldBe audioItem.duration.toSeconds().toInt()
    itemJson["bitRate"]?.jsonPrimitive?.int shouldBe audioItem.bitRate
    itemJson["trackNumber"]?.jsonPrimitive?.intOrNull shouldBe audioItem.trackNumber?.toInt()
    itemJson["discNumber"]?.jsonPrimitive?.intOrNull shouldBe audioItem.discNumber?.toInt()
    itemJson["bpm"]?.jsonPrimitive?.floatOrNull shouldBe audioItem.bpm
    itemJson["encoder"]?.jsonPrimitive?.contentOrNull shouldBe audioItem.encoder
    itemJson["encoding"]?.jsonPrimitive?.contentOrNull shouldBe audioItem.encoding
    itemJson["genre"]?.jsonPrimitive?.content shouldBe audioItem.genre.name
    itemJson["comments"]?.jsonPrimitive?.contentOrNull shouldBe audioItem.comments
    itemJson["playCount"]?.jsonPrimitive?.int shouldBe audioItem.playCount.toInt()

    val artistJson = itemJson["artist"]?.jsonObject
    artistJson?.get("name")?.jsonPrimitive?.content shouldBe audioItem.artist.name
    artistJson?.get("countryCode")?.jsonPrimitive?.content shouldBe audioItem.artist.countryCode.name

    val albumJson = itemJson["album"]?.jsonObject
    albumJson?.get("name")?.jsonPrimitive?.content shouldBe audioItem.album.name
    albumJson?.get("isCompilation")?.jsonPrimitive?.boolean shouldBe audioItem.album.isCompilation
    albumJson?.get("year")?.jsonPrimitive?.intOrNull shouldBe audioItem.album.year?.toInt()

    val albumArtistJson = albumJson?.get("albumArtist")?.jsonObject
    albumArtistJson?.get("name")?.jsonPrimitive?.content shouldBe audioItem.album.albumArtist.name

    val labelJson = albumJson?.get("label")?.jsonObject
    labelJson?.get("name")?.jsonPrimitive?.content shouldBe audioItem.album.label.name

    itemJson["dateOfCreation"]?.jsonPrimitive?.int shouldBe audioItem.dateOfCreation.toEpochSecond(ZoneOffset.UTC).toInt()
    itemJson["lastDateModified"]?.jsonPrimitive?.int shouldBe audioItem.lastDateModified.toEpochSecond(ZoneOffset.UTC).toInt()
}

infix fun File.shouldEqual(audioItemJson: String) {
    this.readText() shouldEqualJson audioItemJson
}
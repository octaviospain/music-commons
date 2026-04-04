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

package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.lirp.persistence.json.LirpEntityPolymorphicSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Serializer map for [MutableAudioPlaylist] instances keyed by [Int] ID.
 *
 * Uses [DefaultPlaylistHierarchy] companion object as the element serializer. Deserialization
 * requires an active [DefaultPlaylistHierarchy] instance set via [DefaultPlaylistHierarchy.Companion.createAndBind]
 * before decoding, so that [MutableAudioPlaylist] instances are constructed directly in the hierarchy.
 */
@get:JvmName("AudioPlaylistMapSerializer")
internal val AudioPlaylistMapSerializer: KSerializer<Map<Int, MutableAudioPlaylist>> =
    MapSerializer(Int.serializer(), DefaultPlaylistHierarchy)

/**
 * Abstract kotlinx serialization serializer for [ReactiveAudioPlaylist] implementations.
 *
 * Serializes playlists by storing audio item IDs and nested playlist IDs rather than full objects.
 * Concrete subclasses provide [createInstance] to construct the appropriate playlist type from
 * the deserialized properties list. The [DefaultPlaylistHierarchy] companion object is the primary
 * implementation, constructing real [MutableAudioPlaylist] instances during deserialization.
 */
abstract class AudioPlaylistSerializerBase<I : ReactiveAudioItem<I>, P : ReactiveAudioPlaylist<I, P>> :
    LirpEntityPolymorphicSerializer<P> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("AudioPlaylist") {
            element<String>("id")
            element<String>("name")
        }

    override fun getPropertiesList(decoder: Decoder): List<Any?> {
        val propertiesList = mutableListOf<Any?>()
        val jsonInput = decoder as? JsonDecoder ?: throw SerializationException("This class can be saved only by Json")
        val jsonObject = jsonInput.decodeJsonElement().jsonObject

        // id
        val id = jsonObject["id"] ?: throw SerializationException("Serialized Playlist should contain id element")
        propertiesList.add(id.jsonPrimitive.int)

        // isDirectory
        val isDirectory = jsonObject["isDirectory"] ?: throw SerializationException("Serialized Playlist should contain isDirectory element")
        propertiesList.add(isDirectory.jsonPrimitive.boolean)

        // name
        val name = jsonObject["name"] ?: throw SerializationException("Serialized Playlist should contain name element")
        propertiesList.add(name.jsonPrimitive.content)

        // audioItemIds
        val audioItemIds = jsonObject["audioItemIds"] ?: throw SerializationException("Serialized Playlist should contain audioItemIds element")
        propertiesList.add(mapAudioItemIds(audioItemIds.jsonArray))

        // playlistIds
        val playlistIds = jsonObject["playlistIds"] ?: throw SerializationException("Serialized Playlist should contain playlistIds element")
        propertiesList.add(mapPlaylistIds(playlistIds.jsonArray))

        return propertiesList
    }

    private fun mapAudioItemIds(ids: JsonArray): List<Int> = ids.map { it.jsonPrimitive.int }

    private fun mapPlaylistIds(ids: JsonArray): Set<Int> = ids.map { it.jsonPrimitive.int }.toSet()

    override fun serialize(
        encoder: Encoder,
        value: P
    ) {
        val jsonOutput = encoder as? JsonEncoder ?: throw SerializationException("This class can be saved only by Json")
        val jsonObject =
            buildJsonObject {
                put("id", value.id)
                put("isDirectory", value.isDirectory)
                put("name", value.name)
                put(
                    "audioItemIds",
                    buildJsonArray {
                        value.audioItems.forEach { add(it.id) }
                    }
                )
                put(
                    "playlistIds",
                    buildJsonArray {
                        value.playlists.forEach { add(it.id) }
                    }
                )
            }

        jsonOutput.encodeJsonElement(jsonObject)
    }
}
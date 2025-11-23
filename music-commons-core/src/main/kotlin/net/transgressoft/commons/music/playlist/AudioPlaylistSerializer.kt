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

import net.transgressoft.commons.event.CrudEvent
import net.transgressoft.commons.event.EntityChangeEvent
import net.transgressoft.commons.event.TransEventPublisher
import net.transgressoft.commons.event.TransEventSubscription
import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.ImmutableAlbum
import net.transgressoft.commons.music.audio.ImmutableArtist
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.persistence.json.TransEntityPolymorphicSerializer
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.Flow
import java.util.function.Consumer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
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

@get:JvmName("AudioPlaylistMapSerializer")
val AudioPlaylistMapSerializer: KSerializer<Map<Int, MutableAudioPlaylist>> = MapSerializer(Int.serializer(), MutableAudioPlaylistSerializer)

/**
 * Kotlinx serialization serializer for [MutableAudioPlaylist] instances.
 *
 * Serializes playlists by storing audio items and nested playlist IDs rather than full objects
 * to minimize storage size. Creates dummy placeholder instances during deserialization that
 * are later resolved to actual entities by [DefaultPlaylistHierarchy].
 */
internal object MutableAudioPlaylistSerializer : AudioPlaylistSerializerBase<AudioItem, MutableAudioPlaylist>() {
    @Suppress("UNCHECKED_CAST")
    override fun createInstance(propertiesList: List<Any?>): MutableAudioPlaylist =
        DummyPlaylist(
            propertiesList[0] as Int,
            propertiesList[1] as Boolean,
            propertiesList[2] as String,
            propertiesList[3] as List<AudioItem>,
            propertiesList[4] as Set<MutableAudioPlaylist>
        )
}

abstract class AudioPlaylistSerializerBase<I: ReactiveAudioItem<I>, P: ReactiveAudioPlaylist<I, P>>: TransEntityPolymorphicSerializer<P> {

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

    @Suppress("UNCHECKED_CAST")
    private fun mapAudioItemIds(ids: JsonArray): List<I> = ids.map { DummyAudioItem(it.jsonPrimitive.int) as I }

    @Suppress("UNCHECKED_CAST")
    private fun mapPlaylistIds(ids: JsonArray): Set<P> = ids.map { DummyPlaylist(it.jsonPrimitive.int) as P }.toSet()

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
                        value.audioItems.forEach {
                            add(it.id)
                        }
                    }
                )
                put(
                    "playlistIds",
                    buildJsonArray {
                        value.playlists.forEach {
                            add(it.id)
                        }
                    }
                )
            }

        jsonOutput.encodeJsonElement(jsonObject)
    }
}

internal class DummyPlaylist(
    override val id: Int,
    override var isDirectory: Boolean = false,
    override var name: String = "",
    override val audioItems: List<AudioItem> = emptyList(),
    override val playlists: Set<MutableAudioPlaylist> = emptySet(),
    override val lastDateModified: LocalDateTime = LocalDateTime.MIN
) : MutableAudioPlaylist {
    override fun addAudioItems(audioItems: Collection<AudioItem>): Boolean = throw IllegalStateException()

    override fun removeAudioItems(audioItems: Collection<AudioItem>): Boolean = throw IllegalStateException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeAudioItemIds")
    override fun removeAudioItems(audioItemIds: Collection<Int>): Boolean = throw IllegalStateException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removePlaylistIds")
    override fun removePlaylists(playlistIds: Collection<Int>): Boolean = throw IllegalStateException()

    override fun clearAudioItems() = throw IllegalStateException()

    override fun clearPlaylists() = throw IllegalStateException()

    override fun subscribe(p0: Flow.Subscriber<in EntityChangeEvent<Int, MutableAudioPlaylist>>?) = throw IllegalStateException()

    override fun removePlaylists(playlists: Collection<MutableAudioPlaylist>): Boolean = throw IllegalStateException()

    override fun addPlaylists(playlists: Collection<MutableAudioPlaylist>): Boolean = throw IllegalStateException()

    override fun clone(): DummyPlaylist = DummyPlaylist(id)

    override val changes: SharedFlow<EntityChangeEvent<Int, MutableAudioPlaylist>>
        get() = throw IllegalStateException()

    override fun emitAsync(event: EntityChangeEvent<Int, MutableAudioPlaylist>): Unit = throw IllegalStateException()

    override fun subscribe(action: suspend (EntityChangeEvent<Int, MutableAudioPlaylist>) -> Unit):
        TransEventSubscription<in MutableAudioPlaylist, CrudEvent.Type, EntityChangeEvent<Int, MutableAudioPlaylist>> =
        FakeSubscription

    override fun subscribe(action: Consumer<in EntityChangeEvent<Int, MutableAudioPlaylist>>):
        TransEventSubscription<in MutableAudioPlaylist, CrudEvent.Type, EntityChangeEvent<Int, MutableAudioPlaylist>> =
        FakeSubscription

    override fun subscribe(vararg eventTypes: CrudEvent.Type, action: Consumer<in EntityChangeEvent<Int, MutableAudioPlaylist>>):
        TransEventSubscription<in MutableAudioPlaylist, CrudEvent.Type, EntityChangeEvent<Int, MutableAudioPlaylist>> =
        FakeSubscription
}

object FakeSubscription : TransEventSubscription<MutableAudioPlaylist, CrudEvent.Type, EntityChangeEvent<Int, MutableAudioPlaylist>> {
    override val source: TransEventPublisher<CrudEvent.Type, EntityChangeEvent<Int, MutableAudioPlaylist>>
        get() = throw IllegalStateException()

    override fun request(n: Long): Unit = throw IllegalStateException()

    override fun cancel() {
        // No-op
    }
}

internal class DummyAudioItem(
    override val id: Int
): AudioItem {
    override val path: Path = Paths.get("")
    override var title: String = ""
    override val duration: Duration = Duration.ZERO
    override val bitRate: Int = 0
    override var artist: Artist = ImmutableArtist.UNKNOWN
    override var album: Album = ImmutableAlbum.UNKNOWN
    override var genre: Genre = Genre.UNDEFINED
    override var comments: String? = null
    override var trackNumber: Short? = null
    override var discNumber: Short? = null
    override var bpm: Float? = null
    override val encoder: String? = null
    override val encoding: String? = null
    override val dateOfCreation: LocalDateTime = LocalDateTime.MIN
    override val lastDateModified: LocalDateTime = LocalDateTime.MIN
    override val changes: SharedFlow<EntityChangeEvent<Int, AudioItem>>
        get() = throw IllegalStateException()

    override fun emitAsync(event: EntityChangeEvent<Int, AudioItem>): Unit = throw IllegalStateException()

    override fun subscribe(action: suspend (EntityChangeEvent<Int, AudioItem>) -> Unit):
        TransEventSubscription<in AudioItem, CrudEvent.Type, EntityChangeEvent<Int, AudioItem>> = throw IllegalStateException()

    override fun subscribe(action: Consumer<in EntityChangeEvent<Int, AudioItem>>):
        TransEventSubscription<in AudioItem, CrudEvent.Type, EntityChangeEvent<Int, AudioItem>> = throw IllegalStateException()

    override fun subscribe(
        vararg eventTypes: CrudEvent.Type,
        action: Consumer<in EntityChangeEvent<Int, AudioItem>>
    ): TransEventSubscription<in AudioItem, CrudEvent.Type, EntityChangeEvent<Int, AudioItem>> = throw IllegalStateException()

    override val uniqueId: String = ""
    override val fileName: String = ""
    override val extension: String = ""
    override val artistsInvolved: Set<Artist> = emptySet()
    override val length: Long = 0
    override var coverImageBytes: ByteArray? = null
    override val playCount: Short = 0

    override fun writeMetadata(): Job = throw IllegalStateException()

    override fun subscribe(p0: Flow.Subscriber<in EntityChangeEvent<Int, AudioItem>>?) = throw IllegalStateException()

    override fun compareTo(other: AudioItem): Int = throw IllegalStateException()

    override fun clone(): DummyAudioItem = DummyAudioItem(id)
}
package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.EntityChangeEvent
import net.transgressoft.commons.data.json.TransEntityPolymorphicSerializer
import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.ImmutableAlbum
import net.transgressoft.commons.music.audio.ImmutableArtist
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.Flow
import kotlinx.coroutines.Job
import kotlinx.serialization.SerializationException
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

object MutableAudioPlaylistSerializer : AudioPlaylistSerializerBase<AudioItem, MutableAudioPlaylist>() {
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
        propertiesList.add(jsonObject["id"]!!.jsonPrimitive.int)
        propertiesList.add(jsonObject["isDirectory"]!!.jsonPrimitive.boolean)
        propertiesList.add(jsonObject["name"]!!.jsonPrimitive.content)
        propertiesList.add(mapAudioItemIds(jsonObject["audioItemIds"]!!.jsonArray))
        propertiesList.add(mapPlaylistIds(jsonObject["playlistIds"]!!.jsonArray))

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
    override val uniqueId: String = ""
    override val fileName: String = ""
    override val extension: String = ""
    override val artistsInvolved: Set<String> = emptySet()
    override val length: Long = 0
    override var coverImageBytes: ByteArray? = null
    override val playCount: Short = 0

    override fun writeMetadata(): Job = throw IllegalStateException()

    override fun subscribe(p0: Flow.Subscriber<in EntityChangeEvent<Int, AudioItem>>?) = throw IllegalStateException()

    override fun clone(): DummyAudioItem = DummyAudioItem(id)
}
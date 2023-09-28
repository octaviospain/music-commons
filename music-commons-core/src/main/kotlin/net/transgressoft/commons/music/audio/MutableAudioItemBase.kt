package net.transgressoft.commons.music.audio

import net.transgressoft.commons.data.StandardDataEventPublisher
import net.transgressoft.commons.music.AudioUtils
import net.transgressoft.commons.music.AudioUtils.audioItemTrackDiscNumberComparator
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.io.path.extension

abstract class MutableAudioItemBase(override val id: Int, attributes: AudioItemAttributes) :
    MutableAudioItem, Comparable<MutableAudioItem>, StandardDataEventPublisher<Int, MutableAudioItem>() {

    /** Immutable properties */

    final override val path: Path = attributes.path

    final override val bitRate = attributes.bitRate

    final override val duration = attributes.duration

    final override val name: String = "AudioItem-${path.fileName}"

    final override val encoder = attributes.encoder

    final override val encoding = attributes.encoding

    final override val dateOfCreation = attributes.dateOfCreation

    override var lastDateModified = attributes.lastDateModified
        protected set

    final override val fileName by lazy {
        path.fileName.toString()
    }

    final override val extension by lazy {
        path.extension
    }

    final override val artistsInvolved by lazy {
        AudioUtils.getArtistsNamesInvolved(title, artist.name, album.albumArtist.name)
    }

    final override val length by lazy {
        path.toFile().length()
    }

    /** Mutable properties */

    override var title: String = attributes.title
        set(value) {
            updateAndPublishEvent(value, field) { newValue -> field = newValue }
        }

    override var artist: Artist = attributes.artist
        set(value) {
            updateAndPublishEvent(value, field) { newValue -> field = newValue }
        }

    override var genre = attributes.genre
        set(value) {
            updateAndPublishEvent(value, field) { newValue -> field = newValue }
        }

    override var comments: String? = attributes.comments
        set(value) {
            updateAndPublishEvent(value, field) { newValue -> field = newValue }
        }

    override var trackNumber: Short? = attributes.trackNumber
        set(value) {
            updateAndPublishEvent(value, field) { newValue -> field = newValue }
        }

    override var discNumber: Short? = attributes.discNumber
        set(value) {
            updateAndPublishEvent(value, field) { newValue -> field = newValue }
        }

    override var bpm: Float? = attributes.bpm
        set(value) {
            updateAndPublishEvent(value, field) { newValue -> field = newValue }
        }

    override var album = attributes.album.also { it.audioItems.add(this) }
        set(value) {
            updateAndPublishEvent(value, field) { newValue -> field = newValue }
        }

    override var coverImageBytes: ByteArray? = attributes.coverImageBytes
        get() = field ?: getCoverBytes(this)
        set(value) {
            updateAndPublishEvent(value, field) { newValue -> field = newValue }
        }

    private fun <T> updateAndPublishEvent(newValue: T, field: T, propertyUpdater: (T) -> Unit) {
        if (newValue != field) {
            val audioItemBeforeChange = InternalMutableAudioItem(id, attributes())
            propertyUpdater(newValue)
            lastDateModified = LocalDateTime.now()
            putUpdateEvent(this, audioItemBeforeChange)
        }
    }

    final override val uniqueId by lazy {
        val fileName = path.fileName.toString().replace(' ', '_')
        "$fileName-$title-${duration.toSeconds()}-$bitRate"
    }

    override fun toImmutableAudioItem(): AudioItem =
        ImmutableAudioItem(
            id,
            path,
            title,
            duration,
            bitRate,
            artist,
            album,
            genre,
            comments,
            trackNumber,
            discNumber,
            bpm,
            encoder,
            encoding,
            coverImageBytes,
            dateOfCreation,
            lastDateModified
        )

    override suspend fun writeMetadata() = JAudioTaggerMetadataWriter().writeMetadata(this)

    override operator fun compareTo(other: MutableAudioItem) = audioItemTrackDiscNumberComparator.compare(this, other)
}
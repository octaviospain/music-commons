package net.transgressoft.commons.music.audio

import net.transgressoft.commons.data.StandardDataEventPublisher
import net.transgressoft.commons.music.AudioUtils
import net.transgressoft.commons.music.AudioUtils.audioItemTrackDiscNumberComparator
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import kotlin.io.path.extension

abstract class MutableAudioItemBase(
    override val id: Int,
    path: Path,
    title: String,
    override val duration: Duration,
    override val bitRate: Int,
    artist: Artist,
    album: Album,
    genre: Genre,
    comments: String?,
    trackNumber: Short?,
    discNumber: Short? = null,
    bpm: Float?,
    override val encoder: String?,
    override val encoding: String?,
    initialCoverImage: ByteArray?,
    override val dateOfCreation: LocalDateTime = LocalDateTime.now(),
    override var lastDateModified: LocalDateTime = dateOfCreation
) : MutableAudioItem, Comparable<MutableAudioItem>, StandardDataEventPublisher<Int, MutableAudioItem>() {

    override val uniqueId by lazy {
        val fileName = path.fileName.toString().replace(' ', '_')
        "$fileName-$title-${duration.toSeconds()}-$bitRate"
    }

    override val name: String = "AudioItem-$uniqueId"

    override val fileName by lazy {
        path.fileName.toString()
    }

    override val extension by lazy {
        path.extension
    }

    override val artistsInvolved by lazy {
        AudioUtils.getArtistsNamesInvolved(title, artist.name, album.albumArtist.name)
    }

    override val length by lazy {
        path.toFile().length()
    }

    private fun <T> updateAndPublishEvent(newValue: T, field: T, propertyUpdater: (T) -> Unit) {
        if (newValue != field) {
            val audioItemBeforeChange = InternalMutableAudioItem(toBuilder())
            propertyUpdater(newValue)
            lastDateModified = LocalDateTime.now()
            putUpdateEvent(this, audioItemBeforeChange)
        }
    }

    override var path: Path = path
        set(value) {
            updateAndPublishEvent(value, field) { newValue -> field = newValue }
        }

    override var title: String = title
        set(value) {
            updateAndPublishEvent(value, field) { newValue -> field = newValue }
        }

    override var artist: Artist = artist
        set(value) {
            updateAndPublishEvent(value, field) { newValue -> field = newValue }
        }

    override var genre = genre
        set(value) {
            updateAndPublishEvent(value, field) { newValue -> field = newValue }
        }

    override var comments: String? = comments
        set(value) {
            updateAndPublishEvent(value, field) { newValue -> field = newValue }
        }

    override var trackNumber: Short? = trackNumber
        set(value) {
            updateAndPublishEvent(value, field) { newValue -> field = newValue }
        }

    override var discNumber: Short? = discNumber
        set(value) {
            updateAndPublishEvent(value, field) { newValue -> field = newValue }
        }

    override var bpm: Float? = bpm
        set(value) {
            updateAndPublishEvent(value, field) { newValue -> field = newValue }
        }

    override var album = album
        set(value) {
            updateAndPublishEvent(value, field) { newValue -> field = newValue }
        }

    override var coverImage: ByteArray? = initialCoverImage
        get() = field ?: AudioUtils.getCoverBytes(this)
        set(value) {
            updateAndPublishEvent(value, field) { newValue -> field = newValue }
        }

    override fun toBuilder(): AudioItemBuilder<out AudioItem> = ImmutableAudioItemBuilder(this)

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
            coverImage,
            dateOfCreation,
            lastDateModified
        )

    override suspend fun writeMetadata() = JAudioTaggerMetadataWriter().writeMetadata(this)

    override operator fun compareTo(other: MutableAudioItem) = audioItemTrackDiscNumberComparator.compare(this, other)
}
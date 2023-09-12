package net.transgressoft.commons.music.audio

import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime

class ImmutableAudioItemBuilder(audioItem: AudioItem?) : AudioItemBuilder<AudioItem> {

    constructor() : this(null)

    internal constructor(builder: AudioItemBuilder<AudioItem>) : this(null) {
        id = builder.id
        path = builder.path
        title = builder.title
        duration = builder.duration
        bitRate = builder.bitRate
        artist = builder.artist
        album = builder.album
        genre = builder.genre
        comments = builder.comments
        trackNumber = builder.trackNumber
        discNumber = builder.discNumber
        bpm = builder.bpm
        encoder = builder.encoder
        encoding = builder.encoding
        coverImage = builder.coverImage
        dateOfCreation = builder.dateOfCreation
        lastDateModified = builder.lastDateModified
    }

    override var id: Int = UNASSIGNED_ID
    override var path: Path = Path.of("")
    override var title: String = ""
    override var duration: Duration = Duration.ZERO
    override var bitRate: Int = 0
    override var artist: Artist = ImmutableArtist.UNKNOWN
    override var album: Album = ImmutableAlbum.UNKNOWN
    override var genre: Genre = Genre.UNDEFINED
    override var comments: String? = null
    override var trackNumber: Short? = null
    override var discNumber: Short? = null
    override var bpm: Float? = null
    override var encoder: String? = null
    override var encoding: String? = null
    override var coverImage: ByteArray? = null
    override var dateOfCreation: LocalDateTime = LocalDateTime.now()
    override var lastDateModified: LocalDateTime = LocalDateTime.now()

    init {
        audioItem?.let {
            id = it.id
            path = it.path
            title = it.title
            duration = it.duration
            bitRate = it.bitRate
            artist = it.artist
            album = it.album
            genre = it.genre
            comments = it.comments
            trackNumber = it.trackNumber
            discNumber = it.discNumber
            bpm = it.bpm
            encoder = it.encoder
            encoding = it.encoding
            coverImage = it.coverImage
            dateOfCreation = it.dateOfCreation
            lastDateModified = it.lastDateModified
        }
    }

    override fun build() =
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

    internal fun id(id: Int) = apply { this.id = id }
    override fun path(path: Path) = apply { this.path = path }
    override fun title(title: String) = apply { this.title = title }
    override fun duration(duration: Duration) = apply { this.duration = duration }
    override fun bitRate(bitRate: Int) = apply { this.bitRate = bitRate }
    override fun artist(artist: Artist) = apply { this.artist = artist }
    override fun album(album: Album) = apply { this.album = album }
    override fun genre(genre: Genre) = apply { this.genre = genre }
    override fun comments(comments: String?) = apply { this.comments = comments }
    override fun trackNumber(trackNumber: Short?) = apply { this.trackNumber = trackNumber }
    override fun discNumber(discNumber: Short?) = apply { this.discNumber = discNumber }
    override fun bpm(bpm: Float?) = apply { this.bpm = bpm }
    override fun encoder(encoder: String?) = apply { this.encoder = encoder }
    override fun encoding(encoding: String?) = apply { this.encoding = encoding }
    override fun coverImage(coverImage: ByteArray?) = apply { this.coverImage = coverImage }
    override fun dateOfCreation(dateOfCreation: LocalDateTime) = apply { this.dateOfCreation = dateOfCreation }
    override fun lastDateModified(lastDateModified: LocalDateTime) = apply { this.lastDateModified = lastDateModified }
}
package net.transgressoft.commons.music.audio

class ImmutableAudioItemBuilder(audioItem: AudioItem?) : AudioItemBuilderBase<AudioItem>(audioItem) {

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

    override fun build(): AudioItem =
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
}
package net.transgressoft.commons.music.audio

data class AudioItemMetadataChange (
    var title: String? = null,
    var artist: Artist? = null,
    var albumName: String? = null,
    var albumArtist: Artist? = null,
    var isCompilation: Boolean? = null,
    var year: Short? = null,
    var label: Label? = null,
    var coverImage: ByteArray? = null,
    var genre: Genre? = null,
    var comments: String? = null,
    var trackNumber: Short? = null,
    var discNumber: Short? = null,
    var bpm: Float? = null
)
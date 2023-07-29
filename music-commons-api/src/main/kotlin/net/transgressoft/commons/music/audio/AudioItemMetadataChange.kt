package net.transgressoft.commons.music.audio

data class AudioItemMetadataChange (
    val title: String? = null,
    val artist: Artist? = null,
    val albumName: String? = null,
    val albumArtist: Artist? = null,
    val isCompilation: Boolean? = null,
    val year: Short? = null,
    val label: Label? = null,
    val coverImage: ByteArray? = null,
    val genre: Genre? = null,
    val comments: String? = null,
    val trackNumber: Short? = null,
    val discNumber: Short? = null,
    val bpm: Float? = null
)
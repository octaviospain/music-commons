package net.transgressoft.commons.music.audio

data class AudioItemMetadataChange(
    val title: String?,
    val artist: Artist?,
    val albumName: String?,
    val albumArtist: Artist?,
    val isCompilation: Boolean?,
    val year: Short?,
    val label: Label?,
    val coveImage: ByteArray?,
    val genre: Genre?,
    val comments: String?,
    val trackNumber: Short?,
    val discNumber: Short?,
    val bpm: Float?
)
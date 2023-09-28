package net.transgressoft.commons.music.audio

import net.transgressoft.commons.IdentifiableEntity

data class AudioItemChange (
    override val id: Int,
    var title: String? = null,
    var artist: Artist? = null,
    var albumName: String? = null,
    var albumArtist: Artist? = null,
    var isCompilation: Boolean? = null,
    var year: Short? = null,
    var label: Label? = null,
    var genre: Genre? = null,
    var comments: String? = null,
    var trackNumber: Short? = null,
    var discNumber: Short? = null,
    var bpm: Float? = null,
    var coverImageBytes: ByteArray? = null
) : IdentifiableEntity<Int> {

    override val uniqueId: String = id.toString()

    var album: Album? = null
        set(value) {
            field = value
            albumName = value?.name
            albumArtist = value?.albumArtist
            isCompilation = value?.isCompilation
            year = value?.year
            label = value?.label
        }
}
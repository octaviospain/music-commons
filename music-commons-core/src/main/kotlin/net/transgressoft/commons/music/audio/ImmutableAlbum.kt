package net.transgressoft.commons.music.audio

import java.util.*

internal data class ImmutableAlbum(
    override val name: String,
    override val albumArtist: Artist = ImmutableArtist.UNKNOWN,
    override val isCompilation: Boolean = false,
    override val year: Short? = null,
    override val label: Label = ImmutableLabel.UNKNOWN,
    private val coverBytes: ByteArray? = null
) : Album {

    override val coverImage: Optional<ByteArray> = Optional.ofNullable(coverBytes)

    override fun compareTo(other: Album): Int {
        val nameComparison = compareValues(name, other.name)
        val artistComparison = compareValues(albumArtist.name, other.albumArtist.name)
        val labelComparison = compareValues(label.name, other.label.name)
        val yearComparison = compareValues(year, other.year)
        return if (nameComparison != 0) {
            nameComparison
        } else if (artistComparison != 0) {
            artistComparison
        } else if (labelComparison != 0) {
            labelComparison
        } else yearComparison
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + albumArtist.hashCode()
        result = 31 * result + isCompilation.hashCode()
        result = 31 * result + year.hashCode()
        result = 31 * result + label.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImmutableAlbum

        if (name != other.name) return false
        if (albumArtist != other.albumArtist) return false
        if (isCompilation != other.isCompilation) return false
        if (year != other.year) return false
        if (label != other.label) return false

        return true
    }

    companion object {
        val UNKNOWN: Album = ImmutableAlbum("")
    }
}

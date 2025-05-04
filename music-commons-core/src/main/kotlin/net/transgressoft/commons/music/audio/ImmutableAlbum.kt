package net.transgressoft.commons.music.audio

import javax.annotation.Nullable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("ImmutableAlbum")
class ImmutableAlbum(
    override val name: String,
    override val albumArtist: Artist,
    override val isCompilation: Boolean = false,
    @Nullable override val year: Short? = null,
    override val label: Label = ImmutableLabel.UNKNOWN
) : Album {

    override fun compareTo(other: Album): Int {
        val nameComparison = compareValues(name, other.name)
        val artistComparison = compareValues(albumArtist.name, other.albumArtist.name)
        val labelComparison = compareValues(label.name, other.label.name)
        val yearComparison = compareValues(year, other.year)
        return when {
            labelComparison != 0 -> labelComparison
            yearComparison != 0 -> yearComparison
            artistComparison != 0 -> artistComparison
            else -> nameComparison
        }
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
        return label == other.label
    }

    override fun toString() = "ImmutableAlbum(name='$name', albumArtist=$albumArtist, isCompilation=$isCompilation, year=$year, label=$label)"

    companion object {
        @get:JvmName("UNKNOWN")
        val UNKNOWN = ImmutableAlbum("", ImmutableArtist.UNKNOWN)
    }
}
package net.transgressoft.commons.music.audio

import com.neovisionaries.i18n.CountryCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @author Octavio Calleya
 */
@Serializable
@SerialName("DefaultArtist")
data class ImmutableArtist(override val name: String, override val countryCode: CountryCode = CountryCode.UNDEFINED) : Artist {

    override fun compareTo(other: Artist): Int {
        val result = compareValues(name, other.name)
        return if (result == 0)
            compareValues(countryCode, other.countryCode)
        else
            result
    }

    companion object {
        @JvmField
        val UNKNOWN: Artist = ImmutableArtist("")
    }
}
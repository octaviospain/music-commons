package net.transgressoft.commons.music.audio

import com.neovisionaries.i18n.CountryCode
import kotlinx.serialization.Serializable

/**
 * @author Octavio Calleya
 */
@Serializable
data class ImmutableLabel(override val name: String, override val countryCode: CountryCode = CountryCode.UNDEFINED) : Label {

    override fun compareTo(other: Label): Int {
        val result = compareValues(name, other.name)
        return if (result == 0)
            compareValues(countryCode, other.countryCode)
        else
            result
    }

    companion object {
        @JvmField
        val UNKNOWN = ImmutableLabel("")
    }
}
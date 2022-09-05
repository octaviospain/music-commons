package net.transgressoft.commons.music.audio

import java.util.Locale.IsoCountryCode

/**
 * @author Octavio Calleya
 */
data class ImmutableLabel(override val name: String, override val countryCode: IsoCountryCode? = null) : Label {

    override fun compareTo(other: Label): Int {
        val result = compareValues(name, other.name)
        return if (result == 0)
            compareValues(countryCode, other.countryCode)
        else
            result
    }

    companion object {
        @JvmField
        val UNKNOWN: Label = ImmutableLabel("")
    }
}
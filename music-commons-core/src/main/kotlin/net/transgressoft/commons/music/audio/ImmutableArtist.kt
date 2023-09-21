package net.transgressoft.commons.music.audio

import com.neovisionaries.i18n.CountryCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @author Octavio Calleya
 */
@Serializable
@SerialName("ImmutableArtist")
class ImmutableArtist internal constructor(override val name: String, override val countryCode: CountryCode = CountryCode.UNDEFINED) : Artist {

    internal val id = id(name, countryCode)

    override fun compareTo(other: Artist): Int {
        val result = compareValues(name, other.name)
        return if (result == 0)
            compareValues(countryCode, other.countryCode)
        else
            result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImmutableArtist

        if (name != other.name) return false
        if (countryCode != other.countryCode) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + countryCode.hashCode()
        return result
    }

    override fun toString() = "Artist($name, ${countryCode.name}"

    companion object {

        private val artistMap: MutableMap<String, Artist> = HashMap<String, Artist>().apply { put("", UNKNOWN) }

        @JvmField
        val UNKNOWN: Artist = ImmutableArtist("")

        @JvmStatic
        fun of(name: String, countryCode: CountryCode = CountryCode.UNDEFINED) =
            artistMap.getOrPut(id(name, countryCode)) { ImmutableArtist(name, countryCode) }

        internal fun id(name: String, countryCode: CountryCode = CountryCode.UNDEFINED) =
            if (countryCode == CountryCode.UNDEFINED) {
                name
            } else {
                "$name-${countryCode.name}"
            }
    }
}

fun Artist.id() = ImmutableArtist.id(name, countryCode)
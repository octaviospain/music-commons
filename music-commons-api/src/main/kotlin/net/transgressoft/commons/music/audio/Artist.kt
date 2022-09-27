package net.transgressoft.commons.music.audio

import com.neovisionaries.i18n.CountryCode

/**
 * @author Octavio Calleya
 */
interface Artist : Comparable<Artist> {
    val name: String
    val countryCode: CountryCode
}
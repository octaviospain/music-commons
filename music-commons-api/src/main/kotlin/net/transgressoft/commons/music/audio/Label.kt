package net.transgressoft.commons.music.audio

import com.neovisionaries.i18n.CountryCode

/**
 * @author Octavio Calleya
 */
interface Label : Comparable<Label> {
    val name: String
    val countryCode: CountryCode
}
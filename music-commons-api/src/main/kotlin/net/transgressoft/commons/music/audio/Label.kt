package net.transgressoft.commons.music.audio

import java.util.Locale.IsoCountryCode

/**
 * @author Octavio Calleya
 */
interface Label : Comparable<Label> {
    val name: String
    val countryCode: IsoCountryCode?
}
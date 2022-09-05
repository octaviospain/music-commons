package net.transgressoft.commons.music.audio

import java.util.Locale.IsoCountryCode

/**
 * @author Octavio Calleya
 */
interface Artist : Comparable<Artist> {
    val name: String
    val countryCode: IsoCountryCode?
}
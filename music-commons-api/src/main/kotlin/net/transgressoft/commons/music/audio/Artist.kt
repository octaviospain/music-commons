package net.transgressoft.commons.music.audio

import com.neovisionaries.i18n.CountryCode

interface Artist : Comparable<Artist> {
    val name: String
    val countryCode: CountryCode
}
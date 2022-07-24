package com.transgressoft.commons.music;

import com.neovisionaries.i18n.CountryCode;

/**
 * @author Octavio Calleya
 */
public interface Artist extends Comparable<Artist> {

    String name();

    CountryCode countryCode();
}

package com.transgressoft.commons.music;

import com.google.common.base.*;
import com.neovisionaries.i18n.CountryCode;

/**
 * @author Octavio Calleya
 */
public class SimpleArtist implements Artist {

    private final String name;
    private final CountryCode countryCode;

    public static final Artist UNKNOWN_ARTIST = new SimpleArtist("");

    public SimpleArtist(String name, CountryCode countryCode) {
        this.name = name;
        this.countryCode = countryCode;
    }

    public SimpleArtist(String name) {
        this.name = name;
        this.countryCode = CountryCode.UNDEFINED;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public CountryCode countryCode() {
        return countryCode;
    }

    @Override
    public int compareTo(Artist other) {
        int result = name.compareTo(other.name());
        return result == 0 ? countryCode.compareTo(other.countryCode()) : result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimpleArtist that = (SimpleArtist) o;
        return Objects.equal(name, that.name) &&
                countryCode == that.countryCode;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, countryCode);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("countryCode", countryCode.getAlpha2())
                .toString();
    }
}

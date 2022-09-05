package net.transgressoft.commons.music.audio;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.neovisionaries.i18n.CountryCode;
import org.apache.commons.text.WordUtils;

/**
 * @author Octavio Calleya
 */
public class ImmutableArtist implements Artist {

    private final String name;
    private final CountryCode countryCode;

    public static final Artist UNKNOWN_ARTIST = new ImmutableArtist("");

    public ImmutableArtist(String name, CountryCode countryCode) {
        this.name = beautifyName(name);
        this.countryCode = countryCode;
    }

    public ImmutableArtist(String name) {
        this.name = beautifyName(name);
        this.countryCode = CountryCode.UNDEFINED;
    }

    private String beautifyName(String name) {
        return WordUtils.capitalize(name)
                .replaceAll("\\s+", " ")
                .replaceAll(" (?i)(vs)(\\.|\\s)", " vs ")
                .replaceAll(" (?i)(versus) ", " versus ");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String countryCode() {
        return countryCode.name();
    }

    @Override
    public int compareTo(Artist other) {
        int result = name.compareTo(other.name());
        return result == 0 ? countryCode.name().compareTo(other.countryCode()) : result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImmutableArtist that = (ImmutableArtist) o;
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
                .add("countryCode", countryCode.name())
                .toString();
    }
}

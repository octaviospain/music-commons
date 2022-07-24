package net.transgressoft.commons.music;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.neovisionaries.i18n.CountryCode;

/**
 * @author Octavio Calleya
 */
public class ImmutableLabel implements Label {

    private final String name;
    private final CountryCode countryCode;

    public static Label UNKNOWN = new ImmutableLabel("");

    public ImmutableLabel(String name, CountryCode countryCode) {
        this.name = name.replaceAll("\\s+", " ");
        this.countryCode = countryCode;
    }

    public ImmutableLabel(String name) {
        this.name = name.replaceAll("\\s+", " ");
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImmutableLabel that = (ImmutableLabel) o;
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
                .add("countryCode", countryCode)
                .toString();
    }
}

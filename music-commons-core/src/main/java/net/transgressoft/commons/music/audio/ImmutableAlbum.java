package net.transgressoft.commons.music.audio;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.neovisionaries.i18n.CountryCode;

import java.util.Optional;

/**
 * @author Octavio Calleya
 */
public class ImmutableAlbum implements Album {

    private final String name;
    private final Artist albumArtist;
    private final boolean isCompilation;
    private final short year;
    private final Label label;
    private final byte[] coverBytes;

    public static final Album UNKNOWN_ALBUM = new ImmutableAlbum("");

    public ImmutableAlbum(String name, Artist albumArtist, boolean isCompilation, short year, Label label, byte[] coverBytes) {
        this.name = beautifyName(name);
        this.albumArtist = albumArtist;
        this.isCompilation = isCompilation;
        this.year = year;
        this.label = label;
        this.coverBytes = coverBytes;
    }

    public ImmutableAlbum(String name, Artist albumArtist, boolean isCompilation, short year, Label label) {
        this.name = beautifyName(name);
        this.albumArtist = albumArtist;
        this.isCompilation = isCompilation;
        this.year = year;
        this.label = label;
        this.coverBytes = null;
    }

    public ImmutableAlbum(String name, short year) {
        this.name = beautifyName(name);
        this.albumArtist = ImmutableArtist.UNKNOWN_ARTIST;
        this.isCompilation = false;
        this.year = year;
        this.label = new ImmutableLabel("", CountryCode.UNDEFINED);
        this.coverBytes = null;
    }

    public ImmutableAlbum(String name) {
        this.name = beautifyName(name);
        this.albumArtist = ImmutableArtist.UNKNOWN_ARTIST;
        this.isCompilation = false;
        this.year = -1;
        this.label = new ImmutableLabel("", CountryCode.UNDEFINED);
        this.coverBytes = null;
    }

    private String beautifyName(String name) {
        return name.replaceAll("\\s+", " ");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Artist albumArtist() {
        return albumArtist;
    }

    @Override
    public boolean isCompilation() {
        return isCompilation;
    }

    @Override
    public short year() {
        return year;
    }

    @Override
    public Label label() {
        return label;
    }

    @Override
    public Optional<byte[]> coverImage() {
        return java.util.Objects.equals(coverBytes, new byte[0]) ? Optional.empty() : Optional.of(coverBytes);
    }

    @Override
    public int compareTo(Album o) {
        int nameComparison = name.compareTo(o.name());
        int artistComparison = albumArtist.name().compareTo(o.albumArtist().name());
        int labelComparison = label.name().compareTo(o.label().name());
        int yearComparison = year - o.year();

        if (nameComparison != 0) {
            return nameComparison;
        } else if (artistComparison != 0) {
            return artistComparison;
        } else if (labelComparison != 0) {
            return labelComparison;
        } else return yearComparison;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImmutableAlbum that = (ImmutableAlbum) o;
        return isCompilation == that.isCompilation &&
                year == that.year &&
                Objects.equal(name, that.name) &&
                Objects.equal(albumArtist, that.albumArtist) &&
                Objects.equal(label, that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, albumArtist, isCompilation, year, label);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("albumArtist", albumArtist)
                .add("isCompilation", isCompilation)
                .add("year", year)
                .add("label", label)
                .toString();
    }
}

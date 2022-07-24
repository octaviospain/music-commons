package com.transgressoft.commons.music;

import com.google.common.base.*;
import org.apache.commons.io.FilenameUtils;

import java.nio.file.Path;
import java.time.Duration;

/**
 * @author Octavio Calleya
 */
public class SimpleAudioItem implements AudioItem {

    private final Path path;
    private final String name;
    private final Artist artist;
    private final Album album;
    private final Genre genre;
    private final String comments;

    private final short trackNumber;
    private final short discNumber;
    private final float bpm;
    private final Duration duration;
    private final int bitRate;
    private final String encoder;
    private final String encoding;

    public static SimpleAudioItemBuilder builder(Path path, String name, Duration duration, int bitRate) {
        return new SimpleAudioItemBuilder(path, name, duration, bitRate);
    }

    public SimpleAudioItem(Path path, String name, Artist artist, Album album, Genre genre, String comments, short trackNumber,
                           short discNumber, float bpm, Duration duration, int bitRate, String encoder, String encoding) {
        this.path = path;
        this.name = name;
        this.artist = artist;
        this.album = album;
        this.genre = genre;
        this.comments = comments;
        this.trackNumber = trackNumber;
        this.discNumber = discNumber;
        this.bpm = bpm;
        this.duration = duration;
        this.bitRate = bitRate;
        this.encoder = encoder;
        this.encoding = encoding;
    }

    @Override
    public Path path() {
        return path;
    }

    @Override
    public AudioItem path(Path path) {
        return new SimpleAudioItemBuilder(path, name, duration, bitRate).build();
    }

    @Override
    public String fileName() {
        return path.getFileName().toString();
    }

    @Override
    public String extension() {
        return FilenameUtils.getExtension(path.toString());
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public AudioItem name(String name) {
        return new SimpleAudioItemBuilder(path, name, duration, bitRate).build();
    }

    @Override
    public Artist artist() {
        return artist;
    }

    @Override
    public AudioItem artist(Artist artist) {
        return new SimpleAudioItemBuilder(this).artist(artist).build();
    }

    @Override
    public Album album() {
        return album;
    }

    @Override
    public AudioItem album(Album album) {
        return new SimpleAudioItemBuilder(this).album(album).build();
    }

    @Override
    public Genre genre() {
        return genre;
    }

    @Override
    public AudioItem genre(Genre genre) {
        return new SimpleAudioItemBuilder(this).genre(genre).build();
    }

    @Override
    public String comments() {
        return comments;
    }

    @Override
    public AudioItem comments(String comments) {
        return new SimpleAudioItemBuilder(this).comments(comments).build();
    }

    @Override
    public short trackNumber() {
        return trackNumber;
    }

    @Override
    public AudioItem trackNumber(short trackNumber) {
        return new SimpleAudioItemBuilder(this).trackNumber(trackNumber).build();
    }

    @Override
    public short discNumber() {
        return discNumber;
    }

    @Override
    public AudioItem discNumber(short discNumber) {
        return new SimpleAudioItemBuilder(this).discNumber(discNumber).build();
    }

    @Override
    public float bpm() {
        return bpm;
    }

    @Override
    public AudioItem bpm(float bpm) {
        return new SimpleAudioItemBuilder(this).bpm(bpm).build();
    }

    @Override
    public Duration duration() {
        return duration;
    }

    @Override
    public long length() {
        return path.toFile().length();
    }

    @Override
    public int bitRate() {
        return bitRate;
    }

    @Override
    public String encoder() {
        return encoder;
    }

    @Override
    public AudioItem encoder(String encoder) {
        return new SimpleAudioItemBuilder(this).encoder(encoder).build();
    }

    @Override
    public String encoding() {
        return encoding;
    }

    @Override
    public AudioItem encoding(String encoding) {
        return new SimpleAudioItemBuilder(this).encoding(encoding).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimpleAudioItem that = (SimpleAudioItem) o;
        return trackNumber == that.trackNumber &&
                discNumber == that.discNumber &&
                Float.compare(that.bpm, bpm) == 0 &&
                Objects.equal(path, that.path) &&
                Objects.equal(name, that.name) &&
                Objects.equal(artist, that.artist) &&
                Objects.equal(album, that.album) &&
                genre == that.genre &&
                Objects.equal(comments, that.comments) &&
                Objects.equal(duration, that.duration);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(path, name, artist, album, genre, comments, trackNumber, discNumber, bpm, duration);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("path", path)
                .add("name", name)
                .add("artist", artist)
                .toString();
    }

    public static interface Builder<E extends AudioItem>  {

        E build();
    }

    public static class SimpleAudioItemBuilder implements Builder<AudioItem> {

        protected final Path path;
        protected final String name;
        protected final Duration duration;
        protected final int bitRate;
        protected Artist artist = SimpleArtist.UNKNOWN;
        protected Album album = new SimpleAlbum("");
        protected Genre genre = Genre.UNDEFINED;
        protected String comments = "";
        protected short trackNumber = - 1;
        protected short discNumber = - 1;
        protected float bpm = - 1;
        protected String encoder = "";
        protected String encoding = "";

        public SimpleAudioItemBuilder(Path path, String name, Duration duration, int bitRate) {
            this.path = path;
            this.name = name;
            this.duration = duration;
            this.bitRate = bitRate;
        }

        protected SimpleAudioItemBuilder(Path path, String name, Duration duration, int bitRate, Artist artist, Album album, Genre genre, String comments,
                                      short trackNumber, short discNumber, float bpm, String encoder, String encoding) {
            this.path = path;
            this.name = name;
            this.duration = duration;
            this.bitRate = bitRate;
            this.artist = artist;
            this.album = album;
            this.genre = genre;
            this.comments = comments;
            this.trackNumber = trackNumber;
            this.discNumber = discNumber;
            this.bpm = bpm;
            this.encoder = encoder;
            this.encoding = encoding;
        }

        private SimpleAudioItemBuilder(AudioItem audioItem) {
            this(audioItem.path(), audioItem.name(), audioItem.duration(), audioItem.bitRate(), audioItem.artist(), audioItem.album(),
                 audioItem.genre(), audioItem.comments(), audioItem.trackNumber(), audioItem.discNumber(), audioItem.bpm(),
                 audioItem.encoder(), audioItem.encoding());
        }

        public SimpleAudioItemBuilder artist(Artist artist) {
            this.artist = artist;
            return this;
        }

        public SimpleAudioItemBuilder album(Album album) {
            this.album = album;
            return this;
        }

        public SimpleAudioItemBuilder genre(Genre genre) {
            this.genre = genre;
            return this;
        }

        public SimpleAudioItemBuilder comments(String comments) {
            this.comments = comments;
            return this;
        }

        public SimpleAudioItemBuilder trackNumber(short trackNumber) {
            this.trackNumber = trackNumber;
            return this;
        }

        public SimpleAudioItemBuilder discNumber(short discNumber) {
            this.discNumber = discNumber;
            return this;
        }

        public SimpleAudioItemBuilder bpm(float bpm) {
            this.bpm = bpm;
            return this;
        }

        public SimpleAudioItemBuilder encoder(String encoder) {
            this.encoder = encoder;
            return this;
        }

        public SimpleAudioItemBuilder encoding(String encoding) {
            this.encoding = encoding;
            return this;
        }

        @Override
        public SimpleAudioItem build() {
            return new SimpleAudioItem(path, name, artist, album, genre, comments, trackNumber, discNumber, bpm, duration, bitRate, encoder, encoding);
        }
    }
}

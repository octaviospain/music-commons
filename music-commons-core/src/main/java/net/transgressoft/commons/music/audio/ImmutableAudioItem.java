package net.transgressoft.commons.music.audio;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.transgressoft.commons.query.EntityAttribute;
import net.transgressoft.commons.query.QueryEntity;
import org.apache.commons.io.FilenameUtils;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import static net.transgressoft.commons.music.audio.DurationAudioItemAttribute.DURATION;
import static net.transgressoft.commons.music.audio.FloatAudioItemAttribute.BPM;
import static net.transgressoft.commons.music.audio.IntegerAudioItemAttribute.BITRATE;
import static net.transgressoft.commons.music.audio.LocalDateTimeAudioItemAttribute.DATE_OF_INCLUSION;
import static net.transgressoft.commons.music.audio.LocalDateTimeAudioItemAttribute.LAST_DATE_MODIFIED;
import static net.transgressoft.commons.music.audio.PathAudioItemAttribute.PATH;
import static net.transgressoft.commons.music.audio.ShortAudioItemAttribute.*;
import static net.transgressoft.commons.music.audio.StringAudioItemAttribute.*;

/**
 * @author Octavio Calleya
 */
class ImmutableAudioItem implements AudioItem, Comparable<AudioItem> {

    private final Path path;
    private final String title;
    private final Artist artist;
    private final Set<String> artistsInvolved;
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
    private final short playCount;
    private final LocalDateTime dateOfInclusion;
    private final LocalDateTime lastDateModified;

    private final Map<EntityAttribute<?>, Object> attributes;

    protected ImmutableAudioItem(Path path, String title, Artist artist, Set<String> artistsInvolved, Album album, Genre genre,
                                 String comments, short trackNumber, short discNumber, float bpm, Duration duration, int bitRate, String encoder,
                                 String encoding, short playCount, LocalDateTime dateOfInclusion, LocalDateTime lastDateModified) {
        this.path = path;
        this.title = title;
        this.artist = artist;
        this.artistsInvolved = artistsInvolved;
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
        this.playCount = playCount;
        this.dateOfInclusion = dateOfInclusion;
        this.lastDateModified = lastDateModified;

        attributes = ImmutableMap.<EntityAttribute<?>, Object>builder()
                .put(PATH, path.toString())
                .put(TITLE, title)
                .put(ARTIST, artist.name())
                .put(ARTISTS_INVOLVED, artistsInvolved.toString())
                .put(ALBUM, album.name())
                .put(YEAR, album.year())
                .put(GENRE, genre.name())
                .put(COMMENTS, comments)
                .put(TRACK_NUMBER, trackNumber)
                .put(DISC_NUMBER, discNumber)
                .put(BPM, bpm)
                .put(DURATION, duration)
                .put(BITRATE, bitRate)
                .put(ENCODER, encoder)
                .put(ENCODING, encoder)
                .put(PLAY_COUNT, playCount)
                .put(DATE_OF_INCLUSION, dateOfInclusion)
                .put(LAST_DATE_MODIFIED, lastDateModified)
                .build();
    }

    @Override
    public int id() {
        return getUniqueId().hashCode();
    }

    @Override
    public String getUniqueId() {
        return new StringJoiner("-")
                .add(fileName().replace(' ', '_'))
                .add(title)
                .add(duration.toString())
                .add(String.valueOf(bitRate()))
                .toString();
    }

    @Override
    public Path path() {
        return path;
    }

    @Override
    public AudioItem path(Path path) {
        return new ImmutableAudioItemBuilder(path, title, duration, bitRate, dateOfInclusion, LocalDateTime.now()).build();
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
    public String title() {
        return title;
    }

    @Override
    public AudioItem title(String title) {
        return new ImmutableAudioItemBuilder(path, title, duration, bitRate, dateOfInclusion, LocalDateTime.now()).build();
    }

    @Override
    public Artist artist() {
        return artist;
    }

    @Override
    public AudioItem artist(Artist artist) {
        return new ImmutableAudioItemBuilder(this).artist(artist).build();
    }

    @Override
    public ImmutableSet<String> artistsInvolved() {
        return ImmutableSet.copyOf(artistsInvolved);
    }

    @Override
    public Album album() {
        return album;
    }

    @Override
    public AudioItem album(Album album) {
        return new ImmutableAudioItemBuilder(this).album(album).build();
    }

    @Override
    public Genre genre() {
        return genre;
    }

    @Override
    public AudioItem genre(Genre genre) {
        return new ImmutableAudioItemBuilder(this).genre(genre).build();
    }

    @Override
    public String comments() {
        return comments;
    }

    @Override
    public AudioItem comments(String comments) {
        return new ImmutableAudioItemBuilder(this).comments(comments).build();
    }

    @Override
    public short trackNumber() {
        return trackNumber;
    }

    @Override
    public AudioItem trackNumber(short trackNumber) {
        return new ImmutableAudioItemBuilder(this).trackNumber(trackNumber).build();
    }

    @Override
    public short discNumber() {
        return discNumber;
    }

    @Override
    public AudioItem discNumber(short discNumber) {
        return new ImmutableAudioItemBuilder(this).discNumber(discNumber).build();
    }

    @Override
    public float bpm() {
        return bpm;
    }

    @Override
    public AudioItem bpm(float bpm) {
        return new ImmutableAudioItemBuilder(this).bpm(bpm).build();
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
        return new ImmutableAudioItemBuilder(this).encoder(encoder).build();
    }

    @Override
    public String encoding() {
        return encoding;
    }

    @Override
    public AudioItem encoding(String encoding) {
        return new ImmutableAudioItemBuilder(this).encoding(encoding).build();
    }

    @Override
    public short playCount() {
        return playCount;
    }

    @Override
    public AudioItem playCount(short playCount) {
        return new ImmutableAudioItemBuilder(this).playCount(playCount).build();
    }

    @Override
    public LocalDateTime dateOfInclusion() {
        return dateOfInclusion;
    }

    @Override
    public LocalDateTime lastDateModified() {
        return lastDateModified;
    }

    @Override
    public <A extends EntityAttribute<V>, V> V getAttribute(A attribute) {
        return (V) attributes.get(attribute);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var that = (ImmutableAudioItem) o;
        return trackNumber == that.trackNumber &&
                discNumber == that.discNumber &&
                Float.compare(that.bpm, bpm) == 0 &&
                Objects.equal(path, that.path) &&
                Objects.equal(title, that.title) &&
                Objects.equal(artist, that.artist) &&
                Objects.equal(album, that.album) &&
                genre == that.genre &&
                Objects.equal(comments, that.comments) &&
                Objects.equal(duration, that.duration);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(path, title, artist, album, genre, comments, trackNumber, discNumber, bpm, duration);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("path", path)
                .add("name", title)
                .add("artist", artist)
                .toString();
    }

    @Override
    public int compareTo(@Nonnull AudioItem object) {
        return Comparator.comparing(QueryEntity::getUniqueId, String.CASE_INSENSITIVE_ORDER).compare(this, object);
    }
}

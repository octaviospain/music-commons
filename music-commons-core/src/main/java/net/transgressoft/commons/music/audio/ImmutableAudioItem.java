package net.transgressoft.commons.music.audio;

import com.google.common.base.CharMatcher;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.transgressoft.commons.query.EntityAttribute;
import net.transgressoft.commons.query.QueryEntity;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.text.WordUtils;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.transgressoft.commons.music.audio.AlbumAttribute.ALBUM;
import static net.transgressoft.commons.music.audio.ArtistAttribute.ALBUM_ARTIST;
import static net.transgressoft.commons.music.audio.ArtistAttribute.ARTIST;
import static net.transgressoft.commons.music.audio.ArtistsInvolvedAttribute.ARTISTS_INVOLVED;
import static net.transgressoft.commons.music.audio.AudioItemDurationAttribute.DURATION;
import static net.transgressoft.commons.music.audio.AudioItemFloatAttribute.BPM;
import static net.transgressoft.commons.music.audio.AudioItemIntegerAttribute.BITRATE;
import static net.transgressoft.commons.music.audio.AudioItemLocalDateTimeAttribute.DATE_OF_CREATION;
import static net.transgressoft.commons.music.audio.AudioItemLocalDateTimeAttribute.LAST_DATE_MODIFIED;
import static net.transgressoft.commons.music.audio.AudioItemPathAttribute.PATH;
import static net.transgressoft.commons.music.audio.AudioItemShortAttribute.DISC_NUMBER;
import static net.transgressoft.commons.music.audio.AudioItemShortAttribute.TRACK_NUMBER;
import static net.transgressoft.commons.music.audio.AudioItemShortAttribute.YEAR;
import static net.transgressoft.commons.music.audio.AudioItemStringAttribute.*;

/**
 * @author Octavio Calleya
 */
class ImmutableAudioItem implements AudioItem, Comparable<AudioItem> {

    private final int id;
    private final Path path;
    private final String title;
    private final Artist artist;
    private final Set<? extends String> artistsInvolved;
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
    private final LocalDateTime dateOfCreation;
    private final LocalDateTime lastDateModified;

    private final AudioItemAttributes attributes;

    protected ImmutableAudioItem(int id, AudioItemAttributes attributes) {
        this.id = id;
        this.attributes = attributes;
        this.path = attributes.get(PATH);
        this.title = attributes.get(TITLE);
        this.artist = attributes.get(ARTIST);
        this.album = attributes.get(ALBUM);
        this.genre = Genre.parseGenre(attributes.get(GENRE_NAME));
        this.comments = attributes.get(COMMENTS);
        this.trackNumber = attributes.get(TRACK_NUMBER);
        this.discNumber = attributes.get(DISC_NUMBER);
        this.bpm = attributes.get(BPM);
        this.duration = attributes.get(DURATION);
        this.bitRate = attributes.get(BITRATE);
        this.encoder = attributes.get(ENCODER);
        this.encoding = attributes.get(ENCODING);
        attributes.set(LABEL_NAME, album.label().name());
        attributes.set(YEAR, album.year());
        attributes.set(ALBUM_ARTIST, album.albumArtist());
        attributes.set(ARTISTS_INVOLVED, getArtistsNamesInvolved(title, artist.name(), album.albumArtist().name()));
        this.artistsInvolved = attributes.get(ARTISTS_INVOLVED);

        var now = LocalDateTime.now();
        attributes.putIfAbsent(DATE_OF_CREATION, now);
        this.dateOfCreation = attributes.get(DATE_OF_CREATION);
        attributes.set(LAST_DATE_MODIFIED, now);
        this.lastDateModified = attributes.get(LAST_DATE_MODIFIED);
    }

    @Override
    public int getId() {
        return id;
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
        return new ImmutableAudioItem(id, attributes.modifiedCopy(PATH, path));
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
        return new ImmutableAudioItem(id, attributes.modifiedCopy(TITLE, title));
    }

    @Override
    public Artist artist() {
        return artist;
    }

    @Override
    public AudioItem artist(Artist artist) {
        return new ImmutableAudioItem(id, attributes.modifiedCopy(ARTIST, artist));
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
        return new ImmutableAudioItem(id, attributes.modifiedCopy(ALBUM, album));
    }

    @Override
    public Genre genre() {
        return genre;
    }

    @Override
    public AudioItem genre(Genre genre) {
        return new ImmutableAudioItem(id, attributes.modifiedCopy(GENRE_NAME, genre.name()));
    }

    @Override
    public String comments() {
        return comments;
    }

    @Override
    public AudioItem comments(String comments) {
        return new ImmutableAudioItem(id, attributes.modifiedCopy(COMMENTS, comments));

    }

    @Override
    public short trackNumber() {
        return trackNumber;
    }

    @Override
    public AudioItem trackNumber(short trackNumber) {
        return new ImmutableAudioItem(id, attributes.modifiedCopy(TRACK_NUMBER, trackNumber));

    }

    @Override
    public short discNumber() {
        return discNumber;
    }

    @Override
    public AudioItem discNumber(short discNumber) {
        return new ImmutableAudioItem(id, attributes.modifiedCopy(DISC_NUMBER, discNumber));

    }

    @Override
    public float bpm() {
        return bpm;
    }

    @Override
    public AudioItem bpm(float bpm) {
        return new ImmutableAudioItem(id, attributes.modifiedCopy(BPM, bpm));
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
        return new ImmutableAudioItem(id, attributes.modifiedCopy(ENCODER, encoder));
    }

    @Override
    public String encoding() {
        return encoding;
    }

    @Override
    public AudioItem encoding(String encoding) {
        return new ImmutableAudioItem(id, attributes.modifiedCopy(ENCODING, encoding));
    }

    @Override
    public LocalDateTime dateOfInclusion() {
        return dateOfCreation;
    }

    @Override
    public LocalDateTime lastDateModified() {
        return lastDateModified;
    }

    @Override
    public <A extends EntityAttribute<V>, V> V getAttribute(A attribute) {
        return attributes.get(attribute);
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
    public int compareTo(@Nonnull AudioItem object) {
        return Comparator.comparing(QueryEntity::getUniqueId, String.CASE_INSENSITIVE_ORDER).compare(this, object);
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

    private static final Map<Pattern, Pattern> artistsRegexMap;
    static {
        var endsWithRemix = Pattern.compile("[(|\\[](\\s*(&?\\s*(\\w+)\\s+)+(?i)(remix))[)|\\]]");
        var startsWithRemixBy = Pattern.compile("[(|\\[](?i)(remix)(\\s+)(?i)(by)(.+)[)|\\]]");
        var hasFt = Pattern.compile("[(|\\[|\\s](?i)(ft) (.+)");
        var hasFeat = Pattern.compile("[(|\\[|\\s](?i)(feat) (.+)");
        var hasFeaturing = Pattern.compile("[(|\\[|\\s](?i)(featuring) (.+)");
        var startsWithWith = Pattern.compile("[(|\\[](?i)(with) (.+)[)|\\]]");

        artistsRegexMap = ImmutableMap.<Pattern, Pattern>builder()
                .put(Pattern.compile(" (?i)(remix)"), endsWithRemix)
                .put(Pattern.compile("(?i)(remix)(\\s+)(?i)(by) "), startsWithRemixBy)
                .put(Pattern.compile("(?i)(ft) "), hasFt).put(Pattern.compile("(?i)(feat) "), hasFeat)
                .put(Pattern.compile("(?i)(featuring) "), hasFeaturing)
                .put(Pattern.compile("(?i)(with) "), startsWithWith).build();
    }

    /**
     * Returns the names of the artists that are involved in the fields of an {@link AudioItem},
     * that is, every artist that could appear in the {@link AudioItem#artist} variable,
     * or {@link Album#albumArtist} or in the {@link AudioItem#title}.
     *
     * <h3>Example</h3>
     *
     * <p>The following AudioItem instance: <pre>   {@code
     *
     *   audioItem.name = "Who Controls (Adam Beyer Remix)"
     *   audioItem.artist = "David Meiser, Black Asteroid & Tiga"
     *   audioItem.albumArtist = "Ida Engberg"
     *
     *   }</pre>
     * ... produces the following (without order): <pre>   {@code
     *      [David Meiser, Black Asteroid, Tiga, Adam Beyer, Ida Engberg]
     *   }</pre>
     *
     * @param title           The title of an audio item
     * @param artistName      The artist name of an audio item
     * @param albumArtistName The album artist name of an audio item
     *
     * @return An {@code ImmutableSet} object with the names of the artists
     */
    private static Set<String> getArtistsNamesInvolved(String title, String artistName, String albumArtistName) {
        Set<String> artistsInvolved = new HashSet<>();
        var albumArtistNames = Splitter.on(CharMatcher.anyOf(",&")).trimResults().omitEmptyStrings().splitToList(albumArtistName);
        artistsInvolved.addAll(albumArtistNames);
        artistsInvolved.addAll(getNamesInArtist(artistName));
        artistsInvolved.addAll(getNamesInTitle(title));
        return artistsInvolved.stream().map(WordUtils::capitalize).collect(Collectors.toSet());
    }

    /**
     * Returns artist names that are in the given artist name.
     * Commonly they can be separated by ',' or '&' characters, or by the words 'versus' or 'vs'.
     *
     * <h3>Example</h3>
     *
     * <p>The given audio item artist field: <pre>   {@code
     *   "David Meiser, Black Asteroid & Tiga"
     *   }</pre>
     * ... produces the following set (without order): <pre>   {@code
     *      [David Meiser, Black Asteroid, Tiga]
     *   }</pre>
     *
     * @param artistName The artist name from where to find more names
     *
     * @return An {@link ImmutableSet} with the artists found
     */
    private static Set<String> getNamesInArtist(String artistName) {
        Set<String> artistsInvolved;
        Optional<List<String>> splittedNames = Stream.of(" versus ", " vs ").filter(artistName::contains)
                .map(s -> Splitter.on(s)
                        .trimResults()
                        .omitEmptyStrings()
                        .splitToList(artistName))
                .findAny();

        if (splittedNames.isPresent())
            artistsInvolved = new HashSet<>(splittedNames.get());
        else {
            var cleanedArtist = artistName.replaceAll("(?i)(feat)(\\.|\\s+)", ",").replaceAll("(?i)(ft)(\\.|\\s+)", ",");
            artistsInvolved = new HashSet<>(Splitter.on(CharMatcher.anyOf(",&"))
                                                    .trimResults()
                                                    .omitEmptyStrings()
                                                    .splitToList(cleanedArtist));
        }
        return artistsInvolved;
    }

    /**
     * Returns the names of the artists that are in a given string which is the title of an {@link AudioItem}.
     * For example:
     *
     * <p>The following audio item name field: <pre>   {@code
     *   "Song name (Adam Beyer & Pete Tong Remix)"
     *   }</pre>
     * ... produces the following (without order): <pre>   {@code
     *      [Adam Beyer, Pete Tong]
     *   }</pre>
     *
     * @param title The {@code String} where to find artist names
     *
     * @return An {@link ImmutableSet} with the artists found
     */
    private static Set<String> getNamesInTitle(String title) {
        var artistsInsideParenthesis = new HashSet<String>();
        for (Map.Entry<Pattern, Pattern> regex : artistsRegexMap.entrySet()) {
            Pattern keyPattern = regex.getKey();
            Matcher matcher = regex.getValue().matcher(title);
            if (matcher.find()) {
                String insideParenthesisString = title.substring(matcher.start())
                        .replaceAll("[(|\\[|)|\\]]", "")
                        .replaceAll(keyPattern.pattern(), "")
                        .replaceAll("\\s(?i)(vs)\\s", "&")
                        .replaceAll("\\s+", " ");

                artistsInsideParenthesis.addAll(Splitter.on(CharMatcher.anyOf("&,"))
                                                        .trimResults()
                                                        .omitEmptyStrings()
                                                        .splitToList(insideParenthesisString));
                break;
            }
        }
        return artistsInsideParenthesis;
    }
}
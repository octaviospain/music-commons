package net.transgressoft.commons.music.audio;

import com.google.common.base.CharMatcher;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.transgressoft.commons.music.Album;
import net.transgressoft.commons.music.Artist;
import net.transgressoft.commons.music.Genre;
import net.transgressoft.commons.music.ImmutableAlbum;
import net.transgressoft.commons.music.ImmutableArtist;
import net.transgressoft.commons.music.ImmutableLabel;
import net.transgressoft.commons.query.QueryEntity;
import net.transgressoft.commons.query.attribute.EntityAttribute;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.text.WordUtils;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static net.transgressoft.commons.music.audio.attribute.DurationAudioItemAttribute.DURATION;
import static net.transgressoft.commons.music.audio.attribute.ShortAudioItemAttribute.*;
import static net.transgressoft.commons.music.audio.attribute.StringAudioItemAttribute.*;
import static net.transgressoft.commons.music.audio.attribute.FloatAudioItemAttribute.BPM;
import static net.transgressoft.commons.music.audio.attribute.IntegerAudioItemAttribute.BITRATE;
import static net.transgressoft.commons.music.audio.attribute.LocalDateTimeAudioItemAttribute.DATE_OF_INCLUSION;
import static net.transgressoft.commons.music.audio.attribute.LocalDateTimeAudioItemAttribute.LAST_DATE_MODIFIED;
import static net.transgressoft.commons.music.audio.attribute.PathAudioItemAttribute.PATH;

/**
 * @author Octavio Calleya
 */
public class ImmutableAudioItem implements AudioItem {

    private final Path path;
    private final String title;
    private final Artist artist;
    private final ImmutableSet<String> artistsInvolved;
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

    public static ImmutableAudioItemBuilder builder(Path path, String title, Duration duration, int bitRate) {
        requireNonNull(path);
        requireNonNull(title);
        requireNonNull(duration);

        var dateOfInclusion = LocalDateTime.now();
        return new ImmutableAudioItemBuilder(path, title, duration, bitRate, dateOfInclusion, dateOfInclusion);
    }

    protected ImmutableAudioItem(Path path, String title, Artist artist, ImmutableSet<String> artistsInvolved, Album album, Genre genre,
                                 String comments, short trackNumber, short discNumber, float bpm, Duration duration, int bitRate, String encoder,
                                 String encoding, short playCount, LocalDateTime dateOfInclusion,
                                 LocalDateTime lastDateModified) {
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
        return artistsInvolved;
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
    @SuppressWarnings("unchecked")
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
    public int compareTo(@Nonnull QueryEntity o) {
        return hashCode() - o.hashCode();
    }

    public static class ImmutableAudioItemBuilder implements Builder<AudioItem> {

        public static final Map<EntityAttribute<?>, Object> defaultValues;
        private static final Map<Pattern, Pattern> artistsRegexMap;

        static {
            defaultValues =
                    ImmutableMap.<EntityAttribute<?>, Object>builder()
                            .put(ARTIST, ImmutableArtist.UNKNOWN_ARTIST)
                            .put(ALBUM, ImmutableAlbum.UNKNOWN_ALBUM)
                            .put(GENRE, Genre.UNDEFINED)
                            .put(COMMENTS, "")
                            .put(ARTISTS_INVOLVED, Collections.emptyList())
                            .put(LABEL, ImmutableLabel.UNKNOWN)
                            .put(TRACK_NUMBER, (short) -1)
                            .put(DISC_NUMBER, (short) -1)
                            .put(BPM, -1f)
                            .build();

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

        protected final Path path;
        protected final String title;
        protected final Duration duration;
        protected final int bitRate;
        protected Artist artist = (Artist) defaultValues.get(ARTIST);
        protected Album album = (Album) defaultValues.get(ALBUM);
        protected Genre genre = (Genre) defaultValues.get(GENRE);
        protected String comments = (String) defaultValues.get(COMMENTS);
        protected short trackNumber = (short) defaultValues.get(TRACK_NUMBER);
        protected short discNumber = (short) defaultValues.get(DISC_NUMBER);
        protected float bpm = (float) defaultValues.get(BPM);
        protected String encoder = "";
        protected String encoding = "";
        protected short playCount = 0;
        protected LocalDateTime dateOfInclusion;
        protected LocalDateTime lastDateModified;

        protected ImmutableAudioItemBuilder(Path path, String title, Duration duration, int bitRate, LocalDateTime dateOfInclusion, LocalDateTime lastDateModified) {
            this.path = path;
            this.title = title;
            this.duration = duration;
            this.bitRate = bitRate;
            this.dateOfInclusion = dateOfInclusion;
            this.lastDateModified = lastDateModified;
        }

        protected ImmutableAudioItemBuilder(Path path, String title, Duration duration, int bitRate, Artist artist, Album album, Genre genre, String comments,
                                            short trackNumber, short discNumber, float bpm, String encoder, String encoding, short playCount,
                                            LocalDateTime dateOfInclusion, LocalDateTime lastDateModified) {
            this.path = path;
            this.title = title;
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
            this.playCount = playCount;
            this.dateOfInclusion = dateOfInclusion;
            this.lastDateModified = lastDateModified;
        }

        private ImmutableAudioItemBuilder(AudioItem audioItem) {
            this(audioItem.path(), audioItem.title(), audioItem.duration(), audioItem.bitRate(), audioItem.artist(), audioItem.album(),
                 audioItem.genre(), audioItem.comments(), audioItem.trackNumber(), audioItem.discNumber(), audioItem.bpm(),
                 audioItem.encoder(), audioItem.encoding(), audioItem.playCount(), audioItem.dateOfInclusion(), LocalDateTime.now());
        }

        public ImmutableAudioItemBuilder artist(Artist artist) {
            this.artist = artist;
            return this;
        }

        public ImmutableAudioItemBuilder album(Album album) {
            this.album = album;
            return this;
        }

        public ImmutableAudioItemBuilder genre(Genre genre) {
            this.genre = genre;
            return this;
        }

        public ImmutableAudioItemBuilder comments(String comments) {
            this.comments = comments;
            return this;
        }

        public ImmutableAudioItemBuilder trackNumber(short trackNumber) {
            this.trackNumber = trackNumber;
            return this;
        }

        public ImmutableAudioItemBuilder discNumber(short discNumber) {
            this.discNumber = discNumber;
            return this;
        }

        public ImmutableAudioItemBuilder bpm(float bpm) {
            this.bpm = bpm;
            return this;
        }

        public ImmutableAudioItemBuilder encoder(String encoder) {
            this.encoder = encoder;
            return this;
        }

        public ImmutableAudioItemBuilder encoding(String encoding) {
            this.encoding = encoding;
            return this;
        }

        public ImmutableAudioItemBuilder playCount(short playCount) {
            this.playCount = playCount;
            return this;
        }

        @Override
        public ImmutableAudioItem build() {
            var artistsNamesInvolved = getArtistsNamesInvolved(title, artist.name(), album.albumArtist().name());
            return new ImmutableAudioItem(path,
                                          beautifyName(),
                                          artist,
                                          artistsNamesInvolved,
                                          album,
                                          genre,
                                          comments.replaceAll("\\s+", " "),
                                          trackNumber,
                                          discNumber,
                                          bpm,
                                          duration,
                                          bitRate,
                                          encoder,
                                          encoding,
                                          playCount,
                                          dateOfInclusion,
                                          lastDateModified);
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
        public static ImmutableSet<String> getArtistsNamesInvolved(String title, String artistName, String albumArtistName) {
            Set<String> artistsInvolved = new HashSet<>();
            var albumArtistNames = Splitter.on(CharMatcher.anyOf(",&")).trimResults().omitEmptyStrings().splitToList(albumArtistName);
            artistsInvolved.addAll(albumArtistNames);
            artistsInvolved.addAll(getNamesInArtist(artistName));
            artistsInvolved.addAll(getNamesInTitle(title));
            return artistsInvolved.stream().map(WordUtils::capitalize).collect(ImmutableSet.toImmutableSet());
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
        private static ImmutableSet<String> getNamesInArtist(String artistName) {
            ImmutableSet<String> artistsInvolved;
            Optional<List<String>> splittedNames = Stream.of(" versus ", " vs ").filter(artistName::contains)
                    .map(s -> Splitter.on(s)
                            .trimResults()
                            .omitEmptyStrings()
                            .splitToList(artistName))
                    .findAny();

            if (splittedNames.isPresent())
                artistsInvolved = ImmutableSet.copyOf(splittedNames.get());
            else {
                var cleanedArtist = artistName.replaceAll("(?i)(feat)(\\.|\\s+)", ",").replaceAll("(?i)(ft)(\\.|\\s+)", ",");
                artistsInvolved = ImmutableSet.copyOf(Splitter.on(CharMatcher.anyOf(",&"))
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

        private String beautifyName() {
            return title.replaceAll("\\s+", " ")
                    .replaceAll(" (?i)(remix)", " Remix")
                    .replaceAll("(?i)(remix)(\\s+)(?i)(by) ", "Remix by ")
                    .replaceAll("(?i)(ft)(\\.|\\s) ", "ft ")
                    .replaceAll("(?i)(feat)(\\.|\\s) ", "feat ")
                    .replaceAll("(?i)(featuring) ", "featuring ")
                    .replaceAll("(?i)(with) ", "with ");
        }
    }
}

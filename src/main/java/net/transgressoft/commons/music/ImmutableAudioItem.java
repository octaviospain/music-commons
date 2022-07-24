package net.transgressoft.commons.music;

import com.google.common.base.CharMatcher;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.text.WordUtils;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * @author Octavio Calleya
 */
public class ImmutableAudioItem implements AudioItem {

    private final Path path;
    private final String name;
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

    public static ImmutableAudioItemBuilder builder(Path path, String name, Duration duration, int bitRate) {
        return new ImmutableAudioItemBuilder(path, name, duration, bitRate);
    }

    public ImmutableAudioItem(Path path, String name, Artist artist, ImmutableSet<String> artistsInvolved, Album album, Genre genre, String comments, short trackNumber,
                              short discNumber, float bpm, Duration duration, int bitRate, String encoder, String encoding) {
        this.path = path;
        this.name = name;
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
    }

    @Override
    public Path path() {
        return path;
    }

    @Override
    public AudioItem path(Path path) {
        return new ImmutableAudioItemBuilder(path, name, duration, bitRate).build();
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
        return new ImmutableAudioItemBuilder(path, name, duration, bitRate).build();
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImmutableAudioItem that = (ImmutableAudioItem) o;
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

    public interface Builder<E extends AudioItem> {

        E build();
    }

    public static class ImmutableAudioItemBuilder implements Builder<AudioItem> {

        private static final Map<Pattern, Pattern> artistsRegexMap;

        static {
            Pattern endsWithRemix = Pattern.compile("[(|\\[](\\s*(&?\\s*(\\w+)\\s+)+(?i)(remix))[)|\\]]");
            Pattern startsWithRemixBy = Pattern.compile("[(|\\[](?i)(remix)(\\s+)(?i)(by)(.+)[)|\\]]");
            Pattern hasFt = Pattern.compile("[(|\\[|\\s](?i)(ft) (.+)");
            Pattern hasFeat = Pattern.compile("[(|\\[|\\s](?i)(feat) (.+)");
            Pattern hasFeaturing = Pattern.compile("[(|\\[|\\s](?i)(featuring) (.+)");
            Pattern startsWithWith = Pattern.compile("[(|\\[](?i)(with) (.+)[)|\\]]");

            artistsRegexMap = ImmutableMap.<Pattern, Pattern>builder()
                    .put(Pattern.compile(" (?i)(remix)"), endsWithRemix)
                    .put(Pattern.compile("(?i)(remix)(\\s+)(?i)(by) "), startsWithRemixBy)
                    .put(Pattern.compile("(?i)(ft) "), hasFt).put(Pattern.compile("(?i)(feat) "), hasFeat)
                    .put(Pattern.compile("(?i)(featuring) "), hasFeaturing)
                    .put(Pattern.compile("(?i)(with) "), startsWithWith).build();
        }

        protected final Path path;
        protected final String name;
        protected final Duration duration;
        protected final int bitRate;
        protected Artist artist = ImmutableArtist.UNKNOWN_ARTIST;
        protected Album album = new ImmutableAlbum("");
        protected Genre genre = Genre.UNDEFINED;
        protected String comments = "";
        protected short trackNumber = -1;
        protected short discNumber = -1;
        protected float bpm = -1;
        protected String encoder = "";
        protected String encoding = "";

        protected ImmutableAudioItemBuilder(Path path, String name, Duration duration, int bitRate) {
            this.path = path;
            this.name = name;
            this.duration = duration;
            this.bitRate = bitRate;
        }

        protected ImmutableAudioItemBuilder(Path path, String name, Duration duration, int bitRate, Artist artist, Album album, Genre genre, String comments,
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

        private ImmutableAudioItemBuilder(AudioItem audioItem) {
            this(audioItem.path(), audioItem.name(), audioItem.duration(), audioItem.bitRate(), audioItem.artist(), audioItem.album(),
                 audioItem.genre(), audioItem.comments(), audioItem.trackNumber(), audioItem.discNumber(), audioItem.bpm(),
                 audioItem.encoder(), audioItem.encoding());
        }

        /**
         * Returns the names of the artists that are involved in the fields of an {@link AudioItem},
         * that is, every artist that could appear in the {@link AudioItem#artist} variable,
         * or {@link Album#albumArtist} or in the {@link AudioItem#name}.
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
         * @param title The title of an audio item
         * @param artistName The artist name of an audio item
         * @param albumArtistName The album artist name of an audio item
         *
         * @return An {@code ImmutableSet} object with the names of the artists
         */
        public static ImmutableSet<String> getArtistsNamesInvolved(String title, String artistName, String albumArtistName) {
            Set<String> artistsInvolved = new HashSet<>();
            List<String> albumArtistNames = Splitter.on(CharMatcher.anyOf(",&")).trimResults().omitEmptyStrings().splitToList(albumArtistName);
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
                String cleanedArtist = artistName.replaceAll("(?i)(feat)(\\.|\\s+)", ",").replaceAll("(?i)(ft)(\\.|\\s+)", ",");
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
            Set<String> artistsInsideParenthesis = new HashSet<>();
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

        @Override
        public ImmutableAudioItem build() {
            ImmutableSet<String> artistsNamesInvolved = getArtistsNamesInvolved(name, artist.name(), album.albumArtist().name());
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
                                          encoding);
        }

        private String beautifyName() {
            return name.replaceAll("\\s+", " ")
                    .replaceAll(" (?i)(remix)"," Remix")
                    .replaceAll("(?i)(remix)(\\s+)(?i)(by) ","Remix by ")
                    .replaceAll("(?i)(ft)(\\.|\\s) ","ft ")
                    .replaceAll("(?i)(feat)(\\.|\\s) ","feat ")
                    .replaceAll("(?i)(featuring) ", "featuring ")
                    .replaceAll("(?i)(with) ", "with ");
        }
    }
}

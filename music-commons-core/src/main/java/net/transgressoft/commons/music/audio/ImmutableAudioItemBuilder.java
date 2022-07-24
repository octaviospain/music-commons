package net.transgressoft.commons.music.audio;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.transgressoft.commons.query.EntityAttribute;
import org.apache.commons.text.WordUtils;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.transgressoft.commons.music.audio.FloatAudioItemAttribute.BPM;
import static net.transgressoft.commons.music.audio.ShortAudioItemAttribute.DISC_NUMBER;
import static net.transgressoft.commons.music.audio.ShortAudioItemAttribute.TRACK_NUMBER;
import static net.transgressoft.commons.music.audio.StringAudioItemAttribute.*;

class ImmutableAudioItemBuilder<I extends AudioItem> implements AudioItemBuilder<I> {

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
    protected LocalDateTime dateOfInclusion;
    protected LocalDateTime lastDateModified;

    public ImmutableAudioItemBuilder(Path path, String title, Duration duration, int bitRate, LocalDateTime dateOfInclusion) {
        this.path = path;
        this.title = title;
        this.duration = duration;
        this.bitRate = bitRate;
        this.dateOfInclusion = dateOfInclusion;
        this.lastDateModified = dateOfInclusion;
    }

    protected ImmutableAudioItemBuilder(Path path, String title, Duration duration, int bitRate, LocalDateTime dateOfInclusion, LocalDateTime lastDateModified) {
        this.path = path;
        this.title = title;
        this.duration = duration;
        this.bitRate = bitRate;
        this.dateOfInclusion = dateOfInclusion;
        this.lastDateModified = lastDateModified;
    }

    protected ImmutableAudioItemBuilder(Path path, String title, Duration duration, int bitRate, Artist artist, Album album, Genre genre, String comments,
                                        short trackNumber, short discNumber, float bpm, String encoder, String encoding,
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
        this.dateOfInclusion = dateOfInclusion;
        this.lastDateModified = lastDateModified;
    }

    ImmutableAudioItemBuilder(AudioItem audioItem) {
        this(audioItem.path(), audioItem.title(), audioItem.duration(), audioItem.bitRate(), audioItem.artist(), audioItem.album(),
             audioItem.genre(), audioItem.comments(), audioItem.trackNumber(), audioItem.discNumber(), audioItem.bpm(),
             audioItem.encoder(), audioItem.encoding(), audioItem.dateOfInclusion(), LocalDateTime.now());
    }

    @Override
    public AudioItemBuilder<I> artist(Artist artist) {
        this.artist = artist;
        return this;
    }

    @Override
    public AudioItemBuilder<I> album(Album album) {
        this.album = album;
        return this;
    }

    @Override
    public AudioItemBuilder<I> genre(Genre genre) {
        this.genre = genre;
        return this;
    }

    @Override
    public AudioItemBuilder<I> comments(String comments) {
        this.comments = comments;
        return this;
    }

    @Override
    public AudioItemBuilder<I> trackNumber(short trackNumber) {
        this.trackNumber = trackNumber;
        return this;
    }

    @Override
    public AudioItemBuilder<I> discNumber(short discNumber) {
        this.discNumber = discNumber;
        return this;
    }

    @Override
    public AudioItemBuilder<I> bpm(float bpm) {
        this.bpm = bpm;
        return this;
    }

    @Override
    public AudioItemBuilder<I> encoder(String encoder) {
        this.encoder = encoder;
        return this;
    }

    @Override
    public AudioItemBuilder<I> encoding(String encoding) {
        this.encoding = encoding;
        return this;
    }

    @Override
    public I build() {
        var artistsNamesInvolved = getArtistsNamesInvolved(title, artist.name(), album.albumArtist().name());
        return (I) new ImmutableAudioItem(path,
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
    public static Set<String> getArtistsNamesInvolved(String title, String artistName, String albumArtistName) {
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

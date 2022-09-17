package net.transgressoft.commons.music.audio;

import com.google.common.collect.ImmutableSet;
import com.neovisionaries.i18n.CountryCode;
import net.transgressoft.commons.query.EntityAttribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static net.transgressoft.commons.music.audio.AlbumAttribute.ALBUM;
import static net.transgressoft.commons.music.audio.ArtistAttribute.ALBUM_ARTIST;
import static net.transgressoft.commons.music.audio.ArtistAttribute.ARTIST;
import static net.transgressoft.commons.music.audio.AudioItemDurationAttribute.DURATION;
import static net.transgressoft.commons.music.audio.AudioItemFloatAttribute.BPM;
import static net.transgressoft.commons.music.audio.AudioItemIntegerAttribute.BITRATE;
import static net.transgressoft.commons.music.audio.AudioItemPathAttribute.PATH;
import static net.transgressoft.commons.music.audio.AudioItemShortAttribute.DISC_NUMBER;
import static net.transgressoft.commons.music.audio.AudioItemShortAttribute.TRACK_NUMBER;
import static net.transgressoft.commons.music.audio.AudioItemStringAttribute.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Octavio Calleya
 */
class ImmutableAudioItemTest {

    int id = 9;
    Path path = Path.of("testfiles", "testeable.mp3");
    String title = "Yesterday";
    Duration duration = Duration.ofMinutes(2);
    int bitRate = 320;
    String artistName = "The Beatles";
    Label label = new ImmutableLabel("EMI", CountryCode.US);
    String albumName = "Help!";
    String albumArtistName = "The Beatles";
    boolean isCompilation = false;
    short year = 1965;
    float bpm = 120;
    short trackNumber = 13;
    short discNumber = 1;
    String comments = "Best song ever!";
    Genre genre = Genre.ROCK;
    String encoding = "Lame MP3";
    String encoder = "transgressoft";
    
    AudioItemAttributes attributes;
    Artist artist;
    Album album;
    Artist albumArtist;

    @BeforeEach
    void beforeEach() {
        artist = mock(Artist.class);
        when(artist.name()).thenReturn(artistName);
        when(artist.countryCode()).thenReturn(CountryCode.UK.name());
        when(artist.toString()).thenReturn("ImmutableArtist{name=The Beatles, countryCode=UK}");

        albumArtist = mock(Artist.class);
        when(albumArtist.name()).thenReturn(albumArtistName);
        when(albumArtist.countryCode()).thenReturn(CountryCode.UK.name());

        album = mock(Album.class);
        when(album.name()).thenReturn(albumName);
        when(album.albumArtist()).thenReturn(albumArtist);
        when(album.label()).thenReturn(label);
        when(album.year()).thenReturn(year);
        when(album.isCompilation()).thenReturn(isCompilation);
        
        Map<EntityAttribute<?>, Object> map = new HashMap<>();
        map.put(TITLE, title);
        map.put(PATH, path);
        map.put(DURATION, duration);
        map.put(ALBUM, album);
        map.put(ARTIST, artist);
        map.put(BPM, bpm);
        map.put(TRACK_NUMBER, trackNumber);
        map.put(DISC_NUMBER, discNumber);
        map.put(COMMENTS, comments);
        map.put(GENRE_NAME, genre.name());
        map.put(BITRATE, bitRate);
        map.put(ENCODING, encoding);
        map.put(ENCODER, encoder); 
        attributes = new SimpleAudioItemAttributes(map);
    }
    
    @Test
    @DisplayName("AudioItem properties")
    void propertiesTest() {
        AudioItem audioItem = new ImmutableAudioItem(id, attributes);

        LocalDateTime dateOfInclusion = audioItem.dateOfInclusion();
        LocalDateTime lastDateModified = audioItem.lastDateModified();
        assertEquals(9, audioItem.getId());
        assertEquals(dateOfInclusion, lastDateModified);
        assertTrue(LocalDateTime.now().isAfter(audioItem.dateOfInclusion()));
        assertEquals(path, audioItem.path());
        assertEquals(dateOfInclusion, audioItem.dateOfInclusion());
        assertEquals("testeable.mp3", audioItem.fileName());
        assertEquals("mp3", audioItem.extension());
        assertEquals(title, audioItem.title());
        assertEquals(duration, audioItem.duration());
        assertEquals(bitRate, audioItem.bitRate());
        assertEquals(album.name(), audioItem.album().name());
        assertEquals(album.albumArtist().name(), audioItem.album().albumArtist().name());
        assertEquals(CountryCode.UK, CountryCode.valueOf(audioItem.album().albumArtist().countryCode()));
        assertFalse(audioItem.album().isCompilation());
        assertEquals(album.year(), audioItem.album().year());
        assertEquals(album.label().name(), audioItem.album().label().name());
        assertEquals(CountryCode.US,  CountryCode.valueOf(audioItem.album().label().countryCode()));
        assertEquals(artist.name(), audioItem.artist().name());
        assertEquals(CountryCode.UK,  CountryCode.valueOf(audioItem.artist().countryCode()));
        assertEquals(bpm, audioItem.bpm());
        assertEquals(trackNumber, audioItem.trackNumber());
        assertEquals(discNumber, audioItem.discNumber());
        assertEquals(comments, audioItem.comments());
        assertEquals(genre, audioItem.genre());
        assertEquals(encoding, audioItem.encoding());
        assertEquals(encoder, audioItem.encoder());

        assertEquals(path.toString(), String.valueOf(audioItem.getAttribute(PATH)));

        audioItem = audioItem.comments("modified");

        assertNotEquals(lastDateModified, audioItem.lastDateModified());
        assertTrue(lastDateModified.isBefore(audioItem.lastDateModified()));
        assertEquals("ImmutableAudioItem{path=testfiles/testeable.mp3, name=Yesterday, artist=ImmutableArtist{name=The Beatles, countryCode=UK}}", audioItem.toString());

        Label label = new ImmutableLabel("New label");
        assertNotEquals(label, audioItem.album().label());
        assertEquals("ImmutableLabel{name=New label, countryCode=UNDEFINED}", label.toString());

        var newAlbum = mock(Album.class);
        when(newAlbum.name()).thenReturn("OtherAlbum");
        when(newAlbum.albumArtist()).thenReturn(new ImmutableArtist("Other artist"));
        when(newAlbum.isCompilation()).thenReturn(true);
        when(newAlbum.year()).thenReturn((short) 1999);
        when(newAlbum.label()).thenReturn(ImmutableLabel.UNKNOWN);

        AudioItem audioItemWithmodifiedAlbum = audioItem
                .title("Other title").album(newAlbum)
                .encoder("New encoder").encoding("New encoding")
                .bpm(128);

        assertEquals("Other Artist", audioItemWithmodifiedAlbum.album().albumArtist().name());
        assertEquals("Other title", audioItemWithmodifiedAlbum.title());
        assertNotEquals(audioItem, audioItemWithmodifiedAlbum);
        assertTrue(audioItemWithmodifiedAlbum.album().isCompilation());
        assertEquals("New encoder", audioItemWithmodifiedAlbum.encoder());
        assertEquals("New encoding", audioItemWithmodifiedAlbum.encoding());
        assertEquals(128, audioItemWithmodifiedAlbum.bpm());
        assertEquals(0, audioItem.length());

        var unknownAlbum = mock(Album.class);
        when(unknownAlbum.name()).thenReturn("");
        when(unknownAlbum.albumArtist()).thenReturn(ImmutableArtist.UNKNOWN_ARTIST);
        when(unknownAlbum.isCompilation()).thenReturn(true);
        when(unknownAlbum.year()).thenReturn((short) 1999);
        when(unknownAlbum.label()).thenReturn(ImmutableLabel.UNKNOWN);

        var modifiedAudioItem = audioItemWithmodifiedAlbum.genre(Genre.UNDEFINED)
                .album(unknownAlbum)
                .comments("Modified");

        assertEquals(audioItem.artist(), modifiedAudioItem.artist());
        assertThat(modifiedAudioItem.album().name()).isEmpty();
        assertEquals(Genre.UNDEFINED, modifiedAudioItem.genre());
        assertEquals("Modified", modifiedAudioItem.comments());
        assertEquals(artist, modifiedAudioItem.artist());

        modifiedAudioItem = modifiedAudioItem.artist(ImmutableArtist.UNKNOWN_ARTIST)
                .path(Path.of("/moved/song.mp3")).discNumber((short) 2).trackNumber((short) 3);

        assertEquals("/moved/song.mp3", modifiedAudioItem.path().toString());
        assertEquals(2, modifiedAudioItem.discNumber());
        assertEquals(3, modifiedAudioItem.trackNumber());
    }

    @Nested
    @DisplayName("Artists involved")
    class artistsInvolved {

        AudioItem audioItem;
        ImmutableSet<String> expectedArtists;

        private void initAudioItemAndExpectedArtists(String title, String artistString, String albumArtistString, String... expectedArtist) {
            var newAttributes = attributes.modifiedCopy(TITLE, title);

            var artist = mock(Artist.class);
            when(artist.name()).thenReturn(beautifyName(artistString));

            var albumArtist = mock(Artist.class);
            when(albumArtist.name()).thenReturn(albumArtistString);

            var album = mock(Album.class);
            when(album.albumArtist()).thenReturn(albumArtist);
            when(album.label()).thenReturn(label);
            when(album.year()).thenReturn(year);
            when(album.name()).thenReturn(albumName);
            when(album.isCompilation()).thenReturn(isCompilation);

            newAttributes.set(ARTIST, artist);
            newAttributes.set(ALBUM, album);
            newAttributes.set(ALBUM_ARTIST, albumArtist);
            audioItem = new ImmutableAudioItem(1, newAttributes);
            expectedArtists = ImmutableSet.<String>builder().add(expectedArtist).build();
        }

        private String beautifyName(String name) {
            return WordUtils.capitalize(name)
                    .replaceAll("\\s+", " ")
                    .replaceAll(" (?i)(vs)(\\.|\\s)", " vs ")
                    .replaceAll(" (?i)(versus) ", " versus ");
        }

        @Nested
        @DisplayName("In artist field")
        class namesInArtistField {

            @Test
            @DisplayName("One name")
            void oneNameInArtist() {
                initAudioItemAndExpectedArtists(title, "Dvs1", "", "Dvs1");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("One name with trail spaces")
            void oneNameWithSpacesInArtist() {
                initAudioItemAndExpectedArtists(title, "Adam Beyer    ", "", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("One name with spaces in between words")
            void oneNameWithLeadingSpacesInArtist() {
                initAudioItemAndExpectedArtists(title, "Adam      Beyer", "", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("One name with leading and trailing spaces")
            void oneNameWthLeadingAndTrailingSpacesInArtist() {
                initAudioItemAndExpectedArtists(title, "   Adam Beyer    ", "", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("One name with leading and trailing spaces")
            void oneNameWthLeadingTrailingAndBetweenSpacesInArtist() {
                initAudioItemAndExpectedArtists(title, "   Adam    Beyer    ","", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Nested
            @DisplayName("Comma separated")
            class commaSeparated {

                @Test
                @DisplayName("Two names")
                void twoNamesCommaSeparated() {
                    initAudioItemAndExpectedArtists(title, "adam Beyer, ida engberg", "", "Adam Beyer", "Ida Engberg");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Two names with trailing spaces")
                void twoNamesCommaSeparatedWithTrailingSpaces() {
                    initAudioItemAndExpectedArtists(title, "Adam Beyer  , Ida Engberg   ", "", "Adam Beyer", "Ida Engberg");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Two names with leading spaces")
                void twoNamesCommaSeparatedWithLeadingSpaces() {
                    initAudioItemAndExpectedArtists(title, "Adam    Beyer, Ida   Engberg", "", "Adam Beyer", "Ida Engberg");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Three names")
                void threeNamesCommaSeparated() {
                    initAudioItemAndExpectedArtists(title, "Adam Beyer, Ida Engberg, UMEK", "", "Adam Beyer", "Ida Engberg", "UMEK");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Three names with leading and trailing spaces")
                void threeNamesCommaWithLeadingAndTrailingSpacesSeparated() {
                    initAudioItemAndExpectedArtists(title, "Adam    Beyer  ,   Ida  Engberg ,   UMEK ", "", "Adam Beyer", "Ida Engberg", "UMEK");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Two repeated names")
                void twoNamesRepeatedCommaSeparated() {
                    initAudioItemAndExpectedArtists(title, "Adam Beyer, Adam Beyer", "", "Adam Beyer");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }
            }

            @Nested
            @DisplayName("& separated")
            class andpersandSeparated {

                @Test
                @DisplayName("Two names")
                void twoNamesAndpersandSeparated() {
                    initAudioItemAndExpectedArtists(title, "Adam Beyer & Ida Engberg", "", "Adam Beyer", "Ida Engberg");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Two names with leading and trailing spaces")
                void twoNamesAndpersandWithSpacesSeparated() {
                    initAudioItemAndExpectedArtists(title, "Adam   Beyer  &     Ida Engberg ", "", "Adam Beyer", "Ida Engberg");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Three names")
                void threeNamesAndpersandSeparated() {
                    initAudioItemAndExpectedArtists(title, "Adam Beyer & Ida Engberg & UMEK", "", "Adam Beyer", "Ida Engberg", "UMEK");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Three names with leading and trailing spaces")
                void threeNamesAndpersandWithSpacesSeparated() {
                    initAudioItemAndExpectedArtists(title, "adam   beyer  & ida  engberg &  uMEK ", "", "Adam Beyer", "Ida Engberg",
                                                    "UMEK");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Two repeated names")
                void twoNamesRepeatedAnpersandSeparated() {
                    initAudioItemAndExpectedArtists(title, "Adam Beyer & Adam Beyer", "", "Adam Beyer");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }
            }

            @Nested
            @DisplayName("'vs' separated")
            class vsSeparated {

                @Test
                @DisplayName("Two names")
                void twoNamesvsSeparated() {
                    initAudioItemAndExpectedArtists(title, "Adam Beyer vs Ida Engberg", "", "Adam Beyer", "Ida Engberg");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Three names")
                void threeNamesVsSeparated() {
                    initAudioItemAndExpectedArtists(title, "Adam Beyer vs Ida Engberg VS UMEK", "", "Adam Beyer", "Ida Engberg", "UMEK");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Two repeated names")
                void twoNamesRepeatedVsSeparated() {
                    initAudioItemAndExpectedArtists(title, "Adam Beyer vs Adam Beyer", "", "Adam Beyer");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }
            }

            @Nested
            @DisplayName("'versus' separated")
            class versusSeparated {

                @Test
                @DisplayName("Two names")
                void twoNamesVersusSeparated() {
                    initAudioItemAndExpectedArtists(title, "Adam Beyer versus Ida Engberg", "", "Adam Beyer", "Ida Engberg");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Three names")
                void threeNamesVersusSeparated() {
                    initAudioItemAndExpectedArtists(title, "adam Beyer versus Ida Engberg Versus umek", "", "Adam Beyer",
                                                    "Ida Engberg", "Umek");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Two repeated names")
                void twoNamesRepeatedVersusSeparated() {
                    initAudioItemAndExpectedArtists(title, "Adam Beyer versus Adam Beyer", "", "Adam Beyer");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }
            }

            @Nested
            @DisplayName("'vs.' separated")
            class versusDotSeparated {

                @Test
                @DisplayName("Two names")
                void twoNamesVsDotSeparated() {
                    initAudioItemAndExpectedArtists(title, "Adam Beyer vs. Ida Engberg", "", "Adam Beyer", "Ida Engberg");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Three names")
                void threeNamesVsDotSeparated() {
                    initAudioItemAndExpectedArtists(title, "Adam Beyer vs. Ida Engberg Vs. UMEK", "", "Adam Beyer",
                                                    "Ida Engberg", "UMEK");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Two repeated names")
                void twoNamesRepeatedVsDotSeparated() {
                    initAudioItemAndExpectedArtists(title, "Adam Beyer Vs. Adam Beyer", "", "Adam Beyer");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }
            }

            @Nested
            @DisplayName("Feat(.) separated")
            class featSeparated {

                @Test
                @DisplayName("Two names feat. separated")
                void twoNamesFeatDotSeparated() {
                    initAudioItemAndExpectedArtists(title, "Benny Benassi Feat. Gary Go", "", "Benny Benassi", "Gary Go");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Two names Feat separated")
                void twoNamesFeatSeparated() {
                    initAudioItemAndExpectedArtists(title, "Dragon Ash Feat Rappagariya", "", "Dragon Ash", "Rappagariya");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }
            }

            @Nested
            @DisplayName("Ft(.) separated")
            class ftSeparated {

                @Test
                @DisplayName("Two names ft. separated")
                void twoNamesFeatSeparated() {
                    initAudioItemAndExpectedArtists(title, "Ludacris Ft. Shawnna", "", "Ludacris", "Shawnna");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Two names ft separated")
                void twoNamesFeatDotSeparated() {
                    initAudioItemAndExpectedArtists(title, "Ludacris Ft Shawnna", "", "Ludacris", "Shawnna");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }
            }

            @Nested
            @DisplayName("Comma and & separated")
            class commaAndpersandSeparated {

                @Test
                @DisplayName("Three names")
                void threeNamesCommaAndpersandSeparated() {
                    initAudioItemAndExpectedArtists(title, "Adam Beyer, Ida Engberg & Ansome", "", "Adam Beyer", "Ida Engberg", "Ansome");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Four names")
                void fourNamesCommaAndpersandSeparated() {
                    initAudioItemAndExpectedArtists(title, "Adam beyer & Ida engberg, UMEK & ansome", "", "Adam Beyer", "Ida Engberg",
                                                    "UMEK", "Ansome");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Five names")
                void fiveNamesCommaAndpersandSeparated() {
                    initAudioItemAndExpectedArtists(title, "Adam Beyer & UMEK, Showtek, Ansome & Ida Engberg", "", "Adam Beyer",
                                                    "Ida Engberg", "UMEK", "Showtek", "Ansome");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Five name with leading and trailing spaces")
                void fiveNamesCommaAndpersandWithSpacesTest() {
                    initAudioItemAndExpectedArtists(title, " Adam  Beyer , UMEK  & Showtek , Ansome   & Ida   Engberg ", "", "Adam Beyer",
                                                    "Ida Engberg", "UMEK", "Showtek", "Ansome");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }
            }

            @Nested
            @DisplayName("Ft(.) and & separated")
            class FtDotAndpersandSeparated {

                @Test
                @DisplayName("Three names with & and Ft. separated")
                void threeNamesWithFtDotAnpersandSeparated() {
                    initAudioItemAndExpectedArtists(title, "Laidback Luke Feat. Chuckie & Martin Solveig", "", "Laidback Luke",
                                                    "Chuckie", "Martin Solveig");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Three names with & and Ft separated")
                void threeNamesWithFtAnpersandSeparated() {
                    initAudioItemAndExpectedArtists(title, "Laidback Luke Feat Chuckie & Martin Solveig", "", "Laidback Luke",
                                                    "Chuckie", "Martin Solveig");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }
            }
        }

        @Nested
        @DisplayName("In title field")
        class namesInTitleField {

            @Test
            @DisplayName("Just the track name")
            void justTheTrackName() {
                initAudioItemAndExpectedArtists("Nothing Left Part 1", "", "");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Original mix")
            void originalMix() {
                initAudioItemAndExpectedArtists("Song title (Original Mix)", "", "");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Edit version")
            void editVersion() {
                initAudioItemAndExpectedArtists("Song title (Special time edit)", "", "");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Ends with 'Remix'")
            void endsWithRemix() {
                initAudioItemAndExpectedArtists("Song title (adam beyer Remix)", "", "", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Has 'Remix' with useless spaces")
            void hasRemixWithUselessSpaces() {
                initAudioItemAndExpectedArtists(" Song   name ( Adam   Beyer  Remix)", "", "", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Has 'Remix by'")
            void hasRemixBy() {
                initAudioItemAndExpectedArtists("Song title (Remix by Adam Beyer)", "", "", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Starts with 'Remix by' with useless spaces")
            void hasRemixByWithUselessSpaces() {
                initAudioItemAndExpectedArtists("Song   name  (Remix    by  Adam   Beyer)", "", "", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Has 'Ft' outside parenthesis")
            void hasFt() {
                initAudioItemAndExpectedArtists("Song title ft Adam Beyer", "", "", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Has 'Ft' inside parenthesis")
            void hasFtInsideParenthesis() {
                initAudioItemAndExpectedArtists("Song title (ft Adam Beyer)", "", "", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Has 'Feat' outside parenthesis")
            void hasFeat() {
                initAudioItemAndExpectedArtists("Song title feat Adam Beyer", "", "", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Has 'Feat' inside parenthesis")
            void hasFeatInsideParenthesis() {
                initAudioItemAndExpectedArtists("Song title (feat Adam Beyer)", "", "", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Has 'featuring' ouside parenthesis")
            void hasFeaturing() {
                initAudioItemAndExpectedArtists("Song title featuring Adam Beyer", "", "", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Has 'featuring' inside parenthesis")
            void hasFeaturingInsideParenthesis() {
                initAudioItemAndExpectedArtists("Song title (featuring Adam Beyer)", "", "", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Has 'With'")
            void hasWith() {
                initAudioItemAndExpectedArtists("Song title (With Adam Beyer)", "", "", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Has 'ft' and ending by 'Remix'")
            @Disabled("User should put the extra artist in the artist field, separated by a comma")
            void twoArtistsDividedByFtWithRemix() {
                initAudioItemAndExpectedArtists("Pretendingtowalkslow ft Zeroh (M. Constant Remix)", "", "", "Zeroh", "M. Constant");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Two names separated by '&' ending with 'Remix'")
            void twoArtistsDividedByAndpersandEndingWithRemix() {
                initAudioItemAndExpectedArtists("Song title (Adam beyer & pete tong Remix)", "", "", "Adam Beyer", "Pete Tong");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Two names separated by 'vs' ending with 'Remix'")
            void vsSeparatedWithRemix() {
                initAudioItemAndExpectedArtists("Fall (M83 vs Big Black Delta Remix)", "", "", "M83", "Big Black Delta");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Four names separated by with comma and & starting with 'feat'")
            void fourNamesCommaAndpersandFeatSeparated() {
                initAudioItemAndExpectedArtists("Jet Blue Jet (feat Leftside, GTA, Razz & Biggy)", "", "", "Leftside",
                                                "GTA", "Razz", "Biggy");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }
        }

        @Nested
        @DisplayName("In album artist field")
        class namesInAlbumArtistField {

            @Test
            @DisplayName("One name")
            void oneNameInAlbumArtist() {
                initAudioItemAndExpectedArtists(title, "Adam Beyer", "Adam Beyer", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Two names separated by commas")
            void twoNamesInAlbumArtistCommSeparated() {
                initAudioItemAndExpectedArtists(title, "Adam Beyer, UMEK", "Adam Beyer", "UMEK", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Two names separated by &")
            void twoNamesInAlbumArtistAndpersandSeparated() {
                initAudioItemAndExpectedArtists(title, "Adam Beyer & Pete Tong", "Adam Beyer", "Pete Tong", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Three names separated by & and comma")
            void threeNamesInAlbumArtistAndpersandCommaSeparated() {
                initAudioItemAndExpectedArtists(title, "Adam Beyer, Pete Tong & UMEK", "Adam Beyer", "Pete Tong", "UMEK", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }
        }

        @Nested
        @DisplayName("In artist, title and album artist fields")
        class namesInArtistTitleAndAlbumFields {

            @Test
            @DisplayName("Simple name, one artist, same album artist")
            void simpleNameOneArtistSameAlbumArtist() {
                initAudioItemAndExpectedArtists("Song title", "Pete Tong", "Pete Tong", "Pete Tong");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Simple name, one artist, one album artist")
            void simpleNameOneArtistOneAlbumArtist() {
                initAudioItemAndExpectedArtists("Song title", "Pete Tong", "Jeff Mills", "Pete Tong", "Jeff Mills");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Simple name, two artists, same album artist")
            void simpleNameTwoArtistsSameAlbumArtist() {
                initAudioItemAndExpectedArtists("Song title", "Pete Tong, UMEK", "Pete Tong", "Pete Tong", "UMEK");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Name with 'Remix', one artist, no album artist")
            void nameWithRemixOneArtistNoAlbumArtist() {
                initAudioItemAndExpectedArtists("Song title (Ansome Remix)", "Pete Tong", "", "Pete Tong", "Ansome");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Name with featuring, two artists with comma, one repeated album artist")
            void oneNameOneArtistOneAlbumArtist() {
                initAudioItemAndExpectedArtists("Song title featuring Lulu Perez", "Pete Tong & Ansome", "Pete Tong", "Pete Tong",
                                             "Lulu Perez", "Ansome");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Name with 'Remix by', two artists with &, one other album artist")
            void nameWithRemixByTwoArtistsWithAndpersandOneOtherAlbumArtist() {
                initAudioItemAndExpectedArtists("Song title (Remix by Bonobo)", "Laurent Garnier & Rone", "Pete Tong",
                                             "Pete Tong", "Bonobo", "Laurent Garnier", "Rone");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }
        }
    }
}

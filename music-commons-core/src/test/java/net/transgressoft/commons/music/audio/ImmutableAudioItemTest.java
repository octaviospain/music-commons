package net.transgressoft.commons.music.audio;

import com.google.common.collect.ImmutableSet;
import com.neovisionaries.i18n.CountryCode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;

import static net.transgressoft.commons.music.audio.PathAudioItemAttribute.PATH;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Octavio Calleya
 */
class ImmutableAudioItemTest {

    AudioItem audioItem;

    Path path = Path.of("testfiles", "testeable.mp3");
    String name = "Yesterday";
    Duration duration = Duration.ofMinutes(2);
    int bitRate = 320;
    Artist artist = new ImmutableArtist("The Beatles", CountryCode.UK);
    Label label = new ImmutableLabel("EMI", CountryCode.US);
    Album album = new ImmutableAlbum("Help!", artist, false, (short) 1965, label);
    int bpm = 120;
    short trackNumber = 13;
    short discNumber = 1;
    String comments = "Best song ever!";
    Genre genre = Genre.ROCK;
    String encoding = "Lame MP3";
    String encoder = "transgressoft";
    short playCount = 0;

    @Test
    @DisplayName("AudioItem properties")
    void propertiesTest() {
        audioItem =
                new ImmutableAudioItemBuilder(path, name, duration, bitRate, LocalDateTime.now())
                        .album(album)
                        .artist(artist)
                        .bpm(bpm)
                        .trackNumber(trackNumber)
                        .discNumber(discNumber)
                        .comments(comments)
                        .genre(genre)
                        .encoding(encoding)
                        .encoder(encoder)
                        .build();

        LocalDateTime dateOfInclusion = audioItem.dateOfInclusion();
        LocalDateTime lastDateModified = audioItem.lastDateModified();
        assertEquals(dateOfInclusion, lastDateModified);
        assertTrue(LocalDateTime.now().isAfter(audioItem.dateOfInclusion()));
        assertEquals(path, audioItem.path());
        assertEquals(dateOfInclusion, audioItem.dateOfInclusion());
        assertEquals("testeable.mp3", audioItem.fileName());
        assertEquals("mp3", audioItem.extension());
        assertEquals(name, audioItem.title());
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
        assertEquals(playCount, audioItem.playCount());

        assertEquals(path.toString(), String.valueOf(audioItem.getAttribute(PATH)));

        audioItem = audioItem.comments("modified");

        assertNotEquals(lastDateModified, audioItem.lastDateModified());
        assertTrue(lastDateModified.isBefore(audioItem.lastDateModified()));
        assertEquals("ImmutableAudioItem{path=testfiles/testeable.mp3, name=Yesterday, artist=ImmutableArtist{name=The Beatles, countryCode=UK}}", audioItem.toString());

        Label label = new ImmutableLabel("New label");
        assertNotEquals(label, audioItem.album().label());
        assertEquals("ImmutableLabel{name=New label, countryCode=UNDEFINED}", label.toString());

        AudioItem modifiedAlbum = audioItem
                .title("Other title").album(new ImmutableAlbum("Other album", new ImmutableArtist("Other artist"), true, (short) 1999, ImmutableLabel.UNKNOWN))
                .encoder("New encoder").encoding("New encoding")
                .bpm(128);

        assertEquals("Other Artist", modifiedAlbum.album().albumArtist().name());
        assertEquals("Other title", modifiedAlbum.title());
        assertNotEquals(audioItem, modifiedAlbum);
        assertTrue(modifiedAlbum.album().isCompilation());
        assertEquals("New encoder", modifiedAlbum.encoder());
        assertEquals("New encoding", modifiedAlbum.encoding());
        assertEquals(128, modifiedAlbum.bpm());
        assertEquals(0, audioItem.length());

        modifiedAlbum = modifiedAlbum.genre(Genre.UNDEFINED).album(ImmutableAlbum.UNKNOWN_ALBUM).comments("Modified");

        assertNotEquals(audioItem.artist(), modifiedAlbum.artist());
        assertEquals(ImmutableAlbum.UNKNOWN_ALBUM, modifiedAlbum.album());
        assertEquals(Genre.UNDEFINED, modifiedAlbum.genre());
        assertEquals("Modified", modifiedAlbum.comments());
        assertEquals(ImmutableArtist.UNKNOWN_ARTIST, modifiedAlbum.artist());

        modifiedAlbum = modifiedAlbum.artist(ImmutableArtist.UNKNOWN_ARTIST)
                .path(Path.of("/moved/song.mp3")).discNumber((short) 2).trackNumber((short) 3);

        assertEquals("/moved/song.mp3", modifiedAlbum.path().toString());
        assertEquals(2, modifiedAlbum.discNumber());
        assertEquals(3, modifiedAlbum.trackNumber());

        assertTrue(audioItem.artist().compareTo(modifiedAlbum.artist()) > 0);
        assertEquals((short) 4, audioItem.playCount((short) 4).playCount());
    }

    @Nested
    @DisplayName("Artists involved")
    class artistsInvolved {

        @Nested
        @DisplayName("In artist field")
        class namesInArtistField {

            AudioItem audioItem;
            ImmutableSet<String> expectedArtists;

            private void initTrackWithArtistAndResult(String artistString, String... expectedArtist) {
                audioItem = new ImmutableAudioItemBuilder(path, name, duration, bitRate, LocalDateTime.now(), LocalDateTime.now())
                        .album(ImmutableAlbum.UNKNOWN_ALBUM)
                        .artist(new ImmutableArtist(artistString))
                        .bpm(bpm)
                        .trackNumber(trackNumber)
                        .discNumber(discNumber)
                        .comments(comments)
                        .genre(genre)
                        .encoding(encoding)
                        .encoder(encoder)
                        .build();
                expectedArtists = ImmutableSet.<String>builder().add(expectedArtist).build();
            }

            @Test
            @DisplayName("One name")
            void oneNameInArtist() {
                initTrackWithArtistAndResult("Dvs1", "Dvs1");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("One name with trail spaces")
            void oneNameWithSpacesInArtist() {
                initTrackWithArtistAndResult("Adam Beyer    ", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("One name with spaces in between words")
            void oneNameWithLeadingSpacesInArtist() {
                initTrackWithArtistAndResult("Adam      Beyer", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("One name with leading and trailing spaces")
            void oneNameWthLeadingAndTrailingSpacesInArtist() {
                initTrackWithArtistAndResult("   Adam Beyer    ", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("One name with leading and trailing spaces")
            void oneNameWthLeadingTrailingAndBetweenSpacesInArtist() {
                initTrackWithArtistAndResult("   Adam    Beyer    ", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Nested
            @DisplayName("Comma separated")
            class commaSeparated {

                @Test
                @DisplayName("Two names")
                void twoNamesCommaSeparated() {
                    initTrackWithArtistAndResult("adam Beyer, ida engberg", "Adam Beyer", "Ida Engberg");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Two names with trailing spaces")
                void twoNamesCommaSeparatedWithTrailingSpaces() {
                    initTrackWithArtistAndResult("Adam Beyer  , Ida Engberg   ", "Adam Beyer", "Ida Engberg");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Two names with leading spaces")
                void twoNamesCommaSeparatedWithLeadingSpaces() {
                    initTrackWithArtistAndResult("Adam    Beyer, Ida   Engberg", "Adam Beyer", "Ida Engberg");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Three names")
                void threeNamesCommaSeparated() {
                    initTrackWithArtistAndResult("Adam Beyer, Ida Engberg, UMEK", "Adam Beyer", "Ida Engberg", "UMEK");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Three names with leading and trailing spaces")
                void threeNamesCommaWithLeadingAndTrailingSpacesSeparated() {
                    initTrackWithArtistAndResult("Adam    Beyer  ,   Ida  Engberg ,   UMEK ", "Adam Beyer", "Ida Engberg",
                                                 "UMEK");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Two repeated names")
                void twoNamesRepeatedCommaSeparated() {
                    initTrackWithArtistAndResult("Adam Beyer, Adam Beyer", "Adam Beyer");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }
            }

            @Nested
            @DisplayName("& separated")
            class andpersandSeparated {

                @Test
                @DisplayName("Two names")
                void twoNamesAndpersandSeparated() {
                    initTrackWithArtistAndResult("Adam Beyer & Ida Engberg", "Adam Beyer", "Ida Engberg");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Two names with leading and trailing spaces")
                void twoNamesAndpersandWithSpacesSeparated() {
                    initTrackWithArtistAndResult("Adam   Beyer  &     Ida Engberg ", "Adam Beyer", "Ida Engberg");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Three names")
                void threeNamesAndpersandSeparated() {
                    initTrackWithArtistAndResult("Adam Beyer & Ida Engberg & UMEK", "Adam Beyer", "Ida Engberg", "UMEK");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Three names with leading and trailing spaces")
                void threeNamesAndpersandWithSpacesSeparated() {
                    initTrackWithArtistAndResult("adam   beyer  & ida  engberg &  uMEK ", "Adam Beyer", "Ida Engberg",
                                                 "UMEK");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Two repeated names")
                void twoNamesRepeatedAnpersandSeparated() {
                    initTrackWithArtistAndResult("Adam Beyer & Adam Beyer", "Adam Beyer");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }
            }

            @Nested
            @DisplayName("'vs' separated")
            class vsSeparated {

                @Test
                @DisplayName("Two names")
                void twoNamesvsSeparated() {
                    initTrackWithArtistAndResult("Adam Beyer vs Ida Engberg", "Adam Beyer", "Ida Engberg");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Three names")
                void threeNamesVsSeparated() {
                    initTrackWithArtistAndResult("Adam Beyer vs Ida Engberg VS UMEK", "Adam Beyer", "Ida Engberg", "UMEK");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Two repeated names")
                void twoNamesRepeatedVsSeparated() {
                    initTrackWithArtistAndResult("Adam Beyer vs Adam Beyer", "Adam Beyer");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }
            }

            @Nested
            @DisplayName("'versus' separated")
            class versusSeparated {

                @Test
                @DisplayName("Two names")
                void twoNamesVersusSeparated() {
                    initTrackWithArtistAndResult("Adam Beyer versus Ida Engberg", "Adam Beyer", "Ida Engberg");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Three names")
                void threeNamesVersusSeparated() {
                    initTrackWithArtistAndResult("adam Beyer versus Ida Engberg Versus umek", "Adam Beyer",
                                                 "Ida Engberg", "Umek");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Two repeated names")
                void twoNamesRepeatedVersusSeparated() {
                    initTrackWithArtistAndResult("Adam Beyer versus Adam Beyer", "Adam Beyer");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }
            }

            @Nested
            @DisplayName("'vs.' separated")
            class versusDotSeparated {

                @Test
                @DisplayName("Two names")
                void twoNamesVsDotSeparated() {
                    initTrackWithArtistAndResult("Adam Beyer vs. Ida Engberg", "Adam Beyer", "Ida Engberg");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Three names")
                void threeNamesVsDotSeparated() {
                    initTrackWithArtistAndResult("Adam Beyer vs. Ida Engberg Vs. UMEK", "Adam Beyer",
                                                 "Ida Engberg", "UMEK");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Two repeated names")
                void twoNamesRepeatedVsDotSeparated() {
                    initTrackWithArtistAndResult("Adam Beyer Vs. Adam Beyer", "Adam Beyer");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }
            }

            @Nested
            @DisplayName("Feat(.) separated")
            class featSeparated {

                @Test
                @DisplayName("Two names feat. separated")
                void twoNamesFeatDotSeparated() {
                    initTrackWithArtistAndResult("Benny Benassi Feat. Gary Go", "Benny Benassi", "Gary Go");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Two names Feat separated")
                void twoNamesFeatSeparated() {
                    initTrackWithArtistAndResult("Dragon Ash Feat Rappagariya", "Dragon Ash", "Rappagariya");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }
            }

            @Nested
            @DisplayName("Ft(.) separated")
            class ftSeparated {

                @Test
                @DisplayName("Two names ft. separated")
                void twoNamesFeatSeparated() {
                    initTrackWithArtistAndResult("Ludacris Ft. Shawnna", "Ludacris", "Shawnna");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Two names ft separated")
                void twoNamesFeatDotSeparated() {
                    initTrackWithArtistAndResult("Ludacris Ft Shawnna", "Ludacris", "Shawnna");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }
            }

            @Nested
            @DisplayName("Comma and & separated")
            class commaAndpersandSeparated {

                @Test
                @DisplayName("Three names")
                void threeNamesCommaAndpersandSeparated() {
                    initTrackWithArtistAndResult("Adam Beyer, Ida Engberg & Ansome", "Adam Beyer", "Ida Engberg", "Ansome");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Four names")
                void fourNamesCommaAndpersandSeparated() {
                    initTrackWithArtistAndResult("Adam beyer & Ida engberg, UMEK & ansome", "Adam Beyer", "Ida Engberg",
                                                 "UMEK", "Ansome");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Five names")
                void fiveNamesCommaAndpersandSeparated() {
                    initTrackWithArtistAndResult("Adam Beyer & UMEK, Showtek, Ansome & Ida Engberg", "Adam Beyer",
                                                 "Ida Engberg", "UMEK", "Showtek", "Ansome");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Five name with leading and trailing spaces")
                void fiveNamesCommaAndpersandWithSpacesTest() {
                    initTrackWithArtistAndResult(" Adam  Beyer , UMEK  & Showtek , Ansome   & Ida   Engberg ", "Adam Beyer",
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
                    initTrackWithArtistAndResult("Laidback Luke Feat. Chuckie & Martin Solveig", "Laidback Luke",
                                                 "Chuckie", "Martin Solveig");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }

                @Test
                @DisplayName("Three names with & and Ft separated")
                void threeNamesWithFtAnpersandSeparated() {
                    initTrackWithArtistAndResult("Laidback Luke Feat Chuckie & Martin Solveig", "Laidback Luke",
                                                 "Chuckie", "Martin Solveig");
                    assertEquals(expectedArtists, audioItem.artistsInvolved());
                }
            }
        }

        @Nested
        @DisplayName("In name field")
        class artistsInNameField {

            AudioItem audioItem;
            ImmutableSet<String> expectedArtists;

            private void initializeWithNameAndResult(String name, String... expectedArtist) {
                audioItem = new ImmutableAudioItemBuilder(path, name, duration, bitRate, LocalDateTime.now(), LocalDateTime.now())
                        .album(ImmutableAlbum.UNKNOWN_ALBUM)
                        .artist(ImmutableArtist.UNKNOWN_ARTIST)
                        .bpm(bpm)
                        .trackNumber(trackNumber)
                        .discNumber(discNumber)
                        .comments(comments)
                        .genre(genre)
                        .encoding(encoding)
                        .encoder(encoder)
                        .build();
                expectedArtists = ImmutableSet.<String>builder().add(expectedArtist).build();
            }

            @Test
            @DisplayName("Just the track name")
            void justTheTrackName() {
                initializeWithNameAndResult("Nothing Left Part 1");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Original mix")
            void originalMix() {
                initializeWithNameAndResult("Song name (Original Mix)");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Edit version")
            void editVersion() {
                initializeWithNameAndResult("Song name (Special time edit)");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Ends with 'Remix'")
            void endsWithRemix() {
                initializeWithNameAndResult("Song name (adam beyer Remix)", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Has 'Remix' with useless spaces")
            void hasRemixWithUselessSpaces() {
                initializeWithNameAndResult(" Song   name ( Adam   Beyer  Remix)", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Has 'Remix by'")
            void hasRemixBy() {
                initializeWithNameAndResult("Song name (Remix by Adam Beyer)", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Starts with 'Remix by' with useless spaces")
            void hasRemixByWithUselessSpaces() {
                initializeWithNameAndResult("Song   name  (Remix    by  Adam   Beyer)", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Has 'Ft' outside parenthesis")
            void hasFt() {
                initializeWithNameAndResult("Song name ft Adam Beyer", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Has 'Ft' inside parenthesis")
            void hasFtInsideParenthesis() {
                initializeWithNameAndResult("Song name (ft Adam Beyer)", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Has 'Feat' outside parenthesis")
            void hasFeat() {
                initializeWithNameAndResult("Song name feat Adam Beyer", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Has 'Feat' inside parenthesis")
            void hasFeatInsideParenthesis() {
                initializeWithNameAndResult("Song name (feat Adam Beyer)", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Has 'featuring' ouside parenthesis")
            void hasFeaturing() {
                initializeWithNameAndResult("Song name featuring Adam Beyer", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Has 'featuring' inside parenthesis")
            void hasFeaturingInsideParenthesis() {
                initializeWithNameAndResult("Song name (featuring Adam Beyer)", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Has 'With'")
            void hasWith() {
                initializeWithNameAndResult("Song name (With Adam Beyer)", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Has 'ft' and ending by 'Remix'")
            @Disabled("User should put the extra artist in the artist field, separated by a comma")
            void twoArtistsDividedByFtWithRemix() {
                initializeWithNameAndResult("Pretendingtowalkslow ft Zeroh (M. Constant Remix)", "Zeroh", "M. Constant");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Two names separated by '&' ending with 'Remix'")
            void twoArtistsDividedByAndpersandEndingWithRemix() {
                initializeWithNameAndResult("Song name (Adam beyer & pete tong Remix)", "Adam Beyer", "Pete Tong");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Two names separated by 'vs' ending with 'Remix'")
            void vsSeparatedWithRemix() {
                initializeWithNameAndResult("Fall (M83 vs Big Black Delta Remix)", "M83", "Big Black Delta");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Four names separated by with comma and & starting with 'feat'")
            void fourNamesCommaAndpersandFeatSeparated() {
                initializeWithNameAndResult("Jet Blue Jet (feat Leftside, GTA, Razz & Biggy)", "Leftside",
                                            "GTA", "Razz", "Biggy");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }
        }

        @Nested
        @DisplayName("In album artist field")
        class namesInsideAlbumArtistField {

            AudioItem audioItem;
            ImmutableSet<String> expectedArtists;

            private void initializeWithNameAndResult(String albumArtistString, String... expectedArtist) {
                audioItem = new ImmutableAudioItemBuilder(path, "", duration, bitRate, LocalDateTime.now(), LocalDateTime.now())
                        .album(new ImmutableAlbum("", new ImmutableArtist(albumArtistString), false, (short) 1969, ImmutableLabel.UNKNOWN))
                        .artist(ImmutableArtist.UNKNOWN_ARTIST)
                        .bpm(bpm)
                        .trackNumber(trackNumber)
                        .discNumber(discNumber)
                        .comments(comments)
                        .genre(genre)
                        .encoding(encoding)
                        .encoder(encoder)
                        .build();
                expectedArtists = ImmutableSet.<String>builder().add(expectedArtist).build();
            }

            @Test
            @DisplayName("One name")
            void oneNameInAlbumArtist() {
                initializeWithNameAndResult("Adam Beyer", "Adam Beyer");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Two names separated by commas")
            void twoNamesInAlbumArtistCommSeparated() {
                initializeWithNameAndResult("Adam Beyer, UMEK", "Adam Beyer", "UMEK");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Two names separated by &")
            void twoNamesInAlbumArtistAndpersandSeparated() {
                initializeWithNameAndResult("Adam Beyer & Pete Tong", "Adam Beyer", "Pete Tong");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Three names separated by & and comma")
            void threeNamesInAlbumArtistAndpersandCommaSeparated() {
                initializeWithNameAndResult("Adam Beyer, Pete Tong & UMEK", "Adam Beyer", "Pete Tong", "UMEK");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }
        }

        @Nested
        @DisplayName("In artist, name and album artist fields")
        class namesInArtistNameAndAlbumFields {

            AudioItem audioItem;
            ImmutableSet<String> expectedArtists;

            private void initializeWithNamesAndResult(String name, String artist, String albumArtist,
                                                      String... expectedArtist) {
                audioItem = new ImmutableAudioItemBuilder(path, name, duration, bitRate, LocalDateTime.now(), LocalDateTime.now())
                        .album(new ImmutableAlbum("", new ImmutableArtist(albumArtist), false, (short) 1969, ImmutableLabel.UNKNOWN))
                        .artist(new ImmutableArtist(artist))
                        .bpm(bpm)
                        .trackNumber(trackNumber)
                        .discNumber(discNumber)
                        .comments(comments)
                        .genre(genre)
                        .encoding(encoding)
                        .encoder(encoder)
                        .build();
                expectedArtists = ImmutableSet.<String>builder().add(expectedArtist).build();
            }

            @Test
            @DisplayName("Simple name, one artist, same album artist")
            void simpleNameOneArtistSameAlbumArtist() {
                initializeWithNamesAndResult("Song name", "Pete Tong", "Pete Tong", "Pete Tong");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Simple name, one artist, one album artist")
            void simpleNameOneArtistOneAlbumArtist() {
                initializeWithNamesAndResult("Song name", "Pete Tong", "Jeff Mills", "Pete Tong", "Jeff Mills");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Simple name, two artists, same album artist")
            void simleNameTwoArtistsSameAlbumArtist() {
                initializeWithNamesAndResult("Song name", "Pete Tong, UMEK", "Pete Tong", "Pete Tong", "UMEK");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Name with 'Remix', one artist, no album artist")
            void nameWithRemixOneArtistNoAlbumArtist() {
                initializeWithNamesAndResult("Song name (Ansome Remix)", "Pete Tong", "", "Pete Tong", "Ansome");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Name with featuring, two artists with comma, one repeated album artist")
            void oneNameOneArtistOneAlbumArtist() {
                initializeWithNamesAndResult("Song name featuring Lulu Perez", "Pete Tong & Ansome", "Pete Tong", "Pete Tong",
                                             "Lulu Perez", "Ansome");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }

            @Test
            @DisplayName("Name with 'Remix by', two artists with &, one other album artist")
            void nameWithRemixByTwoArtistsWithAndpersandOneOtherAlbumArtist() {
                initializeWithNamesAndResult("Song name (Remix by Bonobo)", "Laurent Garnier & Rone", "Pete Tong",
                                             "Pete Tong", "Bonobo", "Laurent Garnier", "Rone");
                assertEquals(expectedArtists, audioItem.artistsInvolved());
            }
        }
    }
}

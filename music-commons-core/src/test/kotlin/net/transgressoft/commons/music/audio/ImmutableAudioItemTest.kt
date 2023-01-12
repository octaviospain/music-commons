package net.transgressoft.commons.music.audio

import com.google.common.truth.Truth.assertThat
import com.neovisionaries.i18n.CountryCode
import net.transgressoft.commons.music.AudioUtils.beautifyArtistName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime

/**
 * @author Octavio Calleya
 */
internal class ImmutableAudioItemTest {

    var id = 9
    var path = Path.of("testfiles", "testeable.mp3")
    var title = "Yesterday"
    var duration = Duration.ofMinutes(2)
    var bitRate = 320
    var artistName = "The Beatles"
    var label: Label = ImmutableLabel("EMI", CountryCode.US)
    var albumName = "Help!"
    var albumArtistName = "The Beatles Band"
    var isCompilation = false
    var year: Short = 1965
    var bpm = 120f
    var trackNumber: Short = 13
    var discNumber: Short = 1
    var comments = "Best song ever!"
    var genre = Genre.ROCK
    var encoding = "Lame MP3"
    var encoder = "transgressoft"
    val dateOfCreation = LocalDateTime.now()
    var artist: Artist = mock {
        on { name } doReturn artistName
        on { countryCode } doReturn CountryCode.UK
        on { toString() } doReturn "ImmutableArtist{name=The Beatles, countryCode=UK}"
    }
    var albumArtist: Artist = mock {
        on { name } doReturn albumArtistName
        on { countryCode } doReturn CountryCode.UK
    }
    var album: Album = mock {
        on { name } doReturn albumName
        on { year } doReturn year
        on { label } doReturn label
        on { isCompilation } doReturn isCompilation
        on { albumArtist } doReturn albumArtist
    }

    @Test
    @DisplayName("AudioItem properties")
    fun propertiesTest() {
        var audioItem = ImmutableAudioItem(id, path, title, duration, bitRate, artist, album, genre, comments, trackNumber, discNumber, bpm, encoder, encoding, dateOfCreation)
        val dateOfCreation = audioItem.dateOfCreation
        val lastDateModified = audioItem.lastDateModified

        assertEquals(9, audioItem.id)
        assertEquals(dateOfCreation, lastDateModified)
        assertTrue(LocalDateTime.now().isAfter(audioItem.dateOfCreation))
        assertEquals(path, audioItem.path)
        assertEquals(dateOfCreation, audioItem.dateOfCreation)
        assertEquals("testeable.mp3", audioItem.fileName)
        assertEquals("mp3", audioItem.extension)
        assertEquals(title, audioItem.title)
        assertEquals(duration, audioItem.duration)
        assertEquals(bitRate, audioItem.bitRate)
        assertEquals(album.name, audioItem.album.name)
        assertEquals(album.albumArtist.name, audioItem.album.albumArtist.name)
        assertEquals(CountryCode.UK, audioItem.album.albumArtist.countryCode)
        assertFalse(audioItem.album.isCompilation)
        assertEquals(album.year, audioItem.album.year)
        assertEquals(album.label.name, audioItem.album.label.name)
        assertEquals(CountryCode.US, audioItem.album.label.countryCode)
        assertEquals(artist.name, audioItem.artist.name)
        assertEquals(CountryCode.UK, audioItem.artist.countryCode)
        assertEquals(bpm, audioItem.bpm)
        assertEquals(trackNumber, audioItem.trackNumber)
        assertEquals(discNumber, audioItem.discNumber)
        assertEquals(comments, audioItem.comments)
        assertEquals(genre, audioItem.genre)
        assertEquals(encoding, audioItem.encoding)
        assertEquals(encoder, audioItem.encoder)

        audioItem = audioItem.copy(comments = "modified", lastDateModified = LocalDateTime.now())
        assertNotEquals(lastDateModified, audioItem.lastDateModified)
        assertTrue(lastDateModified.isBefore(audioItem.lastDateModified))
        assertEquals(
            "ImmutableAudioItem(id=9, path=testfiles/testeable.mp3, title=Yesterday, artist=The Beatles)",
            audioItem.toString()
        )
        assertEquals("testeable.mp3-Yesterday-120-320", audioItem.uniqueId)
        val label: Label = ImmutableLabel("New label")
        assertNotEquals(label, audioItem.album.label)
        assertEquals("ImmutableLabel(name=New label, countryCode=UNDEFINED)", label.toString())

        val newAlbum = mock<Album> {
            on { name } doReturn "OtherAlbum"
            on { albumArtist } doReturn ImmutableArtist("Other Artist")
            on { isCompilation } doReturn true
            on { year } doReturn 1999.toShort()
            on { it.label } doReturn ImmutableLabel.UNKNOWN
        }
        val audioItemWithModifiedAlbum = audioItem.copy(
            title = "Other title",
            album = newAlbum,
            encoder = "New encoder",
            encoding = "New encoding",
            bpm = 128f)
        assertEquals("Other Artist", audioItemWithModifiedAlbum.album.albumArtist.name)
        assertEquals("Other title", audioItemWithModifiedAlbum.title)
        assertNotEquals(audioItem, audioItemWithModifiedAlbum)
        assertTrue(audioItemWithModifiedAlbum.album.isCompilation)
        assertEquals("New encoder", audioItemWithModifiedAlbum.encoder)
        assertEquals("New encoding", audioItemWithModifiedAlbum.encoding)
        assertEquals(128f, audioItemWithModifiedAlbum.bpm)
        assertEquals(0, audioItem.length)

        val unknownAlbum = mock<Album> {
            on { name } doReturn ""
            on { albumArtist } doReturn ImmutableArtist.UNKNOWN
            on { isCompilation } doReturn true
            on { year } doReturn 1999.toShort()
            on { it.label } doReturn ImmutableLabel.UNKNOWN
        }
        var modifiedAudioItem = audioItemWithModifiedAlbum.copy(
            genre = Genre.UNDEFINED,
            album = unknownAlbum,
            comments = "Modified")
        assertEquals(audioItem.artist, modifiedAudioItem.artist)
        assertThat(modifiedAudioItem.album.name).isEmpty()
        assertEquals(Genre.UNDEFINED, modifiedAudioItem.genre)
        assertEquals("Modified", modifiedAudioItem.comments)
        assertEquals(artist, modifiedAudioItem.artist)

        modifiedAudioItem = modifiedAudioItem.copy(
            artist = ImmutableArtist.UNKNOWN,
            path = Path.of("/moved/song.mp3"),
            discNumber = 2.toShort(),
            trackNumber = 3.toShort())
        assertEquals("/moved/song.mp3", modifiedAudioItem.path.toString())
        assertEquals(2.toShort(), modifiedAudioItem.discNumber)
        assertEquals(3.toShort(), modifiedAudioItem.trackNumber)
    }

    @Nested
    @DisplayName("Artists involved")
    internal inner class ArtistsInvolved {
        lateinit var audioItem: AudioItem
        lateinit var expectedArtists: Set<String>

        private fun initAudioItemAndExpectedArtists(
            _title: String,
            _artistString: String,
            _albumArtistString: String,
            vararg expectedArtist: String,
        ) {
            val _artist: Artist = mock {
                on { name } doReturn beautifyArtistName(_artistString)
            }

            val _albumArtist: Artist = mock {
                on { name } doReturn _albumArtistString
            }
            val _album: Album = mock {
                on { it.albumArtist } doReturn _albumArtist
                on { label } doReturn label
                on { year } doReturn year
                on { name } doReturn albumName
                on { isCompilation } doReturn isCompilation
            }

            audioItem = ImmutableAudioItem(id, path, _title, duration, bitRate, _artist, _album)
            expectedArtists = setOf(*expectedArtist)
        }

        @Nested
        @DisplayName("In artist field")
        internal inner class InArtistField {

            @Nested
            @DisplayName("Ft(.) separated")
            internal inner class FtSeparated {
                @Test
                @DisplayName("Two names ft. separated")
                fun twoNamesFeatSeparated() {
                    initAudioItemAndExpectedArtists(title, "Ludacris Ft. Shawnna", "", "Ludacris", "Shawnna")
                    assertEquals(expectedArtists, audioItem.artistsInvolved)
                }

                @Test
                @DisplayName("Two names ft separated")
                fun twoNamesFeatDotSeparated() {
                    initAudioItemAndExpectedArtists(title, "Ludacris Ft Shawnna", "", "Ludacris", "Shawnna")
                    assertEquals(expectedArtists, audioItem.artistsInvolved)
                }
            }

            @Nested
            @DisplayName("Comma and & separated")
            internal inner class CommaAndpersandSeparated {
                @Test
                @DisplayName("Three names")
                fun threeNamesCommaAndpersandSeparated() {
                    initAudioItemAndExpectedArtists(title, "Adam Beyer, Ida Engberg & Ansome", "", "Adam Beyer", "Ida Engberg", "Ansome")
                    assertEquals(expectedArtists, audioItem.artistsInvolved)
                }

                @Test
                @DisplayName("Four names")
                fun fourNamesCommaAndpersandSeparated() {
                    initAudioItemAndExpectedArtists(
                        title, "Adam beyer & Ida engberg, UMEK & ansome", "", "Adam Beyer", "Ida Engberg",
                        "UMEK", "Ansome"
                    )
                    assertEquals(expectedArtists, audioItem.artistsInvolved)
                }

                @Test
                @DisplayName("Five names")
                fun fiveNamesCommaAndpersandSeparated() {
                    initAudioItemAndExpectedArtists(
                        title, "Adam Beyer & UMEK, Showtek, Ansome & Ida Engberg", "", "Adam Beyer",
                        "Ida Engberg", "UMEK", "Showtek", "Ansome"
                    )
                    assertEquals(expectedArtists, audioItem.artistsInvolved)
                }

                @Test
                @DisplayName("Five name with leading and trailing spaces")
                fun fiveNamesCommaAndpersandWithSpacesTest() {
                    initAudioItemAndExpectedArtists(
                        title, " Adam  Beyer , UMEK  & Showtek , Ansome   & Ida   Engberg ", "", "Adam Beyer",
                        "Ida Engberg", "UMEK", "Showtek", "Ansome"
                    )
                    assertEquals(expectedArtists, audioItem.artistsInvolved)
                }
            }

            @Nested
            @DisplayName("Ft(.) and & separated`")
            internal inner class FtAndpersandSeparated {
                @Test
                @DisplayName("Three names with & and Ft. separated")
                fun threeNamesWithFtDotAnpersandSeparated() {
                    initAudioItemAndExpectedArtists(
                        title, "Laidback Luke Feat. Chuckie & Martin Solveig", "", "Laidback Luke",
                        "Chuckie", "Martin Solveig"
                    )
                    assertEquals(expectedArtists, audioItem.artistsInvolved)
                }

                @Test
                @DisplayName("Three names with & and Ft separated")
                fun threeNamesWithFtAnpersandSeparated() {
                    initAudioItemAndExpectedArtists(
                        title, "Laidback Luke Feat Chuckie & Martin Solveig", "", "Laidback Luke",
                        "Chuckie", "Martin Solveig"
                    )
                    assertEquals(expectedArtists, audioItem.artistsInvolved)
                }
            }
        }

        @Nested
        @DisplayName("In title field")
        internal inner class InTitleField {
            
            @Test
            @DisplayName("Just the track name")
            fun justTheTrackName() {
                initAudioItemAndExpectedArtists("Nothing Left Part 1", "", "")
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }

            @Test
            @DisplayName("Original mix")
            fun originalMix() {
                initAudioItemAndExpectedArtists("Song title (Original Mix)", "", "")
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }

            @Test
            @DisplayName("Edit version")
            fun editVersion() {
                initAudioItemAndExpectedArtists("Song title (Special time edit)", "", "")
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }

            @Test
            @DisplayName("Ends with 'Remix'")
            fun endsWithRemix() {
                initAudioItemAndExpectedArtists("Song title (adam beyer Remix)", "", "", "Adam Beyer")
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }

            @Test
            @DisplayName("Has 'Remix' with useless spaces")
            fun hasRemixWithUselessSpaces() {
                initAudioItemAndExpectedArtists(" Song   name ( Adam   Beyer  Remix)", "", "", "Adam Beyer")
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }

            @Test
            @DisplayName("Has 'Remix by'")
            fun hasRemixBy() {
                initAudioItemAndExpectedArtists("Song title (Remix by Adam Beyer)", "", "", "Adam Beyer")
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }

            @Test
            @DisplayName("Starts with 'Remix by' with useless spaces")
            fun hasRemixByWithUselessSpaces() {
                initAudioItemAndExpectedArtists("Song   name  (Remix    by  Adam   Beyer)", "", "", "Adam Beyer")
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }

            @Test
            @DisplayName("Has 'Ft' outside parenthesis")
            fun hasFt() {
                initAudioItemAndExpectedArtists("Song title ft Adam Beyer", "", "", "Adam Beyer")
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }

            @Test
            @DisplayName("Has 'Ft' inside parenthesis")
            fun hasFtInsideParenthesis() {
                initAudioItemAndExpectedArtists("Song title (ft Adam Beyer)", "", "", "Adam Beyer")
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }

            @Test
            @DisplayName("Has 'Feat' outside parenthesis")
            fun hasFeat() {
                initAudioItemAndExpectedArtists("Song title feat Adam Beyer", "", "", "Adam Beyer")
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }

            @Test
            @DisplayName("Has 'Feat' inside parenthesis")
            fun hasFeatInsideParenthesis() {
                initAudioItemAndExpectedArtists("Song title (feat Adam Beyer)", "", "", "Adam Beyer")
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }

            @Test
            @DisplayName("Has 'featuring' ouside parenthesis")
            fun hasFeaturing() {
                initAudioItemAndExpectedArtists("Song title featuring Adam Beyer", "", "", "Adam Beyer")
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }

            @Test
            @DisplayName("Has 'featuring' inside parenthesis")
            fun hasFeaturingInsideParenthesis() {
                initAudioItemAndExpectedArtists("Song title (featuring Adam Beyer)", "", "", "Adam Beyer")
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }

            @Test
            @DisplayName("Has 'With'")
            fun hasWith() {
                initAudioItemAndExpectedArtists("Song title (With Adam Beyer)", "", "", "Adam Beyer")
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }

            @Test
            @DisplayName("Has 'ft' and ending by 'Remix'")
            @Disabled("User should put the extra artist in the artist field, separated by a comma")
            fun twoArtistsDividedByFtWithRemix() {
                initAudioItemAndExpectedArtists("Pretendingtowalkslow ft Zeroh (M. Constant Remix)", "", "", "Zeroh", "M. Constant")
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }

            @Test
            @DisplayName("Two names separated by '&' ending with 'Remix'")
            fun twoArtistsDividedByAndpersandEndingWithRemix() {
                initAudioItemAndExpectedArtists("Song title (Adam beyer & pete tong Remix)", "", "", "Adam Beyer", "Pete Tong")
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }

            @Test
            @DisplayName("Two names separated by 'vs' ending with 'Remix'")
            fun vsSeparatedWithRemix() {
                initAudioItemAndExpectedArtists("Fall (M83 vs Big Black Delta Remix)", "", "", "M83", "Big Black Delta")
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }

            @Test
            @DisplayName("Four names separated by with comma and & starting with 'feat'")
            fun fourNamesCommaAndpersandFeatSeparated() {
                initAudioItemAndExpectedArtists(
                    "Jet Blue Jet (feat Leftside, GTA, Razz & Biggy)", "", "", "Leftside",
                    "GTA", "Razz", "Biggy"
                )
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }
        }

        @Nested
        @DisplayName("In album artist field")
        internal inner class InAlbumArtistField {
            
            @Test
            @DisplayName("One name")
            fun oneNameIngetAlbumArtist() {
                initAudioItemAndExpectedArtists(title, "Adam Beyer", "Adam Beyer", "Adam Beyer")
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }

            @Test
            @DisplayName("Two names separated by commas")
            fun twoNamesInAlbumArtistCommSeparated() {
                initAudioItemAndExpectedArtists(title, "Adam Beyer, UMEK", "Adam Beyer", "UMEK", "Adam Beyer")
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }

            @Test
            @DisplayName("Two names separated by &")
            fun twoNamesInAlbumArtistAndpersandSeparated() {
                initAudioItemAndExpectedArtists(title, "Adam Beyer & Pete Tong", "Adam Beyer", "Pete Tong", "Adam Beyer")
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }

            @Test
            @DisplayName("Three names separated by & and comma")
            fun threeNamesInAlbumArtistAndpersandCommaSeparated() {
                initAudioItemAndExpectedArtists(title, "Adam Beyer, Pete Tong & UMEK", "Adam Beyer", "Pete Tong", "UMEK", "Adam Beyer")
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }
        }

        @Nested
        @DisplayName("In artist, title and album artist fields")
        internal inner class InArtistAndTitleAndAlbumArtistFields {
            
            @Test
            @DisplayName("Simple name, one artist, same album artist")
            fun simpleNameOneArtistSamegetAlbumArtist() {
                initAudioItemAndExpectedArtists("Song title", "Pete Tong", "Pete Tong", "Pete Tong")
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }

            @Test
            @DisplayName("Simple name, one artist, one album artist")
            fun simpleNameOneArtistOnegetAlbumArtist() {
                initAudioItemAndExpectedArtists("Song title", "Pete Tong", "Jeff Mills", "Pete Tong", "Jeff Mills")
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }

            @Test
            @DisplayName("Simple name, two artists, same album artist")
            fun simpleNameTwoArtistsSamegetAlbumArtist() {
                initAudioItemAndExpectedArtists("Song title", "Pete Tong, UMEK", "Pete Tong", "Pete Tong", "UMEK")
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }

            @Test
            @DisplayName("Name with 'Remix', one artist, no album artist")
            fun nameWithRemixOneArtistNogetAlbumArtist() {
                initAudioItemAndExpectedArtists("Song title (Ansome Remix)", "Pete Tong", "", "Pete Tong", "Ansome")
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }

            @Test
            @DisplayName("Name with featuring, two artists with comma, one repeated album artist")
            fun oneNameOneArtistOnegetAlbumArtist() {
                initAudioItemAndExpectedArtists(
                    "Song title featuring Lulu Perez", "Pete Tong & Ansome", "Pete Tong", "Pete Tong",
                    "Lulu Perez", "Ansome"
                )
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }

            @Test
            @DisplayName("Name with 'Remix by', two artists with &, one other album artist")
            fun nameWithRemixByTwoArtistsWithAndpersandOneOthergetAlbumArtist() {
                initAudioItemAndExpectedArtists(
                    "Song title (Remix by Bonobo)", "Laurent Garnier & Rone", "Pete Tong",
                    "Pete Tong", "Bonobo", "Laurent Garnier", "Rone"
                )
                assertEquals(expectedArtists, audioItem.artistsInvolved)
            }
        }
    }
}
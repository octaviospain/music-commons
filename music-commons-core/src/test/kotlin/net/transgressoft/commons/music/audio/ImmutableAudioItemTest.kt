package net.transgressoft.commons.music.audio

import com.google.common.truth.Truth.assertThat
import com.neovisionaries.i18n.CountryCode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
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
}
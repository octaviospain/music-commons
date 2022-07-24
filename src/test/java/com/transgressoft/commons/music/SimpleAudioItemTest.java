package com.transgressoft.commons.music;

import com.neovisionaries.i18n.CountryCode;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Octavio Calleya
 */
class SimpleAudioItemTest {

    @Test
    @DisplayName("AudioItem properties")
    void propertiesTest() {
        AudioItem audioItem =
                SimpleAudioItem.builder(Path.of("/song.mp3"),
                                        "Yesterday",
                                        Duration.ofMinutes(2), 320)
                        .album(new SimpleAlbum("Help!", new SimpleArtist("The Beatles", CountryCode.UK), false, (short) 1965, new SimpleLabel("EMI", CountryCode.US)))
                        .artist(new SimpleArtist("The Beatles", CountryCode.UK))
                        .bpm(120)
                        .trackNumber((short) 13)
                        .discNumber((short) 1)
                        .comments("Best song ever!")
                        .genre(Genre.ROCK)
                        .encoding("Lame MP3")
                        .encoder("transgressoft")
                        .build();

        assertEquals(Path.of("/song.mp3"), audioItem.path());
        assertEquals("song.mp3", audioItem.fileName());
        assertEquals("mp3", audioItem.extension());
        assertEquals("Yesterday", audioItem.name());
        assertEquals(Duration.ofMinutes(2), audioItem.duration());
        assertEquals(320, audioItem.bitRate());
        assertEquals("Help!", audioItem.album().name());
        assertEquals("The Beatles", audioItem.album().albumArtist().name());
        assertEquals(CountryCode.UK, audioItem.album().albumArtist().countryCode());
        assertFalse(audioItem.album().isCompilation());
        assertEquals(1965, audioItem.album().year());
        assertEquals("EMI", audioItem.album().label().name());
        assertEquals(CountryCode.US, audioItem.album().label().countryCode());
        assertEquals("The Beatles", audioItem.artist().name());
        assertEquals(CountryCode.UK, audioItem.artist().countryCode());
        assertEquals(120, audioItem.bpm());
        assertEquals(13, audioItem.trackNumber());
        assertEquals(1, audioItem.discNumber());
        assertEquals("Best song ever!", audioItem.comments());
        assertEquals(Genre.ROCK, audioItem.genre());
        assertEquals("Lame MP3", audioItem.encoding());
        assertEquals("transgressoft", audioItem.encoder());
        assertEquals("SimpleAudioItem{path=/song.mp3, name=Yesterday, artist=SimpleArtist{name=The Beatles, countryCode=UK}}", audioItem.toString());

        Label label = new SimpleLabel("New label");
        assertNotEquals(label, audioItem.album().label());
        assertEquals("SimpleLabel{name=New label, countryCode=UNDEFINED}", label.toString());

        AudioItem modifiedAlbum = audioItem
                .name("Other title").album(new SimpleAlbum("Other album", new SimpleArtist("Other artist"), true, (short) 1999, SimpleLabel.UNKNOWN))
                .encoder("New encoder").encoding("New encoding")
                .bpm(128);

        assertEquals("Other artist", modifiedAlbum.album().albumArtist().name());
        assertEquals("Other title", modifiedAlbum.name());
        assertFalse(audioItem.equals(modifiedAlbum));
        assertTrue(modifiedAlbum.album().isCompilation());
        assertEquals("New encoder", modifiedAlbum.encoder());
        assertEquals("New encoding", modifiedAlbum.encoding());
        assertEquals(128, modifiedAlbum.bpm());
        assertEquals(0, audioItem.length());

        modifiedAlbum = modifiedAlbum.genre(Genre.UNDEFINED).album(SimpleAlbum.UNKNOWN_ALBUM).comments("Modified");

        assertFalse(audioItem.artist().equals(modifiedAlbum.artist()));
        assertEquals(SimpleAlbum.UNKNOWN_ALBUM, modifiedAlbum.album());
        assertEquals(Genre.UNDEFINED, modifiedAlbum.genre());
        assertEquals("Modified", modifiedAlbum.comments());
        assertEquals(SimpleArtist.UNKNOWN_ARTIST, modifiedAlbum.artist());

        modifiedAlbum = modifiedAlbum.artist(SimpleArtist.UNKNOWN_ARTIST)
                .path(Path.of("/moved/song.mp3")).discNumber((short) 2).trackNumber((short) 3);

        assertEquals("/moved/song.mp3", modifiedAlbum.path().toString());
        assertEquals(2, modifiedAlbum.discNumber());
        assertEquals(3, modifiedAlbum.trackNumber());

        assertTrue(audioItem.artist().compareTo(modifiedAlbum.artist()) > 0);

    }
}
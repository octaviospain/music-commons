package com.transgressoft.commons.music;

import org.junit.jupiter.api.*;

import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Octavio Calleya
 */
class SimpleAlbumTest {

    @Test
    @DisplayName("Album comparison test")
    void albumComparisonTest() throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(getClass().getResource("/testfiles/cover.jpg").toURI()));

        Album album = new SimpleAlbum("A night at the opera", (short) 1975);
        Album album2 = new SimpleAlbum("A night at the opera",
                                       new SimpleArtist("Queen"),
                                       false, (short) 1975, SimpleLabel.UNKNOWN, bytes);

        assertTrue(album.compareTo(album2) < 0);

        Album album3 = new SimpleAlbum("A night at the opera",
                                       new SimpleArtist("Queen"),
                                       false, (short) 1975, SimpleLabel.UNKNOWN, bytes);

        assertEquals(album2.compareTo(album3), 0);
        assertTrue(album2.equals(album3));
        assertEquals("SimpleAlbum{name=A night at the opera, albumArtist=SimpleArtist{name=, countryCode=UNDEFINED}," +
                             " isCompilation=false, year=1975, label=SimpleLabel{name=, countryCode=UNDEFINED}}",
                     album.toString());

        Album album4 =  new SimpleAlbum("A night at the opera",
                                        new SimpleArtist("Queen"),
                                        false, (short) 1975, new SimpleLabel("EMI"), bytes);

        assertTrue(album3.compareTo(album4) < 0);
    }
}

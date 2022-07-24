package net.transgressoft.commons.music;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Octavio Calleya
 */
class ImmutableAlbumTest {

    @Test
    @DisplayName("Album comparison test")
    void albumComparisonTest() throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(getClass().getResource("/testfiles/cover.jpg").toURI()));

        Album album = new ImmutableAlbum("A night at the opera", (short) 1975);
        Album album2 = new ImmutableAlbum("A night at the opera",
                                          new ImmutableArtist("Queen"),
                                          false, (short) 1975, ImmutableLabel.UNKNOWN, bytes);

        assertTrue(album.compareTo(album2) < 0);

        Album album3 = new ImmutableAlbum("A night at the opera",
                                          new ImmutableArtist("Queen"),
                                          false, (short) 1975, ImmutableLabel.UNKNOWN, bytes);

        assertEquals(album2.compareTo(album3), 0);
        assertTrue(album2.equals(album3));
        assertEquals("ImmutableAlbum{name=A night at the opera, albumArtist=ImmutableArtist{name=, countryCode=UNDEFINED}," +
                             " isCompilation=false, year=1975, label=ImmutableLabel{name=, countryCode=UNDEFINED}}",
                     album.toString());

        Album album4 = new ImmutableAlbum("A night at the opera",
                                          new ImmutableArtist("Queen"),
                                          false, (short) 1975, new ImmutableLabel("EMI"), bytes);

        assertTrue(album3.compareTo(album4) < 0);
    }
}

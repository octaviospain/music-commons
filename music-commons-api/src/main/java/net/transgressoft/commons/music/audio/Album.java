package net.transgressoft.commons.music.audio;

import java.util.Optional;
import java.util.Set;

/**
 * @author Octavio Calleya
 */
public interface Album extends Comparable<Album> {

    String name();

    Artist albumArtist();

    Set<AudioItem> audioItems();

    boolean isCompilation();

    short year();

    Label label();

    Optional<byte[]> coverImage();
}

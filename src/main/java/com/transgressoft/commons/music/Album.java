package com.transgressoft.commons.music;

import java.util.Optional;

/**
 * @author Octavio Calleya
 */
public interface Album extends Comparable<Album> {

    String name();

    Artist albumArtist();

    boolean isCompilation();

    short year();

    Label label();

    Optional<byte[]> coverImage();
}

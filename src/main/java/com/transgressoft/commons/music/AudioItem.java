package com.transgressoft.commons.music;

import java.nio.file.Path;
import java.time.Duration;

/**
 * @author Octavio Calleya
 */
public interface AudioItem {

    Path path();

    AudioItem path(Path path);

    String fileName();

    String extension();

    String name();

    AudioItem name(String name);

    Artist artist();

    AudioItem artist(Artist artist);

    Album album();

    AudioItem album(Album album);

    Genre genre();

    AudioItem genre(Genre genre);

    String comments();

    AudioItem comments(String comments);

    short trackNumber();

    AudioItem trackNumber(short trackNumber);

    short discNumber();

    AudioItem discNumber(short discNumber);

    float bpm();

    AudioItem bpm(float bpm);

    Duration duration();

    String encoder();

    AudioItem encoder(String encoder);

    String encoding();

    AudioItem encoding(String encoding);

    long length();

    int bitRate();
}

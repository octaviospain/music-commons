package net.transgressoft.commons.music.audio;

import com.google.common.collect.ImmutableSet;
import net.transgressoft.commons.music.Album;
import net.transgressoft.commons.music.Artist;
import net.transgressoft.commons.music.Genre;
import net.transgressoft.commons.query.QueryEntity;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * @author Octavio Calleya
 */
public interface AudioItem extends QueryEntity {

    interface Builder<E extends AudioItem> {

        E build();
    }

    Path path();

    AudioItem path(Path path);

    String fileName();

    String extension();

    String title();

    AudioItem title(String name);

    Artist artist();

    AudioItem artist(Artist artist);

    ImmutableSet<String> artistsInvolved();

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

    short playCount();

    AudioItem playCount(short playCount);

    LocalDateTime dateOfInclusion();

    LocalDateTime lastDateModified();
}

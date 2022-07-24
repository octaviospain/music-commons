package net.transgressoft.commons.music.audio;

import net.transgressoft.commons.query.Repository;

import java.nio.file.Path;
import java.time.Duration;

public interface AudioItemRepository<I extends AudioItem> extends Repository<I> {

    AudioItemBuilder<I> create(Path path, String title, Duration duration, int bitRate);

    boolean containsAudioItemWithArtist(String artistName);
}

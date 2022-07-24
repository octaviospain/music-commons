package net.transgressoft.commons.music.audio;

import net.transgressoft.commons.query.Repository;

import java.nio.file.Path;
import java.time.Duration;

public interface AudioItemRepository extends Repository<AudioItem> {

    AudioItemBuilder<AudioItem> create(Path path, String title, Duration duration, int bitRate);
}

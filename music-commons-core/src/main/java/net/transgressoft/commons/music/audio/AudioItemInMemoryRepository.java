package net.transgressoft.commons.music.audio;

import net.transgressoft.commons.query.InMemoryRepository;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;

import static java.util.Objects.requireNonNull;

/**
 * @author Octavio Calleya
 */
public class AudioItemInMemoryRepository extends InMemoryRepository<AudioItem> implements AudioItemRepository {

    public AudioItemInMemoryRepository() {
        super();
    }

    public AudioItemInMemoryRepository(Collection<AudioItem> entities) {
        super(entities);
    }

    @Override
    public AudioItemBuilder<AudioItem> create(Path path, String title, Duration duration, int bitRate) {
        requireNonNull(path);
        requireNonNull(title);
        requireNonNull(duration);

        return new ImmutableAudioItemBuilder(path, title, duration, bitRate, LocalDateTime.now());
    }
}

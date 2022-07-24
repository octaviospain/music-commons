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
public class AudioItemInMemoryRepository<I extends AudioItem> extends InMemoryRepository<I> implements AudioItemRepository<I> {

    public AudioItemInMemoryRepository() {
        super();
    }

    public AudioItemInMemoryRepository(Collection<I> entities) {
        super(entities);
    }

    @Override
    public AudioItemBuilder<I> create(Path path, String title, Duration duration, int bitRate) {
        requireNonNull(path);
        requireNonNull(title);
        requireNonNull(duration);

        return new ImmutableAudioItemBuilder<I>(path, title, duration, bitRate, LocalDateTime.now()) {
            @Override
            public I build() {
                var audioItem = super.build();
                add(audioItem);
                return audioItem;
            }
        };
    }
}

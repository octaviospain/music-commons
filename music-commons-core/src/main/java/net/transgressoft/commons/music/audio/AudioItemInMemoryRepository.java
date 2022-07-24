package net.transgressoft.commons.music.audio;

import net.transgressoft.commons.query.InMemoryRepository;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * @author Octavio Calleya
 */
public class AudioItemInMemoryRepository<I extends AudioItem> extends InMemoryRepository<I> implements AudioItemRepository<I> {

    protected AudioItemInMemoryRepository(Map<Integer, I> audioItemsById) {
        super(audioItemsById);
    }

    public AudioItemInMemoryRepository() {
        this(Collections.emptyList());
    }

    public AudioItemInMemoryRepository(Collection<I> audioItems) {
        super(audioItems);
    }

    @Override
    public AudioItemBuilder<I> create(Path path, String title, Duration duration, int bitRate) {
        requireNonNull(path);
        requireNonNull(title);
        requireNonNull(duration);

        return new ImmutableAudioItemBuilder<>(path, title, duration, bitRate, LocalDateTime.now()) {
            @Override
            public I build() {
                var audioItem = super.build();
                add(audioItem);
                return audioItem;
            }
        };
    }
}

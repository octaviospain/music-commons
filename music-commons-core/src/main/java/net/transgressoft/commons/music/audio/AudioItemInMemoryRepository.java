package net.transgressoft.commons.music.audio;

import net.transgressoft.commons.event.QueryEventDispatcher;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class AudioItemInMemoryRepository extends AudioItemInMemoryRepositoryBase<AudioItem> {

    public AudioItemInMemoryRepository(Map<Integer, AudioItem> audioItems) {
        super(audioItems);
    }

    public AudioItemInMemoryRepository(QueryEventDispatcher<AudioItem> eventDispatcher) {
        this(new HashMap<>(), eventDispatcher);
    }

    public AudioItemInMemoryRepository(Map<Integer, AudioItem> audioItems, QueryEventDispatcher<AudioItem> eventDispatcher) {
        super(audioItems, eventDispatcher);
    }

    @Override
    public AudioItemBuilder<AudioItem> create(Path path, String title, Duration duration, int bitRate) {
        requireNonNull(path);
        requireNonNull(title);
        requireNonNull(duration);

        return new ImmutableAudioItemBuilder(path, title, duration, bitRate, LocalDateTime.now()) {
            @Override
            public AudioItem build() {
                var audioItem = super.build();
                add(audioItem);
                return audioItem;
            }
        };
    }
}

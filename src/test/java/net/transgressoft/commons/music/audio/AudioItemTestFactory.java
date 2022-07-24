package net.transgressoft.commons.music.audio;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class AudioItemTestFactory {

    private int testCounter = 1;

    private Path getPath(int i) {
        return Paths.get("temp", "transgressoft", "test", getName(i));
    }

    private String getName(int i) {
        return "audioItem" + i;
    }

    private Duration getDuration(int i) {
        return Duration.ofSeconds(i);
    }

    public AudioItem createTestAudioItem() {
        int i = testCounter++;
        return ImmutableAudioItem.builder(getPath(i), getName(i), getDuration(i), 320)
                .build();
    }

    public List<AudioItem> createTestAudioItemsSet(int size) {
        var list = new ArrayList<AudioItem>();
        for (int i = 0; i < size; i++) {
            list.add(createTestAudioItem());
        }
        return list;
    }
}

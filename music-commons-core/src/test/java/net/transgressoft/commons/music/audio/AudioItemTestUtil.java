package net.transgressoft.commons.music.audio;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AudioItemTestUtil {

    AudioItemTestFactory audioItemTestFactory = new AudioItemTestFactory();

    protected AudioItem createTestAudioItem() {
        return audioItemTestFactory.createTestAudioItem();
    }

    protected AudioItem createTestAudioItem(String name) {
        return audioItemTestFactory.createTestAudioItem(name);
    }

    protected AudioItem createTestAudioItem(String name, Duration duration) {
        return audioItemTestFactory.createTestAudioItem(name, duration);
    }

    protected List<AudioItem> createTestAudioItemsSet(int size) {
        return audioItemTestFactory.createTestAudioItemsList(size);
    }

    protected Map<Integer, AudioItem> createTestAudioItemsMap(int size) {
        return createTestAudioItemsSet(size).stream().collect(Collectors.toMap(AudioItem::getId, Function.identity()));
    }
}

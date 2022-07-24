package net.transgressoft.commons.music.event;

import net.transgressoft.commons.music.audio.AudioItem;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Supplier;

public class DefaultMusicEventDispatcher implements MusicLibraryEventDispatcher<AudioItem> {

    @Override
    public void subscribe(Flow.Subscriber<? super Integer> subscriber) {

    }
}

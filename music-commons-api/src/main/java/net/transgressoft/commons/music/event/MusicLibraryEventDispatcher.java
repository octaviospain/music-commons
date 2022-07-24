package net.transgressoft.commons.music.event;

import net.transgressoft.commons.music.audio.AudioItem;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow.Publisher;
import java.util.function.Supplier;

public interface MusicLibraryEventDispatcher<I extends AudioItem> extends Publisher<Integer> {

    default CompletableFuture<I> onPlay(Supplier<I> runnable) {
        return CompletableFuture.supplyAsync(runnable);
    }

    default CompletableFuture<AudioItem> onPlayed(Supplier<AudioItem> runnable) {
        return CompletableFuture.supplyAsync(runnable)
                .thenApply(AudioItem::incrementPlayCount);
    }
}

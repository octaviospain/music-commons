package net.transgressoft.commons.music.audio;


import net.transgressoft.commons.query.QueryEvent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow.Publisher;
import java.util.function.Supplier;

public interface AudioItemEventDispatcher<I extends AudioItem> extends Publisher<QueryEvent<? extends I>> {

    default CompletableFuture<I> onPlay(Supplier<I> runnable) {
        return CompletableFuture.supplyAsync(runnable);
    }

    default CompletableFuture<AudioItem> onPlayed(Supplier<AudioItem> runnable) {
        return CompletableFuture.supplyAsync(runnable)
                .thenApply(AudioItem::incrementPlayCount);
    }
}

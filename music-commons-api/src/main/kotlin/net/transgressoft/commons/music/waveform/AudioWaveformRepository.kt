package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.event.CrudEvent
import net.transgressoft.commons.event.TransEventSubscriber
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.persistence.Repository
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher

interface AudioWaveformRepository<W : AudioWaveform, I : ReactiveAudioItem<I>> : Repository<Int, W> {

    val audioItemEventSubscriber: TransEventSubscriber<I, CrudEvent.Type, CrudEvent<Int, out I>>

    fun getOrCreateWaveformAsync(
        audioItem: I,
        width: Short,
        height: Short,
        dispatcher: CoroutineDispatcher
    ): CompletableFuture<W>

    fun getOrCreateWaveformAsync(
        audioItem: I,
        width: Short,
        height: Short,
        executor: Executor
    ): CompletableFuture<W> = getOrCreateWaveformAsync(audioItem, width, height, executor.asCoroutineDispatcher())

    fun getOrCreateWaveformAsync(
        audioItem: I,
        width: Short,
        height: Short
    ): CompletableFuture<W> = getOrCreateWaveformAsync(audioItem, width, height, Dispatchers.Default)

    fun removeByAudioItemIds(audioItemIds: Set<Int>) {
        audioItemIds.forEach { findById(it).ifPresent { waveform -> remove(waveform) } }
    }
}
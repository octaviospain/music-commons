package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.data.DataEvent
import net.transgressoft.commons.data.Repository
import net.transgressoft.commons.event.TransEventSubscriber
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import java.util.concurrent.CompletableFuture

interface AudioWaveformRepository<W : AudioWaveform, I : ReactiveAudioItem<I>> : Repository<Int, W> {

    val audioItemEventSubscriber: TransEventSubscriber<I, DataEvent<Int, out I>>

    fun getOrCreateWaveformAsync(audioItem: I, width: Short, height: Short): CompletableFuture<W>

    fun removeByAudioItemIds(audioItemIds: Set<Int>) {
        audioItemIds.forEach { findById(it).ifPresent { waveform -> remove(waveform) } }
    }
}

class AudioWaveformProcessingException : Exception {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
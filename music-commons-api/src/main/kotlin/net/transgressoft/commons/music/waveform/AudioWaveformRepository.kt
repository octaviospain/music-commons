package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.event.QueryEntitySubscriber
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.query.Repository
import java.util.concurrent.CompletableFuture

interface AudioWaveformRepository<W : AudioWaveform> : Repository<W> {

    val audioItemEventSubscriber: QueryEntitySubscriber<AudioItem>

    fun getOrCreateWaveformAsync(audioItem: AudioItem, width: Short, height: Short): CompletableFuture<W>

    fun removeByAudioItemIds(audioItemIds: List<Int>) {
        audioItemIds.forEach { findById(it).ifPresent { waveform -> remove(waveform) } }
    }
}

class AudioWaveformProcessingException : Exception {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
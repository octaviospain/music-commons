package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.query.InMemoryRepository
import java.util.*

open class AudioWaveformInMemoryRepository(entitiesById: MutableMap<Int, AudioWaveform> = mutableMapOf()) : InMemoryRepository<AudioWaveform>(entitiesById, null),
    AudioWaveformRepository<AudioWaveform> {

    @Throws(AudioWaveformProcessingException::class)
    override fun create(audioItem: AudioItem, width: Short, height: Short): AudioWaveform {
        Objects.requireNonNull(audioItem)
        val amplitudes = AudioWaveformExtractor().extractWaveform(audioItem.path, width.toInt(), height.toInt())
        val waveform = ImmutableAudioWaveform(audioItem.id, amplitudes, width.toInt(), height.toInt())
        add(waveform)
        return waveform
    }
}
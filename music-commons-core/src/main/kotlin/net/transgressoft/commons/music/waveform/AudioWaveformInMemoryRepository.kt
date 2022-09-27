package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.query.InMemoryRepository
import java.util.*

open class AudioWaveformInMemoryRepository(entitiesById: MutableMap<Int, AudioWaveform>) : InMemoryRepository<AudioWaveform>(entitiesById, null),
    AudioWaveformRepository<AudioWaveform> {

    companion object {
        private val emptyWaveforms: MutableMap<Pair<Short, Short>, Any> = mutableMapOf()

        fun <X : AudioWaveform> emptyWaveform(width: Short, height: Short): X {
            val dimensionPair = Pair(width, height)
            val fakeAmplitudes = FloatArray(width.toInt())
            Arrays.fill(fakeAmplitudes, 0.0f)

            return if (emptyWaveforms.contains(dimensionPair))
                emptyWaveforms[dimensionPair] as X
            else
                ImmutableAudioWaveform(-1, fakeAmplitudes, width.toInt(), height.toInt()) as X
        }
    }

    constructor() : this(mutableMapOf())

    @Throws(AudioWaveformProcessingException::class)
    override fun create(audioItem: AudioItem, width: Short, height: Short): AudioWaveform {
        Objects.requireNonNull(audioItem)
        val amplitudes = AudioWaveformExtractor().extractWaveform(audioItem.path, width.toInt(), height.toInt())
        val waveform = ImmutableAudioWaveform(audioItem.id, amplitudes, width.toInt(), height.toInt())
        add(waveform)
        return waveform
    }
}
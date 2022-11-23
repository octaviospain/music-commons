package net.transgressoft.commons.music.waveform

class AudioWaveformInMemoryRepository : AudioWaveformInMemoryRepositoryBase<AudioWaveformBase>() {

    override fun createWaveform(id: Int, amplitudes: FloatArray, width: Int, height: Int): AudioWaveformBase =
        ImmutableAudioWaveform(id, amplitudes, width, height)
}
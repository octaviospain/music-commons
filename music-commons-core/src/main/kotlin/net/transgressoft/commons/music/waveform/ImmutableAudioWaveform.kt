package net.transgressoft.commons.music.waveform

internal class ImmutableAudioWaveform(
    override val id: Int,
    override val amplitudes: FloatArray,
    override val width: Int,
    override val height: Int,
) : AudioWaveformBase(id, amplitudes, width, height)


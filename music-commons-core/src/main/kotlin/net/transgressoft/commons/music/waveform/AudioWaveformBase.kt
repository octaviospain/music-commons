package net.transgressoft.commons.music.waveform

import java.util.*

open class AudioWaveformBase(
    override val id: Int,
    override val amplitudes: FloatArray,
    override val width: Int,
    override val height: Int,
) : AudioWaveform {

    override val uniqueId: String
        get() {
            val joiner = StringJoiner("-")
            joiner.add(id.toString())
            joiner.add(width.toString())
            joiner.add(height.toString())
            joiner.add(amplitudes.size.toString())
            return joiner.toString()
        }

    override fun scale(width: Int, height: Int): AudioWaveform {
        throw UnsupportedOperationException("Not implemented")
        // TODO Do some math and figure out how to scale the amplitudes given the new width and height without processing again
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as AudioWaveformBase
        return width == that.width && height == that.height &&
                com.google.common.base.Objects.equal(amplitudes, that.amplitudes)
    }

    override fun hashCode(): Int {
        return com.google.common.base.Objects.hashCode(width, height, amplitudes)
    }
}
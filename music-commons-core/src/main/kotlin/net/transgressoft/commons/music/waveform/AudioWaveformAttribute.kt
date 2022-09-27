package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.query.Attribute
import kotlin.reflect.KClass

sealed class AudioWaveformAttribute<W : AudioWaveform, V : Any>(private val dataType: KClass<V>) : Attribute<W, V> {

    object WIDTH : AudioWaveformAttribute<AudioWaveform, Int>(Int::class), Attribute<AudioWaveform, Int>
    object HEIGHT : AudioWaveformAttribute<AudioWaveform, Int>(Int::class), Attribute<AudioWaveform, Int>
}
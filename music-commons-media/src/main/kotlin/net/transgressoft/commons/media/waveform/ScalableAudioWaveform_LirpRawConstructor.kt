@file:Suppress("ktlint:standard:filename")

/******************************************************************************
 * Copyright (C) 2026  Octavio Calleya Garcia                                 *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 * (at your option) any later version.                                        *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.     *
 ******************************************************************************/

package net.transgressoft.commons.media.waveform

import net.transgressoft.lirp.persistence.LirpRawConstructor
import java.nio.file.Path

/**
 * Co-located construction SPI for [ScalableAudioWaveform].
 *
 * Resolved at runtime via `Class.forName` on the entity's binary name plus the
 * `_LirpRawConstructor` suffix — the same convention lirp's loaders use. Living in the entity's
 * own module, it reaches the cache-bearing deserialization constructor that a persistence module
 * cannot call directly, restoring the cached display width and normalized amplitudes so a reloaded
 * waveform serves same-width amplitude requests without re-reading the audio file.
 *
 * The [construct] `params` map is keyed by constructor parameter name. Expected keys:
 * `id` ([Int]), `audioFilePath` ([Path]), `cachedWidth` ([Int]), `normalizedAmplitudes`
 * ([FloatArray]).
 */
@Suppress("ClassName")
internal class ScalableAudioWaveform_LirpRawConstructor : LirpRawConstructor<ScalableAudioWaveform> {
    override fun construct(params: Map<String, Any?>): ScalableAudioWaveform =
        ScalableAudioWaveform(
            params["id"] as Int,
            params["audioFilePath"] as Path,
            params["cachedWidth"] as Int,
            params["normalizedAmplitudes"] as FloatArray
        )
}
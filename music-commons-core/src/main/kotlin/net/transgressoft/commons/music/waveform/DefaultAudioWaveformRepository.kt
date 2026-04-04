/******************************************************************************
 * Copyright (C) 2025  Octavio Calleya Garcia                                 *
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

package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.audio.event.AudioItemEventSubscriber
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.CrudEvent.Type.DELETE
import net.transgressoft.lirp.event.LirpEventSubscriber
import net.transgressoft.lirp.persistence.Repository
import net.transgressoft.lirp.persistence.VolatileRepository
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.future.future

/**
 * Default implementation of [AudioWaveformRepository] managing waveform generation and caching.
 *
 * Creates waveforms on-demand from audio items and caches them for reuse. Automatically
 * removes waveforms when their corresponding audio items are deleted to maintain consistency.
 */
internal class DefaultAudioWaveformRepository<I: ReactiveAudioItem<I>>(
    private val repository: Repository<Int, AudioWaveform> = VolatileRepository("AudioWaveformRepository"),
    private val audioItemEventSubscriber: AudioItemEventSubscriber<I> = AudioItemEventSubscriber("AudioWaveformRepositorySubscriber")
): AudioWaveformRepository<AudioWaveform, I>,
    Repository<Int, AudioWaveform> by repository,
    LirpEventSubscriber<I, CrudEvent.Type, CrudEvent<Int, I>> by audioItemEventSubscriber {

    init {
        addOnNextEventAction(DELETE) { event ->
            removeByAudioItemIds(event.entities.keys)
        }
    }

    override fun getOrCreateWaveformAsync(
        audioItem: I,
        width: Short,
        height: Short,
        dispatcher: CoroutineDispatcher
    ): CompletableFuture<AudioWaveform> =
        findById(audioItem.id)
            .map { CompletableFuture.completedFuture(it) }
            .orElseGet {
                CoroutineScope(dispatcher).future {
                    async {
                        val audioWaveform = ScalableAudioWaveform(audioItem.id, audioItem.path)
                        add(audioWaveform)
                        audioWaveform
                    }.await()
                }
            }

    /**
     * Cancels the audio item event subscription used to synchronize waveform removal with audio item deletions.
     */
    override fun close() {
        audioItemEventSubscriber.cancelSubscription()
    }

    override fun hashCode() = repository.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DefaultAudioWaveformRepository<*>) return false
        if (repository != other.repository) return false
        return true
    }

    override fun toString() = "WaveformRepository(waveformsCount=${size()})"
}

/**
 * Creates an [AudioWaveformRepository] backed by [repository].
 *
 * Use this factory to construct a waveform repository without direct access to
 * the internal [DefaultAudioWaveformRepository] implementation class.
 */
fun <I : ReactiveAudioItem<I>> audioWaveformRepository(
    repository: Repository<Int, AudioWaveform> = VolatileRepository("AudioWaveformRepository")
): AudioWaveformRepository<AudioWaveform, I> = DefaultAudioWaveformRepository(repository)
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

import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.music.waveform.AudioWaveformRepository
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.CrudEvent.Type.DELETE
import net.transgressoft.lirp.event.LirpEventSubscriber
import net.transgressoft.lirp.event.LirpEventSubscriberBase
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
 *
 * The [onClose] callback is invoked when [close] is called, allowing the caller to cancel
 * the audio item event subscription that feeds this repository.
 */
internal class DefaultAudioWaveformRepository<I: ReactiveAudioItem<I>>(
    private val repository: Repository<Int, AudioWaveform> = VolatileRepository("AudioWaveformRepository"),
    subscriber: LirpEventSubscriberBase<I, CrudEvent.Type, CrudEvent<Int, I>>,
    private val onClose: () -> Unit = {}
): AudioWaveformRepository<AudioWaveform, I>,
    Repository<Int, AudioWaveform> by repository,
    LirpEventSubscriber<I, CrudEvent.Type, CrudEvent<Int, I>> by subscriber {

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
     * Closes the waveform repository: invokes [onClose] to cancel the audio item event
     * subscription and then closes the delegated [repository]. After this call any
     * mutating or querying operation on the repository will fail.
     */
    override fun close() {
        try {
            onClose()
        } finally {
            repository.close()
        }
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
 * Creates an [AudioWaveformRepository] backed by [repository] and synchronized with
 * audio item changes via [subscriber].
 *
 * The [subscriber] (typically an [AudioItemEventSubscriber] created in the core module)
 * receives CRUD events that this repository uses to maintain waveform-to-audio-item
 * consistency. The subscriber's lifecycle is managed by the caller — provide an [onClose]
 * callback to cancel the subscription when the repository is closed.
 */
fun <I : ReactiveAudioItem<I>> audioWaveformRepository(
    repository: Repository<Int, AudioWaveform> = VolatileRepository("AudioWaveformRepository"),
    subscriber: LirpEventSubscriberBase<I, CrudEvent.Type, CrudEvent<Int, I>>,
    onClose: () -> Unit = {}
): AudioWaveformRepository<AudioWaveform, I> = DefaultAudioWaveformRepository(repository, subscriber, onClose)
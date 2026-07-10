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
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.LirpEventSubscriber
import net.transgressoft.lirp.persistence.Repository
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * Repository for managing audio waveforms associated with audio items.
 *
 * Provides asynchronous methods to retrieve or create waveforms on demand.
 * @since 1.0
 */
public interface AudioWaveformRepository<W : AudioWaveform, I : ReactiveAudioItem<I>> :
    Repository<Int, W>, LirpEventSubscriber<I, CrudEvent.Type, CrudEvent<Int, I>> {

    /**
     * Retrieves an existing waveform or creates a new one asynchronously for the given audio item,
     * using the provided coroutine [dispatcher] for amplitude computation.
     *
     * @param audioItem the audio item whose waveform is requested
     * @param width desired waveform width in pixels
     * @param height desired waveform height in pixels
     * @param dispatcher coroutine dispatcher for the computation
     * @return a [CompletableFuture] that completes with the waveform once ready
     * @since 1.0
     */
    public fun getOrCreateWaveformAsync(
        audioItem: I,
        width: Short,
        height: Short,
        dispatcher: CoroutineDispatcher
    ): CompletableFuture<W>

    /**
     * Retrieves an existing waveform or creates a new one asynchronously for the given audio item,
     * using the provided Java [executor] as the coroutine dispatcher.
     *
     * @param audioItem the audio item whose waveform is requested
     * @param width desired waveform width in pixels
     * @param height desired waveform height in pixels
     * @param executor Java executor to back the coroutine dispatcher
     * @return a [CompletableFuture] that completes with the waveform once ready
     * @since 1.0
     */
    public fun getOrCreateWaveformAsync(
        audioItem: I,
        width: Short,
        height: Short,
        executor: Executor
    ): CompletableFuture<W> = getOrCreateWaveformAsync(audioItem, width, height, executor.asCoroutineDispatcher())

    /**
     * Retrieves an existing waveform or creates a new one asynchronously for the given audio item,
     * using [Dispatchers.Default] as the coroutine dispatcher.
     *
     * @param audioItem the audio item whose waveform is requested
     * @param width desired waveform width in pixels
     * @param height desired waveform height in pixels
     * @return a [CompletableFuture] that completes with the waveform once ready
     * @since 1.0
     */
    public fun getOrCreateWaveformAsync(
        audioItem: I,
        width: Short,
        height: Short
    ): CompletableFuture<W> = getOrCreateWaveformAsync(audioItem, width, height, Dispatchers.Default)

    /**
     * Removes waveforms associated with audio items whose IDs are in [audioItemIds].
     *
     * Silently ignores IDs for which no waveform exists.
     *
     * @param audioItemIds set of audio item IDs whose waveforms should be removed
     * @since 1.0
     */
    public fun removeByAudioItemIds(audioItemIds: Set<Int>) {
        audioItemIds.forEach { findById(it).ifPresent { waveform -> remove(waveform) } }
    }
}
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

import net.transgressoft.commons.event.CrudEvent
import net.transgressoft.commons.event.TransEventSubscriber
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.persistence.Repository
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * Repository for managing audio waveforms associated with audio items.
 *
 * Provides asynchronous methods to retrieve or create waveforms on demand.
 */
interface AudioWaveformRepository<W : AudioWaveform, I : ReactiveAudioItem<I>> : Repository<Int, W> {

    val audioItemEventSubscriber: TransEventSubscriber<I, CrudEvent.Type, CrudEvent<Int, I>>

    /**
     * Retrieves an existing waveform or creates a new one asynchronously for the given audio item.
     */
    fun getOrCreateWaveformAsync(
        audioItem: I,
        width: Short,
        height: Short,
        dispatcher: CoroutineDispatcher
    ): CompletableFuture<W>

    fun getOrCreateWaveformAsync(
        audioItem: I,
        width: Short,
        height: Short,
        executor: Executor
    ): CompletableFuture<W> = getOrCreateWaveformAsync(audioItem, width, height, executor.asCoroutineDispatcher())

    fun getOrCreateWaveformAsync(
        audioItem: I,
        width: Short,
        height: Short
    ): CompletableFuture<W> = getOrCreateWaveformAsync(audioItem, width, height, Dispatchers.Default)

    fun removeByAudioItemIds(audioItemIds: Set<Int>) {
        audioItemIds.forEach { findById(it).ifPresent { waveform -> remove(waveform) } }
    }
}
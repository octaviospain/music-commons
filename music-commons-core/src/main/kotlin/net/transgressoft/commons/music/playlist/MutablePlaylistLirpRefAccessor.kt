@file:Suppress("ktlint:standard:filename")

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

package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.lirp.entity.CascadeAction
import net.transgressoft.lirp.persistence.AggregateCollectionRef
import net.transgressoft.lirp.persistence.CollectionRefEntry
import net.transgressoft.lirp.persistence.LirpRefAccessor
import net.transgressoft.lirp.persistence.RefEntry

/**
 * Manually-written aggregate reference accessor for [MutablePlaylist].
 *
 * This class replaces the KSP-generated `_LirpRefAccessor` that would normally be produced
 * by the `@Aggregate` annotation processor. A manual implementation is required because the
 * KSP processor generates a `public` accessor that cannot reference `internal` entities,
 * causing a visibility error at compile time.
 *
 * The class name follows the lirp convention `{EntityJvmName}_LirpRefAccessor` so that
 * [net.transgressoft.lirp.persistence.RegistryBase.discoverRefs] locates it via
 * [Class.forName] at runtime, wiring both the `audioItems` and `playlists`
 * delegates for lazy resolution from [net.transgressoft.lirp.persistence.LirpContext].
 */
@Suppress("ClassName")
@SuppressWarnings("kotlin:S101")
internal class MutablePlaylist_LirpRefAccessor : LirpRefAccessor<MutableAudioPlaylist> {

    override val entries: List<RefEntry<*, MutableAudioPlaylist>> = emptyList()

    @Suppress("UNCHECKED_CAST")
    override val collectionEntries: List<CollectionRefEntry<*, MutableAudioPlaylist>> =
        listOf(
            CollectionRefEntry(
                refName = "audioItems",
                idsGetter = { playlist ->
                    (playlist.audioItems as AggregateCollectionRef<*, *>).referenceIds
                },
                delegateGetter = { playlist ->
                    playlist.audioItems as AggregateCollectionRef<*, *>
                },
                referencedClass = AudioItem::class.java,
                cascadeAction = CascadeAction.NONE,
                isOrdered = true
            ),
            CollectionRefEntry(
                refName = "playlists",
                idsGetter = { playlist ->
                    (playlist.playlists as AggregateCollectionRef<*, *>).referenceIds
                },
                delegateGetter = { playlist ->
                    playlist.playlists as AggregateCollectionRef<*, *>
                },
                referencedClass = MutableAudioPlaylist::class.java,
                cascadeAction = CascadeAction.NONE,
                isOrdered = false
            )
        )

    override fun cancelAllBubbleUp(entity: MutableAudioPlaylist) {
        entries.forEach { entry -> entry.delegateGetter(entity).cancelBubbleUp() }
    }
}
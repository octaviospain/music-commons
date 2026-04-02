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
import net.transgressoft.lirp.persistence.AbstractAggregateCollectionRefDelegate
import net.transgressoft.lirp.persistence.CollectionRefEntry
import net.transgressoft.lirp.persistence.LirpRefAccessor
import net.transgressoft.lirp.persistence.ReactiveEntityCollectionReference
import net.transgressoft.lirp.persistence.RefEntry

/**
 * Manually-written aggregate reference accessor for [DefaultPlaylistHierarchy]'s
 * inner playlist class.
 *
 * This class replaces the KSP-generated `_LirpRefAccessor` that would normally be produced
 * by the `@Aggregate` annotation processor. A manual implementation is required because the
 * KSP processor generates a `public` accessor that cannot reference `private inner class`
 * entities, and making the inner class less restrictive would break Kotlin's visibility rules
 * for generic hierarchies.
 *
 * The class name follows the lirp convention `{EntityJvmName}_LirpRefAccessor` so that
 * [net.transgressoft.lirp.persistence.RegistryBase.discoverRefs] locates it via
 * [Class.forName] at runtime, wiring the `audioItemsAggregate` delegate for lazy resolution
 * of audio item IDs from [net.transgressoft.lirp.persistence.LirpContext].
 */
@Suppress("ClassName")
internal class `DefaultPlaylistHierarchy$MutablePlaylist_LirpRefAccessor` :
    LirpRefAccessor<MutableAudioPlaylist> {

    override val entries: List<RefEntry<*, MutableAudioPlaylist>> = emptyList()

    @Suppress("UNCHECKED_CAST")
    override val collectionEntries: List<CollectionRefEntry<*, MutableAudioPlaylist>> =
        listOf(
            CollectionRefEntry(
                refName = "audioItemsAggregate",
                idsGetter = { playlist ->
                    val delegate = getAggregateDelegate(playlist)
                    delegate?.referenceIds ?: emptyList()
                },
                delegateGetter = { playlist ->
                    getAggregateDelegate(playlist) as ReactiveEntityCollectionReference<*, *>?
                        ?: throw IllegalStateException("audioItemsAggregate delegate not found on $playlist")
                },
                referencedClass = AudioItem::class.java,
                cascadeAction = CascadeAction.NONE,
                isOrdered = true
            )
        )

    override fun cancelAllBubbleUp(entity: MutableAudioPlaylist) {
        entries.forEach { entry -> entry.delegateGetter(entity).cancelBubbleUp() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getAggregateDelegate(playlist: MutableAudioPlaylist): AbstractAggregateCollectionRefDelegate<Int, AudioItem>? =
        try {
            val field = playlist.javaClass.getDeclaredField("audioItemsAggregate\$delegate")
            field.isAccessible = true
            field.get(playlist) as? AbstractAggregateCollectionRefDelegate<Int, AudioItem>
        } catch (_: NoSuchFieldException) {
            null
        }
}
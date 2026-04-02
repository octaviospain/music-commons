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

package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent.Type.PLAYED
import net.transgressoft.lirp.event.StandardCrudEvent.Update
import net.transgressoft.lirp.persistence.LirpRepository
import net.transgressoft.lirp.persistence.RegistryBase
import net.transgressoft.lirp.persistence.Repository
import mu.KotlinLogging
import java.nio.file.Path
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Default implementation of [AudioLibrary] for managing [AudioItem] instances.
 *
 * Handles play count tracking by subscribing to player events and incrementing
 * the play count whenever an audio item is played. Provides factory methods to
 * create audio items from file paths by reading their metadata.
 *
 * Registers its backing repository in [net.transgressoft.lirp.persistence.LirpContext] on construction
 * via [RegistryBase.registerRepository], enabling playlist hierarchies to resolve audio item references
 * lazily through the context. Deregisters on [close] to support repeated construction within the same JVM.
 */
@LirpRepository
class DefaultAudioLibrary(repository: Repository<Int, AudioItem>) :
    AudioLibraryBase<AudioItem, ArtistCatalog<AudioItem>>(repository, DefaultArtistCatalogRegistry()) {
    private val logger = KotlinLogging.logger {}

    init {
        deregisterExistingAudioItemRegistration()
        RegistryBase.registerRepository(AudioItem::class.java, repository)
        playerSubscriber.addOnNextEventAction(PLAYED) { event ->
            val audioItem = event.audioItem
            logger.info { "Audio item with id ${audioItem.id} was played" }
            if (audioItem is MutableAudioItem) {
                val audioItemClone = audioItem.clone()
                audioItem.incrementPlayCount()
                repository.emitAsync(Update(audioItem, audioItemClone))
                logger.debug { "Play count for audio item ${audioItem.id} increased to ${audioItem.playCount}" }
            }
        }
    }

    override fun createFromFile(audioItemPath: Path): AudioItem =
        MutableAudioItem(audioItemPath, newId())
            .also { audioItem ->
                add(audioItem)
                logger.debug { "New AudioItem was created from file $audioItemPath with id ${audioItem.id}" }
            }

    override fun close() {
        super.close()
        deregisterFromLirpContext(repository)
    }

    private fun deregisterExistingAudioItemRegistration() {
        try {
            val context = getContext(repository) ?: return
            val registryForMethod =
                context.javaClass.methods
                    .firstOrNull { it.name.startsWith("registryFor") && it.parameterCount == 1 && it.parameterTypes[0] == Class::class.java }
                    ?: return
            val existing = registryForMethod.invoke(context, AudioItem::class.java) ?: return
            if (existing !== repository) {
                val registryInterface = Class.forName("net.transgressoft.lirp.persistence.Registry")
                val deregisterMethod =
                    context.javaClass.methods
                        .firstOrNull { it.name.startsWith("deregister") && it.parameterCount == 1 && registryInterface.isAssignableFrom(it.parameterTypes[0]) }
                        ?: return
                deregisterMethod.invoke(context, existing)
            }
        } catch (_: Exception) {
            // Best-effort; failure is non-critical since registerRepository will detect conflicts
        }
    }

    private fun deregisterFromLirpContext(repo: Any) {
        try {
            val context = getContext(repo) ?: return
            val registryInterface = Class.forName("net.transgressoft.lirp.persistence.Registry")
            val deregisterMethod =
                context.javaClass.methods
                    .firstOrNull { it.name.startsWith("deregister") && it.parameterCount == 1 && registryInterface.isAssignableFrom(it.parameterTypes[0]) }
                    ?: return
            deregisterMethod.invoke(context, repo)
        } catch (_: Exception) {
            // Deregistration is best-effort; failure does not impact lifecycle semantics
        }
    }

    private fun getContext(repo: Any): Any? {
        return try {
            val getContextMethod =
                repo.javaClass.methods
                    .firstOrNull { it.name.startsWith("getContext") && it.parameterCount == 0 }
                    ?: return null
            getContextMethod.invoke(repo)
        } catch (_: Exception) {
            null
        }
    }

    override fun toString() = "AudioItemJsonRepository(audioItemsCount=${size()})"
}

@get:JvmName("audioItemSerializerModule")
internal val audioItemSerializerModule =
    SerializersModule {
        polymorphic(Artist::class) {
            subclass(ImmutableArtist.serializer())
        }
        polymorphic(Album::class) {
            subclass(ImmutableAlbum.serializer())
        }
        polymorphic(Label::class) {
            subclass(ImmutableLabel.serializer())
        }
    }
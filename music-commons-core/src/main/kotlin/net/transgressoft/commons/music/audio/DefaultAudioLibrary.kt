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
import net.transgressoft.commons.util.InvalidAudioFilePathException
import net.transgressoft.lirp.event.StandardCrudEvent.Update
import net.transgressoft.lirp.persistence.RegistryBase
import net.transgressoft.lirp.persistence.Repository
import mu.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path

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
internal class DefaultAudioLibrary
    @JvmOverloads
    constructor(
        repository: Repository<Int, AudioItem>,
        metadataIO: AudioMetadataIO = JAudioTaggerMetadataIO()
    ) : AudioLibraryBase<AudioItem, ArtistCatalog<AudioItem>>(repository, DefaultArtistCatalogRegistry(repository), metadataIO),
        AudioLibrary {
        private val logger = KotlinLogging.logger {}

        init {
            RegistryBase.deregisterRepository(AudioItem::class.java)
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

        init {
            // Wire the metadataIO back-ref onto items already hydrated from the repository.
            // AudioLibraryBase.init iterates repository.forEach directly without routing through add(),
            // so this loop is required in addition to the add() override to cover the hydration path.
            repository.forEach { item ->
                if (item is MutableAudioItem) item.metadataIO = metadataIO
            }
        }

        override fun add(entity: AudioItem): Boolean {
            if (entity is MutableAudioItem) entity.metadataIO = metadataIO
            return super.add(entity)
        }

        override fun createFromFile(audioItemPath: Path): AudioItem {
            if (!Files.exists(audioItemPath)) {
                throw InvalidAudioFilePathException("File '${audioItemPath.toAbsolutePath()}' does not exist")
            }
            if (!Files.isRegularFile(audioItemPath)) {
                throw InvalidAudioFilePathException("Path '${audioItemPath.toAbsolutePath()}' is not a regular file")
            }
            if (!Files.isReadable(audioItemPath)) {
                throw InvalidAudioFilePathException("File '${audioItemPath.toAbsolutePath()}' is not readable")
            }
            val tag = metadataIO.readMetadata(audioItemPath)
            return MutableAudioItem(audioItemPath, newId(), tag).also { audioItem ->
                add(audioItem)
                logger.debug { "New AudioItem was created from file $audioItemPath with id ${audioItem.id}" }
            }
        }

        override fun close() {
            super.close()
            RegistryBase.deregisterRepository(AudioItem::class.java)
        }

        override fun toString() = "AudioItemJsonRepository(audioItemsCount=${size()})"
    }
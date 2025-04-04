package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.event.CrudEvent.Type.CREATE
import net.transgressoft.commons.event.CrudEvent.Type.DELETE
import net.transgressoft.commons.event.CrudEvent.Type.UPDATE
import net.transgressoft.commons.music.audio.AudioItemJsonRepositoryBase
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent.Type.PLAYED
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyListProperty
import javafx.beans.property.SimpleListProperty
import javafx.collections.FXCollections
import javafx.collections.MapChangeListener
import javafx.collections.ObservableMap
import mu.KotlinLogging
import java.io.File
import java.nio.file.Path
import kotlinx.serialization.modules.SerializersModule

class ObservableAudioItemJsonRepository(
    name: String,
    file: File
): AudioItemJsonRepositoryBase<ObservableAudioItem>(
        name,
        file,
        ObservableAudioItemSerializer,
        SerializersModule {
            include(observableAudioItemSerializerModule)
        }
    ) {
    private val logger = KotlinLogging.logger {}

    private val observableAudioItemMap: ObservableMap<Int, ObservableAudioItem> = FXCollections.observableHashMap()

    val audioItemsProperty: ReadOnlyListProperty<ObservableAudioItem> =
        SimpleListProperty(this, "observable audio item entries", FXCollections.observableArrayList())

    init {
        // Subscribe to the events of itself in order to update the observable map
        subscribe(CREATE) { event ->
            synchronized(observableAudioItemMap) {
                observableAudioItemMap.putAll(event.entities)
            }
        }
        subscribe(UPDATE) { event ->
            synchronized(observableAudioItemMap) {
                observableAudioItemMap.putAll(event.entities)
            }
        }
        subscribe(DELETE) { event ->
            synchronized(observableAudioItemMap) {
                event.entities.forEach {
                    observableAudioItemMap.remove(it.key)
                }
            }
        }

        observableAudioItemMap.addListener(
            MapChangeListener { change ->
                change?.valueRemoved?.let { removed ->
                    audioItemsProperty.removeIf { it.id == removed.id }
                }
                change?.valueAdded?.let {
                    audioItemsProperty.add(it)
                }
            }
        )

        // Add all existing audio items to the observable map on initialization
        runForAll { observableAudioItemMap.put(it.id, it) }

        playerSubscriber.addOnNextEventAction(PLAYED) { event ->
            val audioItem = event.entities.values.first()
            if (audioItem is FXAudioItem) {
                val audioItemClone = audioItem.clone()
                audioItem.incrementPlayCount()
                putUpdateEvent(audioItem, audioItemClone)
                logger.debug { "Play count of audio item with id ${audioItem.id} increased to ${audioItem.playCount}" }
            }
        }
    }

    override fun clear() {
        super.clear()
        observableAudioItemMap.clear()
    }

    override fun createFromFile(audioItemPath: Path): FXAudioItem =
        FXAudioItem(audioItemPath, newId())
            .also { fxAudioItem ->
                add(fxAudioItem)
                logger.debug { "New ObservableAudioItem was created from file $audioItemPath with id ${fxAudioItem.id}" }
            }

    fun emptyLibraryProperty(): ReadOnlyBooleanProperty = audioItemsProperty.emptyProperty()

    override fun toString() = "ObservableAudioItemJsonRepository(audioItemsCount=${entitiesById.size})"
}
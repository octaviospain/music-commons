package net.transgressoft.commons.fx.music.audio

import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlySetProperty
import javafx.beans.property.SimpleSetProperty
import javafx.collections.FXCollections
import javafx.collections.MapChangeListener
import javafx.collections.ObservableMap
import kotlinx.serialization.modules.SerializersModule
import mu.KotlinLogging
import net.transgressoft.commons.data.StandardCrudEvent.Type.*
import net.transgressoft.commons.music.audio.AudioItemRepositoryBase
import net.transgressoft.commons.music.audio.event.AudioItemEventSubscriber
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent.Type.*
import java.io.File
import java.nio.file.Path
import java.util.Map.*

class ObservableAudioItemJsonRepository(
    name: String,
    file: File
): AudioItemRepositoryBase<ObservableAudioItem>(
        name,
        file,
        ObservableAudioItemSerializer,
        SerializersModule {
            include(observableAudioItemSerializerModule)
    }
) {
    private val logger = KotlinLogging.logger {}

    private val observableAudioItemMap: ObservableMap<Int, ObservableAudioItem> = FXCollections.observableHashMap()

    val setProperty: ReadOnlySetProperty<Map.Entry<Int, ObservableAudioItem>> =
        SimpleSetProperty(this, "observable audio item entries", FXCollections.observableSet())

    init {
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

    private val internalAudioItemChangesSubscriber =
        AudioItemEventSubscriber<ObservableAudioItem>("InternalAudioItemSubscriber").apply {
            addOnNextEventAction(CREATE, UPDATE) { event ->
                synchronized(observableAudioItemMap) {
                    observableAudioItemMap.putAll(event.entities)
                }
            }
            addOnNextEventAction(DELETE) { event ->
                synchronized(observableAudioItemMap) {
                    event.entities.forEach { observableAudioItemMap.remove(it.key) }
            }
            }
        }

    init {
        subscribe(internalAudioItemChangesSubscriber)
        observableAudioItemMap.addListener(
            MapChangeListener { change ->
                change?.valueRemoved?.let { removed ->
                    setProperty.removeIf { it.key == removed.id }
                }
                change?.valueAdded?.let {
                    setProperty.add(entry(it.id, it))
                }
            },
        )
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

    fun emptyLibraryProperty(): ReadOnlyBooleanProperty = setProperty.emptyProperty()

    override fun toString() = "ObservableAudioItemJsonRepository(name=$name, audioItemsCount=${entitiesById.size})"
}

package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.event.CrudEvent.Type.CREATE
import net.transgressoft.commons.event.CrudEvent.Type.DELETE
import net.transgressoft.commons.event.CrudEvent.Type.UPDATE
import net.transgressoft.commons.event.StandardCrudEvent.Update
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioLibraryBase
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent.Type.PLAYED
import net.transgressoft.commons.persistence.Repository
import javafx.application.Platform
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyListProperty
import javafx.beans.property.SimpleListProperty
import javafx.collections.FXCollections
import javafx.collections.MapChangeListener
import mu.KotlinLogging
import java.nio.file.Path

class ObservableAudioLibrary(repository: Repository<Int, ObservableAudioItem>): AudioLibraryBase<ObservableAudioItem>(repository) {
    private val logger = KotlinLogging.logger {}

    private val observableAudioItemMap =
        FXCollections.observableHashMap<Int, ObservableAudioItem>().apply {
            addListener(
                MapChangeListener { change ->
                    change?.valueRemoved?.let { removed ->
                        audioItemsProperty.removeIf { it.id == removed.id }
                    }
                    change?.valueAdded?.let {
                        audioItemsProperty.add(it)
                    }
                }
            )
        }

    // Subscribe to the events of itself to update the observable properties
    private val internalSubscription =
        subscribe(CREATE, UPDATE, DELETE) { event ->
            synchronized(observableAudioItemMap) {
                if (event.isDelete()) {
                    event.entities.forEach {
                        observableAudioItemMap.remove(it.key)
                        artistsProperty.remove(it.value.artist)
                    }
                } else {
                    observableAudioItemMap.putAll(event.entities)
                    event.entities.values
                        .map(ObservableAudioItem::artistsInvolved)
                        .flatten()
                        .filterNot(artistsProperty::contains)
                        .forEach(artistsProperty::add)
                }
            }
        }

    @get:JvmName("audioItemsProperty")
    val audioItemsProperty: ReadOnlyListProperty<ObservableAudioItem> =
        SimpleListProperty(this, "observable audio items", FXCollections.observableArrayList())

    @get:JvmName("emptyLibraryProperty")
    val emptyLibraryProperty: ReadOnlyBooleanProperty = audioItemsProperty.emptyProperty()

    @get:JvmName("artistsProperty")
    val artistsProperty: ReadOnlyListProperty<Artist> =
        SimpleListProperty(this, "artists", FXCollections.observableArrayList())

    init {
        // Add all existing audio items to the observable collections on initialization
        runForAll {
            observableAudioItemMap.put(it.id, it)
            Platform.runLater { artistsProperty.addAll(it.artistsInvolved) }
        }

        // Subscribe to the player events to update the play count
        playerSubscriber.addOnNextEventAction(PLAYED) { event ->
            val audioItem = event.entities.values.first()
            if (audioItem is FXAudioItem) {
                val audioItemClone = audioItem.clone()
                audioItem.incrementPlayCount()
                repository.emitAsync(Update(audioItem, audioItemClone))
                logger.debug { "Play count of audio item with id ${audioItem.id} increased to ${audioItem.playCount}" }
            }
        }
    }

    override fun clear() {
        super.clear()
        observableAudioItemMap.clear()
        internalSubscription.cancel()
    }

    override fun createFromFile(audioItemPath: Path): FXAudioItem =
        FXAudioItem(audioItemPath, newId())
            .also { fxAudioItem ->
                add(fxAudioItem)
                logger.debug { "New ObservableAudioItem was created from file $audioItemPath with id ${fxAudioItem.id}" }
            }

    override fun toString() = "ObservableAudioItemJsonRepository(audioItemsCount=${size()})"
}
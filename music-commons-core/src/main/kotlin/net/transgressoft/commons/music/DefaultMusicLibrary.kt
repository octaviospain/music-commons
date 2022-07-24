package net.transgressoft.commons.music

import com.google.common.collect.Sets
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemEventType.Type.PLAYED
import net.transgressoft.commons.music.audio.AudioItemRepository
import net.transgressoft.commons.music.playlist.AudioPlaylist
import net.transgressoft.commons.music.playlist.AudioPlaylistDirectory
import net.transgressoft.commons.music.playlist.AudioPlaylistRepository
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.music.waveform.AudioWaveformProcessingException
import net.transgressoft.commons.music.waveform.ImmutableAudioWaveform
import net.transgressoft.commons.query.EntityEvent
import net.transgressoft.commons.query.QueryEntityEvent.Type.*
import net.transgressoft.commons.query.Repository
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Flow.Subscription

typealias StandardMusicLibrary = DefaultMusicLibrary<AudioItem, AudioPlaylist<AudioItem>, AudioPlaylistDirectory<AudioItem>, AudioWaveform>

class DefaultMusicLibrary<I : AudioItem, P : AudioPlaylist<I>, D : AudioPlaylistDirectory<I>, W : AudioWaveform>(
    private val audioItemRepository: AudioItemRepository<I>,
    private val audioPlaylistRepository: AudioPlaylistRepository<I, P, D>,
    private val waveformRepository: Repository<W>,
) : MusicLibrary<I, P, D> {

    companion object {
        private val LOG = LoggerFactory.getLogger(DefaultMusicLibrary::class.java)
    }

    private val artists: MutableSet<String> = HashSet()
    private val playCounts: MutableMap<Int, Short> = HashMap()

    private var audioItemSubscription: Subscription? = null

    init {
        audioItemRepository.iterator().forEachRemaining { audioItem: I -> artists.addAll(audioItem.artistsInvolved()) }
    }

    override fun onSubscribe(subscription: Subscription) {
        audioItemSubscription = subscription
        LOG.info("MusicLibrary subscribed to AudioItem events")
    }

    override fun onNext(item: EntityEvent<out I>) {
        when (item.type) {
            PLAYED -> handlePlayedEvent(item)
            CREATE -> handleCreateEvent(item)
            READ -> handleReadEvent(item)
            UPDATE -> handleUpdateEvent(item)
            DELETE -> handleDeleteEvent(item)
        }
    }

    private fun getEventArtists(event: EntityEvent<out I>): Set<String> = event.entities.flatMap { it.artistsInvolved() }.toSet()

    private fun handlePlayedEvent(event: EntityEvent<out I>) {
        event.entities.forEach {
            playCounts[it.id] = audioItemPlayCount(it).plus(1).toShort()
        }
    }

    private fun handleCreateEvent(event: EntityEvent<out I>) {
        val eventArtists = getEventArtists(event)
        val addedArtists = Sets.difference(eventArtists, artists)
        if (addedArtists.isNotEmpty()) {
            artists += addedArtists
            LOG.debug("Artists added: {}", addedArtists)
        }
    }

    private fun handleReadEvent(event: EntityEvent<out I>) {
        LOG.debug("{} were read", event.entities)
    }

    private fun handleUpdateEvent(event: EntityEvent<out I>) {
        val eventArtists = getEventArtists(event)
        eventArtists.stream()
            .filter { !audioItemRepository.containsAudioItemWithArtist(it) }
            .forEach(artists::remove)

        val addedArtists = Sets.difference(eventArtists, artists)
        if (addedArtists.isNotEmpty()) {
            artists += addedArtists
            LOG.debug("{} new artists were added", addedArtists)
        }
    }

    private fun handleDeleteEvent(event: EntityEvent<out I>) {
        getEventArtists(event).stream()
            .filter { !audioItemRepository.containsAudioItemWithArtist(it) }
            .forEach(artists::remove)
    }

    override fun onError(throwable: Throwable) {
        audioItemSubscription = null;
        LOG.error("Exception while subscribed to AudioItemEvents", throwable)
    }

    override fun onComplete() {
        audioItemSubscription = null
    }

    override fun audioItemPlayCount(audioItem: I): Short = playCounts.getOrDefault(audioItem.id, 0)

    override fun artists(): Set<String> {
        return artists
    }

    override fun deleteAudioItems(audioItems: Set<I>) {
        audioItemRepository.removeAll(audioItems)
        audioPlaylistRepository.removeAudioItems(audioItems)
        audioItems.stream()
            .map(AudioItem::id)
            .map { id: Int? -> waveformRepository.findById(id) }
            .filter { obj: Optional<W> -> obj.isPresent }
            .forEach { waveform: Optional<W> -> waveformRepository.remove(waveform.get()) }
    }

    override fun addAudioItemsToPlaylist(audioItems: Collection<I>, playlist: P) {
        audioPlaylistRepository.addAudioItemsToPlaylist(audioItems, playlist)
    }

    override fun removeAudioItemsFromPlaylist(audioItems: Collection<I>, playlist: P) {
        audioPlaylistRepository.removeAudioItemsFromPlaylist(audioItems, playlist)
    }

    override fun movePlaylist(playlist: P, playlistDirectory: D) {
        audioPlaylistRepository.movePlaylist(playlist, playlistDirectory)
    }

    override fun getOrCreateWaveformAsync(audioItem: I, width: Short, height: Short): CompletableFuture<AudioWaveform> {
        return waveformRepository.findById(audioItem!!.id)
            .map<CompletableFuture<AudioWaveform>> { value: W -> CompletableFuture.completedFuture(value) }
            .orElseGet {
                CompletableFuture.supplyAsync {
                    try {
                        return@supplyAsync ImmutableAudioWaveform.create(audioItem.id, audioItem.path(), width.toInt(), height.toInt())
                    } catch (exception: AudioWaveformProcessingException) {
                        throw CompletionException(exception)
                    }
                }
            }
    }
}
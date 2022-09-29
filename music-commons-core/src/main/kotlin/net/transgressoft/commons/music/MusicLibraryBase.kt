package net.transgressoft.commons.music

import com.google.common.collect.Sets
import net.transgressoft.commons.event.DefaultQueryEntitySubscriber
import net.transgressoft.commons.event.EntityEvent
import net.transgressoft.commons.event.QueryEntityEvent.Type.*
import net.transgressoft.commons.event.QueryEntitySubscriber
import net.transgressoft.commons.event.QueryEventDispatcher
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemEventType.Type.PLAYED
import net.transgressoft.commons.music.audio.AudioItemRepository
import net.transgressoft.commons.music.playlist.AudioPlaylist
import net.transgressoft.commons.music.playlist.AudioPlaylistDirectory
import net.transgressoft.commons.music.playlist.AudioPlaylistRepository
import net.transgressoft.commons.music.waveform.*
import net.transgressoft.commons.music.waveform.ImmutableAudioWaveform
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Flow.Subscription
import kotlin.collections.HashMap
import kotlin.collections.HashSet

abstract class MusicLibraryBase<I : AudioItem, P : AudioPlaylist<I>, D : AudioPlaylistDirectory<I, P>, W : AudioWaveform>(
    final override val audioItemRepository: AudioItemRepository<I>,
    final override val audioPlaylistRepository: AudioPlaylistRepository<I, P, D>,
    final override val audioWaveformRepository: AudioWaveformRepository<W>,
    audioItemEventDispatcher: QueryEventDispatcher<I>
) : MusicLibrary<I, P, D, W> {

    private val log = LoggerFactory.getLogger(MusicLibraryBase::class.java)

    override val audioItemSubscriber: QueryEntitySubscriber<I> = DefaultAudioItemSubscriber()

    private val artists: MutableSet<String> = HashSet()
    private val playCounts: MutableMap<Int, Short> = HashMap()

    init {
        audioItemRepository.iterator().forEachRemaining { audioItem: I -> artists.addAll(audioItem.artistsInvolved) }
        audioItemEventDispatcher.subscribe(audioItemSubscriber)
    }

    override fun artists(): Set<String> = artists

    override fun deleteAudioItems(audioItems: Set<I>) {
        audioItemRepository.removeAll(audioItems)
        audioPlaylistRepository.removeAudioItems(audioItems)
        audioItems.stream()
            .map(AudioItem::id)
            .map { audioWaveformRepository.findById(it) }
            .filter { it.isPresent }
            .forEach { audioWaveformRepository.remove(it.get()) }
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

    override fun getOrCreateWaveformAsync(audioItem: I, width: Short, height: Short): CompletableFuture<W> {
        return audioWaveformRepository.findById(audioItem.id)
            .map<CompletableFuture<W>> { CompletableFuture.completedFuture(it) }
            .orElseGet {
                CompletableFuture.supplyAsync {
                    try {
                        return@supplyAsync audioWaveformRepository.create(audioItem, width, height)
                    } catch (exception: AudioWaveformProcessingException) {
                        return@supplyAsync emptyWaveform(width, height)
                    }
                }
            }
    }

    private val emptyWaveforms: MutableMap<Pair<Short, Short>, Any> = mutableMapOf()

    private fun emptyWaveform(width: Short, height: Short): W {
        val dimensionPair = Pair(width, height)
        val fakeAmplitudes = FloatArray(width.toInt())
        Arrays.fill(fakeAmplitudes, 0.0f)

        return if (emptyWaveforms.contains(dimensionPair))
            emptyWaveforms[dimensionPair] as W
        else
            ImmutableAudioWaveform(-1, fakeAmplitudes, width.toInt(), height.toInt()) as W
    }

    inner class DefaultAudioItemSubscriber : QueryEntitySubscriber<I>, DefaultQueryEntitySubscriber<I>() {
        private var audioItemSubscription: Subscription? = null

        init {
            addOnSubscribeEventAction { subscription ->
                audioItemSubscription = subscription
                log.info("MusicLibrary subscribed to AudioItem events")
            }
            addOnNextEventAction(PLAYED) { event ->
                event.entities.forEach {
                    playCounts[it.id] = playCounts.getOrDefault(it.id, 0).plus(1).toShort()
                }
            }
            addOnNextEventAction(CREATE) { event ->
                val eventArtists = getEventArtists(event)
                val addedArtists = Sets.difference(eventArtists, artists)
                if (addedArtists.isNotEmpty()) {
                    artists += addedArtists
                    log.debug("Artists added: {}", addedArtists)
                }
            }
            addOnNextEventAction(READ) { event ->
                log.debug("{} were read", event.entities)
            }
            addOnNextEventAction(UPDATE) { event ->
                val eventArtists = getEventArtists(event)
                eventArtists.stream()
                    .filter { !audioItemRepository.containsAudioItemWithArtist(it) }
                    .forEach(artists::remove)

                val addedArtists = Sets.difference(eventArtists, artists)
                if (addedArtists.isNotEmpty()) {
                    artists += addedArtists
                    log.debug("{} new artists were added", addedArtists)
                }
            }
            addOnNextEventAction(DELETE) { event ->
                getEventArtists(event).stream()
                    .filter { !audioItemRepository.containsAudioItemWithArtist(it) }
                    .forEach(artists::remove)
            }
            addOnErrorEventAction { throwable ->
                audioItemSubscription?.cancel()
                audioItemSubscription = null
                log.error("Exception while subscribed to AudioItemEvents", throwable)
            }
            addOnCompleteEventAction {
                audioItemSubscription?.cancel()
                audioItemSubscription = null

            }
        }

        private fun getEventArtists(event: EntityEvent<out I>): Set<String> = event.entities.flatMap { it.artistsInvolved }.toSet()
    }
}

package net.transgressoft.commons.music.playlist.event

import net.transgressoft.commons.data.DataEvent
import net.transgressoft.commons.event.TransEventSubscriberBase
import net.transgressoft.commons.event.TransEventSubscription
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.playlist.ReactiveAudioPlaylist
import mu.KotlinLogging

open class AudioPlaylistEventSubscriber<P : ReactiveAudioPlaylist<I, P>, I : ReactiveAudioItem<I>>(override val name: String) : TransEventSubscriberBase<P, DataEvent<Int, out P>>() {

   private val logger = KotlinLogging.logger {}

   protected var audioPlaylistSubscription: TransEventSubscription<P>? = null

   init {
       addOnSubscribeEventAction {
           audioPlaylistSubscription = it
           logger.info { "$name subscribed to ${it.source}" }
       }
   }

    override fun toString() = buildString {
        append("AudioPlaylistEventSubscriber(name=$name")
        audioPlaylistSubscription?.let {
            append(", soruce=${it.source}")
        }
        append(")")
    }
}
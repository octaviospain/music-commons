package net.transgressoft.commons.music.audio

import mu.KotlinLogging

internal data class MutableArtistCatalog(override val artist: Artist) : ArtistCatalog, MutableDiscography() {

    private val logger = KotlinLogging.logger {}

    init {
        logger.debug { "Artist catalog created for ${artist.id()}" }
    }

    constructor(audioItem: MutableAudioItem) : this(audioItem.artist) {
        addAudioItem(audioItem)
    }

    override val id: String = "${artist.name}-${artist.countryCode.name}"

    override val uniqueId: String = "${artist.name}-${artist.countryCode.name}"

    fun mergeAudioItem(audioItem: MutableAudioItem) {
        removeAudioItem(audioItem)
        addAudioItem(audioItem)
    }

    override fun toString() = "MutableArtistCatalog(artist=$artist, size=$size)"
}
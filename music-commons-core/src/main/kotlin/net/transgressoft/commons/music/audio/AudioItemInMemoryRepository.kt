package net.transgressoft.commons.music.audio

import java.time.LocalDateTime
import java.util.function.BiFunction

open class AudioItemInMemoryRepository(
    audioItems: MutableMap<Int, AudioItemBase> = mutableMapOf(),
) : AudioItemRepository<AudioItemBase> by AudioItemInMemoryRepositoryLogic(audioItems, JAudioTaggerMetadataReader(), immutableAudioItemUpdateFunction)

val immutableAudioItemUpdateFunction: BiFunction<AudioItemBase, AudioItemMetadataChange, AudioItemBase> = BiFunction { audioItem, change ->
    val updatedTitle = change.title?: audioItem.title
    val updatedArtist = change.artist?: audioItem.artist

    val updatedAlbumName = change.albumName?: audioItem.album.name
    val updatedAlbumArtist = change.albumArtist?: audioItem.album.albumArtist
    val updatedIsCompilation = change.isCompilation?: audioItem.album.isCompilation
    val updatedYear = change.year?.takeIf { it > 0 } ?: audioItem.album.year
    val updatedLabel = change.label?: audioItem.album.label
    val updatedCoverImage = change.coverImage?: audioItem.album.coverImage
    val updatedAlbum = ImmutableAlbum(updatedAlbumName, updatedAlbumArtist, updatedIsCompilation, updatedYear, updatedLabel, updatedCoverImage)

    val updatedGenre = change.genre?: audioItem.genre
    val updatedComments = change.comments?: audioItem.comments
    val updatedTrackNumber = change.trackNumber?.takeIf { it > 0 } ?: audioItem.trackNumber
    val updatedDiscNumber = change.discNumber?.takeIf { it > 0 } ?: audioItem.discNumber
    val updatedBpm = change.bpm?.takeIf { it > 0 } ?: audioItem.bpm

    ImmutableAudioItem(
        audioItem.id, audioItem.path, updatedTitle, audioItem.duration, audioItem.bitRate, updatedArtist, updatedAlbum, updatedGenre, updatedComments,
        updatedTrackNumber,updatedDiscNumber, updatedBpm, audioItem.encoder, audioItem.encoding, audioItem.dateOfCreation, LocalDateTime.now()
    )
}
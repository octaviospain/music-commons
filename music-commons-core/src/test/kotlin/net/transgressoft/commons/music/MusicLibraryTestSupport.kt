package net.transgressoft.commons.music

import net.transgressoft.commons.media.persistence.waveform.AudioWaveformMapSerializer
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioLibrary
import net.transgressoft.commons.music.playlist.AudioPlaylist
import net.transgressoft.commons.music.playlist.MutableAudioPlaylist
import net.transgressoft.commons.music.waveform.AudioWaveform
import net.transgressoft.commons.persistence.music.audio.AudioItemMapSerializer
import net.transgressoft.commons.persistence.music.playlist.AudioPlaylistMapSerializer
import net.transgressoft.lirp.persistence.AggregateCollectionRef
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import net.transgressoft.lirp.persistence.json.JsonRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

/*
 * Shared arrange/assert scaffolding for the core music-library integration specs. Centralizes the
 * repeated three-JsonFileRepository construction triad and the recurring playlist-reference and
 * catalog-index invariants asserted across the library and lifecycle suites.
 */

/**
 * The three JSON-backed repositories a [CoreMusicLibrary] persists to — audio items, playlists and
 * waveforms — together with the files they are backed by.
 *
 * The files are kept visible so round-trip specs can [reopen] fresh repositories over the same JSON
 * state after closing the originals, and so lifecycle specs can close each repository individually.
 */
class JsonRepoTriad(
    val audioFile: File,
    val playlistsFile: File,
    val waveformsFile: File
) {
    val audioRepository: JsonRepository<Int, AudioItem> = JsonFileRepository(audioFile, AudioItemMapSerializer)
    val playlistRepository: JsonRepository<Int, MutableAudioPlaylist> = JsonFileRepository(playlistsFile, AudioPlaylistMapSerializer)
    val waveformRepository: JsonRepository<Int, AudioWaveform> = JsonFileRepository(waveformsFile, AudioWaveformMapSerializer)

    /** Wires this triad's repositories into [builder], returning it for chaining. */
    fun wireInto(builder: CoreMusicLibrary.Builder): CoreMusicLibrary.Builder =
        builder
            .audioRepository(audioRepository)
            .playlistRepository(playlistRepository)
            .waveformRepository(waveformRepository)

    /** Builds a fresh triad over the same three files, reloading the persisted JSON state. */
    fun reopen(): JsonRepoTriad = JsonRepoTriad(audioFile, playlistsFile, waveformsFile)

    /** Closes all three repositories. */
    fun closeAll() {
        audioRepository.close()
        playlistRepository.close()
        waveformRepository.close()
    }
}

/**
 * Creates three auto-deleting temp files (audio, playlists, waveforms) prefixed with [prefix] and the
 * three JSON repositories backed by them.
 *
 * @param prefix distinguishes the temp files of a given spec when several coexist
 */
fun jsonRepoTriad(prefix: String = "musicLibrary"): JsonRepoTriad =
    JsonRepoTriad(
        tempJsonFile("$prefix-audio"),
        tempJsonFile("$prefix-playlists"),
        tempJsonFile("$prefix-waveforms")
    )

private fun tempJsonFile(prefix: String): File = File.createTempFile(prefix, ".json").apply { deleteOnExit() }

/**
 * The audio-item ids a playlist references without materializing the items — safe to call after the
 * referenced items have been removed from the registry, where iterating [AudioPlaylist.audioItems]
 * would throw `NoSuchElementException`.
 */
fun AudioPlaylist<*>.referenceIds(): List<Int> =
    (audioItems as? AggregateCollectionRef<*, *>)?.referenceIds?.map { it as Int } ?: emptyList()

/** Asserts this playlist still references [itemId] by id, without materializing the item. */
infix fun AudioPlaylist<*>.shouldReferenceItemId(itemId: Int) {
    referenceIds().contains(itemId) shouldBe true
}

/** Asserts this playlist no longer references [itemId] by id, without materializing the item. */
infix fun AudioPlaylist<*>.shouldNotReferenceItemId(itemId: Int) {
    referenceIds().contains(itemId) shouldBe false
}

/** Asserts the artist catalog indexes [item] under the album it belongs to. */
infix fun AudioLibrary.shouldIndex(item: AudioItem) {
    findAlbumAudioItems(Artist.of(item.artist.name), item.album.name).any { it.id == item.id } shouldBe true
}

/** Asserts the artist catalog no longer indexes [item] under the album it belongs to. */
infix fun AudioLibrary.shouldNotIndex(item: AudioItem) {
    findAlbumAudioItems(Artist.of(item.artist.name), item.album.name).any { it.id == item.id } shouldBe false
}

/**
 * Asserts this throwable carries a non-null message containing [fragment], naming the actual message
 * on failure. Replaces the raw `ex.message!! shouldContain "..."` idiom across the import/parser specs.
 */
infix fun Throwable.shouldHaveMessageContaining(fragment: String) {
    message?.shouldContain(fragment) ?: throw AssertionError("expected message containing \"$fragment\" but message was null")
}
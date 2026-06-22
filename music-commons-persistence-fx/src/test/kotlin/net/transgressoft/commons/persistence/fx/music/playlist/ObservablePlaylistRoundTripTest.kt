/******************************************************************************
 * Copyright (C) 2026  Octavio Calleya Garcia                                 *
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

package net.transgressoft.commons.persistence.fx.music.playlist

import net.transgressoft.commons.fx.music.FXMusicLibrary
import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.fx.music.playlist.ObservablePlaylist
import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.AudioFileTagType.ID3_V_24
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.commons.persistence.fx.music.audio.ObservableAudioItemMapSerializer
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import org.testfx.api.FxToolkit
import org.testfx.util.WaitForAsyncUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
@DisplayName("ObservablePlaylist JSON round-trip")
internal class ObservablePlaylistRoundTripTest : StringSpec({

    val reactive = reactiveScope()

    beforeSpec { FxToolkit.registerPrimaryStage() }

    "ObservablePlaylistMapSerializer round-trips a hierarchy without an audio library, preserving identity and folder flag" {
        val playlistsFile = tempfile("fxPlaylistHierarchy-rt", ".json").apply { deleteOnExit() }

        val library =
            FXMusicLibrary.builder()
                .playlistRepository(JsonFileRepository(playlistsFile, ObservablePlaylistMapSerializer, loadOnInit = false))
                .build()
        val written = library.createPlaylistDirectory("Rock")
        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()
        library.close()

        val reloaded =
            FXMusicLibrary.builder()
                .playlistRepository(JsonFileRepository(playlistsFile, ObservablePlaylistMapSerializer, loadOnInit = false))
                .build()

        reloaded.playlistHierarchy().size() shouldBe 1
        reloaded.playlistHierarchy().contains {
            it.id == written.id && it.isDirectory && it.name == "Rock" && it.playlists.isEmpty()
        } shouldBe true

        reloaded.close()
    }

    "ObservablePlaylistMapSerializer restores aggregate audio-item references through the FX RefAccessor and the available audio library" {
        val audioFile = tempfile("fxPlaylist-audio", ".json").apply { deleteOnExit() }
        val playlistsFile = tempfile("fxPlaylistHierarchy-refs", ".json").apply { deleteOnExit() }

        val library =
            FXMusicLibrary.builder()
                .audioRepository(JsonFileRepository(audioFile, ObservableAudioItemMapSerializer))
                .playlistRepository(JsonFileRepository(playlistsFile, ObservablePlaylistMapSerializer, loadOnInit = false))
                .build()
        val audioItem = library.audioItemFromFile(Arb.realAudioFile(ID3_V_24).next())
        val written = library.createPlaylist("Rock", listOf(audioItem))
        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()
        library.close()

        val reloaded =
            FXMusicLibrary.builder()
                .audioRepository(JsonFileRepository(audioFile, ObservableAudioItemMapSerializer))
                .playlistRepository(JsonFileRepository(playlistsFile, ObservablePlaylistMapSerializer, loadOnInit = false))
                .build()

        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()

        reloaded.playlistHierarchy().size() shouldBe 1
        val reloadedPlaylist = reloaded.playlistHierarchy().findByName("Rock").orElse(null)
        (reloadedPlaylist as ObservablePlaylist).id shouldBe written.id
        reloadedPlaylist.audioItems.map { it: ObservableAudioItem -> it.id } shouldBe listOf(audioItem.id)

        reloaded.close()
    }
})
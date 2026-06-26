/******************************************************************************
 * Copyright (C) 2025  Octavio Calleya Garcia                                 *
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

package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.lirp.persistence.VolatileRepository
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
internal class AudioLibraryExtensionsTest : StringSpec({

    val files = virtualFiles()
    val reactive = reactiveScope()
    lateinit var repository: VolatileRepository<Int, AudioItem>
    lateinit var audioLibrary: TestAudioLibrary

    beforeEach {
        repository = VolatileRepository("AudioLibraryExtensionsTest")
        audioLibrary = TestAudioLibrary(repository, files.metadataIO)
    }

    afterEach {
        audioLibrary.close()
    }

    "AudioItem albumIn returns the populated album when the item is in the library" {
        val artist = Arb.artist().next()
        val album = AlbumDetails("Portishead", artist)
        val audioItem =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    title = "Glory Box"
                    trackNumber = 1
                    discNumber = 1
                }.next()
            )

        reactive.advance()
        eventually(2.seconds) {
            val result = audioItem.albumIn(audioLibrary)
            result shouldBePresent { it.album shouldBe album }
        }
    }

    "AudioItem albumIn returns empty when no item with that album is in the library" {
        val artist = Arb.artist().next()
        val album = AlbumDetails("Dummy", artist)
        val audioItem =
            Arb.audioItem {
                this.artist = artist
                this.album = album
            }.next()

        audioItem.albumIn(audioLibrary).shouldBeEmpty()
    }
})
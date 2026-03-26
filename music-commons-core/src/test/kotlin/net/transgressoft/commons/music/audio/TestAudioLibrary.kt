package net.transgressoft.commons.music.audio

import net.transgressoft.lirp.persistence.Repository
import java.nio.file.Path

/**
 * Test stub for [AudioLibraryBase] used to test base class behavior without audio file I/O dependencies.
 *
 * Creates [AudioItem] instances from virtual file data rather than reading real audio files,
 * enabling isolated unit testing of the base class reactive catalog management.
 */
internal class TestAudioLibrary(
    repository: Repository<Int, AudioItem>
) : AudioLibraryBase<AudioItem, ArtistCatalog<AudioItem>>(repository, DefaultArtistCatalogRegistry()) {

    override fun createFromFile(audioItemPath: Path): AudioItem =
        MutableAudioItem(audioItemPath, newId()).also { add(it) }
}
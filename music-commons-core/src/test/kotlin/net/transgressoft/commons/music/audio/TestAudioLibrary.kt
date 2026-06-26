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
    repository: Repository<Int, AudioItem>,
    metadataIO: AudioMetadataIO = JAudioTaggerMetadataIO()
) : AudioLibrary, AudioLibraryBase<AudioItem, ArtistCatalog<AudioItem>, Album<AudioItem>, GenreIndex<AudioItem>>(
        repository,
        DefaultArtistCatalogRegistry(repository),
        DefaultAlbumRegistry(repository),
        DefaultGenreIndexRegistry(repository),
        metadataIO
    ) {

    override fun createFromFile(audioItemPath: Path): AudioItem {
        val tag = metadataIO.readMetadata(audioItemPath)
        val cover = metadataIO.loadCover(audioItemPath)
        return MutableAudioItem(audioItemPath, newId(), tag.copy(coverBytes = cover)).also { add(it) }
    }
}
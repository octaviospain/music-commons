package net.transgressoft.commons.music.itunes

import net.transgressoft.commons.music.audio.AudioFileType
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths
import java.text.Normalizer
import kotlin.io.path.extension

/**
 * Resolves iTunes track file URIs to filesystem paths with NFC filename normalization,
 * and gates unsupported audio file types against the configured [ItunesImportPolicy].
 *
 * Handles the Windows drive-letter and UNC-path edge cases via [Paths.get] on the default
 * filesystem, while supporting in-memory or test filesystems via [fileSystem.getPath].
 */
internal class ItunesTrackResolver(private val fileSystem: FileSystem = FileSystems.getDefault()) {

    internal fun resolveTrackPath(track: ItunesTrack): Path {
        val uri = URI(track.location)
        val rawPath =
            if (fileSystem == FileSystems.getDefault()) {
                // Paths.get(URI) handles Windows drive letters (file:///C:/... -> C:\...) and
                // UNC authority preservation (file:////server/share/... -> \\server\share\...)
                // correctly on the default filesystem. The earlier fileSystem.getPath(uri.path)
                // form mangled both because URI.path returns `/C:/...` which Windows' NIO
                // provider parses ambiguously, and the UNC authority is silently dropped.
                Paths.get(uri)
            } else {
                fileSystem.getPath(uri.path)
            }
        // Normalize filename to NFC: macOS iTunes writes NFD-decomposed Unicode;
        // Linux/Windows expect NFC. Idempotent and no-op for ASCII.
        val normalizedName = Normalizer.normalize(rawPath.fileName.toString(), Normalizer.Form.NFC)
        return rawPath.parent?.resolve(normalizedName) ?: rawPath
    }

    internal fun isUnsupportedFileType(path: Path, policy: ItunesImportPolicy): Boolean {
        val extension = path.extension.lowercase()
        val fileType = AudioFileType.entries.find { it.extension == extension }
        return fileType == null || fileType !in policy.acceptedFileTypes
    }
}
package net.transgressoft.commons.music.m3u

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path

/*
 * Shared filesystem-and-parse plumbing for the M3U parser specs. Collapses the repeated
 * `Jimfs.newFileSystem(...).use { fs -> createDirectories; writeString; M3uParser(baseDir).parse(m3u) }`
 * ceremony into one call while keeping each test's distinct content and assertions in view.
 */

/**
 * Creates a Jimfs filesystem for [config], writes [content] to `[baseDirPath]/[fileName]`, parses it
 * with an [M3uParser] rooted at that base directory, and hands the filesystem, base directory and
 * parse result to [assert]. The filesystem is closed when [assert] returns.
 *
 * @param config the Jimfs configuration selecting Unix or Windows path semantics
 * @param baseDirPath the directory the playlist lives in (created if absent)
 * @param fileName the playlist file name written under [baseDirPath]
 * @param content the playlist text written verbatim
 * @param assert receives the live filesystem, the base directory, and the parse result
 */
internal fun parseM3uOnFs(
    config: Configuration,
    baseDirPath: String,
    fileName: String,
    content: String,
    assert: (fs: FileSystem, baseDir: Path, result: M3uParser.Result) -> Unit
) {
    Jimfs.newFileSystem(config).use { fs ->
        val baseDir = fs.getPath(baseDirPath)
        Files.createDirectories(baseDir)
        val m3u = baseDir.resolve(fileName)
        Files.writeString(m3u, content)
        assert(fs, baseDir, M3uParser(baseDir).parse(m3u))
    }
}
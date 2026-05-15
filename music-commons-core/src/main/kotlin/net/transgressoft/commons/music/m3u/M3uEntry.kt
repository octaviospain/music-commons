package net.transgressoft.commons.music.m3u

import java.nio.file.Path

/**
 * Immutable representation of a single entry parsed from an M3U playlist file.
 *
 * Each entry corresponds to one resolved media file path. M3U directive metadata
 * is intentionally discarded because audio file tags are used during import.
 */
internal data class M3uEntry(val resolvedPath: Path)
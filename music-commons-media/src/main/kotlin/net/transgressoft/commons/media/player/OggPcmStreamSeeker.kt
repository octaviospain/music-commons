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

package net.transgressoft.commons.media.player

import net.transgressoft.commons.media.util.decodeToPcmStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Opens OGG/Vorbis PCM streams at a requested decoded byte offset using a binary-search
 * strategy over OggS page `granulepos` fields.
 *
 * The bisection reads `O(log file size)` pages to confirm the target is backed by real Vorbis
 * data and to clamp out-of-range requests. The full-file SPI stream is then opened (the Vorbis
 * SPI must re-read the header pages from the container start) and PCM is decoded forward to the
 * exact requested offset, so the landing is sample-accurate. Because the SPI decodes from the
 * container start regardless, draining to the precise target rather than the page boundary costs
 * nothing extra; wall-clock seek cost still scales with the target position — only the validation
 * is sub-linear, not the forward decode.
 *
 * Malformed or truncated OGG containers are handled gracefully: granulepos = -1 (header
 * pages) is skipped, `EOFException`/`IOException` causes a null return, and the bisection
 * loop is capped at [MAX_BISECT_ITERATIONS] iterations so it always terminates even on
 * pathological files that never converge.
 *
 * @see PcmStreamSeeker
 */
internal object OggPcmStreamSeeker : PcmStreamSeeker {

    /** Bisection terminates when the search window narrows to less than this many bytes. */
    const val PAGE_SCAN_THRESHOLD = 65_536L

    /** Maximum iterations before the bisection gives up and returns null. */
    const val MAX_BISECT_ITERATIONS = 64

    private val OGGS_SYNC = byteArrayOf('O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte())
    private val VORBIS_IDENT_MAGIC =
        byteArrayOf(0x01, 'v'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(), 'b'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte())

    override fun open(file: File, requestedByteOffset: Long): SeekablePcmStream? {
        if (!file.extension.equals("ogg", ignoreCase = true) || requestedByteOffset <= 0L) return null

        return try {
            seekOgg(file, requestedByteOffset)
        } catch (_: IOException) {
            null
        } catch (_: EOFException) {
            null
        }
    }

    private fun seekOgg(file: File, requestedByteOffset: Long): SeekablePcmStream? {
        val fileLength = file.length()
        if (fileLength < 27) return null

        // Step 1: scan the BOS (beginning-of-stream) pages to find the Vorbis stream serial.
        val vorbisSerial = findVorbisSerial(file) ?: return null

        // Step 2: read the Vorbis identification header to obtain the channel count.
        val vorbisInfo = readVorbisIdentHeader(file, vorbisSerial) ?: return null
        val frameSize = vorbisInfo.channels * 2L // 16-bit PCM

        // Step 3: convert requested PCM byte offset to a granule position target.
        val targetGranule = requestedByteOffset / frameSize

        // Step 4: binary search over Vorbis pages by granulepos to confirm the target is backed
        // by real Vorbis data (null => no in-range page, fall through to the generic skip path).
        bisectToPage(file, fileLength, vorbisSerial, targetGranule) ?: return null

        // Step 5: open the full SPI stream (Vorbis headers must be at the start) and decode
        // forward to the exact requested PCM offset. Because the SPI decodes from the container
        // start regardless, draining to the precise target — rather than to the page boundary —
        // costs nothing extra and makes the seek sample-accurate instead of page-accurate.
        return openAtPcmOffset(file, requestedByteOffset)
    }

    /**
     * Scans BOS pages at the start of the file and returns the serial number of the first Vorbis stream found.
     * Returns null if no Vorbis stream is present or if the file has no readable BOS pages.
     */
    private fun findVorbisSerial(file: File): Int? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                var offset = 0L
                repeat(MAX_BOS_PAGES) {
                    raf.seek(offset)
                    val page = readPageHeader(raf) ?: return null
                    val pageBodyStart = offset + 27L + page.numSegments
                    val pageEnd = pageBodyStart + page.bodySize
                    if ((page.headerType and 0x02) == 0) {
                        // No more BOS pages; Vorbis stream was not found.
                        return null
                    }
                    if (page.bodySize >= VORBIS_IDENT_MAGIC.size) {
                        raf.seek(pageBodyStart)
                        val magic = ByteArray(VORBIS_IDENT_MAGIC.size)
                        raf.readFully(magic)
                        if (magic.contentEquals(VORBIS_IDENT_MAGIC)) {
                            return page.serial
                        }
                    }
                    offset = pageEnd
                }
                null
            }
        } catch (_: IOException) {
            null
        } catch (_: EOFException) {
            null
        }
    }

    /**
     * Reads the Vorbis identification header for the given stream serial.
     * Extracts sample rate and channel count.
     */
    private fun readVorbisIdentHeader(file: File, vorbisSerial: Int): VorbisInfo? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                var offset = 0L
                repeat(MAX_BOS_PAGES) {
                    raf.seek(offset)
                    val page = readPageHeader(raf) ?: return null
                    if (page.serial == vorbisSerial) {
                        parseVorbisInfo(raf, page, offset + 27L + page.numSegments)?.let { return it }
                    }
                    if ((page.headerType and 0x02) == 0) return null // past BOS section
                    offset += 27L + page.numSegments + page.bodySize
                }
                null
            }
        } catch (_: IOException) {
            null
        } catch (_: EOFException) {
            null
        }
    }

    /** Parses the Vorbis identification header from a page body, or null when it is not present. */
    private fun parseVorbisInfo(raf: RandomAccessFile, page: RawPageHeader, pageBodyStart: Long): VorbisInfo? {
        if (page.bodySize < 22) return null
        raf.seek(pageBodyStart)
        val body = ByteArray(page.bodySize.coerceAtMost(30))
        raf.readFully(body)
        if (body.size < 22 || !body.sliceArray(0 until 7).contentEquals(VORBIS_IDENT_MAGIC)) return null
        // Vorbis ident header layout: magic(7) + version(4) + channels(1) + sampleRate(4)
        val channels = body[11].toInt() and 0xFF
        val sampleRate = readLittleEndianInt(body, 12)
        return if (channels > 0 && sampleRate > 0) VorbisInfo(sampleRate, channels) else null
    }

    /**
     * Binary-searches the file for a Vorbis data page at or before [targetGranule], returning that
     * page's granule position as confirmation the target is backed by real audio.
     *
     * Only pages belonging to [vorbisSerial] are considered. Pages with granulepos < 0
     * (OGG header pages) are skipped. The bisection is capped at [MAX_BISECT_ITERATIONS]
     * iterations and terminates early when the window narrows below [PAGE_SCAN_THRESHOLD]
     * bytes, guaranteeing it always terminates.
     *
     * Returns null when no in-range Vorbis data page is located, so the caller falls through
     * to the generic forward-skip path instead of silently seeking to the start of the file.
     */
    private fun bisectToPage(file: File, fileLength: Long, vorbisSerial: Int, targetGranule: Long): Long? =
        try {
            RandomAccessFile(file, "r").use { raf ->
                var lo = 0L
                var hi = fileLength
                // -1L sentinel: no in-range Vorbis data page has been located yet. A genuine
                // seek-to-start must be distinguishable from "bisection found nothing", so the
                // default does not silently collapse to PCM offset 0.
                var bestGranule = -1L
                var iterations = 0

                while (hi - lo > PAGE_SCAN_THRESHOLD && iterations < MAX_BISECT_ITERATIONS) {
                    val page = scanForwardToVorbisPage(raf, (lo + hi) / 2, hi, vorbisSerial)
                    when {
                        // No valid Vorbis page in the upper half; search the lower half.
                        page == null -> hi = (lo + hi) / 2
                        // Header/setup page with granulepos = -1; move lo past it.
                        page.granulePos < 0L -> lo = page.fileOffset + 27L + page.numSegments + page.bodySize
                        page.granulePos <= targetGranule -> {
                            bestGranule = page.granulePos
                            lo = page.fileOffset + 27L + page.numSegments + page.bodySize
                        }
                        else -> hi = page.fileOffset
                    }
                    iterations++
                }

                // The bisection only probes window midpoints and stops once the window is within
                // PAGE_SCAN_THRESHOLD bytes, so the last recorded midpoint page can be many pages
                // (seconds of audio) before the true closest page <= target. Always forward-scan
                // the residual window for the highest in-range granule so the located page is
                // within one page of the target rather than undershooting by the whole window.
                val refinedGranule = scanForBestGranule(raf, lo, hi, vorbisSerial, targetGranule)
                if (refinedGranule > bestGranule) bestGranule = refinedGranule

                // -1L => no in-range Vorbis data page; caller falls through to skipFully.
                bestGranule.takeIf { it >= 0L }
            }
        } catch (_: IOException) {
            null
        } catch (_: EOFException) {
            null
        }

    /**
     * Scans forward from [startOffset] (up to [limitOffset]) for the next OggS page header
     * belonging to [targetSerial], returning a [PageInfo] or null if none is found.
     */
    private fun scanForwardToVorbisPage(
        raf: RandomAccessFile,
        startOffset: Long,
        limitOffset: Long,
        targetSerial: Int
    ): PageInfo? {
        raf.seek(startOffset)
        val fileLength = raf.length()
        val limit = limitOffset.coerceAtMost(fileLength)

        // Scan byte-by-byte for the OggS capture pattern within the allowed window.
        // In practice, the pattern is found quickly since pages are large (>4KB typically).
        val buf = ByteArray(4)
        var pos = startOffset
        while (pos + 27 <= limit) {
            raf.seek(pos)
            val bytesRead = raf.read(buf, 0, 4)
            if (bytesRead < 4) break

            if (buf[0] == OGGS_SYNC[0] && buf[1] == OGGS_SYNC[1] && buf[2] == OGGS_SYNC[2] && buf[3] == OGGS_SYNC[3]) {
                // Found OggS sync word; read the remaining 23 bytes of the page header.
                raf.seek(pos)
                val page = readPageHeader(raf) ?: break
                if (page.serial == targetSerial) {
                    return PageInfo(pos, page.headerType, page.granulePos, page.numSegments, page.bodySize)
                }
                // Wrong stream; advance by the full page to stay aligned.
                pos += 27L + page.numSegments + page.bodySize
            } else {
                pos++
            }
        }
        return null
    }

    /**
     * Forward-scans the window `[startOffset, limitOffset)` for the highest granulepos of a
     * [targetSerial] data page that is still at or before [targetGranule]. Used as the
     * bisection's fallback when the midpoint probes never recorded an in-range page.
     *
     * @return the best in-range granulepos, or -1L when no qualifying data page exists.
     */
    private fun scanForBestGranule(
        raf: RandomAccessFile,
        startOffset: Long,
        limitOffset: Long,
        targetSerial: Int,
        targetGranule: Long
    ): Long {
        var best = -1L
        var pos = startOffset
        while (pos < limitOffset) {
            val page = scanForwardToVorbisPage(raf, pos, limitOffset, targetSerial) ?: break
            if (page.granulePos in 0L..targetGranule) best = page.granulePos
            // Advance past this page to keep scanning forward within the window.
            pos = page.fileOffset + 27L + page.numSegments + page.bodySize
            if (page.granulePos > targetGranule) break
        }
        return best
    }

    /**
     * Opens the full-file SPI stream and drains decoded PCM to [targetPcmOffset], returning a
     * [SeekablePcmStream] positioned there.
     *
     * The Vorbis SPI decodes forward from the container start, so the drain reads and discards
     * up to [targetPcmOffset] bytes of PCM; the cost scales with the target position. When the
     * target lies past the end of the decodable stream the drain stops at end-of-stream and the
     * returned [SeekablePcmStream.startByteOffset] reflects the actual landed position.
     *
     * Returns null if the SPI cannot open the stream, the drain reaches end-of-stream immediately,
     * or any read error occurs.
     */
    private fun openAtPcmOffset(file: File, targetPcmOffset: Long): SeekablePcmStream? {
        // Decode the whole file via the shared prioritized decoder so the Vorbis provider is
        // preferred over Opus for .ogg; the SPI must read the header pages from the start.
        val pcmStream =
            try {
                decodeToPcmStream(file.toPath())
            } catch (_: Exception) {
                return null
            }
        return try {
            // Drain to the exact (frame-aligned) requested offset. If the target is past the end
            // of the stream the drain stops at EOF; land there rather than failing the seek.
            val frameSize = pcmStream.format.frameSize.coerceAtLeast(1)
            val alignedOffset = targetPcmOffset.let { o -> o - o % frameSize }
            // A target inside the first frame aligns to 0: that is a valid landing at stream
            // start, not a failed drain, so return it instead of falling through to null below.
            if (alignedOffset == 0L) {
                return SeekablePcmStream(pcmStream, 0L)
            }
            val drained = drainPcm(pcmStream, alignedOffset)
            if (drained <= 0L) {
                pcmStream.close()
                null
            } else {
                SeekablePcmStream(pcmStream, drained)
            }
        } catch (_: Exception) {
            pcmStream.close()
            null
        }
    }

    /**
     * Drains [targetBytes] of PCM from [stream] by reading and discarding chunks.
     * Returns the actual number of bytes drained.
     */
    private fun drainPcm(stream: javax.sound.sampled.AudioInputStream, targetBytes: Long): Long {
        if (targetBytes <= 0L) return 0L
        val buf = ByteArray(DRAIN_BUFFER_SIZE)
        var drained = 0L
        while (drained < targetBytes) {
            val toRead = minOf(buf.size.toLong(), targetBytes - drained).toInt()
            val bytesRead = stream.read(buf, 0, toRead)
            if (bytesRead <= 0) break
            drained += bytesRead
        }
        return drained
    }

    /** Reads a 27-byte OGG page header from the current position of [raf]. */
    private fun readPageHeader(raf: RandomAccessFile): RawPageHeader? {
        val header = ByteArray(27)
        val read = raf.read(header, 0, 27)
        if (read < 27) return null
        if (header[0] != OGGS_SYNC[0] || header[1] != OGGS_SYNC[1] || header[2] != OGGS_SYNC[2] || header[3] != OGGS_SYNC[3]) {
            return null
        }
        val headerType = header[5].toInt() and 0xFF
        val granulePos = readLittleEndianLong(header, 6)
        val serial = readLittleEndianInt(header, 14)
        val numSegments = header[26].toInt() and 0xFF
        val segTable = ByteArray(numSegments)
        raf.readFully(segTable)
        val bodySize = segTable.sumOf { it.toInt() and 0xFF }
        return RawPageHeader(headerType, granulePos, serial, numSegments, bodySize)
    }

    private fun readLittleEndianInt(buf: ByteArray, offset: Int): Int =
        (buf[offset].toInt() and 0xFF) or
            ((buf[offset + 1].toInt() and 0xFF) shl 8) or
            ((buf[offset + 2].toInt() and 0xFF) shl 16) or
            ((buf[offset + 3].toInt() and 0xFF) shl 24)

    private fun readLittleEndianLong(buf: ByteArray, offset: Int): Long {
        var value = 0L
        for (i in 0 until 8) {
            value = value or ((buf[offset + i].toLong() and 0xFF) shl (i * 8))
        }
        return value
    }

    private const val MAX_BOS_PAGES = 8
    private const val DRAIN_BUFFER_SIZE = 8192

    private data class VorbisInfo(val sampleRate: Int, val channels: Int)

    private data class RawPageHeader(
        val headerType: Int,
        val granulePos: Long,
        val serial: Int,
        val numSegments: Int,
        val bodySize: Int
    )

    private data class PageInfo(
        val fileOffset: Long,
        val headerType: Int,
        val granulePos: Long,
        val numSegments: Int,
        val bodySize: Int
    )
}
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

import net.transgressoft.commons.media.util.decodePcmStreamAt
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * Opens MP3 PCM streams at a requested decoded byte offset using a hybrid seek strategy.
 *
 * When the first MPEG frame contains a Xing or VBRI VBR header with a 100-entry TOC, the
 * TOC is used to locate the closest frame boundary in O(1) time. When no such header is
 * present (CBR or headerless VBR files), a bounded MPEG frame-boundary scan walks frame
 * headers until the accumulated decoded-byte count reaches the requested offset, capped at
 * [MAX_SCAN_FRAMES] to guarantee bounded latency.
 *
 * A malformed or truncated header causes a null return without hanging, allowing the caller
 * to fall through to the full-decode byte-skip path.
 *
 * @see PcmStreamSeeker
 * @see XingHeaderParser
 */
internal object Mp3PcmStreamSeeker : PcmStreamSeeker {

    /** Maximum frames the bounded scan will walk before giving up and returning null. */
    const val MAX_SCAN_FRAMES = 50_000

    /** Maximum bytes [findFrameBoundary] scans forward from an approximate TOC offset. */
    private const val FRAME_RESYNC_MAX_SCAN = 16_384

    /** Consecutive matching frames [findFrameBoundary] requires to confirm a real frame boundary. */
    private const val FRAME_RESYNC_CONFIRM_FRAMES = 3

    /** MPEG1 samples per frame for Layer III. */
    private const val MPEG1_SAMPLES_PER_FRAME = 1152

    /** MPEG2/2.5 samples per frame for Layer III. */
    private const val MPEG2_SAMPLES_PER_FRAME = 576

    /** MPEG1 Layer III bitrate table (kbps), indexed by the 4-bit header field; -1 marks reserved. */
    private val MPEG1_L3_BITRATES_KBPS = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, -1)

    /** MPEG2/2.5 Layer III bitrate table (kbps), indexed by the 4-bit header field; -1 marks reserved. */
    private val MPEG2_L3_BITRATES_KBPS = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, -1)
    private val MPEG_SAMPLE_RATES_HZ = intArrayOf(44100, 48000, 32000, -1)
    private val MPEG2_SAMPLE_RATES_HZ = intArrayOf(22050, 24000, 16000, -1)
    private val MPEG25_SAMPLE_RATES_HZ = intArrayOf(11025, 12000, 8000, -1)

    /**
     * Skips exactly [count] bytes from [stream], looping until the full count is consumed or
     * genuine end-of-stream is reached. [InputStream.skip] may legally return fewer bytes than
     * requested even when more data remains, so a single short skip must not be mistaken for
     * EOF; only a non-positive return is treated as the stream having no more data.
     *
     * @return the total number of bytes actually skipped (== [count] unless EOF was reached)
     */
    private fun skipExactly(stream: InputStream, count: Long): Long {
        var remaining = count
        while (remaining > 0L) {
            val n = stream.skip(remaining)
            if (n <= 0L) break
            remaining -= n
        }
        return count - remaining
    }

    override fun open(file: File, requestedByteOffset: Long): SeekablePcmStream? {
        if (!file.extension.equals("mp3", ignoreCase = true) || requestedByteOffset <= 0L) return null

        return try {
            seekMp3(file, requestedByteOffset)
        } catch (_: IOException) {
            null
        }
    }

    private fun seekMp3(file: File, requestedByteOffset: Long): SeekablePcmStream? {
        // Step 1: skip ID3v2 tag to locate first MPEG frame offset
        val audioStartOffset = skipId3v2Tag(file) ?: return null

        // Step 2: parse first frame header to determine MPEG version and channel mode
        val firstFrameHeader = readFrameHeader(file, audioStartOffset) ?: return null
        val isMpeg1 = (firstFrameHeader.version == 3)
        val isMono = (firstFrameHeader.channelMode == 3)

        // Step 3: try Xing/VBRI TOC seek first (O(1))
        val xingResult = XingHeaderParser.parse(file, audioStartOffset, isMono, isMpeg1)
        if (xingResult != null) {
            val totalAudioBytes = if (xingResult.totalBytes > 0L) xingResult.totalBytes else file.length() - audioStartOffset
            val byteOffset =
                computeTocByteOffset(
                    requestedByteOffset,
                    xingResult,
                    totalAudioBytes,
                    firstFrameHeader,
                    audioStartOffset
                ) ?: return null
            // The TOC yields an approximate byte position that is almost never on a frame
            // boundary. Decoding from a mid-frame offset makes the SPI mis-sync and report a
            // garbage format (e.g. 0 channels), so resync forward to the next real frame header
            // before opening. Fall through to the caller's skip path if no frame is found.
            val approxOffset = byteOffset.coerceAtLeast(audioStartOffset)
            val frameAlignedOffset = findFrameBoundary(file, approxOffset, firstFrameHeader) ?: return null
            // Use requestedByteOffset as the reported PCM start so the player's position
            // reflects the seek target; the SPI decodes forward from the located frame boundary.
            return openAt(file, frameAlignedOffset, requestedByteOffset)
        }

        // Step 4: bounded MPEG frame-boundary scan (CBR fallback)
        val scanResult = frameScanFallback(file, audioStartOffset, requestedByteOffset) ?: return null
        return openAt(file, scanResult.fileOffset, scanResult.pcmOffset)
    }

    /**
     * Reads the 10-byte ID3v2 header and returns the offset of the first MPEG frame.
     * Returns 0 if no ID3v2 tag is present, or null on read error.
     */
    private fun skipId3v2Tag(file: File): Long? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 10) return null
                val header = ByteArray(10)
                raf.readFully(header)
                // ID3v2 header: "ID3" + version(2) + flags(1) + syncsafe size(4)
                if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
                    return 0L // no ID3v2 tag
                }
                // Syncsafe integer: 4 bytes, each bit 7 is 0, encode 28 bits
                val b0 = (header[6].toInt() and 0x7F)
                val b1 = (header[7].toInt() and 0x7F)
                val b2 = (header[8].toInt() and 0x7F)
                val b3 = (header[9].toInt() and 0x7F)
                val tagSize = (b0 shl 21) or (b1 shl 14) or (b2 shl 7) or b3
                // The syncsafe size covers neither the 10-byte header nor an optional 10-byte
                // ID3v2.4 footer (flags bit 0x10); include both so the offset lands on audio.
                val hasFooter = (header[5].toInt() and 0x10) != 0
                10L + tagSize + if (hasFooter) 10L else 0L
            }
        } catch (_: IOException) {
            null
        }
    }

    /**
     * Parses the 4-byte MPEG frame header at [frameOffset] and returns the decoded fields.
     * Returns null if no valid sync word is found.
     */
    private fun readFrameHeader(file: File, frameOffset: Long): FrameHeader? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < frameOffset + 4) return null
                raf.seek(frameOffset)
                val b = ByteArray(4)
                raf.readFully(b)
                parseFrameHeader(b)
            }
        } catch (_: IOException) {
            null
        }
    }

    /**
     * Parses a 4-byte MPEG frame header sync word into a [FrameHeader].
     * Returns null if the sync word is invalid.
     */
    private fun parseFrameHeader(b: ByteArray): FrameHeader? {
        // Sync word: first 11 bits must all be 1
        if (b[0] != 0xFF.toByte() || (b[1].toInt() and 0xE0) != 0xE0) return null

        val version = (b[1].toInt() shr 3) and 0x3 // bits 4-3: 3=MPEG1, 2=MPEG2, 0=MPEG2.5, 1=reserved
        val layer = (b[1].toInt() shr 1) and 0x3 // bits 2-1: 1=Layer III
        val bitrateIdx = (b[2].toInt() shr 4) and 0xF
        val sampleRateIdx = (b[2].toInt() shr 2) and 0x3
        val padding = (b[2].toInt() shr 1) and 0x1
        val channelMode = (b[3].toInt() shr 6) and 0x3 // 3=mono

        if (layer != 1) return null // this seeker handles MP3 (Layer III) only
        if (bitrateIdx == 0 || bitrateIdx == 15) return null // free/bad bitrate
        if (sampleRateIdx == 3) return null // reserved sample rate

        // Bitrate and frame-size math differ between MPEG1 and MPEG2/2.5 Layer III: the bitrate
        // tables are distinct and MPEG2/2.5 packs half the samples per frame (576 vs 1152).
        val bitrateKbps =
            when (version) {
                3 -> MPEG1_L3_BITRATES_KBPS[bitrateIdx]
                2, 0 -> MPEG2_L3_BITRATES_KBPS[bitrateIdx]
                else -> return null // version 1 is reserved
            }
        val bitrateHz = bitrateKbps * 1000
        val sampleRateHz =
            when (version) {
                3 -> MPEG_SAMPLE_RATES_HZ[sampleRateIdx]
                2 -> MPEG2_SAMPLE_RATES_HZ[sampleRateIdx]
                0 -> MPEG25_SAMPLE_RATES_HZ[sampleRateIdx]
                else -> return null
            }
        if (sampleRateHz <= 0 || bitrateHz <= 0) return null

        // Frame size in bytes: MPEG1 Layer III = 144 * bitrate / sampleRate + padding;
        // MPEG2/2.5 Layer III halves the coefficient to 72 to match the smaller frame.
        val frameSizeCoefficient = if (version == 3) 144L else 72L
        val frameSize = (frameSizeCoefficient * bitrateHz / sampleRateHz + padding).toInt()
        if (frameSize <= 4) return null

        val samplesPerFrame = if (version == 3) MPEG1_SAMPLES_PER_FRAME else MPEG2_SAMPLES_PER_FRAME
        // Decoded PCM bytes per frame: samplesPerFrame * channels * 2 (16-bit)
        val channels = if (channelMode == 3) 1 else 2
        val pcmBytesPerFrame = samplesPerFrame.toLong() * channels * 2

        return FrameHeader(version, layer, bitrateHz, sampleRateHz, frameSize, channelMode, padding, pcmBytesPerFrame)
    }

    /**
     * Computes the file byte offset from the Xing TOC for the given requested decoded byte offset.
     */
    private fun computeTocByteOffset(
        requestedByteOffset: Long,
        xingResult: XingHeaderParser.Result,
        totalAudioBytes: Long,
        firstFrame: FrameHeader,
        audioStartOffset: Long
    ): Long? {
        val toc = xingResult.toc ?: return null
        val totalFrames = xingResult.totalFrames
        if (totalFrames <= 0L) return null

        // Convert requested decoded-byte offset to a fraction of the file
        val pcmBytesPerFrame = firstFrame.pcmBytesPerFrame
        val totalPcmBytes = totalFrames * pcmBytesPerFrame
        if (totalPcmBytes <= 0L) return null

        // Coerce strictly below 100 so a seek-to-end maps to the last decodable frame rather
        // than to (or past) EOF, which openAt would reject as fileByteOffset >= totalLen.
        val percent = (requestedByteOffset.toDouble() / totalPcmBytes * 100.0).coerceIn(0.0, 99.999)
        val fa = percent // position in TOC index space (0..100)
        val i = fa.toInt().coerceIn(0, 99)
        val tocA = toc[i].toInt() and 0xFF
        val tocB = if (i < 99) toc[i + 1].toInt() and 0xFF else tocA
        val fraction = fa - i
        val interp = tocA + fraction * (tocB - tocA)
        val byteOffset = (interp / 256.0 * totalAudioBytes).toLong()
        // Clamp below the audio end so the located offset always maps to a decodable frame
        // instead of landing at or past EOF (which openAt rejects, silently failing the seek).
        val fileOffset = audioStartOffset + byteOffset
        return fileOffset.coerceAtMost(audioStartOffset + totalAudioBytes - 1)
    }

    /** Result of the bounded MPEG frame-boundary scan. */
    private data class ScanResult(val fileOffset: Long, val pcmOffset: Long)

    /**
     * Walks MPEG frame boundaries from [audioStartOffset] until accumulated decoded-PCM bytes
     * reach [requestedByteOffset] or [MAX_SCAN_FRAMES] is exceeded.
     *
     * Returns the file byte offset and accumulated PCM byte count at the located frame,
     * or null if the cap is hit or a read error occurs.
     */
    private fun frameScanFallback(file: File, audioStartOffset: Long, requestedByteOffset: Long): ScanResult? =
        try {
            FileInputStream(file).use { fis ->
                if (skipExactly(fis, audioStartOffset) != audioStartOffset) null
                else scanForTargetFrame(fis, audioStartOffset, requestedByteOffset)
            }
        } catch (_: IOException) {
            null
        }

    /**
     * Reads consecutive frame headers from [fis] (already positioned at [startFileOffset]),
     * accumulating decoded-PCM bytes until the running total reaches [requestedByteOffset].
     * The returned `pcmOffset` is the accumulated total *before* the matched frame, so the stream
     * starts at that frame boundary. Returns null on EOF, a malformed header, or the scan cap.
     */
    private fun scanForTargetFrame(fis: InputStream, startFileOffset: Long, requestedByteOffset: Long): ScanResult? {
        val buf = ByteArray(4)
        var currentFileOffset = startFileOffset
        var accumulatedPcmBytes = 0L
        repeat(MAX_SCAN_FRAMES) {
            if (fis.read(buf, 0, 4) < 4) return null
            val header = parseFrameHeader(buf) ?: return null
            accumulatedPcmBytes += header.pcmBytesPerFrame
            if (accumulatedPcmBytes >= requestedByteOffset) {
                return ScanResult(currentFileOffset, accumulatedPcmBytes - header.pcmBytesPerFrame)
            }
            // Skip the rest of this frame (the 4 header bytes are already consumed).
            val remaining = header.frameSize - 4
            if (remaining > 0 && skipExactly(fis, remaining.toLong()) < remaining) return null
            currentFileOffset += header.frameSize
        }
        return null // cap exceeded or invalid file — fall through to skipFully
    }

    /**
     * Scans forward from [approxOffset] for the start of a valid MPEG frame, bounded by
     * [FRAME_RESYNC_MAX_SCAN] bytes. A candidate is accepted only when both it and the following
     * frame (at `pos + frameSize`) parse AND match [reference]'s version, layer, and sample rate.
     * Those fields are constant across a single MP3 (even VBR, where only the bitrate varies);
     * channel mode is deliberately excluded because LAME commonly writes the Xing/Info header
     * frame as plain stereo while the audio frames use joint stereo. Requiring two consecutive
     * matching frames rules out the frequent false-positive sync words inside compressed audio.
     *
     * @return the file byte offset of the next genuine frame boundary, or null if none is found
     *   within the scan window.
     */
    private fun findFrameBoundary(file: File, approxOffset: Long, reference: FrameHeader): Long? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val length = raf.length()
                val scanEnd = (approxOffset + FRAME_RESYNC_MAX_SCAN).coerceAtMost(length - 4)
                var pos = approxOffset.coerceIn(0L, length)
                val buf = ByteArray(4)
                while (pos <= scanEnd) {
                    raf.seek(pos)
                    raf.readFully(buf)
                    val header = parseFrameHeader(buf)
                    if (header != null && header.matchesStream(reference) && hasFrameChain(raf, pos, reference, length)) {
                        return pos
                    }
                    pos++
                }
                null
            }
        } catch (_: IOException) {
            null
        }
    }

    /**
     * Confirms a frame chain starting at [startPos] by walking [FRAME_RESYNC_CONFIRM_FRAMES]
     * consecutive frames (each found at the previous frame's `frameSize`) and requiring every one
     * to parse and match [reference]. A single false sync inside audio data rarely chains this
     * many self-consistent frames, which is what separates a real frame boundary from an ADTS-AAC
     * or in-data sync collision the JavaSound provider chain would otherwise mis-detect.
     */
    private fun hasFrameChain(raf: RandomAccessFile, startPos: Long, reference: FrameHeader, length: Long): Boolean {
        var pos = startPos
        val buf = ByteArray(4)
        repeat(FRAME_RESYNC_CONFIRM_FRAMES) {
            if (pos + 4 > length) return it >= 1 // near EOF: accept a shorter but non-trivial chain
            raf.seek(pos)
            raf.readFully(buf)
            val header = parseFrameHeader(buf) ?: return false
            if (!header.matchesStream(reference)) return false
            pos += header.frameSize
        }
        return true
    }

    /**
     * True when two frame headers share the stream-invariant fields. Bitrate and padding vary
     * per frame in VBR; channel mode is excluded because the Info-header frame and the audio
     * frames can legitimately differ (stereo vs joint stereo).
     */
    private fun FrameHeader.matchesStream(other: FrameHeader): Boolean =
        version == other.version && layer == other.layer && sampleRateHz == other.sampleRateHz

    /**
     * Opens a decoded PCM stream starting at [fileByteOffset] (an MPEG frame boundary), delegating
     * to the shared prioritized decoder so the MPEG provider is preferred over a colliding ADTS-AAC
     * sync at this byte position. Since MPEG sync words are self-synchronizing, decoding is correct
     * from any valid frame boundary.
     *
     * The [SeekablePcmStream.startByteOffset] is set to [pcmByteOffset], the decoded-PCM byte count
     * at the located frame boundary, which [CoreAudioItemPlayer] uses for accurate time reporting.
     *
     * Returns null if no provider can decode at the offset or the offset is beyond end-of-file.
     */
    private fun openAt(file: File, fileByteOffset: Long, pcmByteOffset: Long = 0L): SeekablePcmStream? {
        val stream = decodePcmStreamAt(file, fileByteOffset) ?: return null
        return SeekablePcmStream(stream, pcmByteOffset)
    }

    /** Decoded fields from a 4-byte MPEG frame header. */
    private data class FrameHeader(
        val version: Int,
        val layer: Int,
        val bitrateHz: Int,
        val sampleRateHz: Int,
        val frameSize: Int,
        val channelMode: Int,
        val padding: Int,
        val pcmBytesPerFrame: Long
    )
}

/**
 * Parses the Xing or VBRI VBR header from the first MPEG frame of an MP3 file.
 *
 * The Xing/Info header is written by LAME and most VBR encoders immediately after the
 * side-information block in the first audio frame. The VBRI header is written by the
 * Fraunhofer encoder at a fixed offset of 36 bytes from the frame start.
 *
 * Returns null when neither header is found, allowing the caller to fall through to
 * a frame-boundary scan.
 */
internal object XingHeaderParser {

    /**
     * Parsed Xing/VBRI header fields required for TOC-based seeking.
     *
     * @property totalFrames total MPEG frames in the file (-1 if not present in header)
     * @property totalBytes total audio bytes (-1 if not present in header; use file length in that case)
     * @property toc 100-entry TOC byte array for seek interpolation, or null if not present
     */
    data class Result(val totalFrames: Long, val totalBytes: Long, val toc: ByteArray?) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Result) return false
            return totalFrames == other.totalFrames &&
                totalBytes == other.totalBytes &&
                toc.contentEquals(other.toc)
        }

        override fun hashCode(): Int {
            var result = totalFrames.hashCode()
            result = 31 * result + totalBytes.hashCode()
            result = 31 * result + toc.contentHashCode()
            return result
        }
    }

    private fun ByteArray?.contentEquals(other: ByteArray?): Boolean =
        if (this == null && other == null) true
        else if (this == null || other == null) false
        else this.contentEquals(other)

    private fun ByteArray?.contentHashCode(): Int = this?.contentHashCode() ?: 0

    /**
     * Parses the Xing/Info or VBRI header from the first MPEG frame.
     *
     * Computes the correct marker position based on MPEG version and channel mode
     * (Pitfall 3: side-info sizes differ between MPEG1 and MPEG2/2.5).
     *
     * @param file the MP3 file to read
     * @param frameOffset byte offset of the first MPEG frame within the file
     * @param isMono true if the channel mode is mono
     * @param isMpeg1 true if the MPEG version is 1
     * @return parsed header result, or null if no Xing/VBRI header is present
     */
    fun parse(file: File, frameOffset: Long, isMono: Boolean, isMpeg1: Boolean): Result? =
        try {
            RandomAccessFile(file, "r").use { raf ->
                parseXing(raf, frameOffset, isMono, isMpeg1) ?: parseVbri(raf, frameOffset)
            }
        } catch (_: IOException) {
            null
        } catch (_: EOFException) {
            null
        }

    private fun parseXing(raf: RandomAccessFile, frameOffset: Long, isMono: Boolean, isMpeg1: Boolean): Result? {
        val sideInfoSize =
            when {
                isMpeg1 && isMono -> 17
                isMpeg1 -> 32
                isMono -> 9
                else -> 17
            }
        val xingStart = frameOffset + 4 + sideInfoSize
        if (raf.length() < xingStart + 8) return null

        raf.seek(xingStart)
        val marker = ByteArray(4).also { raf.readFully(it) }
        val markerStr = marker.toString(Charsets.US_ASCII)
        if (markerStr != "Xing" && markerStr != "Info") return null

        val flags = raf.readInt() // big-endian 32-bit flags
        val totalFrames = if (flags and 0x1 != 0) raf.readInt().toLong() else -1L
        val totalBytes = if (flags and 0x2 != 0) raf.readInt().toLong() else -1L
        val toc = if (flags and 0x4 != 0) ByteArray(100).also { raf.readFully(it) } else null

        return Result(totalFrames, totalBytes, toc)
    }

    private fun parseVbri(raf: RandomAccessFile, frameOffset: Long): Result? {
        // VBRI header is always at frame start + 4 + 32 = offset 36, regardless of channel mode
        val vbriStart = frameOffset + 36
        if (raf.length() < vbriStart + 26) return null

        raf.seek(vbriStart)
        val marker = ByteArray(4).also { raf.readFully(it) }
        if (marker.toString(Charsets.US_ASCII) != "VBRI") return null

        raf.skipBytes(6) // version(2) + delay(2) + quality(2)
        val totalBytes = raf.readInt().toLong() // offset 10: total bytes (big-endian)
        val totalFrames = raf.readInt().toLong() // offset 14: total frames
        val entryCount = raf.readShort().toInt() and 0xFFFF // offset 18
        raf.skipBytes(2) // scale factor
        val bytesPerEntry = raf.readShort().toInt() and 0xFFFF // offset 22
        val framesPerEntry = raf.readShort().toInt() and 0xFFFF // offset 24

        if (entryCount <= 0 || bytesPerEntry <= 0 || framesPerEntry <= 0) return Result(totalFrames, totalBytes, null)

        // Build a 100-entry normalized TOC from the VBRI table for compatibility with TOC seek
        val vbriEntries = LongArray(entryCount)
        for (i in 0 until entryCount) {
            vbriEntries[i] =
                when (bytesPerEntry) {
                    1 -> raf.read().toLong()
                    2 -> (raf.readShort().toInt() and 0xFFFF).toLong()
                    3 -> {
                        val hi = raf.read()
                        val mid = raf.read()
                        val lo = raf.read()
                        ((hi shl 16) or (mid shl 8) or lo).toLong()
                    }
                    else -> raf.readInt().toLong()
                }
        }

        // Accumulate byte offsets for 100-entry normalization
        val cumulative = LongArray(entryCount + 1)
        for (i in 0 until entryCount) cumulative[i + 1] = cumulative[i] + vbriEntries[i]
        val totalVbriBytes = cumulative[entryCount]
        if (totalVbriBytes <= 0L) return Result(totalFrames, totalBytes, null)

        val toc =
            ByteArray(100) { idx ->
                val fraction = idx.toDouble() / 100.0
                val targetOffset = (fraction * totalVbriBytes).toLong()
                // Binary search for the entry containing targetOffset
                var lo = 0
                var hi = entryCount
                while (lo < hi) {
                    val mid = (lo + hi) / 2
                    if (cumulative[mid] <= targetOffset) lo = mid + 1 else hi = mid
                }
                val entryIdx = (lo - 1).coerceAtLeast(0)
                val entryByteOffset = cumulative[entryIdx]
                // Normalize to 0..255 range (Xing TOC convention: byte = fraction/256 * totalBytes)
                ((entryByteOffset.toDouble() / totalVbriBytes) * 256.0).toInt().coerceIn(0, 255).toByte()
            }

        return Result(totalFrames, totalBytes, toc)
    }
}
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

import org.kc7bfi.jflac.FLACDecoder
import org.kc7bfi.jflac.PCMProcessor
import org.kc7bfi.jflac.frame.Frame
import org.kc7bfi.jflac.io.RandomFileInputStream
import org.kc7bfi.jflac.metadata.StreamInfo
import org.kc7bfi.jflac.util.ByteData
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream

/**
 * Opens FLAC PCM streams at a requested decoded sample offset using jflac's random-access decoder.
 */
internal object FlacPcmStreamSeeker : PcmStreamSeeker {

    override fun open(file: File, requestedByteOffset: Long): SeekablePcmStream? {
        if (!file.extension.equals("flac", ignoreCase = true) || requestedByteOffset <= 0L) return null

        val input = RandomFileInputStream(file)
        var handedOffToDecoderThread = false
        try {
            val decoder = FLACDecoder(input)
            decoder.readMetadata()
            val streamInfo = decoder.streamInfo ?: return null
            val format = pcmFormat(streamInfo) ?: return null
            val totalSamples = streamInfo.totalSamples
            if (totalSamples <= 0L) return null

            val targetFrame = requestedByteOffset / format.frameSize.toLong()
            if (targetFrame >= totalSamples) {
                val endOffset = totalSamples * format.frameSize.toLong()
                return SeekablePcmStream(
                    AudioInputStream(ByteArrayInputStream(ByteArray(0)), format, 0L),
                    endOffset
                )
            }

            decoder.seek(targetFrame) ?: return null
            val currentFrame = decoder.currentFrame()
            val skipFrames = (targetFrame - currentFrame.header.sampleNumber).coerceAtLeast(0L)
            val pipeInput = PipedInputStream(PIPE_BUFFER_SIZE)
            val pipeOutput = PipedOutputStream(pipeInput)
            val closed = AtomicBoolean(false)
            val failure = AtomicReference<IOException?>(null)
            val thread =
                Thread(
                    {
                        decodeFromCurrentFrame(decoder, input, currentFrame, skipFrames, pipeOutput, closed, failure)
                    },
                    "CoreAudioPlayer-flac-seek"
                ).apply {
                    isDaemon = true
                }
            thread.start()
            handedOffToDecoderThread = true

            val streamInput = DecoderPipeInputStream(pipeInput, thread, closed, failure)
            val remainingFrames = totalSamples - targetFrame
            return SeekablePcmStream(AudioInputStream(streamInput, format, remainingFrames), targetFrame * format.frameSize.toLong())
        } finally {
            if (!handedOffToDecoderThread) {
                runCatching { input.close() }
            }
        }
    }

    private fun decodeFromCurrentFrame(
        decoder: FLACDecoder,
        input: RandomFileInputStream,
        currentFrame: Frame,
        skipFrames: Long,
        pipeOutput: PipedOutputStream,
        closed: AtomicBoolean,
        failure: AtomicReference<IOException?>
    ) {
        try {
            val processor = PipePcmProcessor(pipeOutput, decoder.streamInfo.channels * (decoder.streamInfo.bitsPerSample / 8), skipFrames)
            var pcmData = decoder.decodeFrame(currentFrame, null)
            processor.processPCM(pcmData)

            while (!decoder.isEOF) {
                val frame = decoder.readNextFrame() ?: break
                pcmData = decoder.decodeFrame(frame, pcmData)
                processor.processPCM(pcmData)
            }
        } catch (e: IOException) {
            if (!closed.get()) {
                failure.compareAndSet(null, e)
            }
        } finally {
            runCatching { pipeOutput.close() }
            runCatching { input.close() }
        }
    }

    private fun FLACDecoder.currentFrame(): Frame =
        DECODER_FRAME_FIELD.get(this) as Frame

    private fun pcmFormat(streamInfo: StreamInfo): AudioFormat? {
        val bits = streamInfo.bitsPerSample
        if (bits !in setOf(8, 16, 24)) return null
        val channels = streamInfo.channels
        val sampleRate = streamInfo.sampleRate
        if (channels <= 0 || sampleRate <= 0) return null

        val bytesPerSample = bits / 8
        val encoding = if (bits == 8) AudioFormat.Encoding.PCM_UNSIGNED else AudioFormat.Encoding.PCM_SIGNED
        return AudioFormat(encoding, sampleRate.toFloat(), bits, channels, channels * bytesPerSample, sampleRate.toFloat(), false)
    }

    private const val PIPE_BUFFER_SIZE = 64 * 1024
    private val DECODER_FRAME_FIELD =
        FLACDecoder::class.java.getDeclaredField("frame").apply {
            isAccessible = true
        }
}

/**
 * Writes decoded FLAC PCM frames into a pipe while dropping the leading samples before the seek target.
 */
private class PipePcmProcessor(
    private val output: PipedOutputStream,
    private val bytesPerFrame: Int,
    skipFrames: Long
) : PCMProcessor {

    private var bytesToDrop = skipFrames * bytesPerFrame.toLong()

    override fun processStreamInfo(streamInfo: StreamInfo) = Unit

    override fun processPCM(pcm: ByteData) {
        var offset = 0
        var length = pcm.len
        if (bytesToDrop > 0L) {
            val dropped = minOf(bytesToDrop, length.toLong()).toInt()
            offset += dropped
            length -= dropped
            bytesToDrop -= dropped
        }
        if (length > 0) {
            output.write(pcm.data, offset, length)
        }
    }
}

/**
 * Input stream wrapper that closes the decoder pipe and rethrows asynchronous decoder failures to the reader.
 */
private class DecoderPipeInputStream(
    private val input: PipedInputStream,
    private val decoderThread: Thread,
    private val closed: AtomicBoolean,
    private val failure: AtomicReference<IOException?>
) : InputStream() {

    override fun read(): Int {
        val value = input.read()
        throwFailureIfEof(value)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val bytesRead = input.read(buffer, offset, length)
        throwFailureIfEof(bytesRead)
        return bytesRead
    }

    override fun close() {
        closed.set(true)
        runCatching { input.close() }
        decoderThread.interrupt()
    }

    private fun throwFailureIfEof(bytesRead: Int) {
        if (bytesRead < 0) {
            failure.get()?.let { throw it }
        }
    }
}
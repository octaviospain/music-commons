package net.transgressoft.commons.music.waveform;

import be.tarsos.transcoder.Attributes;
import be.tarsos.transcoder.DefaultAttributes;
import be.tarsos.transcoder.Transcoder;
import be.tarsos.transcoder.ffmpeg.EncoderException;
import org.apache.commons.io.FilenameUtils;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * @author Octavio Calleya
 */
public class InefficientAudioWaveformTool implements AudioWaveformTool<AudioWaveform> {

    private static final int DEFAULT_WIDTH = 500;
    private static final int DEFAULT_HEIGHT = 100;
    private static final CopyOption[] TEMP_COPY_ATTRIBUTES = new CopyOption[]{COPY_ATTRIBUTES, REPLACE_EXISTING};

    @Override
    public SimpleAudioWaveform extractWaveform(Path path) throws AudioWaveformProcessingException {
        return extractWaveform(path, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    @Override
    public SimpleAudioWaveform extractWaveform(Path path, int width, int height) throws AudioWaveformProcessingException {
        if (! path.toFile().exists())
            throw new AudioWaveformProcessingException("File does not exist " + path);

        String extension = FilenameUtils.getExtension(path.getFileName().toString());
        try {
            switch (extension) {
                case "wav":
                    return processWavFile(path, width, height);
                case "mp3":
                case "m4a":
                    return processNonWavFile(path, width, height);
                default:
                    throw new AudioWaveformProcessingException("File extension " + extension + "not supported");
            }
        }
        catch (UnsupportedAudioFileException | IOException | EncoderException exception) {
            throw new AudioWaveformProcessingException("Error processing waveform", exception);
        }
    }

    private SimpleAudioWaveform processWavFile(Path path, int width, int height) throws IOException, UnsupportedAudioFileException {
        int[] audioPcm = getPulseCodeModulation(path.toFile(), height);
        float[] waveformAmplitudes = getWaveformAmplitudes(audioPcm, width);
        return new SimpleAudioWaveform(waveformAmplitudes, width, height);
    }

    private SimpleAudioWaveform processNonWavFile(Path path, int width, int height) throws IOException, EncoderException, UnsupportedAudioFileException {
        File transcodedAudioFile = transcodeToWav(path);
        SimpleAudioWaveform audioWaveform = processWavFile(transcodedAudioFile.toPath(), width, height);
        Files.delete(transcodedAudioFile.toPath());
        return audioWaveform;
    }

    private File transcodeToWav(Path path) throws EncoderException, IOException {
        String fileName = path.getFileName().toString();
        File decodedFile = File.createTempFile("decoded_" + fileName, ".wav");
        File copiedFile = File.createTempFile("original_" + fileName, null);

        Files.copy(path, copiedFile.toPath(), TEMP_COPY_ATTRIBUTES);

        Attributes attributes = DefaultAttributes.WAV_PCM_S16LE_STEREO_44KHZ.getAttributes();
        try {
            Transcoder.transcode(copiedFile.toString(), decodedFile.toString(), attributes);
        }
        catch (EncoderException exception) {
            if (! exception.getMessage().startsWith("Source and target should")) {
                // even with this error message the library does the conversion, who knows why
                Files.delete(decodedFile.toPath());
                Files.delete(copiedFile.toPath());
                throw exception;
            }
        }
        Files.delete(copiedFile.toPath());
        return decodedFile;
    }

    private int[] getPulseCodeModulation(File file, int height) throws UnsupportedAudioFileException, IOException {
        int[] audioPcm;
        try (AudioInputStream input = AudioSystem.getAudioInputStream(file)) {
            AudioFormat baseFormat = input.getFormat();

            AudioFormat.Encoding encoding = AudioFormat.Encoding.PCM_UNSIGNED;
            float sampleRate = baseFormat.getSampleRate();
            int sampleSizeInBits = 16;
            int numChannels = baseFormat.getChannels();
            int frameSize = numChannels * 2;

            AudioFormat decodedFormat = new AudioFormat(encoding, sampleRate, sampleSizeInBits, numChannels, frameSize, sampleRate, false);
            int available = input.available();
            audioPcm = new int[available];

            try (AudioInputStream pcmDecodedInput = AudioSystem.getAudioInputStream(decodedFormat, input)) {
                byte[] buffer = new byte[available];
                pcmDecodedInput.read(buffer, 0, available);
                for (int i = 0; i < available - 1; i += 2) {
                    audioPcm[i] = ((buffer[i + 1] << 8) | buffer[i] & 0xff) << 16;
                    audioPcm[i] /= 32767;
                    audioPcm[i] *= height;
                }
            }
        }
        return audioPcm;
    }

    private float[] getWaveformAmplitudes(int[] audioPcm, int width) {
        float[] waveformAmplitudes = new float[width];
        int samplesPerPixel = audioPcm.length / width;
        float divisor = (float) Math.pow(Byte.SIZE * 2d, 4); // meant to be 65536.0f

        for (int w = 0; w < width; w++) {
            float amplitude = 0.0f;

            for (int s = 0; s < samplesPerPixel; s++) {
                amplitude += (Math.abs(audioPcm[w * samplesPerPixel + s]) / divisor);
            }
            amplitude /= samplesPerPixel;
            waveformAmplitudes[w] = amplitude;
        }
        return waveformAmplitudes;
    }
}

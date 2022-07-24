package net.transgressoft.commons.music.waveform;

import be.tarsos.transcoder.Attributes;
import be.tarsos.transcoder.DefaultAttributes;
import be.tarsos.transcoder.Transcoder;
import be.tarsos.transcoder.ffmpeg.EncoderException;
import com.google.common.base.Objects;
import net.transgressoft.commons.query.EntityAttribute;
import org.apache.commons.io.FilenameUtils;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringJoiner;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * @author Octavio Calleya
 */
public class ImmutableAudioWaveform implements AudioWaveform {

    private final int id;
    private final float[] amplitudes;
    private final int width;
    private final int height;

    public static AudioWaveform create(int id, Path audioFilePath, int width, int height) throws AudioWaveformProcessingException {
        float[] amplitudes = AudioWaveformExtractor.extractWaveform(audioFilePath, width, height);
        return new ImmutableAudioWaveform(id, amplitudes, width, height);
    }

    private ImmutableAudioWaveform(int id, float[] amplitudes, int width, int height) {
        this.id = id;
        this.amplitudes = amplitudes;
        this.width = width;
        this.height = height;
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public String getUniqueId() {
        StringJoiner joiner = new StringJoiner("-");
        joiner.add(String.valueOf(id));
        joiner.add(String.valueOf(width));
        joiner.add(String.valueOf(height));
        joiner.add(String.valueOf(amplitudes.length));
        return joiner.toString();
    }

    @Override
    public <A extends EntityAttribute<V>, V> V getAttribute(A attribute) {
        return null;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public float[] amplitudes() {
        return amplitudes;
    }

    @Override
    public AudioWaveform scale(int width, int height) {
        throw new UnsupportedOperationException("Not implemented");
        // TODO Do some math and figure out how to scale the amplitudes given the new width and height without processing again
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImmutableAudioWaveform that = (ImmutableAudioWaveform) o;
        return width == that.width &&
                height == that.height &&
                Objects.equal(amplitudes, that.amplitudes);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(width, height, amplitudes);
    }

    private static class AudioWaveformExtractor {

        public static float[] extractWaveform(Path path, int width, int height) throws AudioWaveformProcessingException {
            if (! path.toFile().exists())
                throw new AudioWaveformProcessingException("File does not exist " + path);

            String extension = FilenameUtils.getExtension(path.getFileName().toString());
            try {
                return switch (extension) {
                    case "wav" -> processWavFile(path, width, height);
                    case "mp3", "m4a" -> processNonWavFile(path, width, height);
                    default -> throw new AudioWaveformProcessingException("File extension " + extension + "not supported");
                };
            }
            catch (UnsupportedAudioFileException | IOException | EncoderException exception) {
                throw new AudioWaveformProcessingException("Error processing waveform", exception);
            }
        }

        private static float[] processWavFile(Path path, int width, int height) throws IOException, UnsupportedAudioFileException {
            int[] audioPcm = getPulseCodeModulation(path.toFile(), height);
            return getWaveformAmplitudes(audioPcm, width);
        }

        private static float[] processNonWavFile(Path path, int width, int height) throws IOException, EncoderException, UnsupportedAudioFileException {
            File transcodedAudioFile = transcodeToWav(path);
            float[] audioWaveform = processWavFile(transcodedAudioFile.toPath(), width, height);
            Files.delete(transcodedAudioFile.toPath());
            return audioWaveform;
        }

        private static File transcodeToWav(Path path) throws EncoderException, IOException {
            String fileName = path.getFileName().toString();
            File decodedFile = File.createTempFile("decoded_" + fileName, ".wav");
            File copiedFile = File.createTempFile("original_" + fileName, null);

            Files.copy(path, copiedFile.toPath(), COPY_ATTRIBUTES, REPLACE_EXISTING);

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

        private static int[] getPulseCodeModulation(File file, int height) throws UnsupportedAudioFileException, IOException {
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
                    if (pcmDecodedInput.read(buffer, 0, available) > 0) {
                        for (int i = 0; i < available - 1; i += 2) {
                            audioPcm[i] = ((buffer[i + 1] << 8) | buffer[i] & 0xff) << 16;
                            audioPcm[i] /= 32767;
                            audioPcm[i] *= height;
                        }
                    }
                }
            }
            return audioPcm;
        }

        private static float[] getWaveformAmplitudes(int[] audioPcm, int width) {
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
}

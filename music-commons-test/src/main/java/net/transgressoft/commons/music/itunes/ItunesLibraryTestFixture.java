package net.transgressoft.commons.music.itunes;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.NSString;
import com.dd.plist.PropertyListParser;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Materializes classpath iTunes XML fixtures against real temporary audio files.
 *
 * The helper rewrites every track {@code Location} to a copied audio sample so production import
 * paths that require {@link Path#toFile()} can be exercised without depending on the developer's
 * local music library.
 */
public final class ItunesLibraryTestFixture {

    public static final String DEFAULT_SAMPLE_AUDIO_RESOURCE = "/testfiles/testeable.mp3";

    private ItunesLibraryTestFixture() {
    }

    /**
     * Prepares an iTunes XML fixture using the default MP3 sample from music-commons-test.
     *
     * @param tempDir directory where runtime files should be written
     * @param resourceAnchor class used to locate the XML resource
     * @param xmlResource classpath path to the source XML plist
     * @return prepared runtime XML path
     */
    public static PreparedItunesLibrary prepare(Path tempDir, Class<?> resourceAnchor, String xmlResource) {
        return prepare(tempDir, resourceAnchor, xmlResource, DEFAULT_SAMPLE_AUDIO_RESOURCE);
    }

    /**
     * Prepares an iTunes XML fixture using the supplied audio sample resource.
     *
     * @param tempDir directory where runtime files should be written
     * @param resourceAnchor class used to locate the XML resource
     * @param xmlResource classpath path to the source XML plist
     * @param sampleAudioResource classpath path to the audio sample copied for every track
     * @return prepared runtime XML path
     */
    public static PreparedItunesLibrary prepare(
            Path tempDir,
            Class<?> resourceAnchor,
            String xmlResource,
            String sampleAudioResource) {
        try {
            Files.createDirectories(tempDir);
            Path audioDir = tempDir.resolve("audio");
            Files.createDirectories(audioDir);

            NSDictionary root = readXmlResource(resourceAnchor, xmlResource);
            NSDictionary tracks = tracksDictionary(root);
            for (String trackKey : tracks.allKeys()) {
                rebaseTrackLocation((NSDictionary) tracks.objectForKey(trackKey), audioDir, sampleAudioResource);
            }

            Path runtimeXml = tempDir.resolve("itunes-library-runtime.xml");
            PropertyListParser.saveAsXML(root, runtimeXml.toFile());
            return new PreparedItunesLibrary(runtimeXml);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not prepare iTunes XML fixture " + xmlResource, ex);
        }
    }

    private static NSDictionary readXmlResource(Class<?> resourceAnchor, String xmlResource) throws Exception {
        try (InputStream input = resourceStream(resourceAnchor, xmlResource)) {
            return (NSDictionary) PropertyListParser.parse(input);
        }
    }

    private static NSDictionary tracksDictionary(NSDictionary root) {
        NSObject tracks = root.objectForKey("Tracks");
        if (tracks instanceof NSDictionary tracksDictionary) {
            return tracksDictionary;
        }
        throw new IllegalStateException("Fixture XML does not contain a Tracks dictionary");
    }

    private static void rebaseTrackLocation(NSDictionary track, Path audioDir, String sampleAudioResource) {
        try {
            String trackId = text(track, "Track ID");
            Path generated = audioDir.resolve("track-" + trackId + ".mp3");
            try (InputStream input = resourceStream(ItunesLibraryTestFixture.class, sampleAudioResource)) {
                Files.copy(input, generated, StandardCopyOption.REPLACE_EXISTING);
            }
            track.put("Location", new NSString(generated.toUri().toString()));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not materialize audio file for track " + text(track, "Track ID"), ex);
        }
    }

    private static InputStream resourceStream(Class<?> resourceAnchor, String resourcePath) {
        String normalized = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
        InputStream input = resourceAnchor.getResourceAsStream(normalized);
        if (input == null) {
            input = resourceAnchor.getClassLoader().getResourceAsStream(normalized.substring(1));
        }
        if (input == null) {
            throw new IllegalStateException("Could not locate " + resourcePath);
        }
        return input;
    }

    private static String text(NSDictionary track, String key) {
        NSObject value = track.objectForKey(key);
        return value == null ? "" : value.toString();
    }

    /**
     * Runtime iTunes XML prepared for an import test.
     */
    public record PreparedItunesLibrary(Path xmlPath) {
    }
}

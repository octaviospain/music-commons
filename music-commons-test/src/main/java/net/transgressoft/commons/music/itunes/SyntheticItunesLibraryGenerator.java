package net.transgressoft.commons.music.itunes;

import net.transgressoft.commons.music.audio.AudioFileType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Generates a deterministic iTunes XML fixture from an existing directory tree.
 *
 * The generator writes a synthetic XML library and a JSON sidecar with broad filter sentinels.
 * Track metadata is intentionally deterministic so large end-to-end tests can assert import and
 * search behavior without depending on embedded tags from the source files.
 */
public final class SyntheticItunesLibraryGenerator {

    public static final String DEFAULT_LIBRARY_FILE = "compilations-library.xml";
    public static final String DEFAULT_EXPECTATIONS_FILE = "compilations-library-expectations.json";
    private static final String PLACEHOLDER_ROOT = "file:///__MUSIC_LIBRARY_FIXTURE__/";
    private static final Set<String> SUPPORTED_EXTENSIONS = Arrays.stream(AudioFileType.values())
            .map(AudioFileType::getExtension)
            .collect(Collectors.toUnmodifiableSet());

    private SyntheticItunesLibraryGenerator() {
    }

    /**
     * Generates XML and expectation sidecar files into the output directory.
     *
     * @param sourceRoot root directory containing audio files
     * @param outputDir directory where generated files are written
     * @throws Exception if the directory cannot be scanned or written
     */
    public static void generate(Path sourceRoot, Path outputDir) throws Exception {
        generate(sourceRoot, outputDir, DEFAULT_LIBRARY_FILE, DEFAULT_EXPECTATIONS_FILE);
    }

    /**
     * Generates XML and expectation sidecar files into the output directory.
     *
     * @param sourceRoot root directory containing audio files
     * @param outputDir directory where generated files are written
     * @param libraryFile output XML file name
     * @param expectationsFile output JSON sidecar file name
     * @throws Exception if the directory cannot be scanned or written
     */
    public static void generate(Path sourceRoot, Path outputDir, String libraryFile, String expectationsFile) throws Exception {
        Files.createDirectories(outputDir);
        List<Path> files = findAudioFiles(sourceRoot);
        List<TrackEntry> tracks = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            tracks.add(trackEntry(i + 1, sourceRoot, files.get(i)));
        }
        List<PlaylistEntry> playlists = playlistEntries(tracks);
        Files.writeString(outputDir.resolve(libraryFile), xml(tracks, playlists));
        Files.writeString(outputDir.resolve(expectationsFile), expectations(tracks, playlists));
    }

    private static List<Path> findAudioFiles(Path sourceRoot) throws IOException {
        try (var paths = Files.walk(sourceRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> SUPPORTED_EXTENSIONS.contains(extension(path)))
                    .sorted(Comparator.comparing(path -> sourceRoot.relativize(path).toString()))
                    .toList();
        }
    }

    private static TrackEntry trackEntry(int id, Path sourceRoot, Path file) {
        Path relative = sourceRoot.relativize(file);
        String title = "%04d %s".formatted(id, stripExtension(file.getFileName().toString()));
        String album = relative.getNameCount() > 1 ? relative.getName(0).toString() : "Compilations";
        String artist = "Compilations Artist %03d".formatted((id % 97) + 1);
        String albumArtist = "Compilations";
        String genre = "Fixture Genre";
        String comments = "Generated from " + relative;

        if (id == 1) {
            title = "Synthetic Title Sentinel";
        } else if (id == 2) {
            artist = "Synthetic Primary Artist";
        } else if (id == 3) {
            title = "Synthetic Neutral Track featuring Synthetic Guest Artist";
        } else if (id == 4) {
            album = "Synthetic Album Sentinel";
        } else if (id == 5) {
            comments = "Synthetic Related Metadata Sentinel";
        }

        return new TrackEntry(
                id,
                title,
                artist,
                albumArtist,
                album,
                genre,
                comments,
                PLACEHOLDER_ROOT + encodePath(relative),
                persistentId("track-" + id + "-" + relative),
                relative.getParent() == null ? Path.of("") : relative.getParent());
    }

    private static List<PlaylistEntry> playlistEntries(List<TrackEntry> tracks) {
        Map<Path, List<Integer>> trackIdsByDirectory = new TreeMap<>();
        for (TrackEntry track : tracks) {
            trackIdsByDirectory.computeIfAbsent(track.directory(), ignored -> new ArrayList<>()).add(track.id());
        }

        Map<Path, PlaylistEntry> folders = new LinkedHashMap<>();
        folders.put(Path.of(""), new PlaylistEntry(
                "Compilations", persistentId("folder-root"), null, true, List.of()));

        for (Path directory : trackIdsByDirectory.keySet()) {
            Path current = Path.of("");
            for (Path segment : directory) {
                Path parent = current;
                current = current.resolve(segment.toString());
                Path path = current;
                folders.computeIfAbsent(path, ignored -> new PlaylistEntry(
                        path.getFileName().toString(),
                        persistentId("folder-" + path),
                        folders.get(parent).persistentId(),
                        true,
                        List.of()));
            }
        }

        List<PlaylistEntry> playlists = new ArrayList<>(folders.values());
        for (Map.Entry<Path, List<Integer>> entry : trackIdsByDirectory.entrySet()) {
            Path directory = entry.getKey();
            PlaylistEntry folder = folders.get(directory);
            String name = directory.getFileName() == null ? "Compilations Tracks" : directory.getFileName() + " Tracks";
            playlists.add(new PlaylistEntry(
                    name,
                    persistentId("playlist-" + directory),
                    folder.persistentId(),
                    false,
                    List.copyOf(entry.getValue())));
        }
        return playlists;
    }

    private static String xml(List<TrackEntry> tracks, List<PlaylistEntry> playlists) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple Computer//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                    <key>Tracks</key>
                    <dict>
                """);
        for (TrackEntry track : tracks) {
            xml.append("        <key>").append(track.id()).append("</key>\n");
            xml.append("        <dict>\n");
            keyInteger(xml, "Track ID", track.id());
            keyString(xml, "Name", track.title());
            keyString(xml, "Artist", track.artist());
            keyString(xml, "Album Artist", track.albumArtist());
            keyString(xml, "Album", track.album());
            keyString(xml, "Genre", track.genre());
            keyInteger(xml, "Year", 2026);
            keyInteger(xml, "Track Number", track.id());
            keyInteger(xml, "Disc Number", 1);
            keyInteger(xml, "Total Time", 180000);
            keyInteger(xml, "Bit Rate", 320);
            keyInteger(xml, "Play Count", track.id() % 11);
            keyString(xml, "Comments", track.comments());
            keyInteger(xml, "Compilation", 1);
            keyString(xml, "Persistent ID", track.persistentId());
            keyString(xml, "Date Added", LocalDateTime.of(2026, 1, 1, 0, 0)
                    .format(DateTimeFormatter.ISO_DATE_TIME) + "Z");
            keyString(xml, "Location", track.location());
            xml.append("        </dict>\n");
        }
        xml.append("""
                    </dict>
                    <key>Playlists</key>
                    <array>
                """);
        for (PlaylistEntry playlist : playlists) {
            xml.append("        <dict>\n");
            keyString(xml, "Name", playlist.name());
            keyString(xml, "Playlist Persistent ID", playlist.persistentId());
            if (playlist.parentPersistentId() != null) {
                keyString(xml, "Parent Persistent ID", playlist.parentPersistentId());
            }
            if (playlist.folder()) {
                keyInteger(xml, "Folder", 1);
            }
            if (!playlist.trackIds().isEmpty()) {
                xml.append("            <key>Playlist Items</key>\n");
                xml.append("            <array>\n");
                for (Integer trackId : playlist.trackIds()) {
                    xml.append("                <dict><key>Track ID</key><integer>")
                            .append(trackId)
                            .append("</integer></dict>\n");
                }
                xml.append("            </array>\n");
            }
            xml.append("        </dict>\n");
        }
        xml.append("""
                    </array>
                </dict>
                </plist>
                """);
        return xml.toString();
    }

    private static String expectations(List<TrackEntry> tracks, List<PlaylistEntry> playlists) {
        return """
                {
                  "trackCount": %d,
                  "playlistCount": %d,
                  "allTracksFilters": [
                    { "name": "title", "query": "Synthetic Title Sentinel", "expectedCount": 1 },
                    { "name": "artist", "query": "Synthetic Primary Artist", "expectedCount": 1 },
                    { "name": "artistInvolved", "query": "Synthetic Guest Artist", "expectedCount": 1 },
                    { "name": "album", "query": "Synthetic Album Sentinel", "expectedCount": 1 },
                    { "name": "relatedMetadata", "query": "Synthetic Related Metadata Sentinel", "expectedCount": 1 }
                  ],
                  "artistsFilters": [
                    { "name": "artist", "query": "Synthetic Primary Artist", "expectedCount": 1 },
                    { "name": "title", "query": "Synthetic Title Sentinel", "expectedCount": 1 },
                    { "name": "artistInvolved", "query": "Synthetic Guest Artist", "expectedCount": 2 },
                    { "name": "album", "query": "Synthetic Album Sentinel", "expectedCount": 1 },
                    { "name": "relatedMetadata", "query": "Synthetic Related Metadata Sentinel", "expectedCount": 1 }
                  ]
                }
                """.formatted(tracks.size(), playlists.size());
    }

    private static void keyString(StringBuilder xml, String key, String value) {
        xml.append("            <key>").append(key).append("</key><string>")
                .append(escapeXml(value))
                .append("</string>\n");
    }

    private static void keyInteger(StringBuilder xml, String key, int value) {
        xml.append("            <key>").append(key).append("</key><integer>")
                .append(value)
                .append("</integer>\n");
    }

    private static String persistentId(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append("%02X".formatted(digest[i]));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not compute persistent id", ex);
        }
    }

    private static String extension(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    private static String encodePath(Path path) {
        return path.toString().replace('\\', '/').replace(" ", "%20");
    }

    private static String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private record TrackEntry(
            int id,
            String title,
            String artist,
            String albumArtist,
            String album,
            String genre,
            String comments,
            String location,
            String persistentId,
            Path directory) {
    }

    private record PlaylistEntry(
            String name,
            String persistentId,
            String parentPersistentId,
            boolean folder,
            List<Integer> trackIds) {
    }
}

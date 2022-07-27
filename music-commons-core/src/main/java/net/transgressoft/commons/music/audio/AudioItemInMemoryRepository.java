package net.transgressoft.commons.music.audio;

import com.neovisionaries.i18n.CountryCode;
import net.transgressoft.commons.event.QueryEventDispatcher;
import org.apache.commons.io.FilenameUtils;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static net.transgressoft.commons.music.audio.StringAudioItemAttribute.ALBUM;

public class AudioItemInMemoryRepository extends AudioItemInMemoryRepositoryBase<AudioItem> {

    private final Logger log = LoggerFactory.getLogger(AudioItemInMemoryRepository.class.getName());

    public AudioItemInMemoryRepository(Map<Integer, AudioItem> audioItems) {
        super(audioItems);
    }

    public AudioItemInMemoryRepository(QueryEventDispatcher<AudioItem> eventDispatcher) {
        this(new HashMap<>(), eventDispatcher);
    }

    public AudioItemInMemoryRepository(Map<Integer, AudioItem> audioItems, QueryEventDispatcher<AudioItem> eventDispatcher) {
        super(audioItems, eventDispatcher);
    }

    @Override
    public AudioItem createFromFile(Path path) throws AudioItemManipulationException {
        requireNonNull(path);
        if (Files.notExists(path)) {
            throw new IllegalArgumentException("File " + path.toAbsolutePath() + " does not exist");
        }

        var audioItem = readAudioItem(path);
        add(audioItem);
        return audioItem;
    }

    protected AudioItem readAudioItem(Path path) throws AudioItemManipulationException {
        return new JAudioTaggerMetadataReader().readAudioItem(path);
    }

    private class JAudioTaggerMetadataReader {

        private AudioItem readAudioItem(Path audioItemPath) throws AudioItemManipulationException {
            log.debug("Parsing file {}", audioItemPath);

            File audioItemFile = audioItemPath.toFile();
            String extension = FilenameUtils.getExtension(audioItemFile.getName());
            AudioItemBuilder<AudioItem> audioItemBuilder;

            try {
                AudioFile audioFile = AudioFileIO.read(audioItemFile);
                Tag tag = audioFile.getTag();

                String name = tag.hasField(FieldKey.TITLE) ? tag.getFirst(FieldKey.TITLE) : "";
                AudioHeader audioHeader = audioFile.getAudioHeader();
                String encoding = audioHeader.getEncodingType();
                Duration duration = Duration.ofSeconds(audioHeader.getTrackLength());
                int bitRate = getBitRate(audioHeader);

                audioItemBuilder = new ImmutableAudioItemBuilder(getNewId(), audioItemPath, name, duration, bitRate, LocalDateTime.now());
                audioItemBuilder.encoding(encoding);

                readMetadata(audioItemBuilder, tag);
                readAlbum(audioItemBuilder, extension, tag);
            }
            catch (IOException | CannotReadException | ReadOnlyFileException | TagException | InvalidAudioFrameException exception) {
                log.debug("Error parsing file {}: ", audioItemPath, exception);
                throw new AudioItemManipulationException("Error parsing file " + audioItemPath, exception);
            }

            return audioItemBuilder.build();
        }

        private int getBitRate(AudioHeader audioHeader) {
            String bitRate = audioHeader.getBitRate();
            if ("~".equals(bitRate.substring(0, 1))) {
                return Integer.parseInt(bitRate.substring(1));
            } else {
                return Integer.parseInt(bitRate);
            }
        }

        private void readMetadata(AudioItemBuilder<AudioItem> builder, Tag tag) {
            if (tag.hasField(FieldKey.ARTIST)) {
                if (tag.hasField(FieldKey.COUNTRY)) {
                    String country = tag.getFirst(FieldKey.COUNTRY);
                    List<CountryCode> possibleCountries = CountryCode.findByName(country);
                    CountryCode countryCode = possibleCountries.isEmpty() ? CountryCode.UNDEFINED : possibleCountries.get(0);
                    builder.artist(new ImmutableArtist(tag.getFirst(FieldKey.ARTIST), countryCode));
                } else {
                    builder.artist(new ImmutableArtist(tag.getFirst(FieldKey.ARTIST)));
                }
            }
            if (tag.hasField(FieldKey.GENRE)) {
                builder.genre(Genre.parseGenre(tag.getFirst(FieldKey.GENRE)));
            }
            if (tag.hasField(FieldKey.COMMENT)) {
                builder.comments(tag.getFirst(FieldKey.COMMENT));
            }
            if (tag.hasField(FieldKey.ENCODER)) {
                builder.encoder(tag.getFirst(FieldKey.ENCODER));
            }
            if (tag.hasField(FieldKey.BPM)) {
                try {
                    int bpm = Integer.parseInt(tag.getFirst(FieldKey.BPM));
                    builder.bpm(bpm < 1 ? 0 : bpm);
                }
                catch (NumberFormatException e) {
                }
            }
            if (tag.hasField(FieldKey.DISC_NO)) {
                try {
                    short dn = Short.parseShort(tag.getFirst(FieldKey.DISC_NO));
                    builder.discNumber(dn < 1 ? 0 : dn);
                }
                catch (NumberFormatException e) {
                }
            }
            if (tag.hasField(FieldKey.TRACK)) {
                try {
                    short trackNumber = Short.parseShort(tag.getFirst(FieldKey.TRACK));
                    builder.trackNumber(trackNumber < 1 ? 0 : trackNumber);
                }
                catch (NumberFormatException e) {
                }
            }
        }

        private void readAlbum(AudioItemBuilder<AudioItem> builder, String extension, Tag tag) {
            final Album album;

            String albumName = "";
            Artist albumArtist = ImmutableArtist.UNKNOWN_ARTIST;
            boolean isCompilation = false;
            short year = -1;
            Label label = ImmutableLabel.UNKNOWN;
            byte[] coverBytes = new byte[0];

            if (tag.hasField(FieldKey.ALBUM))
                albumName = tag.getFirst(FieldKey.ALBUM);

            if (tag.hasField(FieldKey.ALBUM_ARTIST)) {
                String artistAlbumName = tag.getFirst(FieldKey.ALBUM_ARTIST);
                albumArtist = new ImmutableArtist(artistAlbumName);
            }
            if (tag.hasField(FieldKey.GROUPING)) {
                String labelName = tag.getFirst(FieldKey.GROUPING);
                label = new ImmutableLabel(labelName);
            }
            if (tag.hasField(FieldKey.YEAR)) {
                try {
                    year = Short.parseShort(tag.getFirst(FieldKey.YEAR));
                }
                catch (NumberFormatException e) {
                }
            }

            if (tag.hasField(FieldKey.IS_COMPILATION)) {
                if ("m4a".equals(extension))
                    isCompilation = "1".equals(tag.getFirst(FieldKey.IS_COMPILATION));
                else
                    isCompilation = "true".equals(tag.getFirst(FieldKey.IS_COMPILATION));
            }

            if (!tag.getArtworkList().isEmpty())
                coverBytes = tag.getFirstArtwork().getBinaryData();

            album = new ImmutableAlbum(albumName, albumArtist, isCompilation, year, label, coverBytes) {
                @Override
                public Set<AudioItem> audioItems() {
                    return new HashSet<>(search(ALBUM.equalsTo(name())));
                }
            };

            builder.album(album);
        }
    }
}

package com.transgressoft.commons.music;

import com.neovisionaries.i18n.CountryCode;
import org.apache.commons.io.*;
import org.jaudiotagger.audio.*;
import org.jaudiotagger.audio.exceptions.*;
import org.jaudiotagger.tag.*;
import org.slf4j.*;

import java.io.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/**
 * @author Octavio Calleya
 */
public class JAudioTaggerMetadataParser implements AudioItemMetadataParser {

    private final Logger LOG = LoggerFactory.getLogger(getClass().getName());

    @Override
    public AudioItem parseAudioItem(Path audioItemPath) throws AudioItemManipulationException {
        LOG.debug("Parsing file {}", audioItemPath);

        var audioItemFile = audioItemPath.toFile();
        var extension = FilenameUtils.getExtension(audioItemFile.getName());
        SimpleAudioItem.SimpleAudioItemBuilder audioItemBuilder;

        try {
            var audioFile = AudioFileIO.read(audioItemFile);
            var tag = audioFile.getTag();

            var name = tag.hasField(FieldKey.TITLE) ? tag.getFirst(FieldKey.TITLE) : "";
            var audioHeader = audioFile.getAudioHeader();
            var encoding = audioHeader.getEncodingType();
            var duration = Duration.ofSeconds(audioHeader.getTrackLength());
            var bitRate = getBitRate(audioHeader);

            audioItemBuilder = SimpleAudioItem.builder(audioItemPath, name, duration, bitRate);
            audioItemBuilder.encoding(encoding);

            parseMetadata(audioItemBuilder, tag);
            parseAlbum(audioItemBuilder, extension, tag);
        }
        catch (IOException | CannotReadException | ReadOnlyFileException | TagException | InvalidAudioFrameException exception) {
            LOG.debug("Error parsing file {}: ", audioItemPath, exception);
            throw new AudioItemManipulationException("Error parsing file " + audioItemPath, exception);
        }

        return audioItemBuilder.build();
    }

    private int getBitRate(AudioHeader audioHeader) {
        var bitRate = audioHeader.getBitRate();
        if ("~".equals(bitRate.substring(0, 1))) {
            return Integer.parseInt(bitRate.substring(1));
        }
        else {
            return Integer.parseInt(bitRate);
        }
    }

    private void parseMetadata(SimpleAudioItem.SimpleAudioItemBuilder builder, Tag tag) {
        if (tag.hasField(FieldKey.ARTIST)) {
            if (tag.hasField(FieldKey.COUNTRY)) {
                var country = tag.getFirst(FieldKey.COUNTRY);
                var possibleCountries = CountryCode.findByName(country);
                var countryCode = possibleCountries.isEmpty() ? CountryCode.UNDEFINED : possibleCountries.get(0);
                builder.artist(new SimpleArtist(tag.getFirst(FieldKey.ARTIST), countryCode));
            } else {
                builder.artist(new SimpleArtist(tag.getFirst(FieldKey.ARTIST)));
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
                var bpm = Integer.parseInt(tag.getFirst(FieldKey.BPM));
                builder.bpm(bpm < 1 ? 0 : bpm);
            }
            catch (NumberFormatException e) {
            }
        }
        if (tag.hasField(FieldKey.DISC_NO)) {
            try {
                var dn = Short.parseShort(tag.getFirst(FieldKey.DISC_NO));
                builder.discNumber(dn < 1 ? 0 : dn);
            }
            catch (NumberFormatException e) {
            }
        }
        if (tag.hasField(FieldKey.TRACK)) {
            try {
                var trackNumber = Short.parseShort(tag.getFirst(FieldKey.TRACK));
                builder.trackNumber(trackNumber < 1 ? 0 : trackNumber);
            }
            catch (NumberFormatException e) {
            }
        }
    }

    private void parseAlbum(SimpleAudioItem.SimpleAudioItemBuilder builder, String extension, Tag tag) {
        final Album album;

        var albumName = "";
        var albumArtist = SimpleArtist.UNKNOWN;
        var isCompilation = false;
        short year = - 1;
        var label = SimpleLabel.UNKNOWN;
        var coverBytes = new byte[0];

        if (tag.hasField(FieldKey.ALBUM))
            albumName = tag.getFirst(FieldKey.ALBUM);

        if (tag.hasField(FieldKey.ALBUM_ARTIST)) {
            var artistAlbumName = tag.getFirst(FieldKey.ALBUM_ARTIST);
            albumArtist = new SimpleArtist(artistAlbumName);
        }
        if (tag.hasField(FieldKey.GROUPING)) {
            var labelName = tag.getFirst(FieldKey.GROUPING);
            label = new SimpleLabel(labelName);
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

        if (! tag.getArtworkList().isEmpty())
            coverBytes = tag.getFirstArtwork().getBinaryData();

        album = new SimpleAlbum(albumName, albumArtist, isCompilation, year, label, coverBytes);

        builder.album(album);
    }
}

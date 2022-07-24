package net.transgressoft.commons.music.playlist;

/**
 * @author Octavio Calleya
 */
public class ExportException extends Exception {

    public ExportException(String message) {
        super(message);
    }

    public ExportException(String message, Throwable exception) {
        super(message, exception);
    }
}

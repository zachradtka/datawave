package datawave.encryption;

public class DatawaveEncryptionException extends Exception {
    public DatawaveEncryptionException(String message) {
        super(message);
    }

    public DatawaveEncryptionException(String message, Throwable initCause) {
        super(message, initCause);
    }
}

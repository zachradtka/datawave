package datawave.encryption;

import gov.nsa.dp.eel.EncryptionManager;
import gov.nsa.dp.eel.EncryptionResult;
import gov.nsa.dp.eel.EncryptionServiceException;
import gov.nsa.dp.eel.provider.java.AesGcmEnvelopeBuilder;
import gov.nsa.dp.eel.provider.java.JavaEncryptionProvider;
import gov.nsa.dp.eel.spi.EelEncrypter;
import gov.nsa.dp.eel.spi.EncryptionProvider;
import gov.nsa.dp.eel.spi.WrappedKey;
import org.apache.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Encryption Utility
 *
 */
public class Encrypter {
    
    private static final Logger log = Logger.getLogger(Encrypter.class);
    
    static {
        // route eel-j logs to slf4j/log4j
        org.slf4j.bridge.SLF4JBridgeHandler.removeHandlersForRootLogger();
        org.slf4j.bridge.SLF4JBridgeHandler.install();
        java.util.logging.LogManager.getLogManager().getLogger("").setLevel(java.util.logging.Level.INFO);
    }
    
    private final EelEncrypter encrypter;
    private final Base64.Encoder encoder = Base64.getEncoder();
    private int encodedIvLength = -1;
    
    /** Build an encrypter for the list of specified recipients */
    public Encrypter(List<X509Certificate> recipients) throws DatawaveEncryptionException {
        EncryptionManager manager = EncryptionManager.getInstance();
        EncryptionProvider p = manager.getEncryptionProvider(JavaEncryptionProvider.PROVIDER_NAME);
        JavaEncryptionProvider provider = (JavaEncryptionProvider) p;
        AesGcmEnvelopeBuilder aesGcmBuilder = provider.getAesGcmEnvelopeBuilder();
        aesGcmBuilder.encrypt().setKeySizeInBits(256);
        for (X509Certificate c : recipients) {
            aesGcmBuilder.addRecipientCertificate(c);
        }
        
        try {
            encrypter = aesGcmBuilder.build().getEncrypter();
        } catch (EncryptionServiceException e) {
            throw new DatawaveEncryptionException(e.getMessage(), e);
        }
    }
    
    /**
     * Returns a Base64 encoded encrypted string. The first N characters are the encoded initialization vector while the last characters are the encoded
     * ciphetext. Has the side-effect of updating the encodedIvLength variable with the actual lenth of the Base64 encoded iv.
     * 
     * @param plaintext
     *            plaintext message to encrypt
     * @return base64 concaenated, encoded iv and cyphertext
     * @throws DatawaveEncryptionException
     *             if there is a problem performing encryption
     */
    public String getEncodedEncryptedString(String plaintext) throws DatawaveEncryptionException {
        try {
            byte[] plainBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            EncryptionResult<byte[]> result = encrypter.encrypt(plainBytes);
            byte[] cipherBytes = result.getResult();
            byte[] initVector = result.getIv();
            
            StringBuilder b = new StringBuilder();
            
            // TODO: we may be able to obtain the iv length in some other way, for now we just record it here
            // the size *should not* change throughout the life of the Encryptor, but if it does, log
            // a warning.
            String encodedIvString = new String(encoder.encode(initVector));
            if (encodedIvLength > 0 && encodedIvLength != encodedIvString.length()) {
                log.warn("Encountered nexpected change in iv length, was " + encodedIvLength + ", is now " + encodedIvString.length());
            }
            encodedIvLength = encodedIvString.length();
            
            b.append(encodedIvString);
            b.append(new String(encoder.encode(cipherBytes)));
            return b.toString();
        } catch (EncryptionServiceException e) {
            throw new DatawaveEncryptionException(e.getMessage(), e);
        }
    }
    
    public int getEncodedIvLength() {
        return encodedIvLength;
    }
    
    /**
     * Returns a series of Entries containing a principalIdentifier and a base64 encoded wrapped key as the value the size of the returned object should
     * correspond to the size of the recipient list provided in the constructor.
     * 
     * @return a map of wrapped keys - the key is the principal identifier and value is the bas64 encoded wrapped key
     */
    public Map<String,String> getEncodedWrappedKeys() {
        List<WrappedKey> k = encrypter.getWrappedKeys();
        Map<String,String> result = new HashMap<>();
        for (WrappedKey kk : k) {
            X509Certificate cert = kk.getCertificate();
            String principalIdentifier = WrappedKey.formatPrincipalIdentifierString(cert);
            result.put(principalIdentifier, kk.getWrappedKeyBase64());
        }
        return result;
    }
    
}

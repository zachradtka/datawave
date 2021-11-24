package datawave.encryption;

import gov.nsa.dp.eel.EncryptionManager;
import gov.nsa.dp.eel.EncryptionServiceException;
import gov.nsa.dp.eel.provider.java.AesGcmEnvelopeBuilder;
import gov.nsa.dp.eel.provider.java.JavaDecrypter;
import gov.nsa.dp.eel.provider.java.JavaEncryptionProvider;
import gov.nsa.dp.eel.spi.EncryptionProvider;
import gov.nsa.dp.eel.spi.WrappedKey;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;

/**
 * Decruption Utility
 *
 */
public class Decrypter {
    
    /*
     * static { // route eel-j logs to slf4j/log4j org.slf4j.bridge.SLF4JBridgeHandler.removeHandlersForRootLogger();
     * org.slf4j.bridge.SLF4JBridgeHandler.install(); java.util.logging.LogManager.getLogManager().getLogger("").setLevel(Level.INFO); }
     */
    
    private final JavaDecrypter decrypter;
    private final Base64.Decoder decoder = Base64.getDecoder();
    private final int encodedIvLength;
    
    private final String principalIdentifier;
    
    public Decrypter(String principalIdentifier, PrivateKey privateKey, int encodedIvLength) throws DatawaveEncryptionException {
        this.principalIdentifier = principalIdentifier;
        this.encodedIvLength = encodedIvLength;
        
        EncryptionManager manager = EncryptionManager.getInstance();
        EncryptionProvider p = manager.getEncryptionProvider(JavaEncryptionProvider.PROVIDER_NAME);
        JavaEncryptionProvider provider = (JavaEncryptionProvider) p;
        
        try {
            AesGcmEnvelopeBuilder aesGcmBuilder = provider.getAesGcmEnvelopeBuilder().decrypt().setPrivateKey(privateKey).build();
            decrypter = aesGcmBuilder.getDecrypter();
        } catch (EncryptionServiceException e) {
            throw new DatawaveEncryptionException(e.getMessage(), e);
        }
    }
    
    public byte[] findWrappedKeyForMe(Map<String,String> encodedWrappedKeys) {
        String encodedKey = encodedWrappedKeys.get(principalIdentifier);
        return decoder.decode(encodedKey);
    }
    
    public String getDecryptedEncodedString(byte[] wrappedKeyBytes, String cyphertext) throws DatawaveEncryptionException {
        try {
            String ivb64 = cyphertext.substring(0, encodedIvLength);
            String ctb64 = cyphertext.substring(encodedIvLength);
            byte[] iv = decoder.decode(ivb64);
            byte[] ct = decoder.decode(ctb64);
            byte[] pt = decrypter.decrypt(ct, wrappedKeyBytes, iv);
            return new String(pt);
        } catch (EncryptionServiceException e) {
            throw new DatawaveEncryptionException(e.getMessage(), e);
        }
    }
    
    public static Decrypter getDecrypter(KeyStore keyStore, String password, String aliasName, int ivLength) throws DatawaveEncryptionException {
        try {
            X509Certificate cert = (X509Certificate) keyStore.getCertificate(aliasName);
            String principlaIdentifier = WrappedKey.formatPrincipalIdentifierString(cert);
            PrivateKey pk = (PrivateKey) keyStore.getKey(aliasName, password.toCharArray());
            return new Decrypter(principlaIdentifier, pk, ivLength);
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
            throw new DatawaveEncryptionException(e.getMessage(), e);
        }
    }
}

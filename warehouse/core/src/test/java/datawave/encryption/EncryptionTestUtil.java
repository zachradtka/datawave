package datawave.encryption;

import gov.nsa.dpdrm.utils.pki.PemReader;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

public class EncryptionTestUtil {
    
    final X509Certificate serverCert;
    final X509Certificate clientCert;
    final KeyStore serverKeystore;
    
    static final String serverKeystorePassword = "Changeit1";
    static final String serverAlias = "prototype-server";
    static final int ivLength = 16;
    
    public static final String text = "The rain in spain falls mainly on the plain";
    
    public EncryptionTestUtil() throws Exception {
        serverCert = EncryptionTestUtil.loadCertFromFile("/pki/prototype-server.pem");
        clientCert = EncryptionTestUtil.loadCertFromFile("/pki/prototype-client.pem");
        serverKeystore = EncryptionTestUtil.loadKeystoreFromFile("/pki/prototype-server-keystore.p12", serverKeystorePassword);
    }
    
    public X509Certificate getTestServerCert() {
        return serverCert;
    }
    
    public X509Certificate getTestClientCert() {
        return clientCert;
    }
    
    public KeyStore getTestServerKeystore() {
        return serverKeystore;
    }
    
    public Decrypter getTestServerDecrypter() throws DatawaveEncryptionException {
        return Decrypter.getDecrypter(serverKeystore, serverKeystorePassword, serverAlias, ivLength);
    }
    
    public Encrypter getTestServerEncrypter() throws DatawaveEncryptionException {
        return getTestEncrypter(Collections.singletonList(serverCert));
    }
    
    public Encrypter getTestEncrypter(List<X509Certificate> recipients) throws DatawaveEncryptionException {
        return new Encrypter(recipients);
    }
    
    public static X509Certificate loadCertFromFile(String pemLocation) throws Exception {
        // probably don't need to depend on this here - just use the underlying java classes.
        PemReader pemReader = new PemReader();
        InputStream input = EncrypterDecrypterTest.class.getResourceAsStream(pemLocation);
        Certificate cert = pemReader.generateCert(input);
        return (X509Certificate) cert;
    }
    
    public static KeyStore loadKeystoreFromFile(String keystoreLocation, String password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        InputStream input = EncrypterDecrypterTest.class.getResourceAsStream(keystoreLocation);
        if (input == null)
            throw new IOException("Unable to load keystore from: " + keystoreLocation);
        keyStore.load(input, password.toCharArray());
        return keyStore;
    }
}

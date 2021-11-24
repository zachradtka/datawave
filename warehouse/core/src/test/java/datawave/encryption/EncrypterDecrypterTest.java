package datawave.encryption;

import org.junit.Before;
import org.junit.Test;

import java.security.cert.X509Certificate;
import java.util.ArrayList;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class EncrypterDecrypterTest {
    
    X509Certificate cert1;
    X509Certificate cert2;
    
    public static final String text = "The rain in spain falls mainly on the plain";
    
    EncryptionTestUtil encryptionTestUtil;
    
    @Before
    public void loadPKI() throws Exception {
        encryptionTestUtil = new EncryptionTestUtil();
        cert1 = encryptionTestUtil.getTestServerCert();
        cert2 = encryptionTestUtil.getTestClientCert();
    }
    
    @Test
    public void testEncrypter() throws Exception {
        Encrypter en = encryptionTestUtil.getTestServerEncrypter();
        
        Map<String,String> encodedWrappedKeys = en.getEncodedWrappedKeys();
        String encodedString = en.getEncodedEncryptedString(text);
        
        assertEquals(1, encodedWrappedKeys.size());
        assertFalse(encodedString.contains(text));
        assertFalse(text.contains(encodedString));
    }
    
    @Test
    public void testEncrypterMulti() throws Exception {
        List<X509Certificate> certs = new ArrayList<>();
        certs.add(cert1);
        certs.add(cert2);
        Encrypter en = encryptionTestUtil.getTestEncrypter(certs);
        
        Map<String,String> encodedWrappedKeys = en.getEncodedWrappedKeys();
        String encodedString = en.getEncodedEncryptedString(text);
        
        assertEquals(2, encodedWrappedKeys.size());
        assertFalse(encodedString.contains(text));
        assertFalse(text.contains(encodedString));
    }
    
    @Test
    public void testEncryptDecrypt() throws Exception {
        List<X509Certificate> certs = new ArrayList<>();
        certs.add(cert1);
        certs.add(cert2);
        Encrypter en = encryptionTestUtil.getTestEncrypter(certs);
        Decrypter de = encryptionTestUtil.getTestServerDecrypter();
        
        Map<String,String> encodedWrappedKeys = en.getEncodedWrappedKeys();
        String encodedString = en.getEncodedEncryptedString(text);
        
        assertEquals(2, encodedWrappedKeys.size());
        assertFalse(encodedString.contains(text));
        assertFalse(text.contains(encodedString));
        
        byte[] wrappedKeyBytes = de.findWrappedKeyForMe(encodedWrappedKeys);
        String cleartext = de.getDecryptedEncodedString(wrappedKeyBytes, encodedString);
        assertEquals(text, cleartext);
    }
}

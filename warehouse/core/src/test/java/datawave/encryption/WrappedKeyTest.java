package datawave.encryption;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WrappedKeyTest {
    @Test
    public void testWrappedKey() throws Exception {
        String inputString =
                "{\"WrappedKey\": [ " +
                "{\"SubjectDn\": \"CN=prototype.test.server.internal,OU=Prototype Test Server,O=Prototype,L=Columbia,ST=Maryland,C=US\", " +
                "\"SerialHex\": \"a02a5db37291b252967dd77f5fa1269b453dc3d\", " +
                "\"SerialDecimal\": \"57148957320973527123520764820318336941055794237\" " +
                "} ] }";

        WrappedKey k = WrappedKey.deserialize(inputString);
        WrappedKey.Entry e = k.entries.get(0);
        assertEquals("CN=prototype.test.server.internal,OU=Prototype Test Server,O=Prototype,L=Columbia,ST=Maryland,C=US", e.subjectDn);
        assertEquals("a02a5db37291b252967dd77f5fa1269b453dc3d", e.serialHex);
        assertEquals("57148957320973527123520764820318336941055794237", e.serialDecimal);
    }
}

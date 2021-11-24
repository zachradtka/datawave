package datawave.encryption;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class WrappedKey {
    @JsonProperty("WrappedKey")
    List<Entry> entries = new ArrayList<>();

    public Entry get(int i) {
        return entries.get(i);
    }

    public static class Entry {
        @JsonProperty("SubjectDn")
        String subjectDn;
        @JsonProperty("SerialHex")
        String serialHex;
        @JsonProperty("SerialDecimal")
        String serialDecimal;

        public String getSubjectDn() {
            return subjectDn;
        }

        public String getSerialHex() {
            return serialHex;
        }

        public String getSerialDecimal() {
            return serialDecimal;
        }
    }

    public static WrappedKey deserialize(String jsonString) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(jsonString, WrappedKey.class);
    }
}

package datawave.webservice.query.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import datawave.encryption.Encrypter;
import datawave.encryption.EncryptionTestUtil;
import datawave.encryption.WrappedKey;
import datawave.webservice.query.result.event.DefaultEvent;
import datawave.webservice.query.result.event.DefaultField;
import datawave.webservice.query.result.event.DefaultResponseObjectFactory;
import datawave.webservice.query.result.event.EncryptedPayload;
import datawave.webservice.query.result.event.EncryptedKey;
import datawave.webservice.query.result.event.EventBase;

import datawave.webservice.query.result.event.FieldBase;
import datawave.webservice.query.result.event.ResponseObjectFactory;
import datawave.webservice.result.DefaultEventQueryResponse;
import datawave.webservice.result.EventQueryResponseBase;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class EncryptedEventQueryResponseSerializationTest {
    
    private static final Logger log = LoggerFactory.getLogger(EncryptedEventQueryResponseSerializationTest.class);

    EncryptionTestUtil encryptionTestUtil;
    ResponseObjectFactory responseObjectFactory;

    @Before
    public void setUp() throws Exception {
        encryptionTestUtil = new EncryptionTestUtil();
        responseObjectFactory = new DefaultResponseObjectFactory();
    }
    
    @Test
    public void testEmpty() throws Exception {
        DefaultEventQueryResponse resp = new DefaultEventQueryResponse();
        String result = marshallToString(DefaultEventQueryResponse.class, resp);
        log.info("\n" + result);
    }
    
    @SuppressWarnings("rawtypes")
    @Test
    public void testHandRolledOptionFour() throws Exception {
        Encrypter encrypter = encryptionTestUtil.getTestServerEncrypter();

        DefaultEventQueryResponse response = new DefaultEventQueryResponse();
        List<EventBase> eventList = new ArrayList<>();
        
        // single event here.
        DefaultEvent evt = new DefaultEvent();
        
        // do wrapped keys here.
        List<EncryptedKey> eks = new ArrayList<>();
        for (Map.Entry<String, String> ee: encrypter.getEncodedWrappedKeys().entrySet()) {
            EncryptedKey encryptedKey = new EncryptedKey();
            WrappedKey wk = WrappedKey.deserialize(ee.getKey());
            WrappedKey.Entry wke = wk.get(0);
            encryptedKey.setCipherText(ee.getValue());
            encryptedKey.setDn(wke.getSubjectDn());
            encryptedKey.setSerialNumber(wke.getSerialHex());
            eks.add(encryptedKey);
        }
        evt.setEncryptedKeys(eks);
        
        // do fields here.
        Set<String> fieldNameSet = new TreeSet<>();
        List<DefaultField> fields = new ArrayList<>();

        {
            DefaultField field = new DefaultField();
            field.setName("ID");
            field.setColumnVisibility("Euro");
            field.setValue("d3adb33f-d3ca-fb4d-d3ca-fb4dd00b13s");
            fields.add(field);
            fieldNameSet.add(field.getName());
        }
        
        {
            DefaultField field = new DefaultField();
            field.setName("STATE");
            field.setColumnVisibility("Euro");
            field.setValue("lle-de-France");
            fields.add(field);
            fieldNameSet.add(field.getName());
        }

        evt.setFields(fields);

        List<EncryptedPayload> eps = new ArrayList<>();
        {
            List<FieldBase<?>> wrappedFields = new ArrayList<>();
            {
                DefaultField field = new DefaultField();
                field.setName("STATE");
                field.setColumnVisibility("Euro");
                field.setValue("lle-de-France");
                wrappedFields.add(field);
            }
            
            {
                DefaultField field = new DefaultField();
                field.setName("STATE");
                field.setColumnVisibility("Euro");
                field.setValue("lle-de-France");
                wrappedFields.add(field);
                
            }
            
            {
                DefaultField field = new DefaultField();
                field.setName("STATE");
                field.setColumnVisibility("Euro");
                field.setValue("lle-de-France");
                wrappedFields.add(field);
            }
            String wrappedResult = marshallToString(List.class, wrappedFields);

            log.info("wrapped result: {}\n", wrappedResult);

            EncryptedPayload ep = new EncryptedPayload();
            String cipherText = encrypter.getEncodedEncryptedString(wrappedResult);

            // perform encryption here.
            ep.setCipherText(cipherText);
            ep.setName("ENCRYPTED_PAYLOAD");
            eps.add(ep);
        }

        evt.setEncryptedPayloads(eps);

        eventList.add(evt);
        response.setHasResults(true);
        response.setEvents(eventList);
        response.setFields(new ArrayList<>(fieldNameSet));
        
        String result = marshallToString(DefaultEventQueryResponse.class, response);
        log.info("final result: {}\n", result);
    }
    
    protected <T> String marshallToString(Class<T> clazz, T instance) throws Exception {
        return marshallToJSON(clazz, instance);
    }
    
    protected <T> String marshallToJSON(Class<T> clazz, T instance) throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final SerializationConfig config = mapper.getSerializationConfig();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.enable(MapperFeature.USE_WRAPPER_NAME_AS_PROPERTY_NAME);
        mapper.setAnnotationIntrospector(AnnotationIntrospector.pair(new JacksonAnnotationIntrospector(), new JaxbAnnotationIntrospector(mapper.getTypeFactory())));
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper.writerFor(clazz).with(config.getDefaultPrettyPrinter()).writeValueAsString(instance);
    }
    
    protected <T> String marshallToXML(Class<T> clazz, T instance) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        JAXBContext ctx = JAXBContext.newInstance(clazz);
        Marshaller m = ctx.createMarshaller();
        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        m.marshal(instance, bos);
        return bos.toString();
    }
}

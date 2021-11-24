package datawave.query.transformer.encryption;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;

import datawave.marking.MarkingFunctions;
import datawave.query.RebuildingScannerTestHelper;
import datawave.query.QueryTestTableHelper;
import datawave.query.attributes.Document;
import datawave.query.planner.DefaultQueryPlanner;
import datawave.query.tables.IndexQueryLogic;
import datawave.query.testframework.AbstractFunctionalQuery;
import datawave.query.testframework.AccumuloSetup;
import datawave.query.testframework.CitiesDataType;
import datawave.query.testframework.DataTypeHadoopConfig;
import datawave.query.testframework.FieldConfig;
import datawave.query.testframework.FileType;
import datawave.query.testframework.GenericCityFields;
import datawave.query.testframework.QueryLogicTestHarness;
import datawave.query.testframework.CitiesDataType.CityEntry;
import datawave.query.testframework.CitiesDataType.CityField;
import datawave.query.transformer.DocumentTransformer;
import datawave.query.util.DateIndexHelperFactory;
import datawave.query.util.MetadataHelperFactory;
import datawave.security.authorization.DatawavePrincipal;
import datawave.security.authorization.RecipientResolverStrategy;
import datawave.webservice.query.QueryImpl;
import datawave.webservice.query.configuration.GenericQueryConfiguration;
import datawave.webservice.query.iterator.DatawaveTransformIterator;
import datawave.webservice.query.result.event.DefaultResponseObjectFactory;
import datawave.webservice.query.result.event.EventBase;
import datawave.webservice.query.result.event.FieldBase;
import datawave.webservice.query.result.event.Metadata;
import datawave.webservice.result.BaseQueryResponse;
import datawave.webservice.result.DefaultEventQueryResponse;

import org.apache.commons.collections4.iterators.TransformIterator;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static datawave.query.testframework.RawDataManager.EQ_OP;
import static org.junit.Assert.fail;

public class DocumentEncryptionIntegrationTest extends AbstractFunctionalQuery {
    
    @ClassRule
    public static AccumuloSetup accumuloSetup = new AccumuloSetup();
    
    private static final Logger log = Logger.getLogger(DocumentEncryptionIntegrationTest.class);
    
    final Set<String> sensitiveMarkings = Collections.singleton("Euro");
    final Set<String> cleartextFields = new HashSet<>(Arrays.asList("START_DATE", "EVENT_ID", "CITY", "STATE", "COUNTRY", "CONTINENT", "ACCESS",
                    "EVENT_DATATYPE", "ORIG_FILE", "RAW_FILE", "RECORD_ID", "WRAPPED_KEY", "IV_LENGTH"));
    
    public DocumentEncryptionIntegrationTest() {
        super(CitiesDataType.getManager());
    }
    
    public DatawavePrincipalTestUtil datawavePrincipalTestUtil;
    
    @Before
    public void setup() throws Exception {
        datawavePrincipalTestUtil = new DatawavePrincipalTestUtil();
        
        Collection<DataTypeHadoopConfig> dataTypes = new ArrayList<>();
        FieldConfig generic = new GenericCityFields();
        dataTypes.add(new CitiesDataType(CityEntry.generic, generic));
        accumuloSetup.setData(FileType.CSV, dataTypes);
        connector = accumuloSetup.loadTables(log, RebuildingScannerTestHelper.TEARDOWN.NEVER, RebuildingScannerTestHelper.INTERRUPT.NEVER);
    }
    
    @Before
    public void querySetUp() throws IOException {
        
        log.debug("---------  querySetUp  ---------");
        
        // Super call to pick up authSet initialization
        super.querySetUp();
        
        this.logic = new IndexQueryLogic();
        QueryTestTableHelper.configureLogicToScanTables(this.logic);
        
        this.logic.setFullTableScanEnabled(false);
        this.logic.setIncludeDataTypeAsField(true);
        this.logic.setIncludeGroupingContext(true);
        
        this.logic.setDateIndexHelperFactory(new DateIndexHelperFactory());
        this.logic.setMarkingFunctions(new MarkingFunctions.Default());
        this.logic.setMetadataHelperFactory(new MetadataHelperFactory());
        this.logic.setQueryPlanner(new DefaultQueryPlanner());
        this.logic.setResponseObjectFactory(new DefaultResponseObjectFactory());
        
        this.logic.setCleartextFields(cleartextFields);
        this.logic.setSensitiveMarkings(sensitiveMarkings);
        
        // init must set auths
        testInit();
        
        this.testHarness = new QueryLogicTestHarness(this);
    }
    
    @Test
    public void testQuery001() throws Exception {
        log.info("------ Test a AND b ------");
        
        Set<String> expected = new HashSet<>(2);
        expected.add("ford-eventid-001");
        expected.add("tesla-eventid-002");
        
        String city = "'paris'";
        String query = CityField.CITY.name() + EQ_OP + city;
        
        final List<QueryLogicTestHarness.DocumentChecker> queryCheckers = Collections.singletonList(new FieldEncryptionChecker());
        
        runTest(query, expected, queryCheckers);
    }
    
    public void runTest(String query, Set<String> expected, List<QueryLogicTestHarness.DocumentChecker> queryChecker) throws Exception {
        Date[] startEndDate = this.dataManager.getShardStartEndDate();
        Map<String,String> options = new HashMap<>();
        runTestQuery(expected, query, startEndDate[0], startEndDate[1], options, queryChecker);
    }
    
    @Override
    public void runTestQuery(Collection<String> expected, String queryStr, Date startDate, Date endDate, Map<String,String> options,
                    List<QueryLogicTestHarness.DocumentChecker> checkers) throws Exception {
        log.debug("runTestQueryWithTransformer");
        
        QueryImpl settings = new QueryImpl();
        settings.setBeginDate(startDate);
        settings.setEndDate(endDate);
        settings.setPagesize(Integer.MAX_VALUE);
        settings.setQueryAuthorizations(auths.toString());
        settings.setQuery(queryStr);
        settings.setParameters(options);
        settings.setId(UUID.randomUUID());
        
        log.debug("query: " + settings.getQuery());
        log.debug("logic: " + settings.getQueryLogicName());

        logic.setRecipientResolverStrategy(RecipientResolverStrategy.get());

        DatawavePrincipal principal = datawavePrincipalTestUtil.getDatawavePrincipal();
        logic.setPrincipal(principal);
        
        GenericQueryConfiguration config = logic.initialize(connector, settings, Collections.singleton(auths));
        logic.setupQuery(config);
        
        DocumentTransformer transformer = (DocumentTransformer) (logic.getTransformer(settings));
        TransformIterator<?,?> iter = new DatawaveTransformIterator<>(logic.iterator(), transformer);
        List<Object> eventList = new ArrayList<>();
        while (iter.hasNext()) {
            eventList.add(iter.next());
        }
        
        BaseQueryResponse response = transformer.createResponse(eventList);
        
        // un-comment to look at the json output
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(MapperFeature.USE_WRAPPER_NAME_AS_PROPERTY_NAME);
        mapper.setAnnotationIntrospector(AnnotationIntrospector.pair(new JacksonAnnotationIntrospector(),
                        new JaxbAnnotationIntrospector(mapper.getTypeFactory())));
        mapper.setSerializationInclusion(Include.NON_NULL);
        
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File("encrypted-result.json"), response);
        
        Assert.assertTrue(response instanceof DefaultEventQueryResponse);
        DefaultEventQueryResponse eventQueryResponse = (DefaultEventQueryResponse) response;
        
        List<String> incorrectFields = new ArrayList<>();
        
        for (EventBase<?,?> event : eventQueryResponse.getEvents()) {
            // System.err.println(event);
            boolean eventIsSensitive = false;
            Set<String> cvs = new HashSet<>();
            
            Metadata md = event.getMetadata();
            String eventId = md.getInternalId();
            
            for (FieldBase<?> f : event.getFields()) {
                // System.err.println(f);
                String cv = f.getColumnVisibility();
                String name = f.getName();
                
                cvs.add(cv);
                
                // if we see a single sensitive column visibility, the whole event is considered sensitive
                if (sensitiveMarkings.contains(cv) && !eventIsSensitive) {
                    eventIsSensitive = true;
                }
                
                // if the event is sensitive, and we see a field that's not in the cleartext list...
                if (eventIsSensitive && !cleartextFields.contains(name)) {
                    if (!name.startsWith("ENC")) {
                        incorrectFields.add("  Field " + name + " SHOULD be encrypted in event [" + eventId + "] with column visibilities: ["
                                        + String.join(",", cvs) + "]");
                    }
                }
                
                // if the event is not sensitive, and we see a field that's encrypted..
                if (!eventIsSensitive && name.startsWith("ENC")) {
                    incorrectFields.add("  Field " + name + " should NOT be encrypted in event [" + eventId + "] with column visibilities: ["
                                    + String.join(",", cvs) + "]");
                }
            }
        }
        
        if (incorrectFields.size() > 0) {
            fail("Observed " + incorrectFields.size() + " incorrect fields:\n" + String.join("\n", incorrectFields));
        }
        
        /*
         * for (EventBase event : eventQueryResponse.getEvents()) { boolean found = false; for (Iterator<Set<String>> it = expected.iterator(); it.hasNext();) {
         * Set<String> expectedSet = it.next(); if (expectedSet.contains(event.getMetadata().getInternalId())) { it.remove(); found = true; break; } }
         * Assert.assertTrue(found); } Assert.assertTrue(expected.isEmpty());
         */
    }
    
    @Override
    protected void testInit() {
        this.auths = CitiesDataType.getTestAuths();
        this.documentKey = CityField.EVENT_ID.name();
    }
    
    static class FieldEncryptionChecker implements QueryLogicTestHarness.DocumentChecker {
        @Override
        public void assertValid(Document doc) {
            log.info(doc);
        }
    }
}

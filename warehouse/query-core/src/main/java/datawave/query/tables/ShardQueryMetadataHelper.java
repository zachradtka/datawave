package datawave.query.tables;

import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import datawave.data.MetadataCardinalityCounts;
import datawave.data.type.Type;
import datawave.marking.MarkingFunctions;
import datawave.query.composite.CompositeMetadata;
import datawave.query.model.QueryModel;
import datawave.query.util.AllFieldMetadataHelper;
import datawave.query.util.Metadata;
import datawave.query.util.MetadataHelper;
import datawave.query.util.TypeMetadata;
import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.client.TableNotFoundException;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.Authorizations;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class ShardQueryMetadataHelper extends MetadataHelper {
    
    private final MetadataHelper delegate;
    private final Set<String> evaluationOnlyFields;
    
    public ShardQueryMetadataHelper(Connector connector, MetadataHelper helper, Set<String> evaluationOnlyFields) {
        super(helper.getAllFieldMetadataHelper(), helper.getAllMetadataAuths(), connector, helper.getMetadataTableName(), helper.getAuths(), helper
                        .getFullUserAuths());
        this.delegate = helper;
        this.evaluationOnlyFields = evaluationOnlyFields;
    }
    
    @Override
    public Map<Set<String>,TypeMetadata> getTypeMetadataMap() throws TableNotFoundException {
        return delegate.getTypeMetadataMap();
    }
    
    @Override
    public String getUsersMetadataAuthorizationSubset() {
        return delegate.getUsersMetadataAuthorizationSubset();
    }
    
    @Override
    public Collection<Authorizations> getAllMetadataAuths() {
        return delegate.getAllMetadataAuths();
    }
    
    @Override
    public Set<Authorizations> getAuths() {
        return delegate.getAuths();
    }
    
    @Override
    public Set<Authorizations> getFullUserAuths() {
        return delegate.getFullUserAuths();
    }
    
    @Override
    public AllFieldMetadataHelper getAllFieldMetadataHelper() {
        return delegate.getAllFieldMetadataHelper();
    }
    
    @Override
    public Metadata getMetadata() throws TableNotFoundException, ExecutionException, MarkingFunctions.Exception {
        return delegate.getMetadata();
    }
    
    @Override
    public Metadata getMetadata(Set<String> ingestTypeFilter) throws TableNotFoundException, ExecutionException, MarkingFunctions.Exception {
        return delegate.getMetadata(ingestTypeFilter);
    }
    
    @Override
    public Set<String> getAllFields(Set<String> ingestTypeFilter) throws TableNotFoundException {
        Set<String> fields = delegate.getAllFields(ingestTypeFilter);
        Set<String> allFields = new HashSet<>(fields);
        allFields.addAll(evaluationOnlyFields);
        return allFields;
    }
    
    @Override
    public Set<String> getNonEventFields(Set<String> ingestTypeFilter) throws TableNotFoundException {
        return delegate.getNonEventFields(ingestTypeFilter);
    }
    
    @Override
    public Set<String> getIndexOnlyFields(Set<String> ingestTypeFilter) throws TableNotFoundException {
        return delegate.getIndexOnlyFields(ingestTypeFilter);
    }
    
    @Override
    public QueryModel getQueryModel(String modelTableName, String modelName) throws TableNotFoundException, ExecutionException {
        return delegate.getQueryModel(modelTableName, modelName);
    }
    
    @Override
    public QueryModel getQueryModel(String modelTableName, String modelName, Collection<String> unevaluatedFields) throws TableNotFoundException {
        return delegate.getQueryModel(modelTableName, modelName, unevaluatedFields);
    }
    
    @Override
    public QueryModel getQueryModel(String modelTableName, String modelName, Collection<String> unevaluatedFields, Set<String> ingestTypeFilter)
                    throws TableNotFoundException {
        return delegate.getQueryModel(modelTableName, modelName, unevaluatedFields, ingestTypeFilter);
    }
    
    @Override
    public Set<String> getQueryModelNames(String modelTableName) throws TableNotFoundException {
        return delegate.getQueryModelNames(modelTableName);
    }
    
    @Override
    public boolean isReverseIndexed(String fieldName, Set<String> ingestTypeFilter) throws TableNotFoundException {
        return delegate.isReverseIndexed(fieldName, ingestTypeFilter);
    }
    
    @Override
    public boolean isIndexed(String fieldName, Set<String> ingestTypeFilter) throws TableNotFoundException {
        return delegate.isIndexed(fieldName, ingestTypeFilter);
    }
    
    @Override
    public Multimap<String,String> getFacets(String table) throws InstantiationException, IllegalAccessException, TableNotFoundException {
        return delegate.getFacets(table);
    }
    
    @Override
    public Map<String,Map<String,MetadataCardinalityCounts>> getTermCounts() throws InstantiationException, IllegalAccessException, TableNotFoundException {
        return delegate.getTermCounts();
    }
    
    @Override
    public Map<String,Map<String,MetadataCardinalityCounts>> getTermCountsWithRootAuths() throws InstantiationException, IllegalAccessException,
                    TableNotFoundException, AccumuloSecurityException, AccumuloException {
        return delegate.getTermCountsWithRootAuths();
    }
    
    @Override
    public Set<String> getAllNormalized() throws InstantiationException, IllegalAccessException, TableNotFoundException {
        return delegate.getAllNormalized();
    }
    
    @Override
    public Set<Type<?>> getAllDatatypes() throws InstantiationException, IllegalAccessException, TableNotFoundException {
        return delegate.getAllDatatypes();
    }
    
    @Override
    public Multimap<String,String> getCompositeToFieldMap() throws TableNotFoundException {
        return delegate.getCompositeToFieldMap();
    }
    
    @Override
    public Multimap<String,String> getCompositeToFieldMap(Set<String> ingestTypeFilter) throws TableNotFoundException {
        return delegate.getCompositeToFieldMap(ingestTypeFilter);
    }
    
    @Override
    public Map<String,Date> getCompositeTransitionDateMap() throws TableNotFoundException {
        return delegate.getCompositeTransitionDateMap();
    }
    
    @Override
    public Map<String,Date> getCompositeTransitionDateMap(Set<String> ingestTypeFilter) throws TableNotFoundException {
        return delegate.getCompositeTransitionDateMap(ingestTypeFilter);
    }
    
    @Override
    public Map<String,String> getCompositeFieldSeparatorMap() throws TableNotFoundException {
        return delegate.getCompositeFieldSeparatorMap();
    }
    
    @Override
    public Map<String,String> getCompositeFieldSeparatorMap(Set<String> ingestTypeFilter) throws TableNotFoundException {
        return delegate.getCompositeFieldSeparatorMap(ingestTypeFilter);
    }
    
    @Override
    public Set<Type<?>> getDatatypesForField(String fieldName) throws InstantiationException, IllegalAccessException, TableNotFoundException {
        return delegate.getDatatypesForField(fieldName);
    }
    
    @Override
    public Set<Type<?>> getDatatypesForField(String fieldName, Set<String> ingestTypeFilter) throws InstantiationException, IllegalAccessException,
                    TableNotFoundException {
        return delegate.getDatatypesForField(fieldName, ingestTypeFilter);
    }
    
    @Override
    public TypeMetadata getTypeMetadata() throws TableNotFoundException {
        return delegate.getTypeMetadata();
    }
    
    @Override
    public TypeMetadata getTypeMetadata(Set<String> ingestTypeFilter) throws TableNotFoundException {
        return delegate.getTypeMetadata(ingestTypeFilter);
    }
    
    @Override
    public CompositeMetadata getCompositeMetadata() throws TableNotFoundException {
        return delegate.getCompositeMetadata();
    }
    
    @Override
    public CompositeMetadata getCompositeMetadata(Set<String> ingestTypeFilter) throws TableNotFoundException {
        return delegate.getCompositeMetadata(ingestTypeFilter);
    }
    
    @Override
    public SetMultimap<Key,Value> getEdges() throws TableNotFoundException, ExecutionException {
        return delegate.getEdges();
    }
    
    @Override
    public Multimap<String,Type<?>> getFieldsToDatatypes(Set<String> ingestTypeFilter) throws InstantiationException, IllegalAccessException,
                    TableNotFoundException {
        return delegate.getFieldsToDatatypes(ingestTypeFilter);
    }
    
    @Override
    public Set<String> getFieldsForDatatype(Class<? extends Type<?>> datawaveType) throws InstantiationException, IllegalAccessException,
                    TableNotFoundException {
        return delegate.getFieldsForDatatype(datawaveType);
    }
    
    @Override
    public Set<String> getFieldsForDatatype(Class<? extends Type<?>> datawaveType, Set<String> ingestTypeFilter) throws TableNotFoundException {
        return delegate.getFieldsForDatatype(datawaveType, ingestTypeFilter);
    }
    
    @Override
    public Type<?> getDatatypeFromClass(Class<? extends Type<?>> datatypeClass) throws InstantiationException, IllegalAccessException {
        return delegate.getDatatypeFromClass(datatypeClass);
    }
    
    @Override
    public Set<String> getTermFrequencyFields(Set<String> ingestTypeFilter) throws TableNotFoundException {
        return delegate.getTermFrequencyFields(ingestTypeFilter);
    }
    
    @Override
    public Set<String> getIndexedFields(Set<String> ingestTypeFilter) throws TableNotFoundException {
        return delegate.getIndexedFields(ingestTypeFilter);
    }
    
    @Override
    public Set<String> getReverseIndexedFields(Set<String> ingestTypeFilter) throws TableNotFoundException {
        return delegate.getReverseIndexedFields(ingestTypeFilter);
    }
    
    @Override
    public Set<String> getExpansionFields(Set<String> ingestTypeFilter) throws TableNotFoundException {
        return delegate.getExpansionFields(ingestTypeFilter);
    }
    
    @Override
    public Set<String> getContentFields(Set<String> ingestTypeFilter) throws TableNotFoundException {
        return delegate.getContentFields(ingestTypeFilter);
    }
    
    @Override
    public long getCardinalityForField(String fieldName, Date begin, Date end) throws TableNotFoundException {
        return delegate.getCardinalityForField(fieldName, begin, end);
    }
    
    @Override
    public long getCardinalityForField(String fieldName, String datatype, Date begin, Date end) throws TableNotFoundException {
        return delegate.getCardinalityForField(fieldName, datatype, begin, end);
    }
    
    @Override
    public Set<String> getDatatypes(Set<String> ingestTypeFilter) throws TableNotFoundException {
        return delegate.getDatatypes(ingestTypeFilter);
    }
    
    @Override
    public Long getCountsByFieldForDays(String fieldName, Date begin, Date end) {
        return delegate.getCountsByFieldForDays(fieldName, begin, end);
    }
    
    @Override
    public Long getCountsByFieldForDays(String fieldName, Date begin, Date end, Set<String> ingestTypeFilter) {
        return delegate.getCountsByFieldForDays(fieldName, begin, end, ingestTypeFilter);
    }
    
    @Override
    public Long getCountsByFieldInDay(String fieldName, String date) {
        return delegate.getCountsByFieldInDay(fieldName, date);
    }
    
    @Override
    public Long getCountsByFieldInDayWithTypes(String fieldName, String date, Set<String> datatypes) {
        return delegate.getCountsByFieldInDayWithTypes(fieldName, date, datatypes);
    }
    
    @Override
    public Date getEarliestOccurrenceOfField(String fieldName) {
        return delegate.getEarliestOccurrenceOfField(fieldName);
    }
    
    @Override
    public Date getEarliestOccurrenceOfFieldWithType(String fieldName, String dataType) {
        return delegate.getEarliestOccurrenceOfFieldWithType(fieldName, dataType);
    }
    
    @Override
    public String toString() {
        return delegate.toString();
    }
    
    @Override
    public String getMetadataTableName() {
        return delegate.getMetadataTableName();
    }
    
}

package datawave.webservice.query.result.event;

import datawave.user.AuthorizationsListBase;
import datawave.user.DefaultAuthorizationsList;
import datawave.webservice.query.Query;
import datawave.webservice.query.QueryImpl;
import datawave.webservice.query.cachedresults.CacheableQueryRow;
import datawave.webservice.query.cachedresults.CacheableQueryRowImpl;
import datawave.webservice.query.result.EdgeQueryResponseBase;
import datawave.webservice.query.result.edge.DefaultEdge;
import datawave.webservice.query.result.edge.EdgeBase;
import datawave.webservice.query.result.metadata.DefaultMetadataField;
import datawave.webservice.query.result.metadata.MetadataFieldBase;
import datawave.webservice.response.objects.DefaultKey;
import datawave.webservice.response.objects.KeyBase;
import datawave.webservice.result.DefaultEdgeQueryResponse;
import datawave.webservice.result.DefaultEventQueryResponse;
import datawave.webservice.result.EventQueryResponseBase;
import datawave.webservice.result.FacetQueryResponse;
import datawave.webservice.result.FacetQueryResponseBase;
import datawave.webservice.results.datadictionary.DataDictionaryBase;
import datawave.webservice.results.datadictionary.DefaultDataDictionary;
import datawave.webservice.results.datadictionary.DefaultDescription;
import datawave.webservice.results.datadictionary.DefaultFields;
import datawave.webservice.results.datadictionary.DescriptionBase;
import datawave.webservice.results.datadictionary.FieldsBase;

public class DefaultResponseObjectFactory extends ResponseObjectFactory {

    public DefaultResponseObjectFactory() {
        int i = 0;
    }

    @Override
    public EventBase getEvent() {
        return new DefaultEvent();
    }
    
    @Override
    public FieldBase getField() {
        return new DefaultField();
    }
    
    @Override
    public EventQueryResponseBase getEventQueryResponse() {
        return new DefaultEventQueryResponse();
    }
    
    @Override
    public CacheableQueryRow getCacheableQueryRow() {
        return new CacheableQueryRowImpl();
    }
    
    @Override
    public EdgeBase getEdge() {
        return new DefaultEdge();
    }
    
    @Override
    public EdgeQueryResponseBase getEdgeQueryResponse() {
        return new DefaultEdgeQueryResponse();
    }
    
    @Override
    public FacetQueryResponseBase getFacetQueryResponse() {
        return new FacetQueryResponse();
    }
    
    @Override
    public FacetsBase getFacets() {
        return new DefaultFacets();
    }
    
    @Override
    public FieldCardinalityBase getFieldCardinality() {
        return new DefaultFieldCardinality();
    }
    
    @Override
    public KeyBase getKey() {
        return new DefaultKey();
    }
    
    @Override
    public AuthorizationsListBase getAuthorizationsList() {
        return new DefaultAuthorizationsList();
    }
    
    @Override
    public Query getQueryImpl() {
        return new QueryImpl();
    }
    
    @Override
    public DataDictionaryBase getDataDictionary() {
        return new DefaultDataDictionary();
    }
    
    @Override
    public FieldsBase getFields() {
        return new DefaultFields();
    }
    
    @Override
    public DescriptionBase getDescription() {
        return new DefaultDescription();
    }
    
    @Override
    public MetadataFieldBase getMetadataField() {
        return new DefaultMetadataField();
    }
}

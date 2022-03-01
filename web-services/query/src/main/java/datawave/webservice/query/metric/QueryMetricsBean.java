package datawave.webservice.query.metric;

import datawave.annotation.DateFormat;
import datawave.annotation.Required;
import datawave.configuration.DatawaveEmbeddedProjectStageHolder;
import datawave.interceptor.RequiredInterceptor;
import datawave.interceptor.ResponseInterceptor;
import datawave.metrics.remote.RemoteQueryMetricService;
import datawave.microservice.querymetric.BaseQueryMetric;
import datawave.microservice.querymetric.BaseQueryMetric.PageMetric;
import datawave.microservice.querymetric.BaseQueryMetricListResponse;
import datawave.microservice.querymetric.QueryMetricsSummaryResponse;
import datawave.security.authorization.DatawavePrincipal;
import datawave.webservice.query.map.QueryGeometryResponse;
import org.apache.deltaspike.core.api.exclude.Exclude;
import org.apache.log4j.Logger;
import org.jboss.resteasy.annotations.GZIP;

import javax.annotation.Resource;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.EJBContext;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.inject.Inject;
import javax.interceptor.Interceptors;
import javax.jms.Destination;
import javax.jms.JMSContext;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import java.security.Principal;
import java.util.Date;
import java.util.Iterator;

@Path("/Query/Metrics")
@Produces({"application/xml", "text/xml", "application/json", "text/yaml", "text/x-yaml", "application/x-yaml", "text/html"})
@GZIP
@RolesAllowed({"AuthorizedUser", "AuthorizedQueryServer", "InternalUser", "Administrator", "MetricsAdministrator"})
@DeclareRoles({"AuthorizedUser", "AuthorizedQueryServer", "InternalUser", "Administrator", "MetricsAdministrator"})
@Stateless
@LocalBean
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
@TransactionManagement(TransactionManagementType.BEAN)
@Exclude(ifProjectStage = DatawaveEmbeddedProjectStageHolder.DatawaveEmbedded.class)
public class QueryMetricsBean {
    
    private static final Logger log = Logger.getLogger(QueryMetricsBean.class);
    @Inject
    private JMSContext jmsContext;
    @Resource(mappedName = "java:/queue/QueryMetrics")
    private Destination dest;
    @Resource
    private EJBContext ctx;
    @Inject
    private RemoteQueryMetricService remoteQueryMetricService;
    
    /*
     * @PermitAll is necessary because this method is called indirectly from the @PreDestroy method of the QueryExpirationBean and the QueryExpirationBean's
     * 
     * @RunAs annotation is not being honored in the @PreDestroy hook
     */
    @PermitAll
    public void updateMetric(BaseQueryMetric metric) throws Exception {
        DatawavePrincipal dp = getPrincipal();
        
        if (metric.getLastWrittenHash() != metric.hashCode()) {
            metric.setLastWrittenHash(metric.hashCode());
            try {
                metric.setLastUpdated(new Date());
                sendQueryMetric(dp, metric);
                // PageMetrics now know their own page numbers
                // this should keep large queries from blowing up the queue
                // Leave the last page on the list so that interceptors can update it.
                Iterator<PageMetric> itr = metric.getPageTimes().iterator();
                while (metric.getPageTimes().size() > 1) {
                    itr.next();
                    itr.remove();
                }
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }
    
    /**
     * Returns metrics for the current users queries that are identified by the id
     *
     * @param id
     *
     * @return datawave.webservice.result.QueryMetricListResponse
     *
     * @RequestHeader X-ProxiedEntitiesChain use when proxying request for user, by specifying a chain of DNs of the identities to proxy
     * @RequestHeader X-ProxiedIssuersChain required when using X-ProxiedEntitiesChain, specify one issuer DN per subject DN listed in X-ProxiedEntitiesChain
     * @HTTP 200 success
     * @HTTP 500 internal server error
     */
    @GET
    @POST
    @Path("/id/{id}")
    @Interceptors({RequiredInterceptor.class, ResponseInterceptor.class})
    public BaseQueryMetricListResponse query(@PathParam("id") @Required("id") String id) {
        return remoteQueryMetricService.id(id);
    }
    
    @GET
    @POST
    @Path("/id/{id}/map")
    @Interceptors({RequiredInterceptor.class, ResponseInterceptor.class})
    public QueryGeometryResponse map(@PathParam("id") @Required("id") String id) {
        return remoteQueryMetricService.map(id);
    }
    
    /**
     *
     * Returns a summary of the query metrics
     *
     * @param begin
     *            (optional)
     * @param end
     *            (optional)
     *
     * @return datawave.webservice.result.QueryMetricsSummaryResponse
     *
     * @RequestHeader X-ProxiedEntitiesChain use when proxying request for user, by specifying a chain of DNs of the identities to proxy
     * @RequestHeader X-ProxiedIssuersChain required when using X-ProxiedEntitiesChain, specify one issuer DN per subject DN listed in X-ProxiedEntitiesChain
     * @HTTP 200 success
     * @HTTP 500 internal server error
     */
    @GET
    @Path("/summary/all")
    @Interceptors(ResponseInterceptor.class)
    @RolesAllowed({"Administrator", "MetricsAdministrator"})
    public QueryMetricsSummaryResponse getQueryMetricsSummary(@QueryParam("begin") @DateFormat(defaultTime = "000000", defaultMillisec = "000") Date begin,
                    @QueryParam("end") @DateFormat(defaultTime = "235959", defaultMillisec = "999") Date end) {
        return remoteQueryMetricService.summaryAll(begin, end);
    }
    
    /**
     *
     * Returns a summary of the query metrics
     *
     * @param begin
     *            (optional)
     * @param end
     *            (optional)
     *
     * @return datawave.webservice.result.QueryMetricsSummaryResponse
     *
     * @RequestHeader X-ProxiedEntitiesChain use when proxying request for user, by specifying a chain of DNs of the identities to proxy
     * @RequestHeader X-ProxiedIssuersChain required when using X-ProxiedEntitiesChain, specify one issuer DN per subject DN listed in X-ProxiedEntitiesChain
     * @HTTP 200 success
     * @HTTP 500 internal server error
     * @deprecated use /summary/all instead
     */
    @GET
    @Path("/summary")
    @Interceptors(ResponseInterceptor.class)
    @RolesAllowed({"Administrator", "MetricsAdministrator"})
    public QueryMetricsSummaryResponse getQueryMetricsSummaryDeprecated1(
                    @QueryParam("begin") @DateFormat(defaultTime = "000000", defaultMillisec = "000") Date begin, @QueryParam("end") @DateFormat(
                                    defaultTime = "235959", defaultMillisec = "999") Date end) {
        return remoteQueryMetricService.summaryAll(begin, end);
    }
    
    /**
     *
     * Returns a summary of the query metrics
     *
     * @param begin
     *            (optional)
     * @param end
     *            (optional)
     *
     * @return datawave.webservice.result.QueryMetricsSummaryResponse
     *
     * @RequestHeader X-ProxiedEntitiesChain use when proxying request for user, by specifying a chain of DNs of the identities to proxy
     * @RequestHeader X-ProxiedIssuersChain required when using X-ProxiedEntitiesChain, specify one issuer DN per subject DN listed in X-ProxiedEntitiesChain
     * @HTTP 200 success
     * @HTTP 500 internal server error
     * @deprecated use /summary/all instead
     */
    @GET
    @Path("/summaryCounts")
    @Interceptors(ResponseInterceptor.class)
    @RolesAllowed({"Administrator", "MetricsAdministrator"})
    public QueryMetricsSummaryResponse getQueryMetricsSummaryDeprecated2(
                    @QueryParam("begin") @DateFormat(defaultTime = "000000", defaultMillisec = "000") Date begin, @QueryParam("end") @DateFormat(
                                    defaultTime = "235959", defaultMillisec = "999") Date end) {
        return remoteQueryMetricService.summaryAll(begin, end);
    }
    
    /**
     *
     * Returns a summary of the requesting user's query metrics
     *
     * @param begin
     *            (optional)
     * @param end
     *            (optional)
     *
     * @return datawave.webservice.result.QueryMetricsSummaryResponse
     *
     * @RequestHeader X-ProxiedEntitiesChain use when proxying request for user, by specifying a chain of DNs of the identities to proxy
     * @RequestHeader X-ProxiedIssuersChain required when using X-ProxiedEntitiesChain, specify one issuer DN per subject DN listed in X-ProxiedEntitiesChain
     * @HTTP 200 success
     * @HTTP 500 internal server error
     */
    @GET
    @Path("/summary/user")
    @Interceptors(ResponseInterceptor.class)
    public QueryMetricsSummaryResponse getQueryMetricsUserSummary(@QueryParam("begin") @DateFormat(defaultTime = "000000", defaultMillisec = "000") Date begin,
                    @QueryParam("end") @DateFormat(defaultTime = "235959", defaultMillisec = "999") Date end) {
        return remoteQueryMetricService.summaryUser(begin, end);
    }
    
    /**
     *
     * Returns a summary of the requesting user's query metrics
     *
     * @param begin
     *            (optional)
     * @param end
     *            (optional)
     *
     * @return datawave.webservice.result.QueryMetricsSummaryResponse
     *
     * @RequestHeader X-ProxiedEntitiesChain use when proxying request for user, by specifying a chain of DNs of the identities to proxy
     * @RequestHeader X-ProxiedIssuersChain required when using X-ProxiedEntitiesChain, specify one issuer DN per subject DN listed in X-ProxiedEntitiesChain
     * @HTTP 200 success
     * @HTTP 500 internal server error
     * @deprecated use /summary/user instead
     */
    @GET
    @Path("/summaryCounts/user")
    @Interceptors(ResponseInterceptor.class)
    public QueryMetricsSummaryResponse getQueryMetricsUserSummaryDeprecated(
                    @QueryParam("begin") @DateFormat(defaultTime = "000000", defaultMillisec = "000") Date begin, @QueryParam("end") @DateFormat(
                                    defaultTime = "235959", defaultMillisec = "999") Date end) {
        return remoteQueryMetricService.summaryUser(begin, end);
    }
    
    /**
     * Find out who/what called this method
     *
     * @return
     */
    private DatawavePrincipal getPrincipal() {
        DatawavePrincipal dp = null;
        Principal p = ctx.getCallerPrincipal();
        if (p instanceof DatawavePrincipal) {
            dp = (DatawavePrincipal) p;
        }
        return dp;
    }
    
    public void sendQueryMetric(DatawavePrincipal principal, BaseQueryMetric queryMetric) throws Exception {
        
        QueryMetricHolder queryMetricHolder = new QueryMetricHolder(principal, queryMetric);
        QueryMetricMessage msg = new QueryMetricMessage(queryMetricHolder);
        
        jmsContext.createProducer().send(dest, msg);
    }
}

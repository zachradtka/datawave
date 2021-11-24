package datawave.security.authorization;

import org.apache.log4j.Logger;
import org.apache.log4j.Priority;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

/** An interface and default implementation of a strategy to retrieve X509 Certificates for a given DN */
public abstract class RecipientResolverStrategy {
    public static RecipientResolverStrategy get() {
        return new Default();
    }
    
    public abstract X509Certificate resolveCertificate(String recipientDN);
    
    public static class Default extends RecipientResolverStrategy {
        private static final Logger log = Logger.getLogger(Default.class);
        private static final Map<String,X509Certificate> certificateMap = new HashMap<>();
        
        @Override
        public X509Certificate resolveCertificate(String recipientDN) {
            log.info("Received request for certificate for: " + recipientDN);
            final Map<String,X509Certificate> localMap = certificateMap;
            X509Certificate result = localMap.get(recipientDN);
            if (result == null) {
                log.warn("Unable to find certificate for: " + recipientDN);
            }
            return result;
        }
        
        public static void addCertificate(X509Certificate cert) {
            SubjectIssuerDNPair subjectIssuerDNPair = SubjectIssuerDNPair.of(cert.getSubjectDN().toString(), cert.getIssuerDN().toString());
            addCertificate(subjectIssuerDNPair.toString(), cert);
        }
        
        public static void addCertificate(String subjectIssuerDN, X509Certificate cert) {
            certificateMap.put(subjectIssuerDN, cert);
        }
        
        public static void dumpCachedCertificates(Logger logger, Priority priority) {
            for (Map.Entry<String,X509Certificate> e : certificateMap.entrySet()) {
                logger.log(priority, e.getKey() + " cert: " + e.getValue());
            }
        }
    }
}

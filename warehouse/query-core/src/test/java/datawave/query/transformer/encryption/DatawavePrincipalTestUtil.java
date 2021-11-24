package datawave.query.transformer.encryption;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import datawave.encryption.EncryptionTestUtil;
import datawave.security.authorization.DatawavePrincipal;
import datawave.security.authorization.DatawaveUser;
import datawave.security.authorization.RecipientResolverStrategy;
import datawave.security.authorization.SubjectIssuerDNPair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DatawavePrincipalTestUtil {
    
    final EncryptionTestUtil encryptionTestUtil;
    
    public DatawavePrincipalTestUtil() throws Exception {
        this(new EncryptionTestUtil());
    }
    
    public DatawavePrincipalTestUtil(EncryptionTestUtil encryptionTestUtil) {
        this.encryptionTestUtil = encryptionTestUtil;
        RecipientResolverStrategy.Default.addCertificate(encryptionTestUtil.getTestServerCert());
        RecipientResolverStrategy.Default.addCertificate(encryptionTestUtil.getTestClientCert());
    }
    
    /** Create a fake datawave principal fopr testing */
    public DatawavePrincipal getDatawavePrincipal() {
        String subjectDN = encryptionTestUtil.getTestServerCert().getSubjectDN().getName();
        String issuerDN = encryptionTestUtil.getTestServerCert().getIssuerDN().getName();
        SubjectIssuerDNPair dn = SubjectIssuerDNPair.of(subjectDN, issuerDN);
        
        List<String> userAuths = Collections.emptyList();
        
        Set<String> roles = new HashSet<>();
        roles.add("AuthorizedServer");
        Multimap<String,String> roleToAuthMapping = HashMultimap.create();
        
        DatawaveUser user = new DatawaveUser(dn, DatawaveUser.UserType.SERVER, userAuths, roles, roleToAuthMapping, -1);
        List<DatawaveUser> proxiedUsers = new ArrayList<>();
        proxiedUsers.add(user);
        return new DatawavePrincipal(proxiedUsers);
    }
}

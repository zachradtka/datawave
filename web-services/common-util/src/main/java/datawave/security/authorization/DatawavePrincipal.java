package datawave.security.authorization;

import datawave.security.authorization.DatawaveUser.UserType;
import datawave.security.util.DnUtils;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static datawave.security.authorization.DatawaveUser.ANONYMOUS_USER;

/**
 * A {@link Principal} that represents a set of proxied {@link DatawaveUser}s. For example, this proxied user could represent a GUI server acting on behalf of a
 * user. The GUI server user represents the entity that made the call to us and the other proxied user would be the actual end user.
 */
@XmlRootElement
@XmlType(factoryMethod = "anonymousPrincipal", propOrder = {"name", "proxiedUsers", "creationTime"})
@XmlAccessorType(XmlAccessType.NONE)
public class DatawavePrincipal implements Principal, Serializable {
    private final String username;
    private final DatawaveUser primaryUser;
    @XmlElement
    private final Set<DatawaveUser> proxiedUsers = new LinkedHashSet<>();
    @XmlElement
    private final long creationTime;
    
    /**
     * This constructor should not be used. It is here to allow JAX-B mapping and CDI proxying of this class.
     */
    public DatawavePrincipal() {
        this("ANONYMOUS");
    }
    
    public DatawavePrincipal(String username) {
        this.username = username;
        this.primaryUser = ANONYMOUS_USER;
        this.creationTime = System.currentTimeMillis();
    }
    
    public DatawavePrincipal(Collection<? extends DatawaveUser> proxiedUsers) {
        this(proxiedUsers, System.currentTimeMillis());
    }
    
    public DatawavePrincipal(Collection<? extends DatawaveUser> proxiedUsers, long creationTime) {
        this.proxiedUsers.addAll(proxiedUsers);
        this.creationTime = creationTime;
        this.primaryUser = DatawavePrincipal.findPrimaryUser(this.proxiedUsers);
        this.username = DatawavePrincipal.orderProxiedUsers(this.proxiedUsers).stream().map(DatawaveUser::getName).collect(Collectors.joining(" -> "));
    }
    
    /**
     * Gets the {@link DatawaveUser} that represents the primary user in this DatawavePrincipal. If there is only one DatawaveUser, then it is the primaryUser.
     * If there is more than one DatawaveUser, then the first (and presumably only) DatawaveUser whose {@link DatawaveUser#getUserType()} is
     * {@link UserType#USER} is the primary user. If no such DatawaveUser is present, then the second principal in the list is returned as the primary user.
     * This will be the first entity in the X-ProxiedEntitiesChain which should be the server that originated the request.
     */
    static protected DatawaveUser findPrimaryUser(Collection<DatawaveUser> datawaveUsers) {
        if (datawaveUsers.size() <= 1) {
            return datawaveUsers.stream().findFirst().orElse(null);
        } else {
            DatawaveUser secondInOrder = datawaveUsers.stream().skip(1).findFirst().orElse(null);
            return datawaveUsers.stream().filter(u -> u.getUserType() == UserType.USER).findFirst().orElse(secondInOrder);
        }
    }
    
    /*
     * The purpose here is to return a List of DatawaveUsers where the original caller is first followed by any entities in X-ProxiedEntitiesChain in the order
     * that they were traversed and ending with the entity that made the final call. The List that is passed is not modified. This method makes the following
     * assumptions about the List that is passed to ths method: 1) The first element is the one that made the final call 2) Additional elements (if any) are
     * from X-ProxiedEntitiesChain in chronological order of the calls
     */
    static protected List<DatawaveUser> orderProxiedUsers(Collection<DatawaveUser> datawaveUsers) {
        DatawaveUser primary = DatawavePrincipal.findPrimaryUser(datawaveUsers);
        List<DatawaveUser> users = new ArrayList<>();
        users.add(primary);
        if (datawaveUsers.size() > 1) {
            // @formatter:off
            // Skipping first user because if it is UserType.USER, it is the primary
            // and already added.  If it is UserType.Server, then it will be added at the end
            datawaveUsers.stream()
                    .skip(1)
                    .filter(u -> u != primary)
                    .forEach(u -> users.add(u));
            // @formatter:on
            DatawaveUser first = datawaveUsers.stream().findFirst().orElse(null);
            if (!users.contains(first)) {
                users.add(first);
            }
        }
        return users;
    }
    
    public Collection<DatawaveUser> getProxiedUsers() {
        return Collections.unmodifiableCollection(this.proxiedUsers);
    }
    
    public DatawaveUser getPrimaryUser() {
        return primaryUser;
    }
    
    public Collection<? extends Collection<String>> getAuthorizations() {
        // @formatter:off
        return Collections.unmodifiableCollection(
                DatawavePrincipal.orderProxiedUsers(this.proxiedUsers).stream()
                .map(DatawaveUser::getAuths)
                .collect(Collectors.toList()));
        // @formatter:on
    }
    
    public String[] getDNs() {
        // @formatter:off
        return DatawavePrincipal.orderProxiedUsers(this.proxiedUsers).stream()
                .map(DatawaveUser::getDn)
                .map(SubjectIssuerDNPair::subjectDN)
                .toArray(String[]::new);
        // @formatter:on
    }
    
    public long getCreationTime() {
        return this.creationTime;
    }
    
    @Override
    @XmlElement
    public String getName() {
        return this.username;
    }
    
    public String getShortName() {
        return DnUtils.getShortName(getPrimaryUser().getName());
    }
    
    public SubjectIssuerDNPair getUserDN() {
        return getPrimaryUser().getDn();
    }
    
    public List<String> getProxyServers() {
        
        // @formatter:off
        List<String> proxyServers = orderProxiedUsers(this.proxiedUsers).stream()
                .filter(u -> u.getUserType() == UserType.SERVER)
                .filter(u -> !u.equals(this.primaryUser))
                .map(DatawaveUser::getDn)
                .map(SubjectIssuerDNPair::subjectDN)
                .collect(Collectors.toList());
        // @formatter:on
        return proxyServers.isEmpty() ? null : proxyServers;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        
        DatawavePrincipal that = (DatawavePrincipal) o;
        
        if (!username.equals(that.username))
            return false;
        return proxiedUsers.equals(that.proxiedUsers);
    }
    
    @Override
    public int hashCode() {
        int result = username.hashCode();
        result = 31 * result + proxiedUsers.hashCode();
        return result;
    }
    
    @Override
    public String toString() {
        return "DatawavePrincipal{" + "name='" + username + "'" + ", proxiedUsers=" + DatawavePrincipal.orderProxiedUsers(proxiedUsers) + "}";
    }
    
    public static DatawavePrincipal anonymousPrincipal() {
        return new DatawavePrincipal("ANONYMOUS");
    }
    
}

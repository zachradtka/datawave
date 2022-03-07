package datawave.webservice.query.result.event;

import java.util.List;
import java.util.Map;

import io.protostuff.Message;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlTransient;

import com.google.common.collect.Maps;

@XmlAccessorType(XmlAccessType.NONE)
@XmlSeeAlso(DefaultEvent.class)
public abstract class EventBase<T,F extends FieldBase<F>> implements HasMarkings, Message<T> {
    
    protected transient Map<String,String> markings;
    
    public abstract Metadata getMetadata();
    
    public abstract void setMetadata(Metadata metadata);
    
    /**
     * @param size
     *            the approximate size of this event in bytes
     */
    public abstract void setSizeInBytes(long size);
    
    /**
     * @return The set size in bytes, -1 if unset
     */
    public abstract long getSizeInBytes();
    
    /**
     * Get the approximate size of this event in bytes. Used by the ObjectSizeOf mechanism in the webservice. Throws an exception if the local size was not set
     * to allow the ObjectSizeOf mechanism to do its thang.
     */
    public abstract long sizeInBytes();
    
    public abstract void setFields(List<F> fields);
    
    @XmlTransient
    public abstract List<F> getFields();

    public abstract void setEncryptedKeys(List<EncryptedKey> encryptedKeys);

    public abstract List<EncryptedKey> getEncryptedKeys();

    public abstract void setEncryptedPayloads(List<EncryptedPayload> encryptedPayloads);

    public abstract List<EncryptedPayload> getEncryptedPayloads();

    public Map<String,String> getMarkings() {
        assureMarkings();
        return markings;
    }
    
    protected void assureMarkings() {
        if (this.markings == null)
            this.markings = Maps.newHashMap();
    }
    
    public void setMarkings(Map<String,String> markings) {
        this.markings = markings;
    }


    
}

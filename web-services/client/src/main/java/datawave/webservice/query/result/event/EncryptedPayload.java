package datawave.webservice.query.result.event;

import io.protostuff.Input;
import io.protostuff.Message;
import io.protostuff.Output;
import io.protostuff.Schema;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import javax.xml.bind.annotation.XmlAccessOrder;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorOrder;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;

@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)
@XmlAccessorOrder(XmlAccessOrder.ALPHABETICAL)
public class EncryptedPayload implements Serializable, Message<EncryptedPayload> {
    @XmlAttribute(name = "Name")
    String name;
    @XmlAttribute(name = "Value")
    String cipherText;

    public EncryptedPayload() {}

    public EncryptedPayload(String name, String cipherText) {
        this.name = name;
        this.cipherText = cipherText;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getCipherText() {
        return cipherText;
    }
    
    public void setCipherText(String cipherText) {
        this.cipherText = cipherText;
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("EncryptedPayload [name=").append(name);
        buf.append(" cipherText=").append(cipherText);
        buf.append("]");
        return buf.toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(name).append(cipherText).hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof EncryptedPayload) {
            EncryptedPayload v = (EncryptedPayload) o;

            EqualsBuilder eb = new EqualsBuilder();

            eb.append(this.name, v.name);
            eb.append(this.cipherText, v.cipherText);
            return eb.isEquals();
        }

        return false;
    }

    @Override
    public Schema<EncryptedPayload> cachedSchema() {
        return SCHEMA;
    }

    @XmlTransient
    private static final Schema<EncryptedPayload> SCHEMA = new Schema<EncryptedPayload>() {

        @Override
        public EncryptedPayload newMessage() {
            return new EncryptedPayload();
        }

        @Override
        public Class<? super EncryptedPayload> typeClass() {
            return EncryptedPayload.class;
        }

        @Override
        public String messageName() {
            return EncryptedPayload.class.getSimpleName();
        }

        @Override
        public String messageFullName() {
            return EncryptedPayload.class.getName();
        }

        @Override
        public boolean isInitialized(EncryptedPayload message) {
            return true;
        }

        @Override
        public void writeTo(Output output, EncryptedPayload message) throws IOException {
            if (message.name != null)
                output.writeString(1, message.name, false);
            if (message.cipherText != null)
                output.writeString(2, message.cipherText, false);

        }

        @Override
        public void mergeFrom(Input input, EncryptedPayload message) throws IOException {
            int number;
            while ((number = input.readFieldNumber(this)) != 0) {
                switch (number) {
                    case 1:
                        message.name = input.readString();
                        break;
                    case 2:
                        message.cipherText = input.readString();
                        break;
                    default:
                        input.handleUnknownField(number, this);
                        break;
                }
            }
        }

        @Override
        public String getFieldName(int number) {
            switch (number) {
                case 1:
                    return "name";
                case 2:
                    return "cipherText";
                default:
                    return null;
            }
        }

        @Override
        public int getFieldNumber(String name) {
            final Integer number = fieldMap.get(name);
            return number == null ? 0 : number.intValue();
        }

        private final HashMap<String,Integer> fieldMap = new HashMap<String,Integer>();
        {
            fieldMap.put("name", 1);
            fieldMap.put("cipherText", 2);
        }
    };
}

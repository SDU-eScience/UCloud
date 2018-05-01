
package org.datacite.schema;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for namePart.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="namePart">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="Family"/>
 *     &lt;enumeration value="Given"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "namePart")
@XmlEnum
public enum NamePart {

    @XmlEnumValue("Family")
    FAMILY("Family"),
    @XmlEnumValue("Given")
    GIVEN("Given");
    private final String value;

    NamePart(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static NamePart fromValue(String v) {
        for (NamePart c: NamePart.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}

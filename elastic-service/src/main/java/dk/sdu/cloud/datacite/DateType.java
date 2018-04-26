
package dk.sdu.cloud.datacite;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for dateType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="dateType">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="Accepted"/>
 *     &lt;enumeration value="Available "/>
 *     &lt;enumeration value="Copyrighted"/>
 *     &lt;enumeration value="Created"/>
 *     &lt;enumeration value="EndDate"/>
 *     &lt;enumeration value="Issued"/>
 *     &lt;enumeration value="StartDate"/>
 *     &lt;enumeration value="Submitted"/>
 *     &lt;enumeration value="Updated"/>
 *     &lt;enumeration value="Valid"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "dateType")
@XmlEnum
public enum DateType {

    @XmlEnumValue("Accepted")
    ACCEPTED("Accepted"),
    @XmlEnumValue("Available ")
    AVAILABLE("Available "),
    @XmlEnumValue("Copyrighted")
    COPYRIGHTED("Copyrighted"),
    @XmlEnumValue("Created")
    CREATED("Created"),
    @XmlEnumValue("EndDate")
    END_DATE("EndDate"),
    @XmlEnumValue("Issued")
    ISSUED("Issued"),
    @XmlEnumValue("StartDate")
    START_DATE("StartDate"),
    @XmlEnumValue("Submitted")
    SUBMITTED("Submitted"),
    @XmlEnumValue("Updated")
    UPDATED("Updated"),
    @XmlEnumValue("Valid")
    VALID("Valid");
    private final String value;

    DateType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static DateType fromValue(String v) {
        for (DateType c: DateType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}

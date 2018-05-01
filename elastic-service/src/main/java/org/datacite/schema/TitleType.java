
package org.datacite.schema;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for titleType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="titleType">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="AlternativeTitle"/>
 *     &lt;enumeration value="Subtitle"/>
 *     &lt;enumeration value="TranslatedTitle"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "titleType")
@XmlEnum
public enum TitleType {

    @XmlEnumValue("AlternativeTitle")
    ALTERNATIVE_TITLE("AlternativeTitle"),
    @XmlEnumValue("Subtitle")
    SUBTITLE("Subtitle"),
    @XmlEnumValue("TranslatedTitle")
    TRANSLATED_TITLE("TranslatedTitle");
    private final String value;

    TitleType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static TitleType fromValue(String v) {
        for (TitleType c: TitleType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}

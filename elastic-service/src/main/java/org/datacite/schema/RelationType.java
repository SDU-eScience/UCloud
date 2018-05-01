
package org.datacite.schema;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for relationType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="relationType">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="IsCitedBy"/>
 *     &lt;enumeration value="Cites"/>
 *     &lt;enumeration value="IsSupplementTo"/>
 *     &lt;enumeration value="IsSupplementedBy"/>
 *     &lt;enumeration value="IsContinuedBy"/>
 *     &lt;enumeration value="Continues"/>
 *     &lt;enumeration value="IsNewVersionOf"/>
 *     &lt;enumeration value="IsPreviousVersionOf"/>
 *     &lt;enumeration value="IsPartOf"/>
 *     &lt;enumeration value="HasPart"/>
 *     &lt;enumeration value="IsReferencedBy"/>
 *     &lt;enumeration value="References"/>
 *     &lt;enumeration value="IsDocumentedBy"/>
 *     &lt;enumeration value="Documents"/>
 *     &lt;enumeration value="IsCompiledBy"/>
 *     &lt;enumeration value="Compiles"/>
 *     &lt;enumeration value="IsVariantFormOf"/>
 *     &lt;enumeration value="IsOriginalFormOf"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "relationType")
@XmlEnum
public enum RelationType {

    @XmlEnumValue("IsCitedBy")
    IS_CITED_BY("IsCitedBy"),
    @XmlEnumValue("Cites")
    CITES("Cites"),
    @XmlEnumValue("IsSupplementTo")
    IS_SUPPLEMENT_TO("IsSupplementTo"),
    @XmlEnumValue("IsSupplementedBy")
    IS_SUPPLEMENTED_BY("IsSupplementedBy"),
    @XmlEnumValue("IsContinuedBy")
    IS_CONTINUED_BY("IsContinuedBy"),
    @XmlEnumValue("Continues")
    CONTINUES("Continues"),
    @XmlEnumValue("IsNewVersionOf")
    IS_NEW_VERSION_OF("IsNewVersionOf"),
    @XmlEnumValue("IsPreviousVersionOf")
    IS_PREVIOUS_VERSION_OF("IsPreviousVersionOf"),
    @XmlEnumValue("IsPartOf")
    IS_PART_OF("IsPartOf"),
    @XmlEnumValue("HasPart")
    HAS_PART("HasPart"),
    @XmlEnumValue("IsReferencedBy")
    IS_REFERENCED_BY("IsReferencedBy"),
    @XmlEnumValue("References")
    REFERENCES("References"),
    @XmlEnumValue("IsDocumentedBy")
    IS_DOCUMENTED_BY("IsDocumentedBy"),
    @XmlEnumValue("Documents")
    DOCUMENTS("Documents"),
    @XmlEnumValue("IsCompiledBy")
    IS_COMPILED_BY("IsCompiledBy"),
    @XmlEnumValue("Compiles")
    COMPILES("Compiles"),
    @XmlEnumValue("IsVariantFormOf")
    IS_VARIANT_FORM_OF("IsVariantFormOf"),
    @XmlEnumValue("IsOriginalFormOf")
    IS_ORIGINAL_FORM_OF("IsOriginalFormOf");
    private final String value;

    RelationType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static RelationType fromValue(String v) {
        for (RelationType c: RelationType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}

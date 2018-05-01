
package org.datacite.schema;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for contributorType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="contributorType">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="ContactPerson"/>
 *     &lt;enumeration value="DataCollector"/>
 *     &lt;enumeration value="DataManager"/>
 *     &lt;enumeration value="Editor"/>
 *     &lt;enumeration value="HostingInstitution"/>
 *     &lt;enumeration value="ProjectLeader"/>
 *     &lt;enumeration value="ProjectMember"/>
 *     &lt;enumeration value="RegistrationAgency"/>
 *     &lt;enumeration value="RegistrationAuthority"/>
 *     &lt;enumeration value="Researcher"/>
 *     &lt;enumeration value="WorkPackageLeader"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "contributorType")
@XmlEnum
public enum ContributorType {

    @XmlEnumValue("ContactPerson")
    CONTACT_PERSON("ContactPerson"),
    @XmlEnumValue("DataCollector")
    DATA_COLLECTOR("DataCollector"),
    @XmlEnumValue("DataManager")
    DATA_MANAGER("DataManager"),
    @XmlEnumValue("Editor")
    EDITOR("Editor"),
    @XmlEnumValue("HostingInstitution")
    HOSTING_INSTITUTION("HostingInstitution"),
    @XmlEnumValue("ProjectLeader")
    PROJECT_LEADER("ProjectLeader"),
    @XmlEnumValue("ProjectMember")
    PROJECT_MEMBER("ProjectMember"),
    @XmlEnumValue("RegistrationAgency")
    REGISTRATION_AGENCY("RegistrationAgency"),
    @XmlEnumValue("RegistrationAuthority")
    REGISTRATION_AUTHORITY("RegistrationAuthority"),
    @XmlEnumValue("Researcher")
    RESEARCHER("Researcher"),
    @XmlEnumValue("WorkPackageLeader")
    WORK_PACKAGE_LEADER("WorkPackageLeader");
    private final String value;

    ContributorType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ContributorType fromValue(String v) {
        for (ContributorType c: ContributorType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}

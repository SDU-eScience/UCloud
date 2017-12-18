package org.esciencecloud.auth.services.saml

object AttributeURIs {
    const val DisplayName = "urn:oid:2.16.840.1.113730.3.1.241"
    const val CommonName = "urn:oid:2.5.4.3"
    const val GivenName = "urn:oid:2.5.4.42"
    const val FamilyName = "urn:oid:2.5.4.4"
    const val Email = "urn:oid:0.9.2342.19200300.100.1.3"

    // https://www.internet2.edu/products-services/trust-identity/mace-registries/internet2-object-identifier-oid-registrations/
    const val EduPersonAssurance = "urn:oid:1.3.6.1.4.1.5923.1.1.1.11"
    const val EduPersonAffiliation = "urn:oid:1.3.6.1.4.1.5923.1.1.1.1"
    const val EduPersonPrimaryAffiliation = "urn:oid:1.3.6.1.4.1.5923.1.1.1.5"
    const val EduPersonPrincipalName = "urn:oid:1.3.6.1.4.1.5923.1.1.1.6"
    const val EduPersonTargetedId = "urn:oid:1.3.6.1.4.1.5923.1.1.1.10"

    // https://wiki.refeds.org/display/STAN/SCHAC+OID+Registry
    const val SchacHomeOrganization = "urn:oid:1.3.6.1.4.1.25178.1.2.9"
    const val SchacHomeOrganizationType = "urn:oid:1.3.6.1.4.1.25178.1.2.10"
}
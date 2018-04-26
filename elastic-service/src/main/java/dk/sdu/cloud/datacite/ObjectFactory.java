
package dk.sdu.cloud.datacite;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the org.datacite.schema package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {

    private final static QName _ResourceDescriptionsDescriptionBr_QNAME = new QName("", "br");
    private final static QName _ResourceContributorsContributorContributorName_QNAME = new QName("", "contributorName");
    private final static QName _ResourceContributorsContributorNameIdentifier_QNAME = new QName("", "nameIdentifier");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: org.datacite.schema
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link Resource }
     * 
     */
    public Resource createResource() {
        return new Resource();
    }

    /**
     * Create an instance of {@link Resource.Descriptions }
     * 
     */
    public Resource.Descriptions createResourceDescriptions() {
        return new Resource.Descriptions();
    }

    /**
     * Create an instance of {@link Resource.RelatedIdentifiers }
     * 
     */
    public Resource.RelatedIdentifiers createResourceRelatedIdentifiers() {
        return new Resource.RelatedIdentifiers();
    }

    /**
     * Create an instance of {@link Resource.AlternateIdentifiers }
     * 
     */
    public Resource.AlternateIdentifiers createResourceAlternateIdentifiers() {
        return new Resource.AlternateIdentifiers();
    }

    /**
     * Create an instance of {@link Resource.Dates }
     * 
     */
    public Resource.Dates createResourceDates() {
        return new Resource.Dates();
    }

    /**
     * Create an instance of {@link Resource.Contributors }
     * 
     */
    public Resource.Contributors createResourceContributors() {
        return new Resource.Contributors();
    }

    /**
     * Create an instance of {@link Resource.Contributors.Contributor }
     * 
     */
    public Resource.Contributors.Contributor createResourceContributorsContributor() {
        return new Resource.Contributors.Contributor();
    }

    /**
     * Create an instance of {@link Resource.Subjects }
     * 
     */
    public Resource.Subjects createResourceSubjects() {
        return new Resource.Subjects();
    }

    /**
     * Create an instance of {@link Resource.Titles }
     * 
     */
    public Resource.Titles createResourceTitles() {
        return new Resource.Titles();
    }

    /**
     * Create an instance of {@link Resource.Creators }
     * 
     */
    public Resource.Creators createResourceCreators() {
        return new Resource.Creators();
    }

    /**
     * Create an instance of {@link Resource.Creators.Creator }
     * 
     */
    public Resource.Creators.Creator createResourceCreatorsCreator() {
        return new Resource.Creators.Creator();
    }

    /**
     * Create an instance of {@link Resource.Identifier }
     * 
     */
    public Resource.Identifier createResourceIdentifier() {
        return new Resource.Identifier();
    }

    /**
     * Create an instance of {@link Resource.ResourceType }
     * 
     */
    public Resource.ResourceType createResourceResourceType() {
        return new Resource.ResourceType();
    }

    /**
     * Create an instance of {@link Resource.Sizes }
     * 
     */
    public Resource.Sizes createResourceSizes() {
        return new Resource.Sizes();
    }

    /**
     * Create an instance of {@link Resource.Formats }
     * 
     */
    public Resource.Formats createResourceFormats() {
        return new Resource.Formats();
    }

    /**
     * Create an instance of {@link Resource.Descriptions.Description }
     * 
     */
    public Resource.Descriptions.Description createResourceDescriptionsDescription() {
        return new Resource.Descriptions.Description();
    }

    /**
     * Create an instance of {@link Resource.RelatedIdentifiers.RelatedIdentifier }
     * 
     */
    public Resource.RelatedIdentifiers.RelatedIdentifier createResourceRelatedIdentifiersRelatedIdentifier() {
        return new Resource.RelatedIdentifiers.RelatedIdentifier();
    }

    /**
     * Create an instance of {@link Resource.AlternateIdentifiers.AlternateIdentifier }
     * 
     */
    public Resource.AlternateIdentifiers.AlternateIdentifier createResourceAlternateIdentifiersAlternateIdentifier() {
        return new Resource.AlternateIdentifiers.AlternateIdentifier();
    }

    /**
     * Create an instance of {@link Resource.Dates.Date }
     * 
     */
    public Resource.Dates.Date createResourceDatesDate() {
        return new Resource.Dates.Date();
    }

    /**
     * Create an instance of {@link Resource.Contributors.Contributor.NameIdentifier }
     * 
     */
    public Resource.Contributors.Contributor.NameIdentifier createResourceContributorsContributorNameIdentifier() {
        return new Resource.Contributors.Contributor.NameIdentifier();
    }

    /**
     * Create an instance of {@link Resource.Subjects.Subject }
     * 
     */
    public Resource.Subjects.Subject createResourceSubjectsSubject() {
        return new Resource.Subjects.Subject();
    }

    /**
     * Create an instance of {@link Resource.Titles.Title }
     * 
     */
    public Resource.Titles.Title createResourceTitlesTitle() {
        return new Resource.Titles.Title();
    }

    /**
     * Create an instance of {@link Resource.Creators.Creator.NameIdentifier }
     * 
     */
    public Resource.Creators.Creator.NameIdentifier createResourceCreatorsCreatorNameIdentifier() {
        return new Resource.Creators.Creator.NameIdentifier();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "", name = "br", scope = Resource.Descriptions.Description.class)
    public JAXBElement<String> createResourceDescriptionsDescriptionBr(String value) {
        return new JAXBElement<String>(_ResourceDescriptionsDescriptionBr_QNAME, String.class, Resource.Descriptions.Description.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Object }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "", name = "contributorName", scope = Resource.Contributors.Contributor.class)
    public JAXBElement<Object> createResourceContributorsContributorContributorName(Object value) {
        return new JAXBElement<Object>(_ResourceContributorsContributorContributorName_QNAME, Object.class, Resource.Contributors.Contributor.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Resource.Contributors.Contributor.NameIdentifier }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "", name = "nameIdentifier", scope = Resource.Contributors.Contributor.class)
    public JAXBElement<Resource.Contributors.Contributor.NameIdentifier> createResourceContributorsContributorNameIdentifier(Resource.Contributors.Contributor.NameIdentifier value) {
        return new JAXBElement<Resource.Contributors.Contributor.NameIdentifier>(_ResourceContributorsContributorNameIdentifier_QNAME, Resource.Contributors.Contributor.NameIdentifier.class, Resource.Contributors.Contributor.class, value);
    }

}

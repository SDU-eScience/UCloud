package datacite

type Metadata struct {
	// Mandatory
	Identifier      *Identifier   `json:"identifier,omitempty"`
	Creators        []Creator     `json:"creators,omitempty"`
	Titles          []Title       `json:"titles,omitempty"`
	Publisher       *Publisher    `json:"publisher,omitempty"`
	PublicationYear *int          `json:"publicationYear,omitempty"`
	Types           *ResourceType `json:"types,omitempty"`

	// Recommended
	Subjects           []Subject           `json:"subjects,omitempty"`
	Contributors       []Contributor       `json:"contributors,omitempty"`
	Dates              []Date              `json:"dates,omitempty"`
	RelatedIdentifiers []RelatedIdentifier `json:"relatedIdentifiers,omitempty"`
	Descriptions       []Description       `json:"descriptions,omitempty"`
	GeoLocations       []GeoLocation       `json:"geoLocations,omitempty"`

	// Optional
	Language             *string               `json:"language,omitempty"`
	AlternateIdentifiers []AlternateIdentifier `json:"alternateIdentifiers,omitempty"`
	Sizes                []string              `json:"sizes,omitempty"`
	Formats              []string              `json:"formats,omitempty"`
	Version              *string               `json:"version,omitempty"`
	RightsList           []Rights              `json:"rightsList,omitempty"`
	FundingReferences    []FundingReference    `json:"fundingReferences,omitempty"`
	RelatedItems         []RelatedItem         `json:"relatedItems,omitempty"`
}

type Identifier struct {
	Identifier     string `json:"identifier"`
	IdentifierType string `json:"identifierType"`
}

type Creator struct {
	Name            string           `json:"name"`
	NameType        *string          `json:"nameType,omitempty"`
	GivenName       *string          `json:"givenName,omitempty"`
	FamilyName      *string          `json:"familyName,omitempty"`
	NameIdentifiers []NameIdentifier `json:"nameIdentifiers,omitempty"`
	Affiliation     []Affiliation    `json:"affiliation,omitempty"`
}

type Title struct {
	Title     string  `json:"title"`
	TitleType *string `json:"titleType,omitempty"`
	Lang      *string `json:"lang,omitempty"`
}

type Publisher struct {
	Name                      string  `json:"name"`
	PublisherIdentifier       *string `json:"publisherIdentifier,omitempty"`
	PublisherIdentifierScheme *string `json:"publisherIdentifierScheme,omitempty"`
	SchemeURI                 *string `json:"schemeUri,omitempty"`
	Lang                      *string `json:"lang,omitempty"`
}

type ResourceType struct {
	ResourceType        *string `json:"resourceType,omitempty"`
	ResourceTypeGeneral string  `json:"resourceTypeGeneral"`
}

type Subject struct {
	Subject            string  `json:"subject"`
	SubjectScheme      *string `json:"subjectScheme,omitempty"`
	SchemeURI          *string `json:"schemeUri,omitempty"`
	ValueURI           *string `json:"valueUri,omitempty"`
	ClassificationCode *string `json:"classificationCode,omitempty"`
	Lang               *string `json:"lang,omitempty"`
}

type Contributor struct {
	Name            string           `json:"name"`
	ContributorType string           `json:"contributorType"`
	NameType        *string          `json:"nameType,omitempty"`
	GivenName       *string          `json:"givenName,omitempty"`
	FamilyName      *string          `json:"familyName,omitempty"`
	NameIdentifiers []NameIdentifier `json:"nameIdentifiers,omitempty"`
	Affiliation     []Affiliation    `json:"affiliation,omitempty"`
}

type NameIdentifier struct {
	NameIdentifier       string  `json:"nameIdentifier"`
	NameIdentifierScheme string  `json:"nameIdentifierScheme"`
	SchemeURI            *string `json:"schemeUri,omitempty"`
}

type Affiliation struct {
	Name                        string  `json:"name"`
	AffiliationIdentifier       *string `json:"affiliationIdentifier,omitempty"`
	AffiliationIdentifierScheme *string `json:"affiliationIdentifierScheme,omitempty"`
	SchemeURI                   *string `json:"schemeUri,omitempty"`
}

type Date struct {
	Date            string  `json:"date"`
	DateType        string  `json:"dateType"`
	DateInformation *string `json:"dateInformation,omitempty"`
}

type AlternateIdentifier struct {
	AlternateIdentifier     string `json:"alternateIdentifier"`
	AlternateIdentifierType string `json:"alternateIdentifierType"`
}

type RelatedIdentifier struct {
	RelatedIdentifier       string  `json:"relatedIdentifier"`
	RelatedIdentifierType   string  `json:"relatedIdentifierType"`
	RelationType            string  `json:"relationType"`
	RelatedMetadataScheme   *string `json:"relatedMetadataScheme,omitempty"`
	SchemeURI               *string `json:"schemeUri,omitempty"`
	SchemeType              *string `json:"schemeType,omitempty"`
	ResourceTypeGeneral     *string `json:"resourceTypeGeneral,omitempty"`
	RelationTypeInformation *string `json:"relationTypeInformation,omitempty"`
}

type Description struct {
	Description     string  `json:"description"`
	DescriptionType string  `json:"descriptionType"`
	Lang            *string `json:"lang,omitempty"`
}

type GeoLocation struct {
	GeoLocationPlace   *string      `json:"geoLocationPlace,omitempty"`
	GeoLocationPoint   *GeoPoint    `json:"geoLocationPoint,omitempty"`
	GeoLocationBox     *GeoBox      `json:"geoLocationBox,omitempty"`
	GeoLocationPolygon []GeoPolygon `json:"geoLocationPolygon,omitempty"`
}

type GeoPoint struct {
	PointLongitude float64 `json:"pointLongitude"`
	PointLatitude  float64 `json:"pointLatitude"`
}

type GeoBox struct {
	WestBoundLongitude float64 `json:"westBoundLongitude"`
	EastBoundLongitude float64 `json:"eastBoundLongitude"`
	SouthBoundLatitude float64 `json:"southBoundLatitude"`
	NorthBoundLatitude float64 `json:"northBoundLatitude"`
}

type GeoPolygon struct {
	PolygonPoint   []PolygonPoint `json:"polygonPoint,omitempty"`
	InPolygonPoint []PolygonPoint `json:"inPolygonPoint,omitempty"`
}

type PolygonPoint struct {
	PointLongitude float64 `json:"pointLongitude"`
	PointLatitude  float64 `json:"pointLatitude"`
}

type Rights struct {
	Rights                 string  `json:"rights"`
	RightsURI              *string `json:"rightsUri,omitempty"`
	RightsIdentifier       *string `json:"rightsIdentifier,omitempty"`
	RightsIdentifierScheme *string `json:"rightsIdentifierScheme,omitempty"`
	SchemeURI              *string `json:"schemeUri,omitempty"`
	Lang                   *string `json:"lang,omitempty"`
}

type FundingReference struct {
	FunderName           string  `json:"funderName"`
	FunderIdentifier     *string `json:"funderIdentifier,omitempty"`
	FunderIdentifierType *string `json:"funderIdentifierType,omitempty"`
	SchemeURI            *string `json:"schemeUri,omitempty"`
	AwardNumber          *string `json:"awardNumber,omitempty"`
	AwardURI             *string `json:"awardUri,omitempty"`
	AwardTitle           *string `json:"awardTitle,omitempty"`
}

type RelatedItem struct {
	RelatedItemType         string                 `json:"relatedItemType"`
	RelationType            string                 `json:"relationType"`
	RelationTypeInformation *string                `json:"relationTypeInformation,omitempty"`
	RelatedItemIdentifier   *RelatedItemIdentifier `json:"relatedItemIdentifier,omitempty"`
	Creators                []Creator              `json:"creators,omitempty"`
	Titles                  []Title                `json:"titles,omitempty"`
	PublicationYear         *string                `json:"publicationYear,omitempty"`
	Volume                  *string                `json:"volume,omitempty"`
	Issue                   *string                `json:"issue,omitempty"`
	Number                  *string                `json:"number,omitempty"`
	NumberType              *string                `json:"numberType,omitempty"`
	FirstPage               *string                `json:"firstPage,omitempty"`
	LastPage                *string                `json:"lastPage,omitempty"`
	Publisher               *string                `json:"publisher,omitempty"`
	Edition                 *string                `json:"edition,omitempty"`
	Contributors            []Contributor          `json:"contributors,omitempty"`
}

type RelatedItemIdentifier struct {
	RelatedItemIdentifier     string  `json:"relatedItemIdentifier"`
	RelatedItemIdentifierType string  `json:"relatedItemIdentifierType"`
	RelatedMetadataScheme     *string `json:"relatedMetadataScheme,omitempty"`
	SchemeURI                 *string `json:"schemeUri,omitempty"`
	SchemeType                *string `json:"schemeType,omitempty"`
}

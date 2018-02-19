import React from "react";
import {Button, FormGroup, Radio, FormControl, ControlLabel} from "react-bootstrap";
import FileSelector from "./FileSelector"

class ZenodoPublish extends React.Component {
    constructor(props) {
        super(props);
        const initialFile = "";//props.match.params[0] ? props.match.params[0] : "";
        // Todo: handle URIS passed in url
        this.state = {
            files: [initialFile],
            // community: null, // Recommended
            uploadType: {
                type: "Publication",
                subtype: "Book",
            }, // Required
            basicInformation: { // Required
                digitalObjectIdentifier: "",
                publicationDate: "",
                title: "",
                authors: [{name: "", affiliation: "", orcid: ""}],
                description: null,
                version: null,
                language: null, // TODO
                keywords: [""], // TODO
                additionalNotes: null, // TODO
            },
            license: { // Required
                accessRight: "Open Access", // TODO
                subfields: {
                    license: "Creative Commons Attribution 4.0"
                }, // TODO

            },
            /*funding: { // Recommended
                funder: null,
                numberNameAbbr: null,
            },
            relatedAndAlternativeIdentifiers: { // Recommended
                identifiers: [],
            },*/
        };
        this.handleFileSelection = this.handleFileSelection.bind(this);
        this.updateType = this.updateType.bind(this);
        this.updateSubtype = this.updateSubtype.bind(this);
        this.addAuthor = this.addAuthor.bind(this);
        this.addKeyword = this.addKeyword.bind(this);
        this.updateLicense = this.updateLicense.bind(this);
        this.updateAccessRight = this.updateAccessRight.bind(this);
        this.updateBasicInformationField = this.updateBasicInformationField.bind(this);
        this.updateAuthor = this.updateAuthor.bind(this);
        this.updateKeyword = this.updateKeyword.bind(this);
        this.newFile = this.newFile.bind(this);
    }

    validateFields(form) {

    }

    submit() {

    }

    handleFileSelection(file, index) {
        const files = this.state.files.slice();
        files[index] = file.path.uri;
        this.setState(() => ({
            files: files,
        }));
    }

    newFile() {
        const files = this.state.files.slice();
        files.push("");
        this.setState(() => ({
            files: files,
        }));
    }

    updateKeyword(index, value) {
        let {basicInformation} = this.state;
        let keywords = basicInformation.keywords.slice();
        keywords[index] = value;
        basicInformation.keywords = keywords;
        this.setState(() => ({
            basicInformation: basicInformation,
        }));
    }

    updateAuthor(index, field, value) {
        let {basicInformation} = this.state;
        let authors = basicInformation.authors.slice();
        authors[index][field] = value;
        basicInformation.authors = authors;
        this.setState(() => ({
            basicInformation: basicInformation,
        }))
    }

    updateBasicInformationField(field, value) {
        let {basicInformation} = this.state;
        basicInformation[field] = value;
        this.setState(() => ({
            basicInformation: basicInformation,
        }));
    }


    updateAccessRight(accessRight) {
        let {license} = this.state;
        license.accessRight = accessRight;
        this.setState(() => ({
            license: license,
        }));
    }

    updateLicense(newLicense) {
        let {license} = this.state;
        license.subfields.license = newLicense;
        this.setState(() => ({
            license: license,
        }));
    }

    addAuthor() {
        let {basicInformation} = this.state;
        basicInformation.authors.push({name: "", affiliation: "", orcid: ""});
        this.setState(() => ({
            basicInformation: basicInformation
        }));
    }

    addKeyword() {
        let {basicInformation} = this.state;
        basicInformation.keywords.push("");
        this.setState(() => ({
            basicInformation: basicInformation,
        }));
    }

    updateType(type) {
        let {uploadType} = this.state;
        uploadType.type = type;
        this.setState(() => ({
            uploadType: uploadType
        }))
    }

    updateSubtype(subtype) {
        let {uploadType} = this.state;
        uploadType.subtype = subtype;
        this.setState(() => ({
            uploadType: uploadType
        }))
    }

    render() {
        return (
            <section>
                <div className="container-fluid">
                    {/*<CardAndBody> // Only recommended
                        <Communities/>
                    </CardAndBody>*/}
                    <CardAndBody>
                        <h3>File Selection</h3>
                        <FileSelections handleFileSelection={this.handleFileSelection} files={this.state.files}
                                        newFile={this.newFile}/>
                        <Button onClick={() => this.newFile()}>Add additional file</Button>
                    </CardAndBody>
                    <CardAndBody>
                        <h3>Upload Type</h3>
                        <UploadType updateType={this.updateType} updateSubtype={this.updateSubtype}
                                    type={this.state.uploadType.type}/>
                    </CardAndBody>
                    <CardAndBody>
                        <h3>Basic information</h3>
                        <BasicInformation authors={this.state.basicInformation.authors} addAuthor={this.addAuthor}
                                          updateAuthor={this.updateAuthor} updateKeyword={this.updateKeyword}
                                          keywords={this.state.basicInformation.keywords} addKeyword={this.addKeyword}
                                          updateBasicInformation={this.updateBasicInformationField}/>
                    </CardAndBody>
                    <CardAndBody>
                        <h3>License</h3>
                        <License accessRight={this.state.license.accessRight} updateLicense={this.updateLicense}
                                 updateAccessRight={this.updateAccessRight}/>
                    </CardAndBody>
                    {/*<CardAndBody>    {//  Recommended}
                        <h3>Funding</h3>
                        <Funding/>
                    </CardAndBody> */}
                    {/*<CardAndBody>    {//  Recommended}
                        <h3>Identifiers</h3>
                        <Identifiers/>
                    </CardAndBody>*/}
                </div>
            </section>
        );
    }
}

function CardAndBody(props) {
    return (
        <div className="card">
            <div className="card-body">
                {props.children}
            </div>
        </div>
    )
}

function Communities(props) { // Recommended
    return null;
}

function FileSelections(props) {
    const files = props.files.slice();
    const fileSelectors = files.map((file, index) =>
        <FileSelector key={index} onFileSelectionChange={props.handleFileSelection} parameter={index} isSource={false}/>
    );
    return (
        <FormGroup>
            {fileSelectors}
        </FormGroup>);
}

function UploadType(props) {
    const uploadTypes = ["Publication", "Poster", "Presentation", "Dataset", "Image",
        "Video/Audio", "Software", "Lesson", "Other"];
    let dropdown = null;
    switch (props.type) {
        case "Publication":
            dropdown = (<UploadDropdown type={"publication"} updateSubtype={props.updateSubtype}/>);
            break;
        case "Image":
            dropdown = (<UploadDropdown type={"image"} updateSubtype={props.updateSubtype}/>);
            break;
        default:
            break;
    }
    let radioButtons = uploadTypes.map(type =>
        <Radio onChange={() => props.updateType(type)} key={type} inline checked={type === props.type}>
            {type}
        </Radio>
    );
    return (
        <FormGroup>
            {radioButtons}
            {dropdown}
        </FormGroup>
    );
}

function UploadDropdown(props) {
    let dropdownOptions;
    if (props.type === "publication") {
        dropdownOptions = ["Book", "Book section", "Conference paper", "Journal article", "Patent", "Preprint",
            "Project deliverable", "Project milestone", "Proposal", "Report", "Software documentation",
            "Thesis", "Technical note", "Working paper", "Other"];
    } else {
        dropdownOptions = ["Figure", "Plot", "Drawing", "Diagram", "Photo", "Other"];
    }
    const options = dropdownOptions.map(option =>
        <option key={option}>{option}</option>
    );
    return (
        <FormControl componentClass="select" onChange={e => props.updateSubtype(e.target.value)}>
            {options}
        </FormControl>);
}

function BasicInformation(props) {
    return (
        <FormGroup>
            <fieldset>
                <label className="col-sm-2 control-label">Digital object identifier</label>
                <div className="col-md-4">
                    <input placeholder="e.g. 10.1234/foor.bar" className="form-control"
                           type="text"
                           onChange={e => props.updateBasicInformation("digitalObjectIdentifier", e.target.value)}/>
                    <span className="help-block"><b>Optional.</b> Did your publisher already assign a DOI to your upload? If not, leave the field empty and we will register a new DOI for you. A DOI allows others to easily and unambiguously cite your upload. Please note that it is NOT possible to edit a Zenodo DOI once it has been registered by us, while it is always possible to edit a custom DOI.</span>
                </div>
            </fieldset>
            <fieldset>
                <div className="form-group">
                    <label className="col-sm-2 control-label">Publication date</label>
                    <div className="col-md-4">
                        <input placeholder="YYYY-MM-DD" className="form-control"
                               type="date"
                               onChange={e => props.updateBasicInformation("publicationDate", e.target.value)}/>
                    </div>
                </div>
            </fieldset>
            <fieldset>
                <label className="col-sm-2">Title</label>
                <div className="col-md-4 control-label">
                    <input type="text" className="form-control"
                           onChange={e => props.updateBasicInformation("title", e.target.value)}/>
                </div>
            </fieldset>
            <fieldset>
                <label className="col-sm-2">Authors</label>
                <AuthorList authors={props.authors} updateAuthor={props.updateAuthor}/>
                <Button onClick={() => props.addAuthor()}>Add Author</Button>
            </fieldset>
            {/*<fieldset>
                <div className="form-group">
                    <label className="col-sm-2 control-label">Comment</label>
                    <div className="col-md-4">
                        <textarea required style={{resize: "none"}} placeholder="Describe the upload"
                                  className="col-md-4 form-control" rows="5" onChange={e => props.updateBasicInformation("comment", e.target.value)}/>
                    </div>
                </div>
            </fieldset>*/}
            <fieldset>
                <label className="col-sm-2">Version</label>
                <div className="col-md-4 control-label">
                    <input type="text" className="form-control"
                           onChange={e => props.updateBasicInformation("version", e.target.value)}/>
                    <span className="help-block">Optional. Mostly relevant for software and dataset uploads. Any string will be accepted, but semantically-versioned tag is recommended.
                        See <a href="https://semver.org/" target="_blank">semver.org</a> for more information on semantic versioning.</span>
                </div>
            </fieldset>
            <fieldset>
                <label className="col-sm-2">Language</label>
                <div className="col-md-4 control-label">
                    <input type="text" className="form-control"
                           onChange={e => props.updateBasicInformation("language", e.target.value)}/>
                    <span className="help-block">Optional. Primary language of the record. Start by typing the language's common name in English, or its ISO 639 code (two or three-letter code).
                        See <a href="https://www.loc.gov/standards/iso639-2/php/code_list.php" target="_blank"> ISO 639 language codes list</a> for more information.</span>
                </div>
            </fieldset>
            <fieldset>
                <label className="col-sm-2 control-label">Keywords</label>
                <Keywords keywords={props.keywords} updateKeyword={props.updateKeyword}/>
                <Button onClick={() => props.addKeyword()}>Add keyword</Button>
            </fieldset>
        </FormGroup>)
}

function Keywords(props) {
    const keywordList = props.keywords.map((keyword, index) =>
        <input placeholder="Keyword..." key={index} className="form-control col-sm-4"
               type="text" onChange={e => props.updateKeyword(index, e.target.value)}/>
    );
    return (
        <FormGroup className="col-sm-4 control-label">
            {keywordList}
        </FormGroup>
    );
}

function AuthorList(props) {
    const authorList = props.authors.map((author, index) =>
        <div key={index}>
            <input placeholder="Name" className="form-control"
                   type="text" onChange={e => props.updateAuthor(index, "name", e.target.value)}/>
            <input placeholder="Affiliation" className="form-control"
                   type="text" onChange={e => props.updateAuthor(index, "affiliation", e.target.value)}/>
            <input placeholder="Orcid (Optional)" className="form-control"
                   type="text" onChange={e => props.updateAuthor(index, "orcid", e.target.value)}/>
            <span className="help-block">{`Author ${index + 1}`}</span>
        </div>
    );
    return (
        <FormGroup className="col-sm-4 control-label">
            {authorList}
        </FormGroup>);
}

function License(props) {
    const accessRight = ["Open Access", "Embargoed Access", "Restricted Access", "Closed Access"];
    let radioButtons = accessRight.map(accessRight =>
        <Radio onChange={() => props.updateAccessRight(accessRight)} key={accessRight} inline
               checked={accessRight === props.accessRight}>
            {accessRight}
        </Radio>
    );
    let additionalAccessRightsField = null;
    let licenseOptions = ["MIT License", "Apache Software License 2.0"]; // Retrieve from OpenDefinition
    switch (props.accessRight) {
        case "Open Access": {
            const options = licenseOptions.map(option =>
                <option key={option}>{option}</option>
            );
            additionalAccessRightsField = (
                <FormControl className="form-control" componentClass="select"
                             onChange={e => props.updateLicense(e.target.value)}>
                    {options}
                </FormControl>
            );
            break;
        }
        case "Embargoed Access": {
            // License
            const options = licenseOptions.map(option =>
                <option key={option}>{option}</option>
            );
            additionalAccessRightsField = (
                <div>
                    <label>Embargo Date</label>
                    <input placeholder="YYYY-MM-DD" className="form-control"
                           type="date" onChange={e => console.log("Publication Date for embargoed access")}/>
                    <FormControl componentClass="select" onChange={e => props.updateLicense(e.target.value)}>
                        {options}
                    </FormControl>
                </div>
            );
            break;
        }
        case "Restricted Access": {
            additionalAccessRightsField = (
                <div>
                    <ControlLabel>Conditions</ControlLabel>
                    <FormControl onChange={() => console.log("Conditions")} required componentClass="textarea" placeholder="Describe the condition for the restrictions..."/>
                </div>);
            break;
        }
        case "Closed Access":
            // No additional field. Already set to null;
            break;
        default:
            console.log("Unhandled case for accessRight");
            break;
    }
    return (
        <FormGroup>
            {radioButtons}
            {additionalAccessRightsField}
        </FormGroup>
    );
}

function Funding(props) {
    return null;
}

function Identifiers(props) {
    return null;
}

export default ZenodoPublish;
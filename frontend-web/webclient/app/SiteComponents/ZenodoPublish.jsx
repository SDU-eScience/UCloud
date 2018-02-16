import React from "react";
import {Button, FormGroup, Radio, FormControl} from "react-bootstrap";

class ZenodoPublish extends React.Component {
    constructor(props) {
        super(props);
        // Todo: handle URIS passed in url
        this.state = {
            files: [],
            community: null,
            uploadType: {
                type: "Publication",
                subtype: "Book",
            }, // Required
            basicInformation: { // Required
                digitalObjectIdentifier: null,
                publicationDate: null,
                title: null,
                authors: [{name: "", affiliation: "", orcid: ""}],
                description: null,
                version: null,
                language: null,
                keywords: [""],
                additionalNotes: null,
            },
            license: { // Required
                accessRight: "Open Access", // Radio buttons
                license: "Creative Commons Attribution 4.0" // Dropdown
            },
            funding: { // Recommended
                funder: null,
                numberNameAbbr: null,
            },
            relatedAndAlternativeIdentifiers: { // Recommended
                identifiers: [],
            },
        };
        this.updateType = this.updateType.bind(this);
        this.updateSubtype = this.updateSubtype.bind(this);
        this.addAuthor = this.addAuthor.bind(this);
        this.addKeyword = this.addKeyword.bind(this);
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
                    <CardAndBody>
                        <Communities/>
                    </CardAndBody>
                    <CardAndBody>
                        <FileSelections/>
                    </CardAndBody>
                    <CardAndBody>
                        <h3>Upload Type</h3>
                        <UploadType updateType={this.updateType} updateSubtype={this.updateSubtype}
                                    type={this.state.uploadType.type}/>
                    </CardAndBody>
                    <CardAndBody>
                        <h3>Basic information</h3>
                        <BasicInformation authors={this.state.basicInformation.authors} addAuthor={this.addAuthor}
                                          keywords={this.state.basicInformation.keywords} addKeyword={this.addKeyword}/>
                    </CardAndBody>
                    <CardAndBody>
                        <h3>License</h3>
                        <License/>
                    </CardAndBody>
                    <CardAndBody>
                        <h3>Funding</h3>
                        <Funding/>
                    </CardAndBody>
                    <CardAndBody>
                        <h3>Identifiers</h3>
                        <Identifiers/>
                    </CardAndBody>
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

function Communities(props) {
    return null;
}

function FileSelections(props) {
    return null;
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
        <div>
            <FormGroup>
                {radioButtons}
            </FormGroup>
            <FormGroup>
                {dropdown}
            </FormGroup>
        </div>);
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
                           type="text" onChange={e => console.log("Digital Object identifiers")}/>
                </div>
            </fieldset>
            <fieldset>
                <div className="form-group">
                    <label className="col-sm-2 control-label">Publication date</label>
                    <div className="col-md-4">
                        <input placeholder="YYYY-MM-DD" className="form-control" maxLength={10} minLength={10}
                               type="text" onChange={e => console.log("Publication Date")}/>
                    </div>
                </div>
            </fieldset>
            <fieldset>
                <label className="col-sm-2">Title</label>
                <div className="col-md-4 control-label">
                    <input type="text" className="form-control" onChange={e => console.log("Title")}/>
                </div>
            </fieldset>
            <fieldset>
                <label className="col-sm-2">Authors</label>
                <AuthorList authors={props.authors} addAuthor={props.addAuthor}/>
            </fieldset>
            <fieldset>
                <div className="form-group">
                    <label className="col-sm-2 control-label">Comment</label>
                    <div className="col-md-4">
                        <textarea required style={{resize: "none"}} placeholder="Describe the upload"
                                  className="col-md-4 form-control" rows="5" onChange={e => console.log(1)}/>
                    </div>
                </div>
            </fieldset>
            <fieldset>
                <label className="col-sm-2">Version</label>
                <div className="col-md-4 control-label">
                    <input type="text" className="form-control" onChange={e => console.log(1)}/>
                    <span className="help-block">Optional. Mostly relevant for software and dataset uploads. Any string will be accepted, but semantically-versioned tag is recommended.
                        See <a href="https://semver.org/" target="_blank">semver.org</a> for more information on semantic versioning.</span>
                </div>
            </fieldset>
            <fieldset>
                <label className="col-sm-2">Language</label>
                <div className="col-md-4 control-label">
                    <input type="text" className="form-control" onChange={e => console.log(1)}/>
                    <span className="help-block">Optional. Primary language of the record. Start by typing the language's common name in English, or its ISO 639 code (two or three-letter code).
                        See <a href="https://www.loc.gov/standards/iso639-2/php/code_list.php" target="_blank"> ISO 639 language codes list</a> for more information.</span>
                </div>
            </fieldset>
            <fieldset>
                <label className="col-sm-2 control-label">Keywords</label>
                <Keywords keywords={props.keywords} addKeyword={props.addKeyword}/>
            </fieldset>
            {/* Omitting additional keywords as it is optional*/}
        </FormGroup>)
}

function Keywords(props) {
    let i = 0;
    const keywordList = props.keywords.map(keyword =>
        <div key={i++} className="col-sm-4 input-group control-label">
            <input placeholder="Keyword..." className="form-control"
                type="text" onChange={e => console.log("Keyword")}/>
        </div>
    );
    return (
      <div>
          {keywordList}
          <Button onClick={() => props.addKeyword()}>Add Keyword</Button>
      </div>
    );
}

function AuthorList(props) {
    let i = 0;
    const authorList = props.authors.map(author =>
        <FormGroup key={i++}>
            <div className="col-sm-4 input-group control-label">
                <input placeholder="Name" className="form-control"
                       type="text" onChange={e => console.log("Name")}/>
                <input placeholder="Affiliation" className="form-control"
                       type="text" onChange={e => console.log("Affiliation")}/>
                <input placeholder="Orcid (Optional)" className="form-control"
                       type="text" onChange={e => console.log("Orcid")}/>
            </div>
        </FormGroup>
    );
    return (
        <div>
            {authorList}
            <Button onClick={() => props.addAuthor()}>Add Author</Button>
        </div>);
}

function License(props) {
    return (null);
}

function Funding(props) {
    return null;
}

function Identifiers(props) {
    return null;
}

export default ZenodoPublish;
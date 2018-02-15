import React from "react";
import SectionContainerCard from "./SectionContainerCard";
import {FormGroup, Radio, FormControl} from "react-bootstrap";

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
                authors: [{ name: "", affiliation: "", orcid: ""}],
                description: null,
                version: null,
                language: null,
                keywords: null,
                additionalNotes: null,
            },
            license: { // Required
                accessRight: null, // Radio buttons
                license: null // Dropdown
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
                        <BasicInformation authors={this.state.basicInformation.authors}/>
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
    console.log(props.authors.length);
    return (
        <FormGroup>
            <fieldset>
                <fieldset>
                    <FormGroup>
                        <label className="col-sm-3 control-label">Digital object identifier</label>
                        <div className="col-md-8">
                            <input placeholder="e.g. 10.1234/foor.bar"
                                   type="text" onChange={e => console.log(1)}/>
                        </div>
                    </FormGroup>
                </fieldset>
                <fieldset>
                    <FormGroup>
                        <label className="col-sm-3 control-label">Publication date</label>
                        <div className="col-md-8">
                            <input placeholder="YYYY-MM-DD" maxLength={10} minLength={10}
                                   type="text" onChange={e => console.log(1)}/>
                        </div>
                    </FormGroup>
                </fieldset>
                <fieldset>
                    <FormGroup>
                        <label className="col-sm-3 control-label">Title</label>
                        <div className="col-md-8">
                            <input minLength={1} type="text" onChange={e => console.log(1)}/>
                        </div>
                    </FormGroup>
                </fieldset>
                <fieldset>
                </fieldset>
                <AuthorList/>
            </fieldset>
        </FormGroup>)
}

function AuthorList(props) {
    return null;
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
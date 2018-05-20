import * as React from "react";
import { Search, Form, Header, Dropdown, Button } from "semantic-ui-react";
import { identifierTypes } from "../../DefaultObjects";
import { createRange } from "../../UtilityFunctions";
import { allLicenses } from "./licenses";
import { Creator, Grant, RelatedIdentifier, Subject } from "./api";

const newCollaborator = (): Creator => ({ name: "N", affiliation: "A", orcId: "O", gnd: "G" });
const newGrant = (): Grant => ({ id: "I" });
const newIdentifier = (): RelatedIdentifier => ({ identifier: "I", relation: "isCitedBy" });
const newSubject = (): Subject => ({ term: "T", identifier: "I" });

export class CreateUpdate extends React.Component<any, any> {
    constructor(props: any) {
        super(props);
        this.state = {
            title: "Title",
            description: "Description",
            license: {
                "title": "Adobe Glyph List License",
                "link": "https://spdx.org/licenses/Adobe-Glyph.html",
                "identifier": "Adobe-Glyph"
            },
            keywords: ["KW1", "KW2"],
            notes: "Note",
            collaborators: [newCollaborator()],
            references: ["Ref 1"],
            grants: [newGrant(), newGrant()],
            subjects: [newSubject()],
            identifiers: [newIdentifier()]
        };
        this.setStateEv = this.setStateEv.bind(this);
        this.setStateEvObject = this.setStateEvObject.bind(this);
        this.setStateEvObject = this.setStateEvObject.bind(this);
    }

    onSubmit(e) {
        e.preventDefault();
        console.log(this.state);
        // VALIDATION
        // TODO
        // VALIDATION END
    }

    addRow(e, key) {
        e.preventDefault();
        this.setState(() => ({ [key]: this.state[key].concat([""]) }));
    }

    addCollaborator(e) {
        e.preventDefault();
        this.setState(() => ({ collaborators: this.state.collaborators.concat(newCollaborator()) }));
    }

    addSubject(e) {
        e.preventDefault();
        this.setState(() => ({ subjects: this.state.subjects.concat(newSubject()) }));
    }

    addIdentifier(e) {
        e.preventDefault();
        this.setState(() => ({ identifiers: this.state.identifiers.concat(newIdentifier()) }));
    }

    setStateEv(key) {
        return (e, { value }) => {
            let object = {};
            object[key] = value;
            this.setState(() => object);
        };
    }

    setStateEvList(key) {
        return (value, index) => {
            const list = this.state[key];
            list[index] = value;
            let object = {
                [key]: list
            };
            this.setState(() => object);
        };
    }

    setStateEvObject(key) {
        return (value, index, member) => {
            const list = this.state[key];
            list[index][member] = value;
            let object = {
                [key]: list
            };
            this.setState(() => object);
        };
    }

    render() {
        return (
            <Form onSubmit={this.onSubmit}>
                <Form.Field required>
                    <label>Title</label>
                    <Form.Input
                        placeholder="Title"
                        value={this.state.title}
                        onChange={this.setStateEv("title")}
                        required
                    />
                </Form.Field>
                <Form.Field required>
                    <label>Description</label>
                    <Form.TextArea
                        value={this.state.description}
                        placeholder="Description"
                        onChange={this.setStateEv("description")}
                        required
                    />
                </Form.Field>
                <Form.Field>
                    <label>License</label>
                    <p>
                        {this.state.license ?
                            <span>
                                Selected
                                {" "}
                                <a href={this.state.license.link}>
                                    {this.state.license.title}
                                </a>

                                <Button 
                                    type="button" 
                                    basic 
                                    onClick={() => this.setState({ license: null})} 
                                    icon="remove"
                                    size="tiny"
                                    floated="right"
                                />
                            </span>

                            :

                            <span>
                                No license selected
                            </span>
                        }
                    </p>
                    <LicenseDropdown onChange={this.setStateEv("license")} />
                </Form.Field>

                <Form.Field>
                    <label>Keywords</label>
                    <FormFieldList
                        items={this.state.keywords}
                        name="keyword"
                        onChange={this.setStateEvList("keywords")}
                    />
                    <Button 
                        type="button" 
                        content="New keyword" 
                        onClick={(e) => this.addRow(e, "keywords")} 
                    />
                </Form.Field>

                <Form.Field>
                    <label>Notes</label>
                    <Form.TextArea
                        value={this.state.notes}
                        placeholder="Notes..."
                        onChange={this.setStateEv("notes")}
                    />
                </Form.Field>

                <Form.Field>
                    <label>Collaborators</label>
                    <Collaborators
                        collaborators={this.state.collaborators}
                        onChange={this.setStateEvObject("collaborators")}
                    />
                    <Button 
                        type="button" 
                        content="Add collaborator" 
                        onClick={(e) => this.addCollaborator(e)} 
                    />
                </Form.Field>

                <Form.Field>
                    <label>References</label>
                    <FormFieldList
                        name="reference"
                        items={this.state.references}
                        onChange={this.setStateEvList("references")}
                    />
                    <Button 
                        type="button" 
                        content="Add reference" 
                        onClick={(e) => this.addRow(e, "references")} 
                    />
                </Form.Field>

                <Form.Field>
                    <label>Subjects</label>
                    <Subjects
                        subjects={this.state.subjects}
                        onChange={this.setStateEvObject("subjects")}
                    />
                    <Button 
                        type="button" 
                        content="Add subject" 
                        onClick={(e) => this.addSubject(e)} 
                    />
                </Form.Field>

                <Form.Field>
                    <label>Related Identifiers</label>

                    <RelatedIdentifiers
                        identifiers={this.state.identifiers}
                        onChange={this.setStateEvObject("identifiers")}
                    />
                    <Button 
                        type="button" 
                        content="Add identifier" 
                        onClick={(e) => this.addIdentifier(e)} 
                    />
                </Form.Field>

                <Button 
                    positive
                    type="button" 
                    content="Submit" 
                    floated="right"
                    icon="checkmark"
                    onClick={(e) => this.onSubmit(e)} 
                />
            </Form>
        )
    }
}

interface LicenseDropdownProps {
    onChange: (ev, details) => void
}

interface LicenseDropdownState {
    isLoading: boolean
    value: string
    results: { title: string, identifier: string, link: string }[]
}

class LicenseDropdown extends React.Component<LicenseDropdownProps, LicenseDropdownState> {
    constructor(props: any) {
        super(props)

        this.state = { isLoading: false, value: "", results: [] };
    }

    handleSearchChange(value) {
        this.setState({ isLoading: true, value });
        setTimeout(() => {
            const results = allLicenses
                .filter(e => e.name.toLowerCase().indexOf(value.toLowerCase()) !== -1)
                .map(e => ({ title: e.name, identifier: e.identifier, link: e.link }));

            this.setState({ results });
        }, 0);
    }

    render() {
        return (
            <Search
                placeholder="Search for a license..."
                loading={false}
                onResultSelect={(e, { result }) => this.props.onChange(e, { value: result })}
                onSearchChange={(e, { value }) => this.handleSearchChange(value)}
                results={this.state.results}
                value={this.state.value}
            />
        );
    }
}

interface SubjectsProps {
    subjects: Subject[]
    onChange: (value, index: number, key: string) => void
}

const Subjects = ({ subjects, onChange }: SubjectsProps) =>
    <React.Fragment>
        {
            subjects.map((s, i) =>
                <Form.Group key={i} widths="equal">
                    <Form.Input
                        fluid
                        value={s.term}
                        label="Term"
                        required
                        placeholder="Term..."
                        onChange={(e, { value }) => onChange(value, i, "term")}
                    />
                    <Form.Input
                        fluid
                        value={s.identifier}
                        label="Identifier"
                        placeholder="Identifier..."
                        onChange={(e, { value }) => onChange(value, i, "identifier")}
                    />
                </Form.Group>
            )
        }
    </React.Fragment>

interface RelatedIdentifiersProps {
    identifiers: RelatedIdentifier[]
    onChange: (value, index: number, key: string) => void
}

const RelatedIdentifiers = ({ identifiers, onChange }: RelatedIdentifiersProps) =>
    <React.Fragment>
        {
            identifiers.map((identifier, i) =>
                <Form.Group key={i} widths="equal">
                    <Form.Input
                        fluid label="Identifier"
                        required
                        placeholder="Identifier..."
                        value={identifier.identifier}
                        onChange={(e, { value }) => onChange(value, i, "identifier")}
                    />
                    <Form.Dropdown label="Type"
                        search
                        searchInput={{ type: "string" }}
                        selection
                        options={identifierTypes}
                        value={identifier.relation}
                        placeholder="Select type"
                        onChange={(e, { value }) => onChange(value, i, "type")}
                    />
                </Form.Group>
            )
        }
    </React.Fragment>;

interface CollaboratorsProps {
    collaborators: Creator[]
    onChange: (value, index: number, key: string) => void
}

const Collaborators = ({ collaborators, onChange }: CollaboratorsProps) =>
    <React.Fragment>
        {
            collaborators.map((c, i) =>
                <Form.Group key={i} widths="equal">
                    <Form.Input
                        fluid
                        label="Name"
                        required
                        placeholder="Name..."
                        value={c.name}
                        onChange={(e, { value }) => onChange(value, i, "name")}
                    />

                    <Form.Input
                        fluid
                        label="Affiliation"
                        placeholder="Affiliation..."
                        value={c.affiliation}
                        onChange={(e, { value }) => onChange(value, i, "affiliation")}
                    />

                    <Form.Input
                        fluid
                        label="ORCiD"
                        placeholder="ORCiD..."
                        value={c.orcId}
                        onChange={(e, { value }) => onChange(value, i, "orcid")}
                    />

                    <Form.Input
                        fluid
                        label="GND"
                        placeholder="GND"
                        value={c.gnd}
                        onChange={(e, { value }) => onChange(value, i, "gnd")}
                    />
                </Form.Group>
            )
        }
    </React.Fragment>

const FormFieldList = ({ items, name, onChange }) =>
    <React.Fragment>
        {
            items.map((c, i) =>
                <Form.Input
                    key={i}
                    value={c}
                    placeholder={`Enter ${name}`}
                    onChange={(e, { value }) => onChange(value, i)}
                />)
        }
    </React.Fragment>;
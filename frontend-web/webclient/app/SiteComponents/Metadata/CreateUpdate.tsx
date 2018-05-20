import * as React from "react";
import { Search, Form, Header, Dropdown, Button } from "semantic-ui-react";
import { identifierTypes } from "../../DefaultObjects";
import { createRange } from "../../UtilityFunctions";
import { allLicenses } from "./licenses";
import { Creator, Grant, RelatedIdentifier, Subject } from "./api";

const newCollaborator = (): Creator => ({ name: "", affiliation: "", orcId: "", gnd: "" });
const newGrant = (): Grant => ({ id: "" });
const newIdentifier = (): RelatedIdentifier => ({ identifier: "", relation: "" });
const newSubject = (): Subject => ({ term: "", identifier: "" });

const creatorHasValue = (creator: Creator): boolean => {
    return (
        !blankOrNull(creator.affiliation) ||
        !blankOrNull(creator.orcId) ||
        !blankOrNull(creator.gnd) ||
        !blankOrNull(creator.name)
    );
}

const subjectHasValue = (subject: Subject): boolean => {
    return (
        !blankOrNull(subject.identifier) ||
        !blankOrNull(subject.term)
    );
}

const identifierHasValue = (identifier: RelatedIdentifier): boolean => {
    return (
        !blankOrNull(identifier.identifier) ||
        !blankOrNull(identifier.relation)
    );
};

export class CreateUpdate extends React.Component<any, any> {
    constructor(props: any) {
        super(props);
        this.state = {
            title: "",
            description: "",
            license: null,
            keywords: [""],
            notes: "",
            collaborators: [newCollaborator()],
            references: [""],
            grants: [newGrant(), newGrant()],
            subjects: [newSubject()],
            identifiers: [newIdentifier()],
            errors: { collaborators: {}, subjects: {}, identifiers: {} }
        };
        this.setStateEv = this.setStateEv.bind(this);
        this.setStateEvObject = this.setStateEvObject.bind(this);
        this.setStateEvObject = this.setStateEvObject.bind(this);
    }

    onSubmit(e) {
        e.preventDefault();

        const hasErrors = this.validateForm();
        console.log(hasErrors);

        if (!hasErrors) {
            const {
                title,
                description,
                license,
                keywords,
                notes,
                collaborators,
                references,
                grants,
                subjects,
                identifiers
            } = this.state;

            const licenseIdentifier = license ? license.identifier : null;

            const payload = {
                title,
                description,
                license: licenseIdentifier,
                keywords: keywords.filter(e => !blankOrNull(e)),
                notes,
                collaborators: collaborators.filter(e => creatorHasValue(e)),
                references: references.filter(e => !blankOrNull(e)),
                subjects: subjects.filter(e => subjectHasValue(e)),
                identifiers: identifiers.filter(e => identifierHasValue(e))
            };

            console.log(payload);
        }
    }

    validateForm(): boolean {
        let errors = {};

        if (blankOrNull(this.state.title)) errors["title"] = true;
        if (blankOrNull(this.state.description)) errors["description"] = true;

        let errCollaborators = {};
        this.state.collaborators.forEach((element, index) => {
            if (creatorHasValue(element)) {
                if (blankOrNull(element.name)) errCollaborators[index] = true;
            }
        });
        errors["collaborators"] = errCollaborators;

        let errSubjects = {};
        this.state.subjects.forEach((element, index) => {
            if (subjectHasValue(element)) {
                if (blankOrNull(element.term)) errSubjects[index] = true;
            }
        });
        errors["subjects"] = errSubjects;

        let errIdentifiers = {};
        this.state.identifiers.forEach((element, index) => {
            if (identifierHasValue(element)) {
                if (blankOrNull(element.identifier)) errIdentifiers[index] = true;
            }
        });
        errors["identifiers"] = errIdentifiers;

        this.setState({ errors });

        let hasError = false;
        Object.keys(errors).forEach(key => {
            if (typeof errors[key] === "object") {
                Object.keys(errors[key]).forEach(nestedKey => {
                    if (errors[key][nestedKey] === true) {
                        hasError = true;
                    }
                });
            } else if (errors[key] === true) {
                hasError = true;
            }
        });

        return hasError;
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
                        error={this.state.errors.title}
                        onChange={this.setStateEv("title")}
                        required
                    />
                </Form.Field>
                <Form.Field required>
                    <label>Description</label>
                    <Form.TextArea
                        value={this.state.description}
                        error={this.state.errors.description}
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
                                    onClick={() => this.setState({ license: null })}
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
                        errors={this.state.errors.collaborators}
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
                        errors={this.state.errors.subjects}
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
                        errors={this.state.errors.identifiers}
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
                <div className="clear"></div>
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
    errors: any
}

const Subjects = ({ subjects, errors, onChange }: SubjectsProps) =>
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
                        error={errors[i]}
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
    errors: any
}

const RelatedIdentifiers = ({ identifiers, errors, onChange }: RelatedIdentifiersProps) =>
    <React.Fragment>
        {
            identifiers.map((identifier, i) =>
                <Form.Group key={i} widths="equal">
                    <Form.Input
                        error={errors[i]}
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
                        onChange={(e, { value }) => onChange(value, i, "relation")}
                    />
                </Form.Group>
            )
        }
    </React.Fragment>;

interface CollaboratorsProps {
    collaborators: Creator[]
    onChange: (value, index: number, key: string) => void
    errors: any
}

const Collaborators = ({ collaborators, errors, onChange }: CollaboratorsProps) =>
    <React.Fragment>
        {
            collaborators.map((c, i) =>
                <Form.Group key={i} widths="equal">
                    <Form.Input
                        fluid
                        label="Name"
                        required
                        placeholder="Name..."
                        error={errors[i]}
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


const blankOrNull = (value: string): boolean => {
    return value == null || value.length == 0 || /^\s*$/.test(value);
}
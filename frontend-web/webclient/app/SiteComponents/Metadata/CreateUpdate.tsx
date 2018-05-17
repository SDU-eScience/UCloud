import * as React from "react";
import { Form, Header, Dropdown, Button } from "semantic-ui-react";
import { licenseOptions, identifierTypes } from "../../DefaultObjects";
import { createRange } from "../../UtilityFunctions";

const newCollaborator = () => ({ name: "", affiliation: "", orcid: "", gnd: "" });
const newGrant = () => ({ id: "" });
const newIdentifier = () => ({ identifier: "", type: "" });
const newSubject = () => ({ term: "", identifier: "" });

export class CreateUpdate extends React.Component<any, any> {
    constructor(props: any) {
        super(props);
        this.state = {
            title: "",
            description: "",
            license: "",
            keywords: [""],
            notes: "",
            collaborators: [newCollaborator()],
            references: [""],
            grants: [newGrant()],
            subjects: [newSubject()],
            identifiers: [newIdentifier()]
        };
        this.setStateEv = this.setStateEv.bind(this);
        this.setStateEvObject = this.setStateEvObject.bind(this);
        this.setStateEvObject = this.setStateEvObject.bind(this);
    }

    onSubmit(e) {
        e.preventDefault();
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
            console.log(index, key);
            list[index] = value;
            let object = {
                [key]: list
            };
            this.setState(() => object);
        };
    }

    setStateEvObject(key) {
        console.log(key);
        return (value, index, member) => {
            const list = this.state[key];
            console.log(index, key);
            list[index][member] = value;
            let object = {
                [key]: list
            };
            this.setState(() => object);
        };
    }

    render() {
        console.log(this.state);
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
                    <Form.TextArea placeholder="Description" onChange={this.setStateEv("description")} required />
                </Form.Field>
                <Form.Field>
                    <label>License</label>
                    <LicenseDropdown onChange={this.setStateEv("license")} />
                </Form.Field>
                <Form.Field>
                    <label>Keywords</label>
                    <FormFieldList 
                        amount={this.state.keywords} 
                        name="keyword" 
                        setStateEvList={this.setStateEvList("keywords")} 
                    />
                    <Button content="New keyword" onClick={(e) => this.addRow(e, "keywords")} />
                </Form.Field>
                <Form.Field>
                    <label>Notes</label>
                    <Form.TextArea placeholder="Notes..." onChange={this.setStateEv("notes")} />
                </Form.Field>
                <Form.Field>
                    <label>Collaborators</label>
                    <Collaborators 
                        collaborators={this.state.collaborators} 
                        setStateEvObject={this.setStateEvObject("collaborators")} 
                    />
                    <Button content="Add collaborator" onClick={(e) => this.addCollaborator(e)} />
                </Form.Field>
                <Form.Field>
                    <label>References</label>
                    <FormFieldList 
                        name="reference" 
                        amount={this.state.references} 
                        setStateEvList={this.setStateEvList("references")} 
                    />
                    <Button content="Add reference" onClick={(e) => this.addRow(e, "references")} />
                </Form.Field>
                <Form.Field>
                    <label>Subjects</label>
                    <Subjects subjects={this.state.subjects} setStateEvObject={this.setStateEvObject("subjects")} />
                    <Button content="Add subject" onClick={(e) => this.addSubject(e)} />
                </Form.Field>
                <Form.Field>
                    <label>Related Identifiers</label>
                    <RelatedIdentifiers 
                        identifiers={this.state.identifiers} 
                        setStateEvObject={this.setStateEvObject("identifiers")} 
                    />
                    <Button content="Add identifier" onClick={(e) => this.addIdentifier(e)} />
                </Form.Field>
                <Button type="submit" content="Submit" />
            </Form>
        )
    }
}

interface LicenseDropdownProps {
    onChange: (ev, details) => void
}

class LicenseDropdown extends React.PureComponent<LicenseDropdownProps> {
    constructor(props: any) {
        super(props)
    }

    render() {
        return (
            <Form.Dropdown onChange={(e, other) => this.props.onChange(e, other)}
                search
                searchInput={{ type: "string" }}
                selection
                required
                options={licenseOptions}
                placeholder="Select license"
            />
        );
    }
}

const Subjects = ({ subjects, setStateEvObject }) =>
    subjects.map((s, i) =>
        <Form.Group key={i} widths="equal">
            <Form.Input 
                fluid 
                value={s.term} 
                label="Term" 
                required 
                placeholder="Term..." 
                onChange={(e, { value }) => setStateEvObject(value, i, "term")} 
            />
            <Form.Input 
                fluid 
                value={s.identifier} 
                label="Identifier" 
                placeholder="Identifier..." 
                onChange={(e, { value }) => setStateEvObject(value, i, "identifier")} 
            />
        </Form.Group>
    );

const RelatedIdentifiers = ({ identifiers, setStateEvObject }) =>
    identifiers.map((_, i) =>
        <Form.Group key={i} widths="equal">
            <Form.Input 
                fluid label="Identifier" 
                required 
                placeholder="Identifier..." 
                onChange={(e, { value }) => setStateEvObject(value, i, "identifier")} 
            />
            <Form.Dropdown label="Type"
                search
                searchInput={{ type: "string" }}
                selection
                options={identifierTypes}
                placeholder="Select type"
                onChange={(e, { value }) => setStateEvObject(value, i, "type")}
            />
        </Form.Group>
    );


const Collaborators = ({ collaborators, setStateEvObject }) =>
    collaborators.map((c, i) =>
        <Form.Group key={i} widths="equal">
            <Form.Input 
                fluid 
                label="Name" 
                required 
                placeholder="Name..." 
                onChange={(e, { value }) => setStateEvObject(value, i, "name")} 
            />

            <Form.Input 
                fluid 
                label="Affiliation" 
                placeholder="Affiliation..." 
                onChange={(e, { value }) => setStateEvObject(value, i, "affiliation")} 
            />
            
            <Form.Input 
                fluid 
                label="ORCiD" 
                placeholder="ORCiD..." 
                onChange={(e, { value }) => setStateEvObject(value, i, "orcid")} 
            />
            
            <Form.Input 
                fluid 
                label="GND" 
                placeholder="GND" 
                onChange={(e, { value }) => setStateEvObject(value, i, "gnd")} 
            />
        </Form.Group>
    );

const FormFieldList = ({ amount, name, setStateEvList }) =>
    amount.map((c, i) => <Form.Input key={i} value={c} placeholder={`Enter ${name}`} onChange={(e, { value }) => setStateEvList(value, i)} />);
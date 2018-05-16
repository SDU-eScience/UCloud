import React from "react";
import { Form, Header, Dropdown, Button } from "semantic-ui-react";
import { licenseOptions, identifierTypes } from "../../DefaultObjects";
import { createRange } from "../../UtilityFunctions";

class Metadata extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            keywordCount: 1,
            collaboratorCount: 1,
            referenceCount: 1,
            subjectCount: 1,
            identifierCount: 1
        }
        this.addKeyword = this.addKeyword.bind(this);
        this.addCollaborator = this.addCollaborator.bind(this);
        this.addReference = this.addReference.bind(this);
        this.addSubject = this.addSubject.bind(this);
        this.addIdentifier = this.addIdentifier.bind(this);
        this.setStateEv = this.setStateEv.bind(this);
        this.setStateEvList = this.setStateEvList.bind(this);
    }

    onPublish(e) {
        console.log(e.target);
        e.preventDefault();
    }

    addCollaborator(e) {
        e.preventDefault();
        const collaboratorCount = this.state.collaboratorCount + 1;
        this.setState(() => ({ collaboratorCount }));
    }

    addKeyword(e) {
        e.preventDefault();
        const keywordCount = this.state.keywordCount + 1;
        this.setState(() => ({ keywordCount }));
    }

    addReference(e) {
        e.preventDefault();
        const referenceCount = this.state.referenceCount + 1;
        this.setState(() => ({ referenceCount }));
    }

    addSubject(e) {
        e.preventDefault();
        const subjectCount = this.state.subjectCount + 1;
        this.setState(() => ({ subjectCount }));
    }

    addIdentifier(e) {
        e.preventDefault();
        const identifierCount = this.state.identifierCount + 1;
        this.setState(() => ({ identifierCount }));
    }

    setStateEv(key) {
        return (e, { value }) => {
            console.log(key, value);
            let object = {};
            object[key] = value;
            this.setState(() => object);
        };
    }

    setStateEvList(key, index) {
        return (e, { value }) => {
            console.log(key, index, value);
            const list = this.state[key] != null ? this.state[key] : [];
            list[index] = value;
            let object = {
                [key]: list
            };
            this.setState(() => object);
        };
    }

    render() {
        return (
            <Form onSubmit={(e, d, c) => console.log(e, d, c)}>
                <Form.Field required>
                    <label>Title</label>
                    <Form.Input placeholder="Title" onChange={this.setStateEv("title")} required />
                </Form.Field>
                <Form.Field required>
                    <label>Description</label>
                    <Form.TextArea placeholder="Description" onChange={this.setStateEv("description")} required />
                </Form.Field>
                <Form.Field>
                    <label>License</label>
                    <Form.Dropdown onChange={this.setStateEv("license")}
                        search
                        searchInput={{ type: "string" }}
                        selection
                        required
                        options={licenseOptions}
                        placeholder="Select license"
                    />
                </Form.Field>
                <Form.Field>
                    <label>Keywords</label>
                    <FormFieldList amount={this.state.keywordCount} name="keywords" setStateEvList={this.setStateEvList} />
                    <Button content="New keyword" onClick={(e) => this.addKeyword(e)} />
                </Form.Field>
                <Form.Field>
                    <label>Notes</label>
                    <Form.TextArea placeholder="Notes..." onChange={this.setStateEv("notes")} />
                </Form.Field>
                <Form.Field>
                    <label>Collaborators</label>
                    <Collaborators collaborators={this.state.collaboratorCount} setStateEvList={this.setStateEvList} />
                    <Button content="Add collaborator" onClick={(e) => this.addCollaborator(e)} />
                </Form.Field>
                <Form.Field>
                    <label>References</label>
                    <FormFieldList name="references" amount={this.state.referenceCount} setStateEvList={this.setStateEvList} />
                    <Button content="Add reference" onClick={(e) => this.addReference(e)} />
                </Form.Field>
                <Form.Field>
                    <label>Subjects</label>
                    <Subjects subjects={this.state.subjectCount} setStateEvList={this.setStateEvList} />
                    <Button content="Add subject" onClick={(e) => this.addSubject(e)} />
                </Form.Field>
                <Form.Field>
                    <label>Related Identifiers</label>
                    <RelatedIdentifiers identifiers={this.state.identifierCount} setStateEvList={this.setStateEvList} />
                    <Button content="Add identifier" onClick={(e) => this.addIdentifier(e)} />
                </Form.Field>
                <Button type="submit" content="Submit" />
            </Form>
        )
    }
}

const Subjects = ({ subjects, setStateEvList }) =>
    createRange(subjects).map((_, i) =>
        <Form.Group key={i} widths="equal">
            <Form.Input fluid label="Term" required placeholder="Term..." onChange={setStateEvList("sub_term", i)} />
            <Form.Input fluid label="Identifier" placeholder="Identifier..." onChange={setStateEvList("sub_iden", i)} />
        </Form.Group>
    );

const RelatedIdentifiers = ({ identifiers, setStateEvList }) =>
    createRange(identifiers).map((_, i) =>
        <Form.Group key={i} widths="equal">
            <Form.Input fluid label="Identifier" required placeholder="Identifier..." onChange={setStateEvList("rel_iden", i)} />
            <Form.Dropdown label="Type"
                search
                searchInput={{ type: "string" }}
                selection
                options={identifierTypes}
                placeholder="Select type"
                onChange={setStateEvList("rel_type", i)}
            />
        </Form.Group>
    );


const Collaborators = ({ collaborators, setStateEvList }) =>
    createRange(collaborators).map((_, i) =>
        <Form.Group key={i} widths="equal">
            <Form.Input fluid label="Name" required placeholder="Name..." onChange={setStateEvList("col_name", i)} />
            <Form.Input fluid label="Affiliation" placeholder="Affiliation..." onChange={setStateEvList("col_affiliation", i)} />
            <Form.Input fluid label="ORCiD" placeholder="ORCiD..." onChange={setStateEvList("col_orcid", i)} />
            <Form.Input fluid label="GND" placeholder="GND" onChange={setStateEvList("col_gnd", i)} />
        </Form.Group>
    );

const FormFieldList = ({ amount, name, setStateEvList }) =>
    createRange(amount).map((_, i) => <Form.Input key={i} placeholder={`Enter ${name}`} onChange={setStateEvList(name, i)} />);

export default Metadata;
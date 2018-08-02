import * as React from "react";
import { connect } from "react-redux";
import { Grid, Dropdown, Label, Header, Form, Button, Input, Checkbox } from "semantic-ui-react";
import { addEntryIfNotPresent } from "Utilities/ArrayUtilities"
import { infoNotification } from "UtilityFunctions";

interface DetailedFileSearchProps {

}

type SensitivityLevel = "Open Access" | "Confidential" | "Sensitive";

interface DetailedFileSearchState {
    allowFolders: boolean
    allowFiles: boolean
    filename: string
    extensions: string[]
    extensionValue: string
    tags: string[]
    sensitivities: SensitivityLevel[]
}

class DetailedFileSearch extends React.Component<DetailedFileSearchProps, DetailedFileSearchState> {
    constructor(props) {
        super(props);
        this.state = {
            allowFolders: true,
            allowFiles: true,
            filename: "",
            extensionValue: "",
            extensions: [],
            tags: [],
            sensitivities: []
        }
    }

    onAddSensitivity(sensitivity: SensitivityLevel): void {
        const { sensitivities } = this.state;
        if (sensitivities.includes(sensitivity)) return;
        sensitivities.push(sensitivity);
        this.setState(() => ({ sensitivities }));
    }

    onRemoveSensitivity(sensitivity: SensitivityLevel): void {
        const { sensitivities } = this.state;
        const remainingSensitivities = sensitivities.filter(s => s !== sensitivity)
        this.setState(() => ({ sensitivities: remainingSensitivities }));
    }

    // On Add Sensitivity matches a lot, not DRY
    onAddExtension() {
        const { extensionValue, extensions } = this.state;
        const newExtensions = extensionValue.trim().split(" ").filter(it => it);
        let entryAdded = false;
        newExtensions.forEach(ext => { entryAdded = addEntryIfNotPresent(extensions, ext) || entryAdded });
        this.setState(() => ({
            extensions,
            extensionValue: entryAdded ? "" : extensionValue
        }));
        if (!entryAdded) {
            infoNotification("Extension already added");
        }
    }

    // Not DRY
    onRemoveExtension(extension: string) {
        const { extensions } = this.state;
        const remaining = extensions.filter(e => e !== extension);
        this.setState(() => ({ extensions: remaining }));
    }

    render() {
        const { sensitivities, extensions, extensionValue, allowFiles, allowFolders } = this.state;
        const remainingSensitivities = sensitivityOptions.filter(s => !sensitivities.includes(s.text as SensitivityLevel));
        let sensitiviyDropdown = null;
        if (remainingSensitivities.length) {
            sensitiviyDropdown = (
                <div>
                    <Dropdown
                        text="Add sensitivity level"
                        onChange={(_, { value }) => this.onAddSensitivity(value as SensitivityLevel)}
                        options={remainingSensitivities}
                    />
                </div>
            );
        }
        return (
            <Grid container columns={16}>
                <Grid.Column width={16}>
                    <Header as="h3" content="Filename" />
                    <Input fluid placeholder={"Filename must include..."} onChange={(_, { value }) => this.setState(() => ({ filename: value }))} />
                    <Header as="h3" content="Created at" />
                    <Form onSubmit={(e) => e.preventDefault()}>
                        <Form.Group widths="equal">
                            <Form.Field>
                                <label>Created after date</label>
                                <Input type="date" onChange={(_, { value }) => console.log(value)} />
                            </Form.Field>
                            <Form.Field>
                                <label>Created after time</label>
                                <Input type="time" onChange={(_, { value }) => console.log(value)} />
                            </Form.Field>
                            <Form.Field>
                                <label>Created before date</label>
                                <Input type="date" onChange={(_, { value }) => console.log(value)} />
                            </Form.Field>
                            <Form.Field>
                                <label>Created before time</label>
                                <Input type="time" onChange={(_, { value }) => console.log(value)} />
                            </Form.Field>
                        </Form.Group>
                    </Form>
                    <Header as="h3" content="Modified at" />
                    <Form onSubmit={(e) => e.preventDefault()} >
                        <Form.Group widths="equal">
                            <Form.Field>
                                <label>Modified after date</label>
                                <Input type="date" onChange={(_, { value }) => console.log(value)} />
                            </Form.Field>
                            <Form.Field>
                                <label>Modified after time</label>
                                <Input type="time" onChange={(_, { value }) => console.log(value)} />
                            </Form.Field>
                            <Form.Field>
                                <label>Modified before date</label>
                                <Input type="date" onChange={(_, { value }) => console.log(value)} />
                            </Form.Field>
                            <Form.Field>
                                <label>Modified before time</label>
                                <Input type="time" onChange={(_, { value }) => console.log(value)} />
                            </Form.Field>
                        </Form.Group>
                    </Form>
                    <Header as="h3" content="Type" />
                    <Form.Group widths="equal">
                        <Checkbox style={{ paddingRight: "15px"}} label="Folders" checked={allowFolders} onClick={() => this.setState(() => ({ allowFolders: !allowFolders }))} />
                        <Checkbox style={{ paddingRight: "15px"}} label="Files" checked={allowFiles} onClick={() => this.setState(() => ({ allowFiles: !allowFiles }))} />
                    </Form.Group>
                    <Header as="h3" content="File extensions" />
                    <SearchLabels labels={extensions} onLabelRemove={(l) => this.onRemoveExtension(l)} />
                    <Form onSubmit={(e) => { e.preventDefault(); this.onAddExtension(); }}>
                        <Form.Input value={extensionValue} onChange={(_, { value }) => this.setState(() => ({ extensionValue: value }))} />
                    </Form>
                    <Header as="h3" content="Sensitivity" />
                    <SearchLabels labels={sensitivities} onLabelRemove={(l) => this.onRemoveSensitivity(l)} />
                    {sensitiviyDropdown}
                    <Button style={{ marginTop: "15px" }} content="Search" color="blue" onClick={() => console.log("Almost submitted!")} />
                </Grid.Column>
            </Grid>
        );
    }
}

const SearchLabels = (props) => (
    <div style={{ paddingBottom: "5px" }}>
        {props.labels.map((l, i) => (<Label basic key={i} content={l} onRemove={() => props.onLabelRemove(l)} />))}
    </div>
);


/* 
    Extension - done
    Timestamps
    File names - in progress
    Type - done
    Sensitivity - done
*/

const sensitivityOptions = [
    { key: "open_access", text: "Open Access", value: "Open Access" },
    { key: "confidential", text: "Confidential", value: "Confidential" },
    { key: "sensitive", text: "Sensitive", value: "Sensitive" }
]

export default connect()(DetailedFileSearch);

// Search by extensions, sensitivity, tags, etc.
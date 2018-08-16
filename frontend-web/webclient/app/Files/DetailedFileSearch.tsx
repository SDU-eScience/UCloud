import * as React from "react";
import { connect } from "react-redux";
import { Grid, Dropdown, Label, Header, Form, Button, Input } from "semantic-ui-react";
import { addEntryIfNotPresent } from "Utilities/ArrayUtilities"
import { infoNotification } from "UtilityFunctions";
import { DetailedFileSearchProps, DetailedFileSearchState, SensitivityLevel, Annotation } from ".";

class DetailedFileSearch extends React.Component<DetailedFileSearchProps, DetailedFileSearchState> {
    constructor(props) {
        super(props);
        this.state = {
            allowFolders: true,
            allowFiles: true,
            filename: "",
            extensionValue: "",
            extensions: [],
            tagValue: "",
            tags: [],
            annotations: [],
            sensitivities: [],
            createdBefore: { time: null, date: null },
            createdAfter: { time: null, date: null },
            modifiedBefore: { time: null, date: null },
            modifiedAfter: { time: null, date: null }
        }
    }

    onAddSensitivity(sensitivity: SensitivityLevel): void {
        const { sensitivities } = this.state;
        // FIXME: Shouldn't be able to occur?
        if (sensitivities.includes(sensitivity)) return;
        sensitivities.push(sensitivity);
        this.setState(() => ({ sensitivities }));
    }

    onRemoveSensitivity(sensitivity: SensitivityLevel): void {
        const { sensitivities } = this.state;
        const remainingSensitivities = sensitivities.filter(s => s !== sensitivity)
        this.setState(() => ({ sensitivities: remainingSensitivities }));
    }

    // Not DRY
    onRemoveExtension(extension: string) {
        const { extensions } = this.state;
        const remaining = extensions.filter(e => e !== extension);
        this.setState(() => ({ extensions: remaining }));
    }

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

    onAddPresets(presetExtensions: string) {
        const ext = presetExtensions.trim().split(" ").filter(it => it);
        const { extensions } = this.state;
        ext.forEach(it => addEntryIfNotPresent(extensions, it));
        this.setState(() => ({ extensions }));
    }

    onAddAnnotation(annotation: Annotation) {
        const { annotations } = this.state;
        annotations.push(annotation);
        this.setState(() => ({ annotations }));
    }

    onRemoveAnnotation(annotation: Annotation) {
        const { annotations } = this.state;
        const remaining = annotations.filter(a => a !== annotation);
        this.setState(() => ({ annotations: remaining }));
    }

    onRemoveTag(tag: string) {
        const { tags } = this.state;
        const remaining = tags.filter(t => t !== tag);
        this.setState(() => ({ tags: remaining }));
    }

    onAddTags = () => {
        const { tagValue, tags } = this.state;
        const newTags = tagValue.trim().split(" ").filter(it => it);
        let entryAdded = false;
        newTags.forEach(ext => { entryAdded = addEntryIfNotPresent(tags, ext) || entryAdded });
        this.setState(() => ({
            tags,
            tagValue: entryAdded ? "" : tagValue
        }));
        if (!entryAdded) {
            infoNotification("Tag already added");
        }
    }

    onSearch = () => {
        console.log(this.state);
        console.warn("Todo");
    }

    render() {
        const { sensitivities, extensions, extensionValue, allowFiles, allowFolders, filename, annotations, tags, tagValue } = this.state;
        const remainingSensitivities = sensitivityOptions.filter(s => !sensitivities.includes(s.text as SensitivityLevel));
        const sensitivityDropdown = remainingSensitivities.length ? (
            <div>
                <Dropdown
                    text="Add sensitivity level"
                    onChange={(_, { value }) => this.onAddSensitivity(value as SensitivityLevel)}
                    options={remainingSensitivities}
                />
            </div>
        ) : null;
        const remainingAnnotations = annotationOptions.filter(a => !annotations.includes(a.text as Annotation));
        const annotationsDropdown = remainingAnnotations.length ? (
            <div>
                <Dropdown
                    text="Add annotation"
                    onChange={(_, { value }) => this.onAddAnnotation(value as Annotation)}
                    options={remainingAnnotations}
                />
            </div>
        ) : null;
        return (
            <Grid container columns={16} >
                <Grid.Column width={16}>
                    <Header as="h3" content="Filename" />
                    {filename ? <div className="padding-bottom"><Label content={`Filename contains: ${filename}`} active={false} basic /></div> : null}
                    <Input fluid placeholder="Filename must include..." onChange={(_, { value }) => this.setState(() => ({ filename: value }))} />
                    <Header as="h3" content="Created at" />
                    <Form onSubmit={(e) => e.preventDefault()}>
                        <Form.Group widths="equal">
                            <Form.Field>
                                <label>Created after date</label>
                                <Input type="date" onChange={(_, { value }) => this.setState(() => ({ createdAfter: { date: value, time: this.state.createdAfter.time } }))} />
                            </Form.Field>
                            <Form.Field>
                                <label>Created after time</label>
                                <Input type="time" onChange={(_, { value }) => this.setState(() => ({ createdAfter: { time: value, date: this.state.createdAfter.date } }))} />
                            </Form.Field>
                            <Form.Field>
                                <label>Created before date</label>
                                <Input type="date" onChange={(_, { value }) => this.setState(() => ({ createdBefore: { date: value, time: this.state.createdBefore.time } }))} />
                            </Form.Field>
                            <Form.Field>
                                <label>Created before time</label>
                                <Input type="time" onChange={(_, { value }) => this.setState(() => ({ createdBefore: { time: value, date: this.state.createdBefore.date } }))} />
                            </Form.Field>
                        </Form.Group>
                    </Form>
                    <Header as="h3" content="Modified at" />
                    <Form onSubmit={(e) => e.preventDefault()} >
                        <Form.Group widths="equal">
                            <Form.Field>
                                <label>Modified after date</label>
                                <Input type="date" onChange={(_, { value }) => this.setState(() => ({ modifiedAfter: { date: value, time: this.state.modifiedAfter.time } }))} />
                            </Form.Field>
                            <Form.Field>
                                <label>Modified after time</label>
                                <Input type="time" onChange={(_, { value }) => this.setState(() => ({ modifiedAfter: { time: value, date: this.state.modifiedAfter.date } }))} />
                            </Form.Field>
                            <Form.Field>
                                <label>Modified before date</label>
                                <Input type="date" onChange={(_, { value }) => this.setState(() => ({ modifiedBefore: { date: value, time: this.state.modifiedBefore.time } }))} />
                            </Form.Field>
                            <Form.Field>
                                <label>Modified before time</label>
                                <Input type="time" onChange={(_, { value }) => this.setState(() => ({ modifiedBefore: { time: value, date: this.state.modifiedBefore.date } }))} />
                            </Form.Field>
                        </Form.Group>
                    </Form>
                    <Header as="h3" content="File Types" />
                    <Form.Group inline>
                        <Form.Checkbox label="Folders" checked={allowFolders} onClick={() => this.setState(() => ({ allowFolders: !allowFolders }))} />
                        <Form.Checkbox label="Files" checked={allowFiles} onClick={() => this.setState(() => ({ allowFiles: !allowFiles }))} />
                    </Form.Group>
                    <Header as="h3" content="File extensions" />
                    <SearchLabels labels={extensions} onLabelRemove={(l) => this.onRemoveExtension(l)} clearAll={() => this.setState(() => ({ extensions: [] }))} />
                    <Form onSubmit={(e) => { e.preventDefault(); this.onAddExtension(); }}>
                        <Form.Input value={extensionValue} onChange={(_, { value }) => this.setState(() => ({ extensionValue: value }))} />
                        <Dropdown
                            text="Add extension preset"
                            onChange={(_, { value }) => this.onAddPresets(value as string)}
                            options={extensionPresets}
                        />
                    </Form>
                    <Header as="h3" content="Sensitivity" />
                    <SearchLabels labels={sensitivities} onLabelRemove={(l) => this.onRemoveSensitivity(l)} clearAll={() => this.setState(() => ({ sensitivities: [] }))} />
                    {sensitivityDropdown}

                    <Header as="h3" content="Annotations" />
                    <SearchLabels labels={annotations} onLabelRemove={(l) => this.onRemoveAnnotation(l)} clearAll={() => this.setState(() => ({ annotations: [] }))} />
                    {annotationsDropdown}

                    <Header as="h3" content="Tags" />
                    <SearchLabels labels={tags} onLabelRemove={(l) => this.onRemoveTag(l)} clearAll={() => this.setState(() => ({ tags: [] }))} />
                    <Form onSubmit={({ preventDefault }) => { preventDefault(); this.onAddTags(); }}>
                        <Form.Input value={tagValue} onChange={(_, { value }) => this.setState(() => ({ tagValue: value }))} />
                    </Form>
                    <Button style={{ marginTop: "15px" }} content="Search" color="blue" onClick={() => this.onSearch()} />
                </Grid.Column>
            </Grid>
        );
    }
}

const SearchLabels = (props) => (
    <div className="padding-bottom">
        {props.labels.map((l, i) => (<Label className="label-padding" basic key={i} content={l} onRemove={() => props.onLabelRemove(l)} />))}
        {props.labels.length > 1 ? (<Label className="label-padding" color="blue" content="Clear all" onRemove={props.clearAll} />) : null}
    </div>
);

const extensionPresets = [
    { key: "text", content: "Text", value: ".txt .docx .rtf .csv .pdf" },
    { key: "image", content: "Image", value: ".png .jpeg .jpg .ppm .gif" },
    { key: "sound", content: "Sound", value: ".mp3 .ogg .wav .flac .aac" },
    { key: "compressed", content: "Compressed files", value: ".zip .tar.gz" }
]

const sensitivityOptions = [
    { key: "open_access", text: "Open Access", value: "Open Access" },
    { key: "confidential", text: "Confidential", value: "Confidential" },
    { key: "sensitive", text: "Sensitive", value: "Sensitive" }
]

const annotationOptions = [
    { key: "project", text: "Project", value: "Project" }
]

export default connect()(DetailedFileSearch);
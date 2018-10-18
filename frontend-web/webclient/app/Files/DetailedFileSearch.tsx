import * as React from "react";
import { connect } from "react-redux";
import { Grid, Dropdown, Label, Header, Form, Button, Input, Message } from "semantic-ui-react";
import { addEntryIfNotPresent } from "Utilities/CollectionUtilities"
import { infoNotification } from "UtilityFunctions";
import { DetailedFileSearchProps, DetailedFileSearchState, SensitivityLevel, Annotation, PossibleTime } from ".";
import DatePicker from "react-datepicker";
import { Moment } from "moment";

class DetailedFileSearch extends React.Component<DetailedFileSearchProps, DetailedFileSearchState> {
    constructor(props) {
        super(props);
        this.state = {
            allowFolders: true,
            allowFiles: true,
            filename: "",
            extensionValue: "",
            extensions: new Set(),
            tagValue: "",
            tags: new Set(),
            annotations: new Set(),
            sensitivities: new Set(),
            createdBefore: undefined,
            createdAfter: undefined,
            modifiedBefore: undefined,
            modifiedAfter: undefined,
            error: undefined
        }
    }

    onAddSensitivity(sensitivity: SensitivityLevel): void {
        const { sensitivities } = this.state;
        sensitivities.add(sensitivity);
        this.setState(() => ({ sensitivities }));
    }

    onRemoveSensitivity(sensitivity: SensitivityLevel): void {
        const { sensitivities } = this.state;
        sensitivities.delete(sensitivity);
        this.setState(() => ({ sensitivities }));
    }

    onRemoveExtension(extension: string) {
        const { extensions } = this.state;
        extensions.delete(extension)
        this.setState(() => ({ extensions }));
    }

    onAddExtension() {
        const { extensionValue, extensions } = this.state;
        if (!extensionValue) return;
        const newExtensions = extensionValue.trim().split(" ").filter(it => it);
        let entryAdded = false;
        newExtensions.forEach(ext => { entryAdded = addEntryIfNotPresent(extensions, ext) || entryAdded });
        this.setState(() => ({
            extensions,
            extensionValue: entryAdded ? "" : extensionValue
        }));
        if (!entryAdded && !!extensionValue.trim()) {
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
        if (!annotation) return;
        const { annotations } = this.state;
        annotations.add(annotation);
        this.setState(() => ({ annotations }));
    }

    onRemoveAnnotation(annotation: Annotation) {
        const { annotations } = this.state;
        annotations.delete(annotation);
        this.setState(() => ({ annotations }));
    }

    onRemoveTag = (tag: string) => {
        const { tags } = this.state;
        tags.delete(tag);
        this.setState(() => ({ tags }));
    }

    onAddTags = () => {
        const { tagValue, tags } = this.state;
        if (!tagValue) return;
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

    // FIXME, should show errors in fields instead, the upper corner error is not very noticeable;
    validateAndSetDate(m: Moment | null, property: PossibleTime) {
        if (m === null) {
            const state = { ...this.state }
            state[property] = undefined;
            this.setState(() => ({ ...state }));
            return;
        }
        const { createdAfter, createdBefore, modifiedAfter, modifiedBefore } = this.state;
        const isBefore = property.includes("Before")
        if (property.startsWith("created")) {
            if (isBefore) { // Created Before
                if (createdAfter === undefined || !createdAfter.isAfter(m)) {
                    this.setState(() => ({ createdBefore: m }));
                } else {
                    this.setState(() => ({ createdBefore: m, createdAfter: undefined, error: "Created before must be after created after" }));
                }
            } else { // Created After
                if (createdBefore === undefined || !createdBefore.isBefore(m)) {
                    this.setState(() => ({ createdAfter: m }));
                } else {
                    this.setState(() => ({ createdAfter: m, createdBefore: undefined, error: "Created after must be before created before" }));
                }
            }
        } else { // Modified
            if (isBefore) { // Modified Before
                if (modifiedAfter === undefined || !modifiedAfter.isAfter(m)) {
                    this.setState(() => ({ modifiedBefore: m }));
                } else {
                    this.setState(() => ({ modifiedBefore: m, modifiedAfter: undefined }));
                }
            } else { // Modified After
                if (modifiedBefore === undefined || !modifiedBefore.isBefore(m)) {
                    this.setState(() => ({ modifiedAfter: m }));
                } else {
                    this.setState(() => ({ modifiedAfter: m, modifiedBefore: undefined }));
                }
            }
        }
    }

    onSearch = () => {
        console.log(this.state);
        console.warn("Todo");
    }

    render() {
        const { sensitivities, extensions, extensionValue, allowFiles, allowFolders, filename, annotations, tags, tagValue } = this.state;
        const remainingSensitivities = sensitivityOptions.filter(s => !sensitivities.has(s.text as SensitivityLevel));
        const sensitivityDropdown = remainingSensitivities.length ? (
            <div>
                <Dropdown
                    text="Add sensitivity level"
                    onChange={(_, { value }) => this.onAddSensitivity(value as SensitivityLevel)}
                    options={remainingSensitivities}
                />
            </div>
        ) : null;
        const remainingAnnotations = annotationOptions.filter(a => !annotations.has(a.text as Annotation));
        const annotationsDropdown = remainingAnnotations.length ? (
            <div>
                <Dropdown
                    text="Add annotation"
                    onChange={(_, { value }) => this.onAddAnnotation(value as Annotation)}
                    options={remainingAnnotations}
                />
            </div>
        ) : null;
        const error = !!this.state.error ? <Message error content={this.state.error} onDismiss={() => this.setState(() => ({ error: undefined }))} /> : null;
        return (
            <Grid container columns={16} >
                <Grid.Column width={16}>
                    {error}
                    <Header as="h3" content="Filename" />
                    {filename ? <div className="padding-bottom"><Label className="label-padding" content={`Filename contains: ${filename}`} active={false} basic /></div> : null}
                    <Input fluid placeholder="Filename must include..." onChange={(_, { value }) => this.setState(() => ({ filename: value }))} />
                    <Header as="h3" content="Created at" />
                    <Form onSubmit={(e) => e.preventDefault()}>
                        <Form.Group>
                            <Form.Field>
                                <label>Created after</label>
                                <DatePicker
                                    selected={this.state.createdAfter}
                                    onChange={(d) => this.validateAndSetDate(d, "createdAfter")}
                                    showTimeSelect
                                    timeIntervals={15}
                                    isClearable
                                    timeFormat="HH:mm"
                                    dateFormat="DD/MM/YY HH:mm"
                                    timeCaption="time"
                                />
                            </Form.Field>
                            <Form.Field>
                                <label>Created before</label>
                                <DatePicker
                                    selected={this.state.createdBefore}
                                    onChange={(d) => this.validateAndSetDate(d, "createdBefore")}
                                    showTimeSelect
                                    timeIntervals={15}
                                    isClearable
                                    timeFormat="HH:mm"
                                    dateFormat="DD/MM/YY HH:mm"
                                    timeCaption="time"
                                />
                            </Form.Field>
                        </Form.Group>
                    </Form>
                    <Header as="h3" content="Modified at" />
                    <Form onSubmit={(e) => e.preventDefault()}>
                        <Form.Group>
                            <Form.Field>
                                <label>Modified after</label>
                                <DatePicker
                                    selected={this.state.modifiedAfter}
                                    onChange={(d) => this.validateAndSetDate(d, "modifiedAfter")}
                                    showTimeSelect
                                    timeIntervals={15}
                                    isClearable
                                    timeFormat="HH:mm"
                                    dateFormat="DD/MM/YY HH:mm"
                                    timeCaption="time"
                                />
                            </Form.Field>
                            <Form.Field>
                                <label>Modified before</label>
                                <DatePicker
                                    selected={this.state.modifiedBefore}
                                    onChange={(d) => this.validateAndSetDate(d, "modifiedBefore")}
                                    showTimeSelect
                                    timeIntervals={15}
                                    isClearable
                                    timeFormat="HH:mm"
                                    dateFormat="DD/MM/YY HH:mm"
                                    timeCaption="time"
                                />
                            </Form.Field>
                        </Form.Group>
                    </Form>
                    <Header as="h3" content="File Types" />
                    <Form.Group inline>
                        <Form.Checkbox label="Folders" checked={allowFolders} onClick={() => this.setState(() => ({ allowFolders: !allowFolders }))} />
                        <Form.Checkbox label="Files" checked={allowFiles} onClick={() => this.setState(() => ({ allowFiles: !allowFiles }))} />
                    </Form.Group>
                    <Header as="h3" content="File extensions" />
                    <SearchLabels labels={extensions} onLabelRemove={(l) => this.onRemoveExtension(l)} clearAll={() => this.setState(() => ({ extensions: new Set() }))} />
                    <Form onSubmit={(e) => { e.preventDefault(); this.onAddExtension(); }}>
                        <Form.Input placeholder="Add extensions..." value={extensionValue} onChange={(_, { value }) => this.setState(() => ({ extensionValue: value }))} />
                        <Dropdown
                            text="Add extension preset"
                            onChange={(_, { value }) => this.onAddPresets(value as string)}
                            options={extensionPresets}
                        />
                    </Form>
                    <Header as="h3" content="Sensitivity" />
                    <SearchLabels labels={sensitivities} onLabelRemove={(l) => this.onRemoveSensitivity(l)} clearAll={() => this.setState(() => ({ sensitivities: new Set() }))} />
                    {sensitivityDropdown}

                    <Header as="h3" content="Annotations" />
                    <SearchLabels labels={annotations} onLabelRemove={(l) => this.onRemoveAnnotation(l)} clearAll={() => this.setState(() => ({ annotations: new Set() }))} />
                    {annotationsDropdown}

                    <Header as="h3" content="Tags" />
                    <SearchLabels labels={tags} onLabelRemove={(l) => this.onRemoveTag(l)} clearAll={() => this.setState(() => ({ tags: new Set() }))} />
                    <Form onSubmit={(e) => { e.preventDefault(); this.onAddTags(); }}>
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
        {[...props.labels].map((l, i) => (<Label className="label-padding" basic key={i} content={l} onRemove={() => props.onLabelRemove(l)} />))}
        {props.labels.size > 1 ? (<Label className="label-padding" color="blue" content="Clear all" onRemove={props.clearAll} />) : null}
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
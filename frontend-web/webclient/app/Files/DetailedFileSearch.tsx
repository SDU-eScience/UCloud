import * as React from "react";
import { connect } from "react-redux";
import { Grid, Dropdown, Label, Header, Form, Button, Input } from "semantic-ui-react";
import { addEntryIfNotPresent } from "Utilities/ArrayUtilities"
import { infoNotification, failureNotification } from "UtilityFunctions";
import { DetailedFileSearchProps, DetailedFileSearchState, SensitivityLevel, Annotation, PossibleTime } from ".";
import DatePicker from "react-datepicker";
import "react-datepicker/dist/react-datepicker.css";
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
            modifiedAfter: undefined
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
        if (!m) return;
        const { createdAfter, createdBefore, modifiedAfter, modifiedBefore } = this.state;
        const isBefore = property.includes("Before")
        if (property.startsWith("created")) {
            if (isBefore) {
                if (!createdAfter || m.isAfter(createdAfter)) {
                    this.setState(() => ({ createdBefore: m }));
                } else {
                    failureNotification("Created before must be after created after");
                }
            } else {
                if (!createdBefore || m.isBefore(createdBefore)) {
                    this.setState(() => ({ createdAfter: m }));
                } else {
                    failureNotification("Created after must be before created before");
                }
            }
        } else { // Modified
            if (isBefore) { // Modified Before
                if ((!createdAfter || m.isAfter(createdAfter)) && (!modifiedAfter || m.isAfter(modifiedAfter))) {
                    this.setState(() => ({ modifiedBefore: m }));
                } else {
                    failureNotification("Modified before must be after created after and modified after");
                }
            } else { // Modified After
                if ((!createdBefore || m.isBefore(createdBefore)) && (!modifiedBefore || m.isBefore(modifiedBefore))) {
                    this.setState(() => ({ modifiedAfter: m }));
                } else {
                    failureNotification("Modified after must be before created before and modified before");
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
        return (
            <Grid container columns={16} >
                <Grid.Column width={16}>
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
                                    timeFormat="hh:mm"
                                    timeIntervals={15}
                                    dateFormat="LLL"
                                    timeCaption="time"
                                />
                            </Form.Field>
                            <Form.Field>
                                <label>Created before</label>
                                <DatePicker
                                    selected={this.state.createdBefore}
                                    onChange={(d) => this.validateAndSetDate(d, "createdBefore")}
                                    showTimeSelect
                                    timeFormat="HH:mm"
                                    timeIntervals={15}
                                    dateFormat="LLL"
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
                                    timeFormat="HH:mm"
                                    timeIntervals={15}
                                    dateFormat="LLL"
                                    timeCaption="time"
                                />
                            </Form.Field>
                            <Form.Field>
                                <label>Modified before</label>
                                <DatePicker
                                    selected={this.state.modifiedBefore}
                                    onChange={(d) => this.validateAndSetDate(d, "modifiedBefore")}
                                    showTimeSelect
                                    timeFormat="HH:mm"
                                    timeIntervals={15}
                                    dateFormat="LLL"
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
                        <Form.Input value={extensionValue} onChange={(_, { value }) => this.setState(() => ({ extensionValue: value }))} />
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
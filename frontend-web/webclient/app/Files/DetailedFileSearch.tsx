import * as React from "react";
import { connect } from "react-redux";
import { addEntryIfNotPresent } from "Utilities/CollectionUtilities"
import { infoNotification } from "UtilityFunctions";
import { DetailedFileSearchProps, DetailedFileSearchState, SensitivityLevel, Annotation, PossibleTime, FileType, AdvancedSearchRequest, File } from ".";
import { DatePicker } from "ui-components/DatePicker";
import { Moment } from "moment";
import Box from "ui-components/Box";
import ClickableDropdown from "ui-components/ClickableDropdown";
import { Flex, Input, Label, Button, InputGroup, Stamp, Checkbox, Error, OutlineButton } from "ui-components";
import * as Heading from "ui-components/Heading"
import CloseButton from "ui-components/CloseButton";
import { Cloud } from "Authentication/SDUCloudObject";
import { emptyPage } from "DefaultObjects";
import { SimpleFileList } from "SimpleSearch/SimpleSearch";
import { Page } from "Types";

class DetailedFileSearch extends React.Component<DetailedFileSearchProps, DetailedFileSearchState> {
    constructor(props) {
        super(props);
        this.state = {
            hidden: true,
            allowFolders: true,
            allowFiles: true,
            fileName: "",
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
            error: undefined,
            result: emptyPage,
            loading: false
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
        let fileTypes: [FileType?, FileType?] = [];
        if (this.state.allowFiles) fileTypes.push("FILE");
        if (this.state.allowFolders) fileTypes.push("DIRECTORY");
        const createdAt = {
            after: !!this.state.createdAfter ? this.state.createdAfter.valueOf() : undefined,
            before: !!this.state.createdBefore ? this.state.createdBefore.valueOf() : undefined,
        };
        const modifiedAt = {
            after: !!this.state.modifiedAfter ? this.state.modifiedAfter.valueOf() : undefined,
            before: !!this.state.modifiedBefore ? this.state.modifiedBefore.valueOf() : undefined,
        };
        const request: AdvancedSearchRequest = {
            fileName: this.state.fileName,
            extensions: Array.from(this.state.extensions),
            fileTypes,
            createdAt: typeof createdAt.after === "number" || typeof createdAt.before === "number" ? createdAt : undefined,
            modifiedAt: typeof modifiedAt.after === "number" || typeof modifiedAt.before === "number" ? modifiedAt : undefined,
            itemsPerPage: 25,
            page: 0
        }
        Cloud.post<Page<File>>("/file-search/advanced", request).then(({ response }) => this.setState(() => ({ result: response })));
    }

    render() {
        if (this.state.hidden) { return (<OutlineButton fullWidth color="green" onClick={() => this.setState(() => ({ hidden: false }))}>Advanced Search</OutlineButton>) }
        const { sensitivities, extensions, extensionValue, allowFiles, allowFolders, fileName, annotations, tags, tagValue } = this.state;
        const remainingSensitivities = sensitivityOptions.filter(s => !sensitivities.has(s.text as SensitivityLevel));
        const sensitivityDropdown = remainingSensitivities.length ? (
            <Box>
                <ClickableDropdown
                    chevron
                    trigger={"Add sensitivity level"}
                    onChange={key => this.onAddSensitivity(key as SensitivityLevel)}
                    options={remainingSensitivities}
                />
            </Box>
        ) : null;
        return (
            <Flex flexDirection="column" pl="0.5em" pr="0.5em">
                <Box mt="0.5em">
                    <Heading.h3>Advanced File Search</Heading.h3>
                    <Error error={this.state.error} clearError={() => this.setState(() => ({ error: undefined }))} />
                    <Heading.h5 pb="0.3em" pt="0.5em">Filename</Heading.h5>
                    {fileName ? <Box mb="1em"><Stamp bg="white">{`Filename contains: ${fileName}`}</Stamp></Box> : null}
                    <Input
                        pb="6px"
                        pt="8px"
                        mt="-2px"
                        width="100%"
                        placeholder="Filename must include..."
                        onChange={({ target: { value } }) => this.setState(() => ({ fileName: value }))}
                    />
                    <Heading.h5 pb="0.3em" pt="0.5em">Created at</Heading.h5>

                    <InputGroup>
                        <DatePicker
                            popperPlacement="left"
                            pb="6px"
                            pt="8px"
                            mt="-2px"
                            placeholderText="Created after..."
                            selected={this.state.createdAfter}
                            onChange={d => this.validateAndSetDate(d, "createdAfter")}
                            showTimeSelect
                            timeIntervals={15}
                            isClearable
                            timeFormat="HH:mm"
                            dateFormat="DD/MM/YY HH:mm"
                            timeCaption="time"
                        />
                        <DatePicker
                            popperPlacement="left"
                            pb="6px"
                            pt="8px"
                            mt="-2px"
                            placeholderText="Created before..."
                            selected={this.state.createdBefore}
                            onChange={(d) => this.validateAndSetDate(d, "createdBefore")}
                            showTimeSelect
                            timeIntervals={15}
                            isClearable
                            timeFormat="HH:mm"
                            dateFormat="DD/MM/YY HH:mm"
                            timeCaption="time"
                        />
                    </InputGroup>

                    <Heading.h5 pb="0.3em" pt="0.5em">Modified at</Heading.h5>
                    <InputGroup>
                        <DatePicker
                            popperPlacement="left"
                            pb="6px"
                            pt="8px"
                            mt="-2px"
                            placeholderText="Modified after..."
                            selected={this.state.modifiedAfter}
                            onChange={d => this.validateAndSetDate(d, "modifiedAfter")}
                            showTimeSelect
                            timeIntervals={15}
                            isClearable
                            timeFormat="HH:mm"
                            dateFormat="DD/MM/YY HH:mm"
                            timeCaption="time"
                        />
                        <DatePicker
                            popperPlacement="left"
                            pb="6px"
                            pt="8px"
                            mt="-2px"
                            placeholderText="Modified before..."
                            selected={this.state.modifiedBefore}
                            onChange={d => this.validateAndSetDate(d, "modifiedBefore")}
                            showTimeSelect
                            timeIntervals={15}
                            isClearable
                            timeFormat="HH:mm"
                            dateFormat="DD/MM/YY HH:mm"
                            timeCaption="time"
                        />
                    </InputGroup>
                    <Heading.h5 pb="0.3em" pt="0.5em">File Types</Heading.h5>
                    <Flex>
                        <Label fontSize={1} color="black">
                            <Checkbox
                                checked={allowFolders}
                                onChange={e => e.stopPropagation()}
                                onClick={({ target: { checked: allowFolders } }) => this.setState(() => ({ allowFolders }))}
                            />
                            Folders
                        </Label>
                        <Label fontSize={1} color="black">
                            <Checkbox
                                checked={allowFiles}
                                onChange={e => e.stopPropagation()}
                                onClick={({ target: { checked: allowFiles } }) => this.setState(() => ({ allowFiles }))}
                            />
                            Files
                        </Label>
                    </Flex>

                    <Heading.h5 pb="0.3em" pt="0.5em">File extensions</Heading.h5>
                    <SearchStamps stamps={extensions} onStampRemove={(l) => this.onRemoveExtension(l)} clearAll={() => this.setState(() => ({ extensions: new Set() }))} />
                    <form onSubmit={(e) => { e.preventDefault(); this.onAddExtension(); }}>
                        <Input pb="6px" pt="8px" mt="-2px" placeholder={"Add extensions..."} value={extensionValue} onChange={({ target: { value: extensionValue } }) => this.setState(() => ({ extensionValue }))} />
                        <ClickableDropdown
                            chevron
                            trigger={"Add extension preset"}
                            onChange={value => this.onAddPresets(value)}
                            options={extensionPresets}
                        />
                    </form>
                    <Heading.h5 pb="0.3em" pt="0.5em">Sensitivity</Heading.h5>
                    <SearchStamps stamps={sensitivities} onStampRemove={l => this.onRemoveSensitivity(l)} clearAll={() => this.setState(() => ({ sensitivities: new Set() }))} />
                    {sensitivityDropdown}

                    <Heading.h5>Tags</Heading.h5>
                    <SearchStamps stamps={tags} onStampRemove={(l) => this.onRemoveTag(l)} clearAll={() => this.setState(() => ({ tags: new Set() }))} />
                    <form onSubmit={e => { e.preventDefault(); this.onAddTags(); }}>
                        <Input pb="6px" pt="8px" mt="-2px" value={tagValue} onChange={({ target: { value } }) => this.setState(() => ({ tagValue: value }))} />
                    </form>
                    <Button mt="1em" mb={"1.5em"} color={"blue"} onClick={() => this.onSearch()}>Search</Button>
                </Box>
                <SimpleFileList files={this.state.result.items} />
            </Flex>
        );
    }
}

const SearchStamps = ({ stamps, onStampRemove, clearAll }) => (
    <Box pb="5px">
        {[...stamps].map((l, i) => (<Stamp ml="2px" mt="2px" bg="white" key={i}>{l}<CloseButton onClick={() => onStampRemove(l)} size={12} /></Stamp>))}
        {stamps.size > 1 ? (<Stamp ml="2px" mt="2px" bg="blue" borderColor="white" color="white" onClick={clearAll}>Clear all<CloseButton size={12} /></Stamp>) : null}
    </Box>
);

const extensionPresets = [
    { text: "Text", value: ".txt .docx .rtf .csv .pdf" },
    { text: "Image", value: ".png .jpeg .jpg .ppm .gif" },
    { text: "Sound", value: ".mp3 .ogg .wav .flac .aac" },
    { text: "Compressed files", value: ".zip .tar.gz" }
];

const sensitivityOptions = [
    { text: "Open Access", value: "Open Access" },
    { text: "Confidential", value: "Confidential" },
    { text: "Sensitive", value: "Sensitive" }
];

export default connect()(DetailedFileSearch);
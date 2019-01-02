import * as React from "react";
import { connect } from "react-redux";
import {
    DetailedFileSearchProps, DetailedFileSearchReduxState, SensitivityLevel, PossibleTime, FileType,
    AdvancedSearchRequest, DetailedFileSearchOperations
} from ".";
import { DatePicker } from "ui-components/DatePicker";
import "react-datepicker/dist/react-datepicker.css";
import Box from "ui-components/Box";
import ClickableDropdown from "ui-components/ClickableDropdown";
import { Flex, Input, Label, Stamp, InputGroup, OldStamp, Checkbox, Error, OutlineButton, LoadingButton, Icon } from "ui-components";
import * as Heading from "ui-components/Heading";
import { ReduxObject, KeyCode } from "DefaultObjects";
import { Dispatch } from "redux";
import { History } from "history";
import { searchPage } from "Utilities/SearchUtilities";
import * as PropTypes from "prop-types";

class DetailedFileSearch extends React.Component<DetailedFileSearchProps> {

    constructor(props) {
        super(props);
    }

    context: { router: { history: History } }

    static contextTypes = {
        router: PropTypes.object
    }

    private extensionsInput = React.createRef<HTMLInputElement>();

    componentWillUnmount() {
        if (!this.props.hidden)
            this.props.toggleHidden();
    }

    onAddExtension() {
        const extensions = this.extensionsInput.current;
        if (!extensions || !extensions.value) return;
        const newExtensions = extensions.value.trim().split(" ").filter(it => it);
        this.props.addExtensions(newExtensions);
        extensions.value = "";
    }

    onAddPresets(presetExtensions: string) {
        const ext = presetExtensions.trim().split(" ").filter(it => it);
        this.props.addExtensions(ext);
    }

    // FIXME, should show errors in fields instead, the upper corner error is not very noticeable;
    validateAndSetDate(m: Date | null, property: PossibleTime) {
        const { setTimes, setError, createdBefore, modifiedBefore, createdAfter, modifiedAfter } = this.props;
        if (m == null) { setTimes({ [property]: undefined }); return }
        const before = property.includes("Before");
        if (property.includes("created")) {
            if (before && createdAfter) {
                if (m.getTime() > createdAfter.getTime()) {
                    setTimes({ createdBefore: m });
                    return;
                } else {
                    setError("Invalid date range");
                    return;
                }
            } else if (!before && createdBefore) {
                if (m.getTime() < createdBefore.getTime()) {
                    setTimes({ createdAfter: m })
                    return;
                } else {
                    setError("Invalid date range");
                    return;
                }
            }
        } else { // includes Modified
            if (before && modifiedAfter) {
                if (m.getTime() > modifiedAfter.getTime()) {
                    setTimes({ modifiedBefore: m })
                    return;
                } else {
                    setError("Invalid date range");
                    return;
                }
            } else if (!before && modifiedBefore) {
                if (m.getTime() < modifiedBefore.getTime()) {
                    setTimes({ modifiedAfter: m })
                    return;
                } else {
                    setError("Invalid date range");
                    return;
                }
            }
        }
        setTimes({ [property]: m });
    }

    onSearch = () => {
        this.onAddExtension();
        let fileTypes: [FileType?, FileType?] = [];
        if (this.props.allowFiles) fileTypes.push("FILE");
        if (this.props.allowFolders) fileTypes.push("DIRECTORY");
        const createdAt = {
            after: !!this.props.createdAfter ? this.props.createdAfter.valueOf() : undefined,
            before: !!this.props.createdBefore ? this.props.createdBefore.valueOf() : undefined,
        };
        const modifiedAt = {
            after: !!this.props.modifiedAfter ? this.props.modifiedAfter.valueOf() : undefined,
            before: !!this.props.modifiedBefore ? this.props.modifiedBefore.valueOf() : undefined,
        };
        const request: AdvancedSearchRequest = {
            fileName: this.props.fileName,
            extensions: [...this.props.extensions],
            fileTypes,
            createdAt: typeof createdAt.after === "number" || typeof createdAt.before === "number" ? createdAt : undefined,
            modifiedAt: typeof modifiedAt.after === "number" || typeof modifiedAt.before === "number" ? modifiedAt : undefined,
            itemsPerPage: 25,
            page: 0
        }
        this.props.fetchPage(request, () => this.context.router.history.push(searchPage("files", this.props.fileName)));
        this.props.setLoading(true);
    }

    render() {
        if (this.props.hidden) { return (<OutlineButton fullWidth color="darkGreen" onClick={this.props.toggleHidden}>Advanced Search</OutlineButton>) }
        const { sensitivities, extensions, allowFiles, allowFolders } = this.props;
        const remainingSensitivities = sensitivityOptions.filter(s => !sensitivities.has(s.text as SensitivityLevel));
        const sensitivityDropdown = remainingSensitivities.length ? (
            <Box>
                <ClickableDropdown
                    chevron
                    trigger={"Add sensitivity level"}
                    onChange={key => this.props.addSensitivity(key as SensitivityLevel)}
                    options={remainingSensitivities}
                />
            </Box>
        ) : null;

        return (
            <>
                <OutlineButton fullWidth color="darkGreen" onClick={this.props.toggleHidden}>Hide Advanced Search</OutlineButton>
                <Flex flexDirection="column" pl="0.5em" pr="0.5em">
                    <Box mt="0.5em">
                        <form onSubmit={e => { e.preventDefault(); this.onSearch() }}>
                            <Error error={this.props.error} clearError={() => this.props.setError()} />
                            <Heading.h5 pb="0.3em" pt="0.5em">Filename</Heading.h5>
                            <Input
                                pb="6px"
                                pt="8px"
                                mt="-2px"
                                width="100%"
                                placeholder="Filename must include..."
                                defaultValue={this.props.fileName}
                                onChange={({ target: { value } }) => this.props.setFilename(value)}
                            />
                            <Heading.h5 pb="0.3em" pt="0.5em">Created at</Heading.h5>
                            <InputGroup>
                                <DatePicker
                                    popperPlacement="left"
                                    pb="6px"
                                    pt="8px"
                                    mt="-2px"
                                    placeholderText="After"
                                    selected={this.props.createdAfter}
                                    startDate={this.props.createdAfter}
                                    endDate={this.props.createdBefore}
                                    onChange={d => this.validateAndSetDate(d, "createdAfter")}
                                    showTimeSelect
                                    timeIntervals={15}
                                    isClearable
                                    selectsStart
                                    timeFormat="HH:mm"
                                    dateFormat="dd/MM/yy HH:mm"
                                    timeCaption="time"
                                />
                                <DatePicker
                                    popperPlacement="left"
                                    pb="6px"
                                    pt="8px"
                                    mt="-2px"
                                    selectsEnd
                                    placeholderText="Before"
                                    selected={this.props.createdBefore}
                                    startDate={this.props.createdAfter}
                                    endDate={this.props.createdBefore}
                                    onChange={d => this.validateAndSetDate(d, "createdBefore")}
                                    showTimeSelect
                                    timeIntervals={15}
                                    isClearable
                                    timeFormat="HH:mm"
                                    dateFormat="dd/MM/yy HH:mm"
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
                                    placeholderText="After"
                                    selected={this.props.modifiedAfter}
                                    selectsStart
                                    startDate={this.props.modifiedAfter}
                                    endDate={this.props.modifiedBefore}
                                    onChange={d => this.validateAndSetDate(d, "modifiedAfter")}
                                    showTimeSelect
                                    timeIntervals={15}
                                    isClearable
                                    timeFormat="HH:mm"
                                    dateFormat="dd/MM/yy HH:mm"
                                    timeCaption="time"
                                />
                                <DatePicker
                                    popperPlacement="left"
                                    pb="6px"
                                    pt="8px"
                                    mt="-2px"
                                    placeholderText="Before"
                                    selected={this.props.modifiedBefore}
                                    selectsEnd
                                    startDate={this.props.modifiedAfter}
                                    endDate={this.props.modifiedBefore}
                                    onChange={d => this.validateAndSetDate(d, "modifiedBefore")}
                                    showTimeSelect
                                    timeIntervals={15}
                                    isClearable
                                    timeFormat="HH:mm"
                                    dateFormat="dd/MM/yy HH:mm"
                                    timeCaption="time"
                                />
                            </InputGroup>
                            <Heading.h5 pb="0.3em" pt="0.5em">File Types</Heading.h5>
                            <Flex>
                                <Label fontSize={1} color="black">
                                    <Checkbox
                                        checked={allowFolders}
                                        onChange={(e: React.SyntheticEvent) => e.stopPropagation()}
                                        onClick={() => this.props.toggleFolderAllowed()}
                                    />
                                    Folders
                            </Label>
                                <Label fontSize={1} color="black">
                                    <Checkbox
                                        checked={allowFiles}
                                        onChange={(e: React.SyntheticEvent) => e.stopPropagation()}
                                        onClick={() => this.props.toggleFilesAllowed()}
                                    />
                                    Files
                            </Label>
                            </Flex>
                            <Heading.h5 pb="0.3em" pt="0.5em">File extensions</Heading.h5>
                            <SearchStamps stamps={extensions} onStampRemove={l => this.props.removeExtensions([l])} clearAll={() => this.props.removeExtensions([...extensions])} />
                            <Input
                                type="text"
                                pb="6px"
                                pt="8px"
                                mt="-2px"
                                onKeyDown={e => {
                                    if (e.keyCode === KeyCode.ENTER) { e.preventDefault(); this.onAddExtension() }
                                }}
                                ref={this.extensionsInput}
                                placeholder={"Add extensions..."}
                            />
                            <ClickableDropdown
                                width={"100%"}
                                chevron
                                trigger={"Extension presets"}
                                onChange={value => this.onAddPresets(value)}
                                options={extensionPresets}
                            />
                            <Heading.h5 pb="0.3em" pt="0.5em">Sensitivity</Heading.h5>
                            <SearchStamps stamps={sensitivities} onStampRemove={l => this.props.removeSensitivity([l as SensitivityLevel])} clearAll={() => this.props.removeSensitivity([...sensitivities])} />
                            {sensitivityDropdown}
                            <LoadingButton type="submit" fullWidth loading={this.props.loading} mt="1em" mb={"1.5em"} color={"blue"} onClick={() => this.onSearch()} content="Search" />
                        </form>
                    </Box>
                </Flex>
            </>
        );
    }
}

interface SearchStampsProps {
    stamps: Set<string>
    onStampRemove: (stamp: string) => void
    clearAll: () => void
}
const SearchStamps = ({ stamps, onStampRemove, clearAll }: SearchStampsProps) => (
    <Box pb="5px">
        {[...stamps].map((l) => (<Stamp onClick={() => onStampRemove(l)} ml="2px" mt="2px" color="blue" key={l} text={l} />))}
        {stamps.size > 1 ? (<Stamp ml="2px" mt="2px" color="white" onClick={() => clearAll()} text={"Clear all"} />) : null}
    </Box >
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

const mapStateToProps = ({ detailedFileSearch }: ReduxObject): DetailedFileSearchReduxState & { sizeCount: number } => ({
    ...detailedFileSearch,
    sizeCount: detailedFileSearch.extensions.size + detailedFileSearch.tags.size + detailedFileSearch.sensitivities.size
});


import * as DFSActions from "Files/Redux/DetailedFileSearchActions";
import { DETAILED_FILES_ADD_EXTENSIONS, DETAILED_FILES_REMOVE_EXTENSIONS, DETAILED_FILES_ADD_SENSITIVITIES, DETAILED_FILES_REMOVE_SENSITIVITIES, DETAILED_FILES_ADD_TAGS, DETAILED_FILES_REMOVE_TAGS } from "./Redux/DetailedFileSearchReducer";
import { searchFiles } from "Search/Redux/SearchActions";
const mapDispatchToProps = (dispatch: Dispatch): DetailedFileSearchOperations => ({
    toggleHidden: () => dispatch(DFSActions.toggleFilesSearchHidden()),
    addExtensions: ext => dispatch(DFSActions.extensionAction(DETAILED_FILES_ADD_EXTENSIONS, ext)),
    removeExtensions: ext => dispatch(DFSActions.extensionAction(DETAILED_FILES_REMOVE_EXTENSIONS, ext)),
    toggleFolderAllowed: () => dispatch(DFSActions.toggleFoldersAllowed()),
    toggleFilesAllowed: () => dispatch(DFSActions.toggleFilesAllowed()),
    addSensitivity: sens => dispatch(DFSActions.sensitivityAction(DETAILED_FILES_ADD_SENSITIVITIES, [sens])),
    removeSensitivity: sens => dispatch(DFSActions.sensitivityAction(DETAILED_FILES_REMOVE_SENSITIVITIES, sens)),
    addTags: tags => dispatch(DFSActions.tagAction(DETAILED_FILES_ADD_TAGS, tags)),
    removeTags: tags => dispatch(DFSActions.tagAction(DETAILED_FILES_REMOVE_TAGS, tags)),
    setFilename: filename => dispatch(DFSActions.setFilename(filename)),
    fetchPage: async (req, callback) => {
        dispatch(await searchFiles(req));
        dispatch(DFSActions.setFilesSearchLoading(false));
        if (typeof callback === "function") callback();
    },
    setLoading: loading => dispatch(DFSActions.setFilesSearchLoading(loading)),
    setTimes: times => dispatch(DFSActions.setTime(times)),
    setError: error => dispatch(DFSActions.setErrorMessage(error))
});

export default connect(mapStateToProps, mapDispatchToProps)(DetailedFileSearch);
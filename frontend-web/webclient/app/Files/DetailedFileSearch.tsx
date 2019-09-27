import {KeyCode, ReduxObject} from "DefaultObjects";
import * as DFSActions from "Files/Redux/DetailedFileSearchActions";
import * as React from "react";
import {connect} from "react-redux";
import {useHistory} from "react-router";
import {Dispatch} from "redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Button, Checkbox, Flex, Input, InputGroup, Label, OutlineButton, Stamp} from "ui-components";
import Box from "ui-components/Box";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {DatePicker} from "ui-components/DatePicker";
import * as Heading from "ui-components/Heading";
import {searchPage} from "Utilities/SearchUtilities";
import {
    DetailedFileSearchOperations,
    DetailedFileSearchReduxState,
    DetailedFileSearchStateProps,
    PossibleTime
} from ".";
import {
    DETAILED_FILES_ADD_EXTENSIONS,
    DETAILED_FILES_ADD_SENSITIVITIES,
    DETAILED_FILES_ADD_TAGS,
    DETAILED_FILES_REMOVE_EXTENSIONS,
    DETAILED_FILES_REMOVE_SENSITIVITIES,
    DETAILED_FILES_REMOVE_TAGS
} from "./Redux/DetailedFileSearchReducer";

interface DetailedFileSearchGivenProps {
    defaultFilename?: string;
    cantHide?: boolean;
    omitFileName?: boolean;
    onSearch: () => void;
}

type DetailedFileSearchProps = DetailedFileSearchStateProps & DetailedFileSearchGivenProps;

function DetailedFileSearch(props: DetailedFileSearchProps) {
    const extensionsInput = React.useRef<HTMLInputElement>(null);
    const history = useHistory();

    React.useEffect(() => {
        if (!!props.defaultFilename) props.setFilename(props.defaultFilename);
        return () => {if (!props.hidden) props.toggleHidden();}
    });

    const {hidden, cantHide, extensions, allowFiles, allowFolders, includeShares} = props;
    if (hidden && !cantHide) {
        return (<OutlineButton fullWidth color="darkGreen" onClick={props.toggleHidden}>Advanced
                Search</OutlineButton>);
    }

    return (
        <>
            {!cantHide ? <OutlineButton fullWidth color="darkGreen" onClick={props.toggleHidden}>Hide Advanced
                    Search</OutlineButton> : null}
            <Flex flexDirection="column" pl="0.5em" pr="0.5em">
                <Box mt="0.5em">
                    <form onSubmit={e => (e.preventDefault(), onSearch())}>
                        <Heading.h5 pb="0.3em" pt="0.5em">Created at</Heading.h5>
                        <InputGroup>
                            <DatePicker
                                pb="6px"
                                pt="8px"
                                mt="-2px"
                                placeholderText="After"
                                selected={props.createdAfter}
                                startDate={props.createdAfter}
                                endDate={props.createdBefore}
                                onChange={d => validateAndSetDate(d, "createdAfter")}
                                showTimeSelect
                                timeIntervals={15}
                                isClearable
                                selectsStart
                                timeFormat="HH:mm"
                                dateFormat="dd/MM/yy HH:mm"
                            />
                            <DatePicker
                                pb="6px"
                                pt="8px"
                                mt="-2px"
                                selectsEnd
                                placeholderText="Before"
                                selected={props.createdBefore}
                                startDate={props.createdAfter}
                                endDate={props.createdBefore}
                                onChange={d => validateAndSetDate(d, "createdBefore")}
                                showTimeSelect
                                timeIntervals={15}
                                isClearable
                                timeFormat="HH:mm"
                                dateFormat="dd/MM/yy HH:mm"
                            />
                        </InputGroup>
                        <Heading.h5 pb="0.3em" pt="0.5em">Modified at</Heading.h5>
                        <InputGroup>
                            <DatePicker
                                pb="6px"
                                pt="8px"
                                mt="-2px"
                                placeholderText="After"
                                selected={props.modifiedAfter}
                                selectsStart
                                startDate={props.modifiedAfter}
                                endDate={props.modifiedBefore}
                                onChange={d => validateAndSetDate(d, "modifiedAfter")}
                                showTimeSelect
                                timeIntervals={15}
                                isClearable
                                timeFormat="HH:mm"
                                dateFormat="dd/MM/yy HH:mm"
                            />
                            <DatePicker
                                pb="6px"
                                pt="8px"
                                mt="-2px"
                                placeholderText="Before"
                                selected={props.modifiedBefore}
                                selectsEnd
                                startDate={props.modifiedAfter}
                                endDate={props.modifiedBefore}
                                onChange={d => validateAndSetDate(d, "modifiedBefore")}
                                showTimeSelect
                                timeIntervals={15}
                                isClearable
                                timeFormat="HH:mm"
                                dateFormat="dd/MM/yy HH:mm"
                            />
                        </InputGroup>
                        <Heading.h5 pb="0.3em" pt="0.5em">File Types</Heading.h5>
                        <Flex>
                            <Label fontSize={1} color="black">
                                <Checkbox
                                    checked={allowFolders}
                                    onChange={(e: React.SyntheticEvent) => e.stopPropagation()}
                                    onClick={() => props.toggleFolderAllowed()}
                                />
                                Folders
                                </Label>
                            <Label fontSize={1} color="black">
                                <Checkbox
                                    checked={allowFiles}
                                    onChange={(e: React.SyntheticEvent) => e.stopPropagation()}
                                    onClick={() => props.toggleFilesAllowed()}
                                />
                                Files
                                </Label>
                        </Flex>
                        <Heading.h5 pb="0.3em" pt="0.5em">Include Shares</Heading.h5>
                        <Flex>
                            <Label fontSize={1} color="black">
                                <Checkbox
                                    checked={(includeShares)}
                                    onChange={(e: React.SyntheticEvent) => e.stopPropagation()}
                                    onClick={props.toggleIncludeShares}
                                />
                            </Label>
                        </Flex>
                        <Heading.h5 pb="0.3em" pt="0.5em">File extensions</Heading.h5>
                        <SearchStamps
                            stamps={extensions}
                            onStampRemove={l => props.removeExtensions([l])}
                            clearAll={() => props.removeExtensions([...extensions])}
                        />
                        <Input
                            type="text"
                            pb="6px"
                            pt="8px"
                            mt="-2px"
                            onKeyDown={e => {
                                if (e.keyCode === KeyCode.ENTER) {
                                    e.preventDefault();
                                    onAddExtension();
                                }
                            }}
                            ref={extensionsInput}
                            placeholder={"Add extensions..."}
                        />
                        <ClickableDropdown
                            width="100%"
                            chevron
                            trigger={"Extension presets"}
                            onChange={val => onAddPresets(val)}
                            options={extensionPresets}
                        />
                        <Button
                            type="submit"
                            fullWidth
                            disabled={props.loading}
                            mt="1em"
                            color="blue"
                        >Search</Button>
                    </form>
                </Box>
            </Flex>
        </>
    );

    function onAddExtension() {
        const extensions = extensionsInput.current;
        if (!extensions || !extensions.value) return;
        const newExtensions = extensions.value.trim().split(" ").filter(it => it);
        props.addExtensions(newExtensions);
        extensions.value = "";
    }

    function onAddPresets(presetExtensions: string) {
        const ext = presetExtensions.trim().split(" ").filter(it => it);
        props.addExtensions(ext);
    }

    function validateAndSetDate(m: Date | null, property: PossibleTime) {
        const {setTimes, createdBefore, modifiedBefore, createdAfter, modifiedAfter} = props;
        if (m == null) {
            setTimes({[property]: undefined});
            return;
        }
        const before = property.includes("Before");
        if (property.includes("created")) {
            if (before && createdAfter) {
                if (m.getTime() > createdAfter.getTime()) {
                    setTimes({createdBefore: m});
                    return;
                } else {
                    snackbarStore.addFailure("Invalid date range");
                    return;
                }
            } else if (!before && createdBefore) {
                if (m.getTime() < createdBefore.getTime()) {
                    setTimes({createdAfter: m});
                    return;
                } else {
                    snackbarStore.addFailure("Invalid date range");
                    return;
                }
            }
        } else { // includes Modified
            if (before && modifiedAfter) {
                if (m.getTime() > modifiedAfter.getTime()) {
                    setTimes({modifiedBefore: m});
                    return;
                } else {
                    snackbarStore.addFailure("Invalid date range");
                    return;
                }
            } else if (!before && modifiedBefore) {
                if (m.getTime() < modifiedBefore.getTime()) {
                    setTimes({modifiedAfter: m});
                    return;
                } else {
                    snackbarStore.addFailure("Invalid date range");
                    return;
                }
            }
        }
        setTimes({[property]: m});
    }

    function onSearch() {
        onAddExtension();
        history.push(searchPage("files", props.fileName));
        props.onSearch();
    }
}

interface SearchStampsProps {
    stamps: Set<string>;
    onStampRemove: (stamp: string) => void;
    clearAll: () => void;
}

export const SearchStamps = ({stamps, onStampRemove, clearAll}: SearchStampsProps) => (
    <Box pb="5px">
        {[...stamps].map(l => (
            <Stamp onClick={() => onStampRemove(l)} ml="2px" mt="2px" color="blue" key={l} text={l} />))}
        {stamps.size > 1 ? (<Stamp ml="2px" mt="2px" color="red" onClick={() => clearAll()} text="Clear all" />) : null}
    </Box>
);

const extensionPresets = [
    {text: "Text", value: ".txt .docx .rtf .csv .pdf"},
    {text: "Image", value: ".png .jpeg .jpg .ppm .gif"},
    {text: "Sound", value: ".mp3 .ogg .wav .flac .aac"},
    {text: "Compressed files", value: ".zip .tar.gz"}
];

const mapStateToProps = ({detailedFileSearch}: ReduxObject): DetailedFileSearchReduxState & {sizeCount: number} => ({
    ...detailedFileSearch,
    sizeCount: detailedFileSearch.extensions.size + detailedFileSearch.tags.size + detailedFileSearch.sensitivities.size
});


const mapDispatchToProps = (dispatch: Dispatch): DetailedFileSearchOperations => ({
    toggleHidden: () => dispatch(DFSActions.toggleFilesSearchHidden()),
    addExtensions: ext => dispatch(DFSActions.extensionAction(DETAILED_FILES_ADD_EXTENSIONS, ext)),
    removeExtensions: ext => dispatch(DFSActions.extensionAction(DETAILED_FILES_REMOVE_EXTENSIONS, ext)),
    toggleFolderAllowed: () => dispatch(DFSActions.toggleFoldersAllowed()),
    toggleFilesAllowed: () => dispatch(DFSActions.toggleFilesAllowed()),
    toggleIncludeShares: () => dispatch(DFSActions.toggleIncludeShares()),
    addSensitivity: sens => dispatch(DFSActions.sensitivityAction(DETAILED_FILES_ADD_SENSITIVITIES, [sens])),
    removeSensitivity: sens => dispatch(DFSActions.sensitivityAction(DETAILED_FILES_REMOVE_SENSITIVITIES, sens)),
    addTags: tags => dispatch(DFSActions.tagAction(DETAILED_FILES_ADD_TAGS, tags)),
    removeTags: tags => dispatch(DFSActions.tagAction(DETAILED_FILES_REMOVE_TAGS, tags)),
    setFilename: filename => dispatch(DFSActions.setFilename(filename)),
    setLoading: loading => dispatch(DFSActions.setFilesSearchLoading(loading)),
    setTimes: times => dispatch(DFSActions.setTime(times)),
});

export default connect(mapStateToProps, mapDispatchToProps)(DetailedFileSearch);

import {KeyCode} from "DefaultObjects";
import * as DFSActions from "Files/Redux/DetailedFileSearchActions";
import * as React from "react";
import {connect} from "react-redux";
import {useHistory} from "react-router";
import {Dispatch} from "redux";
import {setSearch} from "Search/Redux/SearchActions";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Button, Checkbox, Flex, Hide, Input, InputGroup, Label, OutlineButton, Stamp} from "ui-components";
import Box from "ui-components/Box";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {DatePicker} from "ui-components/DatePicker";
import * as Heading from "ui-components/Heading";
import {searchPage} from "Utilities/SearchUtilities";
import {stopPropagation} from "UtilityFunctions";
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
    search: string;
}

type DetailedFileSearchProps = DetailedFileSearchStateProps & DetailedFileSearchGivenProps;

function DetailedFileSearch(props: DetailedFileSearchProps): JSX.Element {
    const extensionsInput = React.useRef<HTMLInputElement>(null);
    const history = useHistory();

    React.useEffect(() => {
        if (!!props.defaultFilename) props.setFilename(props.defaultFilename);
        return () => {if (!props.hidden) props.toggleHidden();};
    }, []);

    const {hidden, cantHide, extensions, allowFiles, allowFolders, includeShares} = props;
    if (hidden && !cantHide) {
        return (
            <OutlineButton fullWidth color="darkGreen" onClick={props.toggleHidden}>
                Advanced
                Search
            </OutlineButton>
        );
    }

    function onSubmit(event: React.FormEvent<HTMLFormElement>): void {
        event.preventDefault();
        onSearch();
    }

    return (
        <>
            {cantHide ? null : (
                <OutlineButton
                    fullWidth
                    color="darkGreen"
                    onClick={props.toggleHidden}
                >
                    Hide Advanced Search
                </OutlineButton>
            )}
            <Flex flexDirection="column" pl="0.5em" pr="0.5em">
                <Box mt="0.5em">
                    <form onSubmit={onSubmit}>
                        <Hide lg xl xxl>
                            <Heading.h5 pb="0.3em" pt="0.5em">Filename</Heading.h5>
                            <Input value={props.search} onChange={e => props.setSearch(e.target.value)} />
                        </Hide>
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
                                onChange={d => validateAndSetDate(d as Date, "modifiedAfter")}
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
                                onChange={d => validateAndSetDate(d as Date, "modifiedBefore")}
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
                                    onChange={stopPropagation}
                                    onClick={props.toggleFolderAllowed}
                                />
                                Folders
                                </Label>
                            <Label fontSize={1} color="black">
                                <Checkbox
                                    checked={allowFiles}
                                    onChange={stopPropagation}
                                    onClick={props.toggleFilesAllowed}
                                />
                                Files
                                </Label>
                        </Flex>
                        <Heading.h5 pb="0.3em" pt="0.5em">Include Shares</Heading.h5>
                        <Flex>
                            <Label fontSize={1} color="black">
                                <Checkbox
                                    checked={(includeShares)}
                                    onChange={stopPropagation}
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
                            trigger="Extension presets"
                            onChange={onAddPresets}
                            options={extensionPresets}
                        />
                        <Button
                            type="submit"
                            fullWidth
                            disabled={props.loading}
                            mt="1em"
                            color="blue"
                        >
                            Search
                        </Button>
                    </form>
                </Box>
            </Flex>
        </>
    );

    function onAddExtension(): void {
        const extensions = extensionsInput.current;
        if (!extensions || !extensions.value) return;
        const newExtensions = extensions.value.trim().split(" ").filter(it => it);
        props.addExtensions(newExtensions);
        extensions.value = "";
    }

    function onAddPresets(presetExtensions: string): void {
        const ext = presetExtensions.trim().split(" ").filter(it => it);
        props.addExtensions(ext);
    }

    function validateAndSetDate(m: Date | null, property: PossibleTime): void {
        const {setTimes, modifiedBefore, modifiedAfter} = props;
        if (m == null) {
            setTimes({[property]: undefined});
            return;
        }
        const before = property.includes("Before");
        if (before && modifiedAfter) {
            if (m.getTime() > modifiedAfter.getTime()) {
                setTimes({modifiedBefore: m});
                return;
            } else {
                snackbarStore.addFailure("Invalid date range", false);
                return;
            }
        } else if (!before && modifiedBefore) {
            if (m.getTime() < modifiedBefore.getTime()) {
                setTimes({modifiedAfter: m});
                return;
            } else {
                snackbarStore.addFailure("Invalid date range", false);
                return;
            }
        }
        setTimes({[property]: m});
    }

    function onSearch(): void {
        onAddExtension();
        history.push(searchPage("files", props.search));
    }
}

interface SearchStampsProps {
    stamps: Set<string>;
    onStampRemove: (stamp: string) => void;
    clearAll: () => void;
}

export const SearchStamps = ({stamps, onStampRemove, clearAll}: SearchStampsProps): JSX.Element => (
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

const mapStateToProps = ({
    detailedFileSearch,
    simpleSearch
}: ReduxObject): DetailedFileSearchReduxState & {search: string; sizeCount: number} => ({
    ...detailedFileSearch,
    search: simpleSearch.search,
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
    setSearch: search => dispatch(setSearch(search))
});

export default connect(mapStateToProps, mapDispatchToProps)(DetailedFileSearch);

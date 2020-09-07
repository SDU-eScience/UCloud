import {DetailedApplicationOperations, DetailedApplicationSearchReduxState} from "Applications";
import {KeyCode} from "DefaultObjects";
import {SearchStamps} from "Files/DetailedFileSearch";
import * as React from "react";
import {connect} from "react-redux";
import {useHistory} from "react-router";
import {Dispatch} from "redux";
import {setSearch} from "Search/Redux/SearchActions";
import {Box, Button, Checkbox, Flex, Hide, Input, Label} from "ui-components";
import * as Heading from "ui-components/Heading";
import {searchPage} from "Utilities/SearchUtilities";
import {stopPropagation} from "UtilityFunctions";
import {
    clearTags,
    fetchApplications,
    setAppQuery,
    setShowAllVersions,
    tagAction
} from "./Redux/DetailedApplicationSearchActions";

interface DetailedApplicationSearchProps extends
    DetailedApplicationOperations, DetailedApplicationSearchReduxState {
    defaultAppQuery?: string;
    search: string;
}

function DetailedApplicationSearch(props: Readonly<DetailedApplicationSearchProps>) {
    React.useEffect(() => {
        if (!!props.defaultAppQuery) props.setAppQuery(props.defaultAppQuery);
    }, []);

    const history = useHistory();
    const ref = React.useRef<HTMLInputElement>(null);

    function onSearch(e: React.FormEvent<HTMLFormElement>) {
        e.preventDefault();
        props.addTag(ref.current!.value);
        ref.current!.value = "";
        history.push(searchPage("applications", props.search));
    }

    return (
        <Flex flexDirection="column" pl="0.5em" pr="0.5em">
            <Box mt="0.5em">
                <form onSubmit={e => onSearch(e)}>
                    <Hide lg xl xxl>
                        <Heading.h5 pb="0.3em" pt="0.5em">Application name</Heading.h5>
                        <Input value={props.search} onChange={e => props.setSearch(e.target.value)} />
                    </Hide>
                    <Heading.h5 pb="0.3em" pt="0.5em">Show All Versions</Heading.h5>
                    <Flex>
                        <Label fontSize={1} color="black">
                            <Checkbox
                                checked={(props.showAllVersions)}
                                onChange={stopPropagation}
                                onClick={props.setShowAllVersions}
                            />
                        </Label>
                    </Flex>
                    <Heading.h5 pb="0.3em" pt="0.5em">Tags</Heading.h5>
                    <SearchStamps
                        clearAll={() => props.clearTags()}
                        onStampRemove={stamp => props.removeTag(stamp)}
                        stamps={props.tags}
                    />
                    <Input
                        pb="6px"
                        pt="8px"
                        mt="-2px"
                        width="100%"
                        ref={ref}
                        onKeyDown={e => {
                            if (e.keyCode === KeyCode.ENTER) {
                                e.preventDefault();
                                props.addTag(ref.current!.value);
                                ref.current!.value = "";
                            }
                        }}
                        placeholder="Add tag with enter..."
                    />
                    <Button mt="0.5em" type="submit" fullWidth disabled={props.loading} color="blue">Search</Button>
                </form>
            </Box>
        </Flex>
    );
}

const mapStateToProps = ({detailedApplicationSearch, simpleSearch}: ReduxObject) => ({
    ...detailedApplicationSearch,
    search: simpleSearch.search,
    sizeCount: detailedApplicationSearch.tags.size
});

const mapDispatchToProps = (dispatch: Dispatch): DetailedApplicationOperations => ({
    setAppQuery: appQuery => dispatch(setAppQuery(appQuery)),
    addTag: tags => dispatch(tagAction("DETAILED_APPS_ADD_TAG", tags)),
    removeTag: tag => dispatch(tagAction("DETAILED_APPS_REMOVE_TAG", tag)),
    clearTags: () => dispatch(clearTags()),
    setShowAllVersions: () => dispatch(setShowAllVersions()),
    fetchApplications: async (body, callback) => {
        dispatch(await fetchApplications(body));
        if (typeof callback === "function") callback();
    },
    setSearch: search => dispatch(setSearch(search))
});

export default connect(mapStateToProps, mapDispatchToProps)(DetailedApplicationSearch);

import {DetailedApplicationOperations, DetailedApplicationSearchReduxState} from "Applications";
import {KeyCode, ReduxObject} from "DefaultObjects";
import {SearchStamps} from "Files/DetailedFileSearch";
import * as React from "react";
import {connect} from "react-redux";
import {RouteComponentProps, withRouter} from "react-router";
import {Dispatch} from "redux";
import {Box, Button, Flex, Input} from "ui-components";
import * as Heading from "ui-components/Heading";
import {
    clearTags,
    fetchApplications,
    setAppName,
    setVersion,
    tagAction
} from "./Redux/DetailedApplicationSearchActions";

interface DetailedApplicationSearchProps extends
    DetailedApplicationOperations, DetailedApplicationSearchReduxState, RouteComponentProps {
    onSearch: () => void;
    defaultAppName?: string;
    controlledSearch?: [string, (path: string) => void];
}

function DetailedApplicationSearch(props: Readonly<DetailedApplicationSearchProps>) {
    React.useEffect(() => {
        if (!!props.defaultAppName) props.setAppName(props.defaultAppName);
    }, []);

    const ref = React.useRef<HTMLInputElement>(null);

    const localSearch = React.useState(props.appName);
    const [search, setSearch] = props.controlledSearch ? props.controlledSearch : localSearch;

    function onSearch(e: React.FormEvent<HTMLFormElement>) {
        e.preventDefault();
        props.setAppName(search);
        props.addTag(ref.current!.value);
        ref.current!.value = "";
        props.onSearch();
    }

    return (
        <Flex flexDirection="column" pl="0.5em" pr="0.5em">
            <Box mt="0.5em">
                <form onSubmit={e => onSearch(e)}>
                    <Heading.h5 pb="0.3em" pt="0.5em">Application Name</Heading.h5>
                    <Input
                        pb="6px"
                        pt="8px"
                        mt="-2px"
                        width="100%"
                        value={search}
                        onChange={({target}) => setSearch(target.value)}
                        placeholder="Search by name..."
                    />
                    <Heading.h5 pb="0.3em" pt="0.5em">Version Name</Heading.h5>
                    <Input
                        pb="6px"
                        pt="8px"
                        mt="-2px"
                        width="100%"
                        value={props.appVersion}
                        onChange={({target}) => props.setVersionName(target.value)}
                        placeholder="Search by version..."
                    />
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
                        placeholder="Add tag. "
                    />
                    <Button mt="0.5em" type="submit" fullWidth disabled={props.loading} color="blue">Search</Button>
                </form>
            </Box>
        </Flex>);
}

const mapStateToProps = ({detailedApplicationSearch}: ReduxObject) => ({
    ...detailedApplicationSearch,
    sizeCount: detailedApplicationSearch.tags.size
});
const mapDispatchToProps = (dispatch: Dispatch): DetailedApplicationOperations => ({
    setAppName: appName => dispatch(setAppName(appName)),
    setVersionName: version => dispatch(setVersion(version)),
    addTag: tags => dispatch(tagAction("DETAILED_APPS_ADD_TAG", tags)),
    removeTag: tag => dispatch(tagAction("DETAILED_APPS_REMOVE_TAG", tag)),
    clearTags: () => dispatch(clearTags()),
    fetchApplications: async (body, callback) => {
        dispatch(await fetchApplications(body));
        if (typeof callback === "function") callback();
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(withRouter(DetailedApplicationSearch));

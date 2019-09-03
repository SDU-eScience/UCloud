import {DetailedApplicationOperations, DetailedApplicationSearchReduxState} from "Applications";
import {ReduxObject} from "DefaultObjects";
import * as React from "react";
import {connect} from "react-redux";
import {RouteComponentProps, withRouter} from "react-router";
import {Dispatch} from "redux";
import {Box, Button, Flex, Input} from "ui-components";
import * as Heading from "ui-components/Heading";
import {searchPage} from "Utilities/SearchUtilities";
import {
    fetchApplicationPageFromName,
    fetchApplicationPageFromTag,
    setAppName,
    setVersion
} from "./Redux/DetailedApplicationSearchActions";

interface DetailedApplicationSearchProps extends
    DetailedApplicationOperations, DetailedApplicationSearchReduxState, RouteComponentProps {
    defaultAppName?: string;
    controlledSearch?: [string, (path: string) => void];
}

function DetailedApplicationSearch(props: Readonly<DetailedApplicationSearchProps>) {
    React.useEffect(() => {
        if (!!props.defaultAppName) props.setAppName(props.defaultAppName);
    }, []);

    const localSearch = React.useState(props.appName);
    const [search, setSearch] = props.controlledSearch ? props.controlledSearch : localSearch;

    function onSearch(e: React.FormEvent<HTMLFormElement>) {
        e.preventDefault();

        props.setAppName(search);
        props.fetchApplicationsFromName(
            search, 25, 0, () => props.history.push(searchPage("applications", search))
        );
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
                    <Button mt="0.5em" type="submit" fullWidth disabled={props.loading} color="blue">Search</Button>
                </form>
            </Box>
        </Flex>);
}

const mapStateToProps = ({detailedApplicationSearch}: ReduxObject) => detailedApplicationSearch;
const mapDispatchToProps = (dispatch: Dispatch): DetailedApplicationOperations => ({
    setAppName: appName => dispatch(setAppName(appName)),
    setVersionName: version => dispatch(setVersion(version)),
    fetchApplicationsFromName: async (query, itemsPerPage, page, callback) => {
        dispatch(await fetchApplicationPageFromName(query, itemsPerPage, page));
        if (typeof callback === "function") callback();
    },
    fetchApplicationsFromTag: async (tags, itemsPerPage, page, callback) => {
        dispatch(await fetchApplicationPageFromTag(tags, itemsPerPage, page));
        if (typeof callback === "function") callback();
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(withRouter(DetailedApplicationSearch));
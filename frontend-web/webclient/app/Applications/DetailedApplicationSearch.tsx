import * as React from "react";
import {Flex, Input, Box, Button} from "ui-components";
import * as Heading from "ui-components/Heading";
import {ReduxObject} from "DefaultObjects";
import {DetailedApplicationSearchReduxState, DetailedApplicationOperations} from "Applications";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {
    setAppName,
    setVersion,
    fetchApplicationPageFromName,
    fetchApplicationPageFromTag
} from "./Redux/DetailedApplicationSearchActions";
import {searchPage} from "Utilities/SearchUtilities";
import {withRouter, RouteComponentProps} from "react-router";

interface DetailedApplicationSearchProps extends
    DetailedApplicationOperations, DetailedApplicationSearchReduxState, RouteComponentProps {
    defaultAppName?: string
}

function DetailedApplicationSearch(props: Readonly<DetailedApplicationSearchProps>) {
    React.useEffect(() => {
        if (!!props.defaultAppName) props.setAppName(props.defaultAppName);
    }, []);

    const inputField = React.useRef<HTMLInputElement>(null);

    function onSearch(e: React.FormEvent<HTMLFormElement>) {
        e.preventDefault();
        if (!inputField.current) return;
        const inputFieldValue = inputField.current.value;
        props.setAppName(inputFieldValue);
        props.fetchApplicationsFromName(
            inputField.current.value, 25, 0, () => props.history.push(searchPage("applications", inputFieldValue))
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
                        defaultValue={props.appName}
                        placeholder="Search by name..."
                        ref={inputField}
                    />
                    <Button mt="0.5em" type="submit" fullWidth disabled={props.loading} color="blue">Search</Button>
                </form>
            </Box>
        </Flex>)
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
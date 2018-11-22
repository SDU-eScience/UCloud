import * as React from "react";
import { Flex, Input, Box, Error, LoadingButton } from "ui-components";
import * as Heading from "ui-components/Heading";
import { ReduxObject } from "DefaultObjects";
import { DetailedApplicationSearchReduxState, DetailedApplicationOperations } from "Applications";
import { connect } from "react-redux";
import { Dispatch } from "redux";
import { setAppName, setVersion, fetchApplicationPageFromName, fetchApplicationPageFromTag } from "./Redux/DetailedApplicationSearchActions";
import { object } from "prop-types";
import { History } from "history";

type DetailedApplicationSearchProps = DetailedApplicationOperations & DetailedApplicationSearchReduxState;
class DetailedApplicationSearch extends React.Component<DetailedApplicationSearchProps> {
    constructor(props) {
        super(props);
    }

    context: { router: { history: History } }

    static contextTypes = {
        router: object
    }

    private inputField: any = React.createRef();

    onSearch(e: React.FormEvent<HTMLFormElement>) {
        e.preventDefault();
        const inputFieldValue = this.inputField.current.value;
        this.props.setAppName(inputFieldValue);
        this.props.fetchApplicationsFromName(this.inputField.current.value, 25, 0,
            () => this.context.router.history.push(`/simplesearch/applications/${inputFieldValue}`));
    }

    render() {
        return (
            <Flex flexDirection="column" pl="0.5em" pr="0.5em">
                <Box mt="0.5em">
                    <Heading.h3>Search</Heading.h3>
                    <Error clearError={console.log} error={this.props.error} />
                    <form onSubmit={e => this.onSearch(e)}>
                        <Heading.h5 pb="0.3em" pt="0.5em">Application Name</Heading.h5>
                        <Input
                            pb="6px"
                            pt="8px"
                            mt="-2px"
                            width="100%"
                            defaultValue={this.props.appName}
                            placeholder="Search by name..."
                            ref={this.inputField}
                        />
                        <Flex mt="1em">
                            <LoadingButton type="submit" loading={this.props.loading} content="By Name" color="blue" />
                            <Box ml="auto" />
                            <LoadingButton type="submit" loading={false} color="blue" content="By Tag" disabled />
                        </Flex>
                    </form>
                </Box>
            </Flex>)
    }
}

const mapStateToProps = ({ detailedApplicationSearch }: ReduxObject) => detailedApplicationSearch;
const mapDispatchToProps = (dispatch: Dispatch): DetailedApplicationOperations => ({
    setAppName: (appName) => dispatch(setAppName(appName)),
    setVersionName: (version) => dispatch(setVersion(version)),
    fetchApplicationsFromName: async (query, itemsPerPage, page, callback) => {
        dispatch(await fetchApplicationPageFromName(query, itemsPerPage, page));
        if (typeof callback === "function") callback();
    },
    fetchApplicationsFromTag: async (tags, itemsPerPage, page, callback) => {
        dispatch(await fetchApplicationPageFromTag(tags, itemsPerPage, page));
        if (typeof callback === "function") callback();
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(DetailedApplicationSearch);
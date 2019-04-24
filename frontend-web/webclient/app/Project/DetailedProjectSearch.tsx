import * as React from "react";
import { searchPage } from "Utilities/SearchUtilities";
import { Flex, Box, Error, Input, Button } from "ui-components";
import * as Heading from "ui-components/Heading";
import { connect } from "react-redux";
import { ReduxObject } from "DefaultObjects";
import { Dispatch } from "redux";
import { DetailedProjectSearchReduxState, DetailedProjectSearchOperations } from "Project";
import { setError, setProjectName } from "./Redux/ProjectSearchActions";
import { searchProjects } from "Search/Redux/SearchActions";
import { History } from "history";
import { withRouter } from "react-router";

type DetailedProjectSearchProps = DetailedProjectSearchReduxState & DetailedProjectSearchOperations;
class DetailedProjectSearch extends React.Component<DetailedProjectSearchProps & { history: History }> {
    constructor(props) {
        super(props);
        this.props.setProjectName(props.defaultProjectName);
    }

    private readonly inputField = React.createRef<HTMLInputElement>();

    private onSearch(e: React.FormEvent<HTMLFormElement>) {
        e.preventDefault();
        if (!this.inputField.current) return;
        const inputFieldValue = this.inputField.current.value;
        this.props.setProjectName(inputFieldValue);
        this.props.fetchProjectsFromName(
            inputFieldValue,
            25,
            0,
            () => this.props.history.push(searchPage("projects", inputFieldValue))
        );
    }

    render() {
        return (
            <Flex flexDirection="column" pl="0.5em" pr="0.5em">
                <Box mt="0.5em">
                    <Error clearError={() => this.props.setError()} error={this.props.error} />
                    <form onSubmit={e => this.onSearch(e)}>
                        <Heading.h5 pb="0.3em" pt="0.5em">Project Name</Heading.h5>
                        <Input
                            pb="6px"
                            pt="8px"
                            mt="-2px"
                            width="100%"
                            defaultValue={this.props.projectName}
                            placeholder="Search by name..."
                            ref={this.inputField}
                        />
                        <Button mt="0.5em" type="submit" fullWidth disabled={this.props.loading} color="blue">Search</Button>
                    </form>
                </Box>
            </Flex>)
    }
}

const mapStateToProps = ({ detailedProjectSearch }: ReduxObject): DetailedProjectSearchReduxState => detailedProjectSearch;
const mapDispatchToProps = (dispatch: Dispatch): DetailedProjectSearchOperations => ({
    setProjectName: name => dispatch(setProjectName(name)),
    fetchProjectsFromName: async (name, itemsPerPage, page, callback) => {
        dispatch(await searchProjects(name, page, itemsPerPage))
        if (typeof callback === "function") callback()
    },
    setError: err => dispatch(setError(err))
});

export default connect(mapStateToProps, mapDispatchToProps)(withRouter(DetailedProjectSearch));
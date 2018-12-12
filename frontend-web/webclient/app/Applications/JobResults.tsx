import * as React from "react";
import { toLowerCaseAndCapitalize, shortUUID } from "UtilityFunctions"
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { Cloud } from "Authentication/SDUCloudObject";
import { Link } from "ui-components";
import { List } from "Pagination/List";
import { connect } from "react-redux";
import { setLoading, fetchAnalyses } from "./Redux/AnalysesActions";
import { AnalysesProps, AnalysesState, AnalysesOperations, AnalysesStateProps } from ".";
import { setErrorMessage } from "./Redux/AnalysesActions";
import { Dispatch } from "redux";
import { Table, TableBody, TableCell, TableHeader, TableHeaderCell, TableRow } from "ui-components/Table";
import { Heading } from "ui-components";
import { fileTablePage } from "Utilities/FileUtilities";
import { MainContainer } from "MainContainer/MainContainer";
import { History } from "history";

class JobResults extends React.Component<AnalysesProps & { history: History }, AnalysesState> {
    constructor(props) {
        super(props);
        this.state = {
            reloadIntervalId: -1
        };
        this.props.updatePageTitle("Results");
    }

    componentDidMount() {
        this.getAnalyses(false);
        let reloadIntervalId = window.setInterval(() => {
            this.getAnalyses(true)
        }, 10_000);
        this.setState({ reloadIntervalId });
    }

    componentWillUnmount() {
        clearInterval(this.state.reloadIntervalId);
    }

    getAnalyses(silent) {
        const { page } = this.props;
        if (!silent) this.props.setLoading(true);
        this.props.fetchAnalyses(page.itemsPerPage, page.pageNumber);
    }

    render() {
        const { page, loading, fetchAnalyses, error, onErrorDismiss, history } = this.props;
        const content = <List
            customEmptyPage={<Heading>No jobs have been run on this account.</Heading>}
            loading={loading}
            onErrorDismiss={onErrorDismiss}
            errorMessage={error}
            onRefresh={() => fetchAnalyses(page.itemsPerPage, page.pageNumber)}
            pageRenderer={(page) =>
                <Table>
                    <Header />
                    <TableBody>
                        {page.items.map((a, i) => <Analysis
                            to={() => history.push(`/applications/results/${a.jobId}`)} analysis={a} key={i} 
                        />)}
                    </TableBody>
                </Table>
            }
            page={page}
            onItemsPerPageChanged={size => this.props.fetchAnalyses(size, 0)}
            onPageChanged={pageNumber => this.props.fetchAnalyses(page.itemsPerPage, pageNumber)}
        />;

        return (
            <React.StrictMode>
                <MainContainer main={content} />
            </React.StrictMode>
        )
    }
}

const Header = () => (
    <TableHeader>
        <TableRow>
            <TableHeaderCell textAlign="left">App Name</TableHeaderCell>
            <TableHeaderCell textAlign="left">Job Id</TableHeaderCell>
            <TableHeaderCell textAlign="left">State</TableHeaderCell>
            <TableHeaderCell textAlign="left" xs sm>Status</TableHeaderCell>
            <TableHeaderCell textAlign="left" xs sm>Started at</TableHeaderCell>
            <TableHeaderCell textAlign="left" xs sm>Last updated at</TableHeaderCell>
        </TableRow>
    </TableHeader>
);

const Analysis = ({ analysis, to }) => {
    const jobIdField = analysis.status === "COMPLETE" ?
        (<Link to={fileTablePage(`${Cloud.jobFolder}/${analysis.jobId}`)}>{analysis.jobId}</Link>) : analysis.jobId;
    return (
        <TableRow cursor="pointer" onClick={() => to()}>
            <TableCell>{analysis.appName}@{analysis.appVersion}</TableCell>
            <TableCell><span title={jobIdField}>{shortUUID(jobIdField)}</span></TableCell>
            <TableCell>{toLowerCaseAndCapitalize(analysis.state)}</TableCell>
            <TableCell xs sm>{analysis.status}</TableCell>
            <TableCell xs sm>{formatDate(analysis.createdAt)}</TableCell>
            <TableCell xs sm>{formatDate(analysis.modifiedAt)}</TableCell>
        </TableRow>)
};

const formatDate = (millis) => {
    let d = new Date(millis);
    return `${pad(d.getDate(), 2)}/${pad(d.getMonth() + 1, 2)}/` +
        `${pad(d.getFullYear(), 2)} ${pad(d.getHours(), 2)}:` +
        `${pad(d.getMinutes(), 2)}:${pad(d.getSeconds(), 2)}`;
};

const pad = (value, length) =>
    (value.toString().length < length) ? pad("0" + value, length) : value;

const mapDispatchToProps = (dispatch: Dispatch): AnalysesOperations => ({
    onErrorDismiss: () => dispatch(setErrorMessage(undefined)),
    updatePageTitle: () => dispatch(updatePageTitle("Results")),
    setLoading: (loading: boolean) => dispatch(setLoading(loading)),
    fetchAnalyses: async (itemsPerPage: number, pageNumber: number) => dispatch(await fetchAnalyses(itemsPerPage, pageNumber))
});

const mapStateToProps = ({ analyses }): AnalysesStateProps => analyses;
export default connect(mapStateToProps, mapDispatchToProps)(JobResults);
import * as React from "react";
import { capitalized, shortUUID } from "UtilityFunctions"
import { updatePageTitle, setActivePage } from "Navigation/Redux/StatusActions";
import { Cloud } from "Authentication/SDUCloudObject";
import { Link } from "ui-components";
import { List } from "Pagination/List";
import { connect } from "react-redux";
import { setLoading, fetchAnalyses } from "./Redux/AnalysesActions";
import { AnalysesProps, AnalysesState, AnalysesOperations, AnalysesStateProps } from ".";
import { setErrorMessage } from "./Redux/AnalysesActions";
import { Dispatch } from "redux";
import { Table, TableBody, TableCell, TableHeader, TableHeaderCell, TableRow } from "ui-components/Table";
import { fileTablePage } from "Utilities/FileUtilities";
import { MainContainer } from "MainContainer/MainContainer";
import { History } from "history";
import { ReduxObject } from "DefaultObjects";
import { SidebarPages } from "ui-components/Sidebar";
import * as Heading from "ui-components/Heading";
import { setRefreshFunction } from "Navigation/Redux/HeaderActions";
import { EntriesPerPageSelector } from "Pagination";
import { Spacer } from "ui-components/Spacer";

class JobResults extends React.Component<AnalysesProps & { history: History }, AnalysesState> {
    constructor(props: Readonly<AnalysesProps & { history: History }>) {
        super(props);
        this.state = {
            reloadIntervalId: -1
        };
        props.setActivePage();
        props.updatePageTitle("Results");
    }

    componentDidMount() {
        this.getAnalyses(false);
        let reloadIntervalId = window.setInterval(() => {
            this.getAnalyses(true)
        }, 10_000);
        this.setState({ reloadIntervalId });
        this.props.setRefresh(() => this.getAnalyses(false));
    }

    componentWillReceiveProps(nextProps: AnalysesProps) {
        const { setRefresh } = nextProps;
        setRefresh(() => this.getAnalyses(false));
    }

    componentWillUnmount() {
        clearInterval(this.state.reloadIntervalId);
        this.props.setRefresh();
    }

    getAnalyses(silent: boolean) {
        const { page } = this.props;
        if (!silent) this.props.setLoading(true);
        this.props.fetchAnalyses(page.itemsPerPage, page.pageNumber);
    }

    render() {
        const { page, loading, fetchAnalyses, error, onErrorDismiss, history, responsive } = this.props;
        const hide = responsive.lessThan.lg;
        const content = <List
            customEmptyPage={<Heading.h1>No jobs have been run on this account.</Heading.h1>}
            loading={loading}
            onErrorDismiss={onErrorDismiss}
            errorMessage={error}
            pageRenderer={page =>
                <Table>
                    <Header hide={hide} />
                    <TableBody>
                        {page.items.map((a, i) => <Analysis
                            hide={hide} to={() => history.push(`/applications/results/${a.jobId}`)} analysis={a} key={i}
                        />)}
                    </TableBody>
                </Table>
            }
            page={page}
            onPageChanged={pageNumber => fetchAnalyses(page.itemsPerPage, pageNumber)}
        />;

        return (<MainContainer
            header={<Spacer left={<Heading.h1>Job Results</Heading.h1>} right={
                <EntriesPerPageSelector
                    content="Jobs per page"
                    entriesPerPage={page.itemsPerPage}
                    onChange={itemsPerPage => fetchAnalyses(itemsPerPage, page.pageNumber)}
                />
            } />}
            main={content}
        />);
    }
}

const Header = ({ hide }: { hide: boolean }) => (
    <TableHeader>
        <TableRow>
            <TableHeaderCell textAlign="left">App Name</TableHeaderCell>
            {hide ? null : <TableHeaderCell textAlign="left">Job Id</TableHeaderCell>}
            <TableHeaderCell textAlign="left">State</TableHeaderCell>
            {hide ? null : <TableHeaderCell textAlign="left">Status</TableHeaderCell>}
            {hide ? null : <TableHeaderCell textAlign="left">Started at</TableHeaderCell>}
            <TableHeaderCell textAlign="left">Last updated at</TableHeaderCell>
        </TableRow>
    </TableHeader>
);

// FIXME: Typesafety. But how has this worked setting Link as title?
const Analysis = ({ analysis, to, hide }) => {
    const jobIdField = analysis.status === "COMPLETE" ?
        (<Link to={fileTablePage(`${Cloud.jobFolder}/${analysis.jobId}`)}>{analysis.jobId}</Link>) : analysis.jobId;
    return (
        <TableRow cursor="pointer" onClick={() => to()}>
            <TableCell>{analysis.appName}@{analysis.appVersion}</TableCell>
            {hide ? null : <TableCell><span title={jobIdField}>{shortUUID(jobIdField)}</span></TableCell>}
            <TableCell>{capitalized(analysis.state)}</TableCell>
            {hide ? null : <TableCell>{analysis.status}</TableCell>}
            {hide ? null : <TableCell>{formatDate(analysis.createdAt)}</TableCell>}
            <TableCell>{formatDate(analysis.modifiedAt)}</TableCell>
        </TableRow>)
};

const formatDate = (millis: number) => {
    let d = new Date(millis);
    return `${pad(d.getDate(), 2)}/${pad(d.getMonth() + 1, 2)}/` +
        `${pad(d.getFullYear(), 2)} ${pad(d.getHours(), 2)}:` +
        `${pad(d.getMinutes(), 2)}:${pad(d.getSeconds(), 2)}`;
};

const pad = (value: string | number, length: number) =>
    (value.toString().length < length) ? pad("0" + value, length) : value;

const mapDispatchToProps = (dispatch: Dispatch): AnalysesOperations => ({
    onErrorDismiss: () => dispatch(setErrorMessage(undefined)),
    updatePageTitle: () => dispatch(updatePageTitle("Results")),
    setLoading: loading => dispatch(setLoading(loading)),
    fetchAnalyses: async (itemsPerPage, pageNumber) => dispatch(await fetchAnalyses(itemsPerPage, pageNumber)),
    setActivePage: () => dispatch(setActivePage(SidebarPages.MyResults)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh))
});

const mapStateToProps = ({ analyses, responsive }: ReduxObject): AnalysesStateProps => ({ ...analyses, responsive: responsive! });
export default connect(mapStateToProps, mapDispatchToProps)(JobResults);
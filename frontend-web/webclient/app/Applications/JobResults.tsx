import * as React from "react";
import { capitalized } from "UtilityFunctions"
import { updatePageTitle, setActivePage } from "Navigation/Redux/StatusActions";
import { ContainerForText } from "ui-components";
import { List } from "Pagination/List";
import { connect } from "react-redux";
import { setLoading, fetchAnalyses } from "./Redux/AnalysesActions";
import { AnalysesProps, AnalysesState, AnalysesOperations, AnalysesStateProps, ApplicationMetadata, Analysis } from ".";
import { setErrorMessage } from "./Redux/AnalysesActions";
import { Dispatch } from "redux";
import { Table, TableBody, TableCell, TableHeader, TableHeaderCell, TableRow } from "ui-components/Table";
import { MainContainer } from "MainContainer/MainContainer";
import { History } from "history";
import { ReduxObject } from "DefaultObjects";
import { SidebarPages } from "ui-components/Sidebar";
import * as Heading from "ui-components/Heading";
import { setRefreshFunction } from "Navigation/Redux/HeaderActions";
import { EntriesPerPageSelector } from "Pagination";
import { Spacer } from "ui-components/Spacer";
import * as moment from "moment";
import "moment/locale/en-gb";
import { JobStateIcon } from "./JobStateIcon";

interface FetchJobsOptions {
    itemsPerPage?: number
    pageNumber?: number
}

class JobResults extends React.Component<AnalysesProps & { history: History }, AnalysesState> {
    constructor(props: Readonly<AnalysesProps & { history: History }>) {
        super(props);
        console.error(this.props);
        moment.locale("en-gb");
        props.setActivePage();
        props.updatePageTitle();
    }

    componentDidMount() {
        this.fetchJobs();
        this.props.setRefresh(() => this.fetchJobs());
    }

    componentWillReceiveProps(nextProps: AnalysesProps) {
        const { setRefresh } = nextProps;
        setRefresh(() => this.fetchJobs());
    }

    componentWillUnmount() {
        this.props.setRefresh(undefined);
    }

    fetchJobs(options?: FetchJobsOptions) {
        const opts = options || {};
        const { page, setLoading } = this.props;
        const itemsPerPage = opts.itemsPerPage !== undefined ? opts.itemsPerPage : page.itemsPerPage;
        const pageNumber = opts.pageNumber !== undefined ? opts.pageNumber : page.pageNumber;
        setLoading(true);
        this.props.fetchJobs(itemsPerPage, pageNumber);
    }

    render() {
        const { page, loading, error, onErrorDismiss, history, responsive } = this.props;
        const hide = responsive.lessThan.lg;
        const content = <List
            customEmptyPage={<Heading.h1>No jobs have been run on this account.</Heading.h1>}
            loading={loading}
            onErrorDismiss={onErrorDismiss}
            errorMessage={error}
            pageRenderer={page =>
                <ContainerForText>
                    <Table>
                        <Header hide={hide} />
                        <TableBody>
                            {page.items.map((a, i) =>
                                <Row
                                    hide={hide}
                                    to={() => history.push(`/applications/results/${a.jobId}`)}
                                    analysis={a}
                                    key={i}
                                />)
                            }
                        </TableBody>
                    </Table>
                </ContainerForText>
            }
            page={page}
            onPageChanged={pageNumber => this.fetchJobs({ pageNumber })}
        />;

        return (<MainContainer
            header={
                <Spacer
                    left={null}
                    right={
                        <EntriesPerPageSelector
                            content="Jobs per page"
                            entriesPerPage={page.itemsPerPage}
                            onChange={itemsPerPage => this.fetchJobs({ itemsPerPage })}
                        />
                    }
                />
            }
            headerSize={48}
            main={content}
        />);
    }
}

const Header = ({ hide }: { hide: boolean }) => (
    <TableHeader>
        <TableRow>
            <TableHeaderCell textAlign="left">State</TableHeaderCell>
            <TableHeaderCell textAlign="left">Application</TableHeaderCell>
            {hide ? null : <TableHeaderCell textAlign="left">Started at</TableHeaderCell>}
            <TableHeaderCell textAlign="left">Last update</TableHeaderCell>
        </TableRow>
    </TableHeader>
);

const Row = ({ analysis, to, hide }: { analysis: Analysis, to: () => void, hide: boolean }) => {
    const metadata: ApplicationMetadata = analysis.metadata;
    return (
        <TableRow cursor="pointer" onClick={() => to()}>
            <TableCell><JobStateIcon state={analysis.state} mr={"8px"} /> {capitalized(analysis.state)}</TableCell>
            <TableCell>{metadata.title} v{metadata.version}</TableCell>
            {hide ? null : <TableCell>{moment(analysis.createdAt).calendar()}</TableCell>}
            <TableCell>{moment(analysis.modifiedAt).calendar()}</TableCell>
        </TableRow>)
};

const mapDispatchToProps = (dispatch: Dispatch): AnalysesOperations => ({
    onErrorDismiss: () => dispatch(setErrorMessage()),
    updatePageTitle: () => dispatch(updatePageTitle("My Results")),
    setLoading: loading => dispatch(setLoading(loading)),
    fetchJobs: async (itemsPerPage, pageNumber) => dispatch(await fetchAnalyses(itemsPerPage, pageNumber)),
    setActivePage: () => dispatch(setActivePage(SidebarPages.MyResults)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh))
});

const mapStateToProps = ({ analyses, responsive }: ReduxObject): AnalysesStateProps => ({ ...analyses, responsive: responsive! });
export default connect(mapStateToProps, mapDispatchToProps)(JobResults);
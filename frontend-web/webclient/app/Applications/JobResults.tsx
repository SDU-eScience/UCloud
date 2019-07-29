import * as React from "react";
import {capitalized, inDevEnvironment, errorMessageOrDefault} from "UtilityFunctions"
import {updatePageTitle, setActivePage} from "Navigation/Redux/StatusActions";
import {ContainerForText, Box, InputGroup, Label, Checkbox, Button} from "ui-components";
import {List} from "Pagination/List";
import {connect} from "react-redux";
import {setLoading, fetchAnalyses, checkAnalysis, checkAllAnalyses} from "./Redux/AnalysesActions";
import {AnalysesProps, AnalysesOperations, Analysis, AppState, RunsSortBy, AnalysesStateProps} from ".";
import {Dispatch} from "redux";
import {Table, TableBody, TableCell, TableHeader, TableHeaderCell, TableRow} from "ui-components/Table";
import {MainContainer} from "MainContainer/MainContainer";
import {History} from "history";
import {ReduxObject} from "DefaultObjects";
import {SidebarPages} from "ui-components/Sidebar";
import * as Heading from "ui-components/Heading";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {EntriesPerPageSelector} from "Pagination";
import {Spacer} from "ui-components/Spacer";
import * as moment from "moment";
import "moment/locale/en-gb";
import {JobStateIcon} from "./JobStateIcon";
import {TextSpan} from "ui-components/Text";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {DatePicker} from "ui-components/DatePicker";
import {prettierString} from "UtilityFunctions";
import styled from "styled-components";
import {MasterCheckbox} from "UtilityComponents";
import {inCancelableState, cancelJobDialog, cancelJob} from "Utilities/ApplicationUtilities";
import {Cloud} from "Authentication/SDUCloudObject";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {SnackType} from "Snackbar/Snackbars";
import {SortOrder} from "Files";

interface FetchJobsOptions {
    itemsPerPage?: number
    pageNumber?: number
    sortBy?: RunsSortBy
    sortOrder?: SortOrder
}

/* FIXME: Almost identical to similar one in FilesTable.tsx */
const JobResultsHeaderCell = styled(TableHeaderCell)`
    background-color: ${({theme}) => theme.colors.white};
    top: 96px; //topmenu + header size
    z-index: 10;
    position: sticky;
`;

function JobResults(props: AnalysesProps & {history: History}) {

    React.useEffect(() => {
        moment.locale("en-gb");
        props.onInit();
        fetchJobs();
        props.setRefresh(() => fetchJobs());
        return () => props.setRefresh();
    }, []);

    function fetchJobs(options?: FetchJobsOptions) {
        const opts = options || {};
        const {page, setLoading} = props;
        const itemsPerPage = opts.itemsPerPage !== undefined ? opts.itemsPerPage : page.itemsPerPage;
        const pageNumber = opts.pageNumber !== undefined ? opts.pageNumber : page.pageNumber;
        const sortOrder = opts.sortOrder !== undefined ? opts.sortOrder : props.sortOrder;
        const sortBy = opts.sortBy !== undefined ? opts.sortBy : props.sortBy;
        setLoading(true);
        props.fetchJobs(itemsPerPage, pageNumber, sortOrder, sortBy);
        props.setRefresh(() => props.fetchJobs(itemsPerPage, pageNumber, sortOrder, sortBy));
    }

    const {page, loading, history, responsive} = props;
    const selectedAnalyses = page.items.filter(it => it.checked);
    const cancelableAnalyses = selectedAnalyses.filter(it => inCancelableState(it.state));

    const hide = responsive.lessThan.lg;
    const masterCheckboxChecked = selectedAnalyses.length === page.items.length && page.items.length > 0;
    const masterCheckbox = <MasterCheckbox
        checked={masterCheckboxChecked}
        onClick={checked => props.checkAllAnalyses(checked)}
    />;
    const content = <List
        customEmptyPage={<Heading.h1>No jobs have been run on this account.</Heading.h1>}
        loading={loading}
        pageRenderer={page =>
            <ContainerForText>
                <Table>
                    <Header hide={hide} masterCheckbox={masterCheckbox} />
                    <TableBody>
                        {page.items.map((a, i) =>
                            <Row
                                hide={hide}
                                checkAnalysis={props.checkAnalysis}
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
        onPageChanged={pageNumber => fetchJobs({pageNumber})}
    />;

    const [currentStateFilter, setFilter] = React.useState("don't filter");
    const [firstDate, setFirstDate] = React.useState<Date | null>(null);
    const [secondDate, setSecondDate] = React.useState<Date | null>(null);

    const appStates = Object.keys(AppState).map(it => ({text: prettierString(it), value: it}))
    appStates.push({text: "Don't Filter", value: "Don't filter"})

    const sidebar = (<Box pt={48}>
        <Heading.h3>
            Quick Filters
        </Heading.h3>
        <Box><TextSpan>Today</TextSpan></Box>
        <Box><TextSpan>Yesterday</TextSpan></Box>
        <Box><TextSpan>This week</TextSpan></Box>
        <Box><TextSpan>No filter</TextSpan></Box>
        <Heading.h3 mt={16}>Active Filters</Heading.h3>
        <Label>Filter by app state</Label>
        <ClickableDropdown
            chevron
            trigger={<TextSpan>{prettierString(currentStateFilter)}</TextSpan>}
            onChange={setFilter}
            options={appStates.filter(it => it.value != currentStateFilter)}
        />
        <Box mb={16} mt={16}>
            <Label>App started after</Label>
            <InputGroup>
                <DatePicker
                    placeholderText="Don't filter"
                    isClearable
                    selected={firstDate}
                    onChange={setFirstDate}
                />
            </InputGroup>
        </Box>
        <Box mb={16}>
            <Label>App started before</Label>
            <InputGroup>
                <DatePicker
                    placeholderText="Don't filter"
                    isClearable
                    selected={secondDate}
                    onChange={setSecondDate}
                />
            </InputGroup>
        </Box>
        <AnalysisOperations cancelableAnalyses={cancelableAnalyses} onFinished={() => fetchJobs()} />
    </Box>);

    return (<MainContainer
        header={
            <Spacer
                left={null}
                right={
                    <EntriesPerPageSelector
                        content="Jobs per page"
                        entriesPerPage={page.itemsPerPage}
                        onChange={itemsPerPage => fetchJobs({itemsPerPage})}
                    />
                }
            />
        }
        headerSize={48}
        sidebarSize={340}
        main={content}
        sidebar={inDevEnvironment() ? sidebar : null}
    />);
}

interface AnalysisOperationsProps {
    cancelableAnalyses: Analysis[]
    onFinished: () => void
}

const AnalysisOperations = ({cancelableAnalyses, onFinished}: AnalysisOperationsProps) =>
    cancelableAnalyses.length === 0 ? null : (
        <Button fullWidth color="red" onClick={() => cancelJobDialog({
            jobCount: cancelableAnalyses.length,
            jobId: cancelableAnalyses[0].jobId,
            onConfirm: async () => {
                try {
                    await Promise.all(cancelableAnalyses.map(a => cancelJob(Cloud, a.jobId)));
                    snackbarStore.addSnack({type: SnackType.Success, message: "Jobs cancelled"});
                } catch (e) {
                    snackbarStore.addFailure(errorMessageOrDefault(e, "An error occured"))
                } finally {
                    onFinished();
                }
            }
        })}>
            Cancel selected ({cancelableAnalyses.length}) jobs
    </Button>
    );

const Header = ({hide, masterCheckbox}: {hide: boolean, masterCheckbox: JSX.Element}) => (
    <TableHeader>
        <TableRow>
            {inDevEnvironment() ? <JobResultsHeaderCell width="4%" textAlign="center">
                {masterCheckbox}
            </JobResultsHeaderCell> : null}
            <JobResultsHeaderCell textAlign="left">State</JobResultsHeaderCell>
            <JobResultsHeaderCell textAlign="left">Application</JobResultsHeaderCell>
            {hide ? null : <JobResultsHeaderCell textAlign="left">Started at</JobResultsHeaderCell>}
            <JobResultsHeaderCell textAlign="left">Last update</JobResultsHeaderCell>
        </TableRow>
    </TableHeader>
);

interface RowProps {
    hide: boolean
    analysis: Analysis
    to: () => void
    checkAnalysis: (jobId: string, checked: boolean) => void
}
const Row = ({analysis, to, hide, checkAnalysis}: RowProps) => {
    const metadata = analysis.metadata;
    return (
        <TableRow cursor={"pointer"}>
            {inDevEnvironment() ? <TableCell textAlign="center">
                <Box><Label>
                    <Checkbox
                        checked={analysis.checked}
                        onClick={(e: {target: {checked: boolean}}) => checkAnalysis(analysis.jobId, e.target.checked)}
                    />
                </Label></Box>
            </TableCell> : null}
            <TableCell onClick={to}><JobStateIcon state={analysis.state} mr={"8px"} /> {capitalized(analysis.state)}
            </TableCell>
            <TableCell onClick={to}>{metadata.title} v{metadata.version}</TableCell>
            {hide ? null : <TableCell onClick={to}>{moment(analysis.createdAt).calendar()}</TableCell>}
            <TableCell onClick={to}>{moment(analysis.modifiedAt).calendar()}</TableCell>
        </TableRow>)
};

const mapDispatchToProps = (dispatch: Dispatch): AnalysesOperations => ({
    setLoading: loading => dispatch(setLoading(loading)),
    fetchJobs: async (itemsPerPage, pageNumber, sortOrder, sortBy) =>
        dispatch(await fetchAnalyses(itemsPerPage, pageNumber, sortOrder, sortBy)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
    onInit: () => {
        dispatch(setActivePage(SidebarPages.Runs));
        dispatch(updatePageTitle("Runs"))
    },
    checkAnalysis: (jobId, checked) => dispatch(checkAnalysis(jobId, checked)),
    checkAllAnalyses: checked => dispatch(checkAllAnalyses(checked))
});

const mapStateToProps = ({analyses, responsive}: ReduxObject): AnalysesStateProps => ({
    ...analyses,
    responsive: responsive!
});
export default connect(mapStateToProps, mapDispatchToProps)(JobResults);
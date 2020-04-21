import {getStartOfDay, getStartOfWeek} from "Activity/Page";
import {Client} from "Authentication/HttpClientInstance";
import {formatRelative} from "date-fns/esm";
import {enGB} from "date-fns/locale";
import {ReduxObject} from "DefaultObjects";
import {SortOrder} from "Files";
import {History} from "history";
import {MainContainer} from "MainContainer/MainContainer";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setActivePage, updatePageTitle} from "Navigation/Redux/StatusActions";
import {EntriesPerPageSelector} from "Pagination";
import {List} from "Pagination/List";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {SnackType} from "Snackbar/Snackbars";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Box, Button, Checkbox, Label, List as ItemList, Flex, Text, Icon, Divider} from "ui-components";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {DatePicker} from "ui-components/DatePicker";
import * as Heading from "ui-components/Heading";
import InputGroup from "ui-components/InputGroup";
import {SidebarPages} from "ui-components/Sidebar";
import {Spacer} from "ui-components/Spacer";
import {TextSpan} from "ui-components/Text";
import {cancelJob, cancelJobDialog, inCancelableState, isRunExpired} from "Utilities/ApplicationUtilities";
import {prettierString, stopPropagation} from "UtilityFunctions";
import {capitalized, errorMessageOrDefault, shortUUID} from "UtilityFunctions";
import {
    AnalysesOperations,
    AnalysesProps,
    AnalysesStateProps,
    JobState,
    JobWithStatus,
    RunsSortBy,
    isJobStateFinal
} from ".";
import {JobStateIcon} from "./JobStateIcon";
import {checkAllAnalyses, checkAnalysis, fetchAnalyses, setLoading} from "./Redux/AnalysesActions";
import {AppToolLogo} from "./AppToolLogo";
import styled from "styled-components";
import {ListRow} from "ui-components/List";

interface FetchJobsOptions {
    itemsPerPage?: number;
    pageNumber?: number;
    sortBy?: RunsSortBy;
    sortOrder?: SortOrder;
    minTimestamp?: number;
    maxTimestamp?: number;
    filter?: string;
}

const StickyBox = styled(Box)`
    position: sticky;
    top: 95px;
    z-index: 50;
`;

function Runs(props: AnalysesProps & {history: History}): React.ReactElement {

    React.useEffect(() => {
        props.onInit();
        fetchJobs();
        props.setRefresh(() => fetchJobs());
        return (): void => props.setRefresh();
    }, []);

    function fetchJobs(options?: FetchJobsOptions): void {
        const opts = options ?? {};
        const {page, setLoading} = props;
        const itemsPerPage = opts.itemsPerPage != null ? opts.itemsPerPage : page.itemsPerPage;
        const pageNumber = opts.pageNumber != null ? opts.pageNumber : page.pageNumber;
        const sortOrder = opts.sortOrder != null ? opts.sortOrder : props.sortOrder;
        const sortBy = opts.sortBy != null ? opts.sortBy : props.sortBy;
        const minTimestamp = opts.minTimestamp != null ? opts.minTimestamp : undefined;
        const maxTimestamp = opts.maxTimestamp != null ? opts.maxTimestamp : undefined;
        const filterValue = opts.filter && opts.filter !== "Don't filter" ? opts.filter as JobState : undefined;

        setLoading(true);
        props.fetchJobs(itemsPerPage, pageNumber, sortOrder, sortBy, minTimestamp, maxTimestamp, filterValue);
        props.setRefresh(() =>
            props.fetchJobs(itemsPerPage, pageNumber, sortOrder, sortBy, minTimestamp, maxTimestamp, filterValue)
        );
    }

    const {page, loading, history, sortBy, sortOrder} = props;
    const {itemsPerPage, pageNumber} = page;

    const selectedAnalyses = page.items.filter(it => it.checked);
    const cancelableAnalyses = selectedAnalyses.filter(it => inCancelableState(it.state));

    const allChecked = selectedAnalyses.length === page.items.length && page.items.length > 0;

    const content = (
        <>
            <StickyBox backgroundColor="white">
                <Spacer
                    left={(
                        <Label ml={10} width="auto">
                            <Checkbox
                                size={27}
                                onClick={() => props.checkAllAnalyses(!allChecked)}
                                checked={allChecked}
                                onChange={stopPropagation}
                            />
                            <Box as={"span"}>Select all</Box>
                        </Label>
                    )}
                    right={(
                        <Box>
                            <ClickableDropdown
                                trigger={(
                                    <>
                                        <Icon
                                            cursor="pointer"
                                            name="arrowDown"
                                            rotation={sortOrder === SortOrder.ASCENDING ? 180 : 0}
                                            size=".7em"
                                            mr=".4em"
                                        />
                                        Sort by: {prettierString(sortBy)}
                                    </>
                                )}
                                chevron
                            >
                                <Box
                                    ml="-16px"
                                    mr="-16px"
                                    pl="15px"
                                    onClick={() => fetchJobs({
                                        sortOrder: sortOrder === SortOrder.ASCENDING ?
                                            SortOrder.DESCENDING : SortOrder.ASCENDING
                                    })}
                                >
                                    <>
                                        {prettierString(sortOrder === SortOrder.ASCENDING ?
                                            SortOrder.DESCENDING : SortOrder.ASCENDING
                                        )}
                                    </>
                                </Box>
                                <Divider />
                                {Object.values(RunsSortBy)
                                    .filter(it => it !== sortBy)
                                    .map((sortByValue: RunsSortBy, j) => (
                                        <Box
                                            ml="-16px"
                                            mr="-16px"
                                            pl="15px"
                                            key={j}
                                            onClick={() =>
                                                fetchJobs({sortBy: sortByValue, sortOrder: SortOrder.ASCENDING})}
                                        >
                                            {prettierString(sortByValue)}
                                        </Box>
                                    ))}
                            </ClickableDropdown>
                        </Box>
                    )}
                />
            </StickyBox>
            <List
                customEmptyPage={<Heading.h1>No jobs found.</Heading.h1>}
                loading={loading}
                pageRenderer={({items}) => (
                    <ItemList>
                        {items.map(it => {
                            const isExpired = isRunExpired(it);
                            const hideExpiration = isExpired || it.expiresAt === null || isJobStateFinal(it.state);
                            return (
                                <ListRow
                                    key={it.jobId}
                                    navigate={() => history.push(`/applications/results/${it.jobId}`)}
                                    icon={<AppToolLogo size="36px" type="APPLICATION" name={it.metadata.name} />}
                                    isSelected={it.checked!}
                                    select={() => props.checkAnalysis(it.jobId, !it.checked)}
                                    left={<Text cursor="pointer">{it.name ? it.name : shortUUID(it.jobId)}</Text>}
                                    leftSub={<>
                                        <Text color="gray" fontSize={0}>
                                            <Icon color="gray" mr="5px" size="10px" name="id" />
                                            {it.metadata.title} v{it.metadata.version}
                                        </Text>
                                        <Text color="gray" fontSize={0}>
                                            <Icon color="gray" ml="4px" mr="2px" size="10px" name="chrono" />
                                            Started {formatRelative(it.createdAt, new Date(), {locale: enGB})}
                                        </Text>
                                    </>}
                                    right={<>
                                        {hideExpiration ? null : (
                                            <Text mr="25px">
                                                Expires {formatRelative(it.expiresAt ?? 0, new Date(), {locale: enGB})}
                                            </Text>
                                        )}
                                        <Flex width="110px">
                                            <JobStateIcon state={it.state} isExpired={isExpired} mr="8px" />
                                            <Flex mt="-3px">{isExpired ? "Expired" : capitalized(it.state)}</Flex>
                                        </Flex>
                                    </>}
                                />
                            );
                        })}
                    </ItemList>
                )
                }
                page={page}
                onPageChanged={pageNumber => fetchJobs({pageNumber})}
            />
        </>
    );

    const defaultFilter = {text: "Don't filter", value: "Don't filter"};
    const [filter, setFilter] = React.useState(defaultFilter);
    const [firstDate, setFirstDate] = React.useState<Date | null>(null);
    const [secondDate, setSecondDate] = React.useState<Date | null>(null);

    const appStates = Object.keys(JobState).map(it => ({text: prettierString(it), value: it}));
    appStates.push(defaultFilter);

    function fetchJobsInRange(minDate: Date | null, maxDate: Date | null) {
        return () => fetchJobs({
            itemsPerPage,
            pageNumber,
            sortOrder,
            sortBy,
            minTimestamp: minDate?.getTime() ?? undefined,
            maxTimestamp: maxDate?.getTime() ?? undefined,
            filter: filter.value === "Don't filter" ? undefined : filter.value
        });
    }

    const startOfToday = getStartOfDay(new Date());
    const dayInMillis = 24 * 60 * 60 * 1000;
    const startOfYesterday = getStartOfDay(new Date(startOfToday.getTime() - dayInMillis));
    const startOfWeek = getStartOfWeek(new Date()).getTime();

    function updateFilterAndFetchJobs(value: string): void {
        setFilter({text: prettierString(value), value});
        fetchJobs({
            itemsPerPage,
            pageNumber,
            sortBy,
            sortOrder,
            filter: value === "Don't filter" ? undefined : value as JobState
        });
    }

    const sidebar = (
        <Box pt={48}>
            <Heading.h3>
                Quick Filters
            </Heading.h3>
            <Box cursor="pointer" onClick={fetchJobsInRange(getStartOfDay(new Date()), null)}>
                <TextSpan>Today</TextSpan>
            </Box>
            <Box
                cursor="pointer"
                onClick={fetchJobsInRange(
                    new Date(startOfYesterday),
                    new Date(startOfYesterday.getTime() + dayInMillis)
                )}
            >
                <TextSpan>Yesterday</TextSpan>
            </Box>
            <Box
                cursor="pointer"
                onClick={fetchJobsInRange(new Date(startOfWeek), null)}
            >
                <TextSpan>This week</TextSpan>
            </Box>
            <Box cursor="pointer" onClick={fetchJobsInRange(null, null)}><TextSpan>No filter</TextSpan></Box>
            <Heading.h3 mt={16}>Active Filters</Heading.h3>
            <Label>Filter by app state</Label>
            <ClickableDropdown
                chevron
                trigger={filter.text}
                onChange={updateFilterAndFetchJobs}
                options={appStates.filter(it => it.value !== filter.value)}
            />
            <Box mb={16} mt={16}>
                <Label>Job created after</Label>
                <InputGroup>
                    <DatePicker
                        placeholderText="Don't filter"
                        isClearable
                        selectsStart
                        showTimeInput
                        startDate={firstDate}
                        endDate={secondDate}
                        selected={firstDate}
                        onChange={(date): void => (setFirstDate(date), fetchJobsInRange(date, secondDate)())}
                        timeFormat="HH:mm"
                        dateFormat="dd/MM/yy HH:mm"
                    />
                </InputGroup>
            </Box>
            <Box mb={16}>
                <Label>Job created before</Label>
                <InputGroup>
                    <DatePicker
                        placeholderText="Don't filter"
                        isClearable
                        selectsEnd
                        showTimeInput
                        startDate={firstDate}
                        endDate={secondDate}
                        selected={secondDate}
                        onChange={(date): void => (setSecondDate(date), fetchJobsInRange(firstDate, date)())}
                        onSelect={d => fetchJobsInRange(firstDate, d)}
                        timeFormat="HH:mm"
                        dateFormat="dd/MM/yy HH:mm"
                    />
                </InputGroup>
            </Box>
            <AnalysisOperations cancelableAnalyses={cancelableAnalyses} onFinished={() => fetchJobs()} />
        </Box>
    );

    return (
        <MainContainer
            header={(
                <Spacer
                    left={null}
                    right={(
                        <Box width="170px">
                            <EntriesPerPageSelector
                                content="Jobs per page"
                                entriesPerPage={page.itemsPerPage}
                                onChange={items => fetchJobs({itemsPerPage: items})}
                            />
                        </Box>
                    )}
                />
            )}
            headerSize={48}
            sidebarSize={340}
            main={content}
            sidebar={sidebar}
        />
    );
}

interface AnalysisOperationsProps {
    cancelableAnalyses: JobWithStatus[];
    onFinished: () => void;
}

function AnalysisOperations({cancelableAnalyses, onFinished}: AnalysisOperationsProps): JSX.Element | null {
    if (cancelableAnalyses.length === 0) return null;
    return (
        <Button
            fullWidth
            color="red"
            onClick={() => cancelJobDialog({
                jobCount: cancelableAnalyses.length,
                jobId: cancelableAnalyses[0].jobId,
                onConfirm: async () => {
                    try {
                        await Promise.all(cancelableAnalyses.map(a => cancelJob(Client, a.jobId)));
                        snackbarStore.addSnack({type: SnackType.Success, message: "Jobs cancelled"});
                    } catch (e) {
                        snackbarStore.addFailure(errorMessageOrDefault(e, "An error occured"));
                    } finally {
                        onFinished();
                    }
                }
            })}
        >
            Cancel selected ({cancelableAnalyses.length}) jobs
        </Button >
    );
}

const mapDispatchToProps = (dispatch: Dispatch): AnalysesOperations => ({
    setLoading: loading => dispatch(setLoading(loading)),
    fetchJobs: async (itemsPerPage, pageNumber, sortOrder, sortBy, minTimestamp, maxTimestamp, filter) =>
        dispatch(await fetchAnalyses(itemsPerPage, pageNumber, sortOrder, sortBy, minTimestamp, maxTimestamp, filter)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
    onInit: () => {
        dispatch(setActivePage(SidebarPages.Runs));
        dispatch(updatePageTitle("Runs"));
    },
    checkAnalysis: (jobId, checked) => dispatch(checkAnalysis(jobId, checked)),
    checkAllAnalyses: checked => dispatch(checkAllAnalyses(checked))
});

const mapStateToProps = ({analyses}: ReduxObject): AnalysesStateProps => analyses;
export default connect(mapStateToProps, mapDispatchToProps)(Runs);

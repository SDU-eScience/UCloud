import * as React from "react";
import * as UCloud from "UCloud";
import {useLoading, useTitle} from "Navigation/Redux/StatusActions";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import {useRefreshFunction} from "Navigation/Redux/HeaderActions";
import {useCallback, useEffect, useState} from "react";
import {useCloudAPI} from "Authentication/DataHook";
import * as Heading from "ui-components/Heading";
import {emptyPageV2} from "DefaultObjects";
import {useProjectId} from "Project";
import * as Pagination from "Pagination";
import {MainContainer} from "MainContainer/MainContainer";
import {useHistory} from "react-router";
import {AppToolLogo} from "Applications/AppToolLogo";
import {ListRow, ListRowStat} from "ui-components/List";
import Text, {TextSpan} from "ui-components/Text";
import {prettierString, shortUUID} from "UtilityFunctions";
import {formatRelative} from "date-fns/esm";
import {enGB} from "date-fns/locale";
import {isRunExpired} from "Utilities/ApplicationUtilities";
import {Box, Button, Flex, InputGroup, Label} from "ui-components";
import {Client} from "Authentication/HttpClientInstance";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {DatePicker} from "ui-components/DatePicker";
import {getStartOfDay, getStartOfWeek} from "Activity/Page";
import {JobState} from "./index";
import {JobStateIcon} from "./JobStateIcon";

function mockApp(): UCloud.compute.Job {
    return {
        id: "string112312" + (Math.random() * 10 | 0),
        owner: {launchedBy: Client.username!},
        updates: [{timestamp: new Date().getTime()}],
        billing: {creditsCharged: 200, pricePerUnit: 100},
        parameters: {
            application: {name: "foo", version: "1.3.3.7"},
            product: {id: "bar", category: "foo", provider: "foo"},
            name: "string" + (Math.random() * 100 | 0),
            replicas: 0,
            allowDuplicateJob: false,
            parameters: {},
            resources: [],
            timeAllocation: {
                hours: 5,
                minutes: 0,
                seconds: 0
            },
            resolvedProduct: {
                id: "string",
                pricePerUnit: 120000 /* int64 */,
                category: {id: "foo", provider: "bar"},
                description: "string",
                availability: {type: "available"},
                priority: 0,
                type: "compute"
            },
            resolvedApplication: {
                metadata: {
                    name: "string",
                    version: "1.3.3.7",
                    authors: ["fooey"],
                    title: "string",
                    description: "string",
                    public: true
                },
                invocation: {

                } as any
            }
        }
    }
}


function isJobStateFinal(state?: JobState): boolean {
    if (state === undefined) return false;
    return ["SUCCESS", "FAILURE"].includes(state);
}

const itemsPerPage = 50;

// const items = [mockApp(), mockApp(), mockApp()];

export const Browse: React.FunctionComponent = () => {
    useTitle("Runs")
    useSidebarPage(SidebarPages.Runs);

    const [infScrollId, setInfScrollId] = useState(0);
    const [jobs, fetchJobs] = useCloudAPI<UCloud.PageV2<UCloud.compute.Job>>(
        {noop: true},
        emptyPageV2
    );

    const refresh = useCallback(() => {
        fetchJobs(UCloud.compute.jobs.browse({itemsPerPage}));
        setInfScrollId(id => id + 1);
    }, []);

    const history = useHistory();

    useRefreshFunction(refresh);

    useLoading(jobs.loading);
    const projectId = useProjectId();

    useEffect(() => {
        fetchJobs(UCloud.compute.jobs.browse({itemsPerPage}));
    }, [projectId]);

    const loadMore = useCallback(() => {
        fetchJobs(UCloud.compute.jobs.browse({itemsPerPage, next: jobs.data.next}));
    }, [jobs.data]);

    const [checked, setChecked] = useState(new Set<string>());

    const pageRenderer = useCallback((page: UCloud.PageV2<UCloud.compute.Job>): React.ReactNode => (
        page.items.map(job => {
            const isExpired = isRunExpired(job);
            const latestUpdate = job.updates[job.updates.length - 1];
            const state = latestUpdate?.state;
            const hideExpiration = isExpired || job.parameters.timeAllocation === null || isJobStateFinal(state);
            return (
                <ListRow
                    key={job.id}
                    navigate={() => history.push(`/applications/results/${job.id}`)}
                    icon={<AppToolLogo size="36px" type="APPLICATION" name={job.parameters.application.name} />}
                    isSelected={checked[job.parameters.name!]}
                    select={() => {
                        if (checked.has(job.parameters.name!)) {
                            checked.delete(job.parameters.name!)
                        } else {
                            checked.add(job.parameters.name!);
                        }
                        setChecked(new Set(...checked));
                    }}
                    left={<Text cursor="pointer">{job.parameters.name ?? shortUUID(job.id)}</Text>}
                    leftSub={<>
                        <ListRowStat color="gray" icon="id">
                            {job.parameters.application.name} v{job.parameters.application.version}
                        </ListRowStat>
                        <ListRowStat color="gray" color2="gray" icon="chrono">
                            {/* TODO: Created at timestamp */}
                            {/* Started {formatRelative(0, new Date(), {locale: enGB})} */}
                        </ListRowStat>
                    </>}
                    right={<>
                        {hideExpiration || job.parameters.timeAllocation == null ? null : (
                            <Text mr="25px">
                                {/* TODO: Expiration */}
                                {/* Expires {formatRelative(0, new Date(), {locale: enGB})} */}
                            </Text>
                        )}
                        <Flex width="110px">
                            <JobStateIcon state={state} isExpired={isExpired} mr="8px" />
                            <Flex mt="-3px">{isExpired ? "Expired" : prettierString(state ?? "")}</Flex>
                        </Flex>
                    </>}
                />
            )
        })
    ), []);

    return <MainContainer
        main={
            <Pagination.ListV2
                page={jobs.data}
                loading={jobs.loading}
                onLoadMore={loadMore}
                pageRenderer={pageRenderer}
                infiniteScrollGeneration={infScrollId}
            />
        }

        sidebar={<FilterOptions onUpdateFilter={onUpdateFilter} />}
    />;

    function onUpdateFilter(f: FetchJobsOptions) {
        console.log(f);
    }
};

type Filter = {text: string; value: string};
const dayInMillis = 24 * 60 * 60 * 1000;
const appStates: Filter[] =
    (["IN_QUEUE", "RUNNING", "CANCELING", "SUCCESS", "FAILURE"] as JobState[])
        .map(it => ({text: prettierString(it), value: it})).concat([{text: "Don't filter", value: "Don't filter" as JobState}]);


interface FetchJobsOptions {
    itemsPerPage?: number;
    pageNumber?: number;
    minTimestamp?: number;
    maxTimestamp?: number;
    filter?: string;
}

function FilterOptions({onUpdateFilter}: {onUpdateFilter: (filter: FetchJobsOptions) => void}) {
    const [filter, setFilter] = useState({text: "Don't filter", value: "Don't filter"});
    const [firstDate, setFirstDate] = useState<Date | undefined>();
    const [secondDate, setSecondDate] = useState<Date | undefined>();

    function updateFilterAndFetchJobs(value: string) {
        setFilter({text: prettierString(value), value});
        onUpdateFilter({filter: value, minTimestamp: firstDate?.getTime(), maxTimestamp: secondDate?.getTime()});
    }

    function fetchJobsInRange(start?: Date, end?: Date) {
        onUpdateFilter({filter: filter.value, minTimestamp: start?.getTime(), maxTimestamp: end?.getTime()});
    }

    function fetchJobs() {
        // TODO
    }

    const startOfToday = getStartOfDay(new Date());
    const startOfYesterday = getStartOfDay(new Date(startOfToday.getTime() - dayInMillis));
    const startOfWeek = getStartOfWeek(new Date()).getTime();

    return (
        <Box pt={48}>
            <Heading.h3>
                Quick Filters
            </Heading.h3>
            <Box cursor="pointer" onClick={() => fetchJobsInRange(
                new Date(startOfToday),
                undefined
            )}>
                <TextSpan>Today</TextSpan>
            </Box>
            <Box
                cursor="pointer"
                onClick={() => fetchJobsInRange(
                    new Date(startOfYesterday),
                    new Date(startOfYesterday.getTime() + dayInMillis)
                )}
            >
                <TextSpan>Yesterday</TextSpan>
            </Box>
            <Box
                cursor="pointer"
                onClick={() => fetchJobsInRange(new Date(startOfWeek), undefined)}
            >
                <TextSpan>This week</TextSpan>
            </Box>
            <Box cursor="pointer" onClick={() => fetchJobsInRange()}><TextSpan>No filter</TextSpan></Box>
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
                        onChange={(date: Date) => (setFirstDate(date), fetchJobsInRange(date, secondDate))}
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
                        onChange={(date: Date) => (setSecondDate(date), fetchJobsInRange(firstDate, date))}
                        onSelect={d => fetchJobsInRange(firstDate, d)}
                        timeFormat="HH:mm"
                        dateFormat="dd/MM/yy HH:mm"
                    />
                </InputGroup>
            </Box>
            <AnalysisOperations cancelableAnalyses={[]/* cancelableAnalyses */} onFinished={() => fetchJobs()} />
        </Box>
    );
}

// Old component pasted below

/*
const StickyBox = styled(Box)`
    position: sticky;
    top: 95px;
    z-index: 50;
`;


const Runs: React.FunctionComponent = () => {
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

    const {page, loading, sortBy, sortOrder} = props;
    const {itemsPerPage, pageNumber} = page;
    const history = useHistory();

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

    const appStates = Object.keys(JobState)
        .filter(it => it !== "CANCELLING").map(it => ({text: prettierString(it), value: it}));
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
                        onChange={(date: Date) => (setFirstDate(date), fetchJobsInRange(date, secondDate)())}
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
                        onChange={(date: Date) => (setSecondDate(date), fetchJobsInRange(firstDate, date)())}
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

*/


interface AnalysisOperationsProps {
    cancelableAnalyses: UCloud.compute.Job[];
    onFinished: () => void;
}

function AnalysisOperations({cancelableAnalyses, onFinished}: AnalysisOperationsProps): JSX.Element | null {
    if (cancelableAnalyses.length === 0) return null;
    return (
        <Button
            fullWidth
            color="red"
            onClick={() => /* cancelJobDialog({
                jobCount: cancelableAnalyses.length,
                jobId: cancelableAnalyses[0].id,
                onConfirm: async () => {
                    try {
                        await Promise.all(cancelableAnalyses.map(a => cancelJob(Client, a.id)));
                        snackbarStore.addSuccess("Jobs cancelled", false);
                    } catch (e) {
                        snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred "), false);
                    } finally {
                        onFinished();
                    }
                }
            }) */ console.log("FOOO-do.")}
        >
            Cancel selected ({cancelableAnalyses.length}) jobs
        </Button>
    );
}

export default Browse;
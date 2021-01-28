import * as React from "react";
import * as UCloud from "UCloud";
import {useLoading, useTitle} from "Navigation/Redux/StatusActions";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import {useRefreshFunction} from "Navigation/Redux/HeaderActions";
import {useCallback, useEffect, useState} from "react";
import {InvokeCommand, useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import * as Heading from "ui-components/Heading";
import {emptyPageV2} from "DefaultObjects";
import {useProjectId} from "Project";
import * as Pagination from "Pagination";
import {MainContainer} from "MainContainer/MainContainer";
import {useHistory} from "react-router";
import {AppToolLogo} from "Applications/AppToolLogo";
import List, {ListRow, ListRowStat, ListStatContainer} from "ui-components/List";
import Text, {TextSpan} from "ui-components/Text";
import {prettierString, stopPropagation, useEffectSkipMount} from "UtilityFunctions";
import {formatRelative} from "date-fns/esm";
import {enGB} from "date-fns/locale";
import {inCancelableState, isRunExpired} from "Utilities/ApplicationUtilities";
import {Box, Checkbox, Flex, InputGroup, Label} from "ui-components";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {DatePicker} from "ui-components/DatePicker";
import {getStartOfDay, getStartOfWeek} from "Activity/Page";
import {isJobStateTerminal, jobAppTitle, jobAppVersion, JobSortBy, JobState, jobTitle, stateToTitle} from "./index";
import {JobStateIcon} from "./JobStateIcon";
import styled from "styled-components";
import {useToggleSet} from "Utilities/ToggleSet";
import {compute} from "UCloud";
import JobsBrowseRequest = compute.JobsBrowseRequest;
import {Operation, Operations} from "ui-components/Operation";
import {creditFormatter} from "Project/ProjectUsage";

const itemsPerPage = 50;

const entityName = "Run";

interface JobOperationCallbacks {
    invokeCommand: InvokeCommand;
    onFinish(): void
}

const jobOperations: Operation<UCloud.compute.Job, JobOperationCallbacks>[] = [
    {
        text: "Cancel",
        confirm: true,
        color: "red",
        icon: "trash",
        onClick: async (selected, extra) => {
            await extra.invokeCommand(UCloud.compute.jobs.remove({
                type: "bulk",
                items: selected.map(it => ({id: it.id}))
            }));
            extra.onFinish();
        },
        enabled: selected => {
            if (selected.some(it => inCancelableState(it.status.state))) {
                return true;
            } else if (selected.length <= 0) {
                return false;
            } else {
                return "No cancelable runs selected";
            }
        },
    }
];

const flags: Partial<JobsBrowseRequest> = {includeApplication: true, includeProduct: true};

export const Browse: React.FunctionComponent = () => {
    useTitle("Runs")
    useSidebarPage(SidebarPages.Runs);

    const [sortBy, setSortBy] = useState<JobSortBy>("CREATED_AT");
    const [filters, setFilters] = useState<Partial<JobsBrowseRequest>>({});
    const [infScrollId, setInfScrollId] = useState(0);
    const [jobs, fetchJobs] = useCloudAPI<UCloud.PageV2<UCloud.compute.Job>>({noop: true}, emptyPageV2);

    const refresh = useCallback(() => {
        fetchJobs(UCloud.compute.jobs.browse({itemsPerPage, ...flags, ...filters, sortBy}));
        setInfScrollId(id => id + 1);
    }, [sortBy, filters]);

    const history = useHistory();

    const [, invokeCommand] = useCloudCommand();


    useRefreshFunction(refresh);

    useLoading(jobs.loading);
    const projectId = useProjectId();

    useEffect(() => {
        fetchJobs(UCloud.compute.jobs.browse({itemsPerPage, ...flags, ...filters, sortBy}));
        setInfScrollId(id => id + 1);
    }, [projectId, filters, sortBy]);

    const loadMore = useCallback(() => {
        fetchJobs(UCloud.compute.jobs.browse({itemsPerPage, next: jobs.data.next, ...flags, ...filters, sortBy}));
    }, [jobs.data, filters, sortBy]);

    const [allJobs, setAllJobs] = useState<UCloud.compute.Job[]>([]);
    const toggleSet = useToggleSet(allJobs);

    console.log(toggleSet.checked.items);

    const pageRenderer = useCallback((items: UCloud.compute.Job[]): React.ReactNode => {
        setAllJobs(items);
        return <>
            <List bordered={false} childPadding={"8px"}>
                {items.map(job => {
                    const isExpired = isRunExpired(job);
                    return (
                        <ListRow
                            key={job.id}
                            navigate={() => history.push(`/applications/jobs/${job.id}`)}
                            icon={
                                <AppToolLogo size="36px" type="APPLICATION" name={job.specification.application.name} />
                            }
                            isSelected={toggleSet.checked.has(job)}
                            select={() => toggleSet.toggle(job)}
                            left={<Text cursor="pointer">{jobTitle(job)}</Text>}
                            leftSub={
                                <ListStatContainer>
                                    <ListRowStat color="gray" icon="id">
                                        {jobAppTitle(job)} v{jobAppVersion(job)}
                                    </ListRowStat>
                                    {job.status.startedAt == null ? null :
                                        <ListRowStat color="gray" color2="gray" icon="chrono">
                                            Started {formatRelative(job.status.startedAt, new Date(), {locale: enGB})}
                                        </ListRowStat>
                                    }

                                    <ListRowStat icon="grant">
                                        Price: {creditFormatter(job.billing.creditsCharged)}
                                    </ListRowStat>
                                </ListStatContainer>
                            }
                            right={<>
                                {isJobStateTerminal(job.status.state) || job.status.expiresAt == null ? null : (
                                    <Text mr="25px">
                                        Expires {formatRelative(job.status.expiresAt, new Date(), {locale: enGB})}
                                    </Text>
                                )}
                                <Flex width="110px">
                                    <JobStateIcon state={job.status.state} isExpired={isExpired} mr="8px" />
                                    <Flex mt="-3px">{stateToTitle(job.status.state)}</Flex>
                                </Flex>
                                <Operations
                                    selected={toggleSet.checked.items}
                                    location="IN_ROW"
                                    entityNameSingular={entityName}
                                    operations={jobOperations}
                                    row={job}
                                    extra={{
                                        invokeCommand, onFinish: () => {
                                            const toRemove = toggleSet.checked.items.filter(it => !isJobStateTerminal(it.status.state));
                                            for (const job of toRemove) toggleSet.toggle(job);
                                            refresh();
                                        }
                                    }}
                                />
                            </>}
                        />
                    )
                })}
            </List>
        </>
    }, [toggleSet]);

    return <MainContainer
        main={
            <>
                <StickyBox backgroundColor="white">
                    <Box flexGrow={1}>
                        <Label ml={10} width="auto">
                            <Checkbox
                                size={27}
                                onClick={toggleSet.checkAll}
                                checked={toggleSet.allChecked}
                                onChange={stopPropagation}
                            />
                            <Box as={"span"}>Select all</Box>
                        </Label>
                    </Box>
                    <ClickableDropdown
                        chevron
                        trigger={"Sort by: " + sortBys.find(it => it.value === sortBy)?.text ?? ""}
                        onChange={setSortBy}
                        options={sortBys}
                    />
                </StickyBox>
                <Pagination.ListV2
                    page={jobs.data}
                    loading={jobs.loading}
                    onLoadMore={loadMore}
                    pageRenderer={pageRenderer}
                    infiniteScrollGeneration={infScrollId}
                />
            </>
        }

        sidebar={<>
            <FilterOptions onUpdateFilter={f => {
                console.log("Updating filters");
                setFilters(f)
            }} />
            <Operations
                selected={toggleSet.checked.items}
                location="SIDEBAR"
                entityNameSingular={entityName}
                operations={jobOperations}
                extra={{
                    invokeCommand, onFinish: () => {
                        const toRemove = toggleSet.checked.items.filter(it => !isJobStateTerminal(it.status.state));
                        for (const job of toRemove) toggleSet.toggle(job);
                        refresh();
                    }
                }}
            />
        </>}
    />;
};

type SortBy = {text: string, value: JobSortBy};
const sortBys: SortBy[] = [
    {text: "Created at", value: "CREATED_AT"},
    {text: "State", value: "STATE"},
    {text: "Application", value: "APPLICATION"},
];

type Filter = {text: string; value: JobState | "null"};
const dayInMillis = 24 * 60 * 60 * 1000;
const appStates: Filter[] =
    (["IN_QUEUE", "RUNNING", "CANCELING", "SUCCESS", "FAILURE", "EXPIRED"] as JobState[])
        .map(it => ({text: prettierString(it), value: it} as Filter))
        .concat([{
            text: "Don't filter",
            value: "null"
        }]);

const FilterOptions: React.FunctionComponent<{
    onUpdateFilter: (filters: Partial<JobsBrowseRequest>) => void
}> = ({onUpdateFilter, children}) => {
    const [filter, setFilter] = useState<Filter>({text: "Don't filter", value: "null"} as Filter);
    const [firstDate, setFirstDate] = useState<Date | undefined>();
    const [secondDate, setSecondDate] = useState<Date | undefined>();

    function updateFilterAndFetchJobs(value: string) {
        const filter = appStates.find(it => it.value === value);
        if (filter) {
            setFilter(filter);
        }
    }

    function fetchJobsInRange(start?: Date, end?: Date) {
        setFirstDate(start);
        setSecondDate(end);
    }

    useEffectSkipMount(() => {
        onUpdateFilter({
            filterAfter: firstDate?.getTime(),
            filterBefore: secondDate?.getTime(),
            filterState: filter.value === "null" ? undefined : filter.value
        });
    }, [filter, firstDate, secondDate]);

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
                        onChange={(date: Date) => fetchJobsInRange(date, secondDate)}
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
                        onChange={(date: Date) => fetchJobsInRange(firstDate, date)}
                        onSelect={d => fetchJobsInRange(firstDate, d)}
                        timeFormat="HH:mm"
                        dateFormat="dd/MM/yy HH:mm"
                    />
                </InputGroup>
            </Box>
            {children}
        </Box>
    );
}

const StickyBox = styled(Box)`
  position: sticky;
  z-index: 50;
  top: 48px;
  height: 48px;
  display: flex;
  align-items: center;
`;

export default Browse;
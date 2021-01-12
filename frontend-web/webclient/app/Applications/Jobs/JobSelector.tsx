import * as React from "react";
import {Box, Button, Flex} from "ui-components";
import {defaultModalStyle} from "Utilities/ModalUtilities";
import ReactModal from "react-modal";
import {emptyPageV2} from "DefaultObjects";
import * as UCloud from "UCloud";
import {useCloudAPI} from "Authentication/DataHook";
import {ListV2} from "Pagination";
import {dateToString} from "Utilities/DateUtilities";
import {jobTitle} from "Applications/Jobs";

interface JobSelectorProps {
    isShown: boolean;
    trigger: JSX.Element;
    allowAutoConfigure: boolean;
    hasSelectedJob: boolean;
    setShown: (show: boolean) => void;
    onSelect: (job: UCloud.compute.Job) => void;
    suggestedApplication?: {name: string, version: string};
}

export function JobSelector({
    isShown,
    setShown,
    onSelect,
    trigger,
    suggestedApplication,
    allowAutoConfigure,
    hasSelectedJob
}: JobSelectorProps): JSX.Element {
    const [jobs, fetchJobs] = useCloudAPI<UCloud.PageV2<UCloud.compute.Job>, UCloud.compute.JobsBrowseRequest>(
        {noop: true}, emptyPageV2
    );

    React.useEffect(() => {
        fetchJobs(UCloud.compute.jobs.browse({
            filterApplication: suggestedApplication?.name,
            itemsPerPage: 25,
            filterState: "RUNNING",
            includeApplication: true,
            includeParameters: false,
            includeProduct: false,
            includeUpdates: false
        }));
    }, [suggestedApplication])


    React.useEffect(() => {
        if (!hasSelectedJob && jobs.data.items.length > 0 && allowAutoConfigure && suggestedApplication != null) {
            // Auto-configure a job if one can be selected
            onSelect(jobs.data.items[0]);
        }
    }, [hasSelectedJob, jobs.data, allowAutoConfigure, suggestedApplication]);

    const loadMore = React.useCallback(() => {
        fetchJobs(UCloud.compute.jobs.browse({
            next: jobs.data.next,
            itemsPerPage: 25,
            filterState: "RUNNING",
            includeApplication: true,
            includeParameters: false,
            includeProduct: false,
            includeUpdates: false
        }));
    }, [jobs.data]);

    ReactModal.setAppElement("#app");

    return (<>
        <span onClick={() => setShown(true)}>
            {trigger}
        </span>

        <ReactModal
            isOpen={isShown}
            shouldCloseOnEsc
            shouldCloseOnOverlayClick
            onRequestClose={() => setShown(false)}
            style={defaultModalStyle}
        >
            <ListV2
                customEmptyPage={
                    <Box width={500}>
                        You don&#39;t currently have any running jobs. You can start a new job by selecting an
                        application
                        (in &quot;Apps&quot;) and submitting it to be run.
                            </Box>
                }
                page={jobs.data}
                loading={jobs.loading}
                onLoadMore={loadMore}
                pageRenderer={pageRenderer}
            />
        </ReactModal>
    </>);

    function pageRenderer(page: UCloud.PageV2<UCloud.compute.Job>): React.ReactNode {
        return page.items.map(job => (
            <Flex key={job.id} mb={8}>
                <Box flexGrow={1}>
                    {job.parameters.application.name}
                    {" "}
                    ({jobTitle(job)})
                    <br />
                    {dateToString(job.status.startedAt!)}
                </Box>
                <Button type="button" onClick={() => onSelect(job)}>
                    Select
                </Button>
            </Flex>
        ));
    }
}

export function ControlledJobSelector(props: Omit<JobSelectorProps, "isShown" | "setShown">): JSX.Element {
    const [isShown, setIsShown] = React.useState(false);
    return <JobSelector
        isShown={isShown}
        setShown={setIsShown}
        allowAutoConfigure={props.allowAutoConfigure}
        hasSelectedJob={props.hasSelectedJob}
        trigger={props.trigger}
        suggestedApplication={props.suggestedApplication}
        onSelect={job => {
            setIsShown(false);
            props.onSelect(job);
        }}
    />
}

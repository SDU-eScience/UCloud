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
    setShown: (show: boolean) => void;
    onSelect: (job: UCloud.compute.Job) => void;
    trigger: JSX.Element;
}

function JobSelector({isShown, setShown, onSelect, trigger}: JobSelectorProps): JSX.Element {
    const [jobs, fetchJobs] = useCloudAPI<UCloud.PageV2<UCloud.compute.Job>, UCloud.compute.JobsBrowseRequest>({noop: true}, emptyPageV2);

    const loadMore = React.useCallback(() => {
        fetchJobs(UCloud.compute.jobs.browse({
            next: jobs.data.next,
            itemsPerPage: 25,
            filterState: "RUNNING",
            includeApplication: false,
            includeParameters: false,
            includeProduct: false,
            includeUpdates: false
        }));
    }, [jobs.data]);

    return (<>
        <span onClick={() => setShown(true)}>
            {trigger}
        </span>

        <ReactModal isOpen={isShown} shouldCloseOnEsc shouldCloseOnOverlayClick onRequestClose={() => setShown(false)} style={defaultModalStyle}>
            <ListV2 page={jobs.data} loading={jobs.loading} onLoadMore={loadMore} pageRenderer={pageRenderer} />
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
                <Button type={"button"} onClick={() => onSelect(job)}>
                    Select
                </Button>
            </Flex>
        ));
    }
}

export function ControlledJobSelector({trigger, onSelect}: Omit<JobSelectorProps, "isShown" | "setShown">): JSX.Element {
    const [isShown, setIsShown] = React.useState(false);
    return <JobSelector isShown={isShown} setShown={setIsShown} trigger={trigger} onSelect={onSelect} />
}

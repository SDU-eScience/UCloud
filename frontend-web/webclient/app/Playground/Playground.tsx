import {MainContainer} from "MainContainer/MainContainer";
import * as React from "react";
import {EveryIcon} from "ui-components/Icon";
import {Grid, Box, Button, Flex} from "ui-components";
import * as PublicLinks from "Applications/PublicLinks/Management";
import {dialogStore} from "Dialog/DialogStore";
import {ThemeColor} from "ui-components/theme";
import {getCssVar} from "Utilities/StyledComponentsUtilities";
import {ConfirmationButton} from "ui-components/ConfirmationAction";
import {defaultModalStyle} from "Utilities/ModalUtilities";
import ReactModal from "react-modal";
import {emptyPageV2} from "DefaultObjects";
import * as UCloud from "UCloud";
import {useCloudAPI} from "Authentication/DataHook";
import {ListV2} from "Pagination";
import {dateToString} from "Utilities/DateUtilities";
import {jobTitle} from "Applications/Jobs";

export const Playground: React.FunctionComponent = () => {
    const main = (
        <>
            <Grid gridTemplateColumns={"repeat(5, 1fr)"} mb={"32px"}>
                <EveryIcon />
            </Grid>
            <Grid
                gridTemplateColumns="repeat(10, 1fr)"
                style={{overflowY: "scroll"}}
                mb={"32px"}
            >
                {colors.map((c: ThemeColor) => (
                    <Box
                        title={`${c}, ${getCssVar(c)}`}
                        key={c}
                        backgroundColor={c}
                        height={"100px"}
                        width={"100%"}
                    />
                ))}
            </Grid>

            <Button onClick={() => {
                dialogStore.addDialog(<PublicLinks.PublicLinkManagement onSelect={e => console.log(e)} />, () => 0);
            }}>
                Trigger me
            </Button>
            <ConfirmationButton icon={"trash"} actionText={"Delete"} color={"red"} />
            <ControlledJobSelector trigger={<Button>Show!</Button>} onSelect={job => console.log(job)} />
        </>
    );
    return <MainContainer main={main} />;
};

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

function ControlledJobSelector({trigger, onSelect}: Omit<JobSelectorProps, "isShown" | "setShown">): JSX.Element {
    const [isShown, setIsShown] = React.useState(false);
    return <JobSelector isShown={isShown} setShown={setIsShown} trigger={trigger} onSelect={onSelect} />
}

const colors = [
    "black",
    "white",
    "textBlack",
    "lightGray",
    "midGray",
    "gray",
    "darkGray",
    "lightBlue",
    "lightBlue2",
    "blue",
    "darkBlue",
    "lightGreen",
    "green",
    "darkGreen",
    "lightRed",
    "red",
    "darkRed",
    "orange",
    "darkOrange",
    "lightPurple",
    "purple",
    "yellow",
    "text",
    "textHighlight",
    "headerText",
    "headerBg",
    "headerIconColor",
    "headerIconColor2",
    "borderGray",
    "paginationHoverColor",
    "paginationDisabled",
    "iconColor",
    "iconColor2",
    "FtIconColor",
    "FtIconColor2",
    "FtFolderColor",
    "FtFolderColor2",
    "spinnerColor",
    "tableRowHighlight",
    "appCard",
    "wayfGreen",
];

export default Playground;

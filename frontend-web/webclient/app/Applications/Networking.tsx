import * as React from "react";
import {createRef, useState} from "react";
import {APICallParameters, APICallState, useCloudAPI} from "Authentication/DataHook";
import {Analysis, AppState} from "Applications/index";
import {Page} from "Types";
import {emptyPage} from "DefaultObjects";
import {buildQueryString} from "Utilities/URIUtilities";
import * as ReactModal from "react-modal";
import Box from "ui-components/Box";
import Button from "ui-components/Button";
import * as Heading from "ui-components/Heading";
import * as Pagination from "Pagination";
import Divider from "ui-components/Divider";
import {dateToString} from "Utilities/DateUtilities";
import {addStandardDialog} from "UtilityComponents";
import {dialogStore} from "Dialog/DialogStore";
import Input from "ui-components/Input";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {SnackType} from "Snackbar/Snackbars";
import Flex from "ui-components/Flex";

interface NetworkingProps {

}

interface ApplicationPeer {
    name: string
    jobId: string
}

interface ApplicationPeerDetailed {
    name: string
    job: Analysis
}

export interface ListJobsParameters {
    page?: number
    itemsPerPage?: number
    state?: AppState
}

export const listJobs = (
    {
        page = 0,
        itemsPerPage = 50,
        state
    }: ListJobsParameters
): APICallParameters<ListJobsParameters> => ({
    method: "GET",
    path: buildQueryString("/hpc/jobs", {page, itemsPerPage, state}),
    reloadId: Math.random(),
    parameters: {page, itemsPerPage, state}
});

/**
 * A component for configuring networking with other applications.
 */
const Networking: React.FunctionComponent<NetworkingProps> = props => {
    const [apps, setAppFetchParams] = useCloudAPI<Page<Analysis>>(listJobs({state: AppState.RUNNING}), emptyPage);
    const [isDialogOpen, setIsDialogOpen] = useState(false);
    const [selectedPeers, setSelectedPeers] = useState<ApplicationPeerDetailed[]>([]);

    return <Box>
        Networking can be configured between multiple running applications. Select a running application and
        SDUCloud will automatically make these machines available with a user supplied hostname.

        {
            selectedPeers.map(m =>
                <Flex mt={8} alignItems={"center"}>
                    <Box as={"p"}>
                        {m.job.metadata.title} at <code>{m.name}</code>
                    </Box>
                    <Box ml={"auto"}/>
                    <Button
                        color={"blue"}
                        type={"button"}
                        onClick={() => {
                            let newSelectedPeers = selectedPeers.filter(it => it.job.jobId !== m.job.jobId);
                            setSelectedPeers(newSelectedPeers);
                        }}>
                        ✗
                    </Button>
                </Flex>
            )
        }

        <Button type={"button"} onClick={() => setIsDialogOpen(true)}>
            Add new peer
        </Button>

        <NetworkingDialog
            isOpen={isDialogOpen}
            data={apps}
            onPageChanged={page => setAppFetchParams(listJobs({page, state: AppState.RUNNING}))}
            resolve={async ({app}) => {
                setIsDialogOpen(false);
                if (app !== undefined) {
                    const {hostname} = await hostnameDialog(app.metadata.title);
                    if (hostname !== undefined) {
                        console.log("Got hostname", app, hostname);
                        const newSelectedPeers = selectedPeers.concat([{job: app, name: hostname}]);
                        setSelectedPeers(newSelectedPeers);
                    }
                }
            }}
        />
    </Box>;
};

const NetworkingDialog: React.FunctionComponent<{
    isOpen: boolean,
    resolve: (data: { app?: Analysis }) => void,
    data: APICallState<Page<Analysis>>,
    onPageChanged: (page: number) => void
}> = props => {
    return <ReactModal
        isOpen={props.isOpen}
        shouldCloseOnEsc={true}
        onRequestClose={() => props.resolve({})}
        style={{
            content: {
                top: "50%",
                left: "50%",
                right: "auto",
                bottom: "auto",
                marginRight: "-50%",
                transform: "translate(-50%, -50%)",
                background: ""
            }
        }}
    >
        <Heading.h3>Select a Running Application</Heading.h3>
        <Divider/>
        <Pagination.List
            loading={props.data.loading}
            page={props.data.data}
            onPageChanged={props.onPageChanged}
            pageRenderer={page => page.items.map(item =>
                <Flex alignItems={"center"}>
                    {item.metadata.title}@{item.metadata.version} ({dateToString(item.createdAt)})
                    <Box ml={"auto"} mr={8}/>
                    <Button type={"button"} onClick={() => props.resolve({app: item})}>Select</Button>
                </Flex>
            )}
        />
    </ReactModal>;
};

const hostnameDialog = (title: string): Promise<{ hostname?: string }> => {
    return new Promise(resolve => {
        // This regex verifies that the input string is a valid hostname. Valid in the sense that most operating
        // systems would actually recognize it as a valid hostname.
        const hostnameRegex = /^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])$/;

        const ref = createRef<HTMLInputElement>();
        const onConfirm = () => {
            const hostname = ref.current!.value;
            resolve({hostname});
        };

        const validator = () => {
            const hostname = ref.current!.value;
            const isValid = hostname.match(hostnameRegex) !== null;
            if (!isValid) {
                snackbarStore.addSnack({
                    type: SnackType.Failure,
                    message: "Invalid hostname"
                });
            }

            return isValid;
        };

        addStandardDialog({
            title: `Select a hostname for '${title}'`,
            onConfirm,
            validator,
            onCancel: () => resolve({}),
            message: (
                <form onSubmit={e => {
                    e.preventDefault();
                    if (validator()) {
                        onConfirm();
                        dialogStore.popDialog();
                    }
                }}>
                    <Input autoFocus ref={ref} placeholder={"hostname"}/>
                </form>
            )
        });
    });
};

export default Networking;
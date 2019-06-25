import * as React from "react";
import {createRef, useState} from "react";
import {APICallParameters, APICallState, useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import * as Heading from "ui-components/Heading";
import {createFileSystem, listFileSystems, SharedFileSystem, SharedFileSystemMount} from "Applications/FileSystems/index";
import {Page} from "Types";
import {emptyPage} from "DefaultObjects";
import * as Pagination from "Pagination";
import Box from "ui-components/Box";
import Flex from "ui-components/Flex";
import Button from "ui-components/Button";
import Input from "ui-components/Input";
import {addStandardDialog} from "UtilityComponents";
import {dialogStore} from "Dialog/DialogStore";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {SnackType} from "Snackbar/Snackbars";
import normalize from "normalize-path";
import {TextP} from "ui-components/Text";
import Divider from "ui-components/Divider";
import {dateToString} from "Utilities/DateUtilities";
import ButtonGroup from "ui-components/ButtonGroup";
import Label from "ui-components/Label";
import * as ReactModal from "react-modal";
import {inDevEnvironment} from "UtilityFunctions";

interface ManagementProps {
    onMountsChange?: (mounts: SharedFileSystemMount[]) => void
}

const Management: React.FunctionComponent<ManagementProps> = (
    {
        onMountsChange = (mounts) => 42
    }: ManagementProps
) => {
    if (!inDevEnvironment()) return null;

    const [selectedMounts, setSelectedMounts] = useState<SharedFileSystemMount[]>([]);

    const [currentPage, setListParams] = useCloudAPI<Page<SharedFileSystem>>(
        listFileSystems({}),
        emptyPage
    );

    const [isCommandLoading, invokeCommand] = useAsyncCommand();

    const [isMountDialogOpen, setIsMountDialogOpen] = useState(false);

    return <Box mb={16}>
        <Box mb={16}>
            Shared file systems can be mounted by multiple running applications.
            Changes from one applications is immediately visible by others. The contents of a shared file system
            is not visible in SDUCloud.
        </Box>

        {
            selectedMounts.map(m =>
                <Flex mt={8} alignItems={"center"}>
                    <Box as={"p"}>
                        {m.sharedFileSystem.title} at <code>{m.mountedAt}</code>
                    </Box>
                    <Box ml={"auto"}/>
                    <Button
                        color={"blue"}
                        type={"button"}
                        onClick={() => {
                            let newSelectedMounts = selectedMounts.filter(it => it.sharedFileSystem.id !== m.sharedFileSystem.id);
                            setSelectedMounts(newSelectedMounts);
                            onMountsChange(newSelectedMounts);
                        }}>
                        ✗
                    </Button>
                </Flex>
            )
        }

        <ButtonGroup>
            <Button fullWidth type={"button"} onClick={() => setIsMountDialogOpen(true)}>
                Mount existing file system
            </Button>

            <Button
                fullWidth
                type={"button"}
                color={"green"}
                disabled={isCommandLoading}
                onClick={async () => {
                    const {command} = await createNewDialog();
                    if (command !== undefined && !isCommandLoading) {
                        await invokeCommand(command);
                        setListParams(listFileSystems({}));
                    }
                }}
            >
                Create new file system
            </Button>

            <MountDialogStep1
                isOpen={isMountDialogOpen}
                selectedMounts={selectedMounts}
                currentPage={currentPage}
                onPageChanged={(page) => setListParams(listFileSystems({page}))}
                resolve={async (data) => {
                    setIsMountDialogOpen(false);
                    const {mount} = await mountDialog(selectedMounts, data.sharedFileSystem);
                    if (mount !== undefined) {
                        let newSelectedMounts = selectedMounts.concat([mount]);
                        setSelectedMounts(newSelectedMounts);
                        onMountsChange(newSelectedMounts);
                    }
                }}

            />
        </ButtonGroup>

    </Box>;
};

const blacklistLocations = [
    "",
    "/",
    "/bin",
    "/boot",
    "/cdrom",
    "/dev",
    "/etc",
    "/home",
    "/lib",
    "/lost+found",
    "/media",
    "/mnt",
    "/opt",
    "/proc",
    "/root",
    "/run",
    "/sbin",
    "/selinux",
    "/srv",
    "/sys",
    "/tmp",
    "/usr",
    "/var"
];

async function createNewDialog(): Promise<{ command?: APICallParameters }> {
    return new Promise(resolve => {
        const ref = createRef<HTMLInputElement>();
        const validator = () => {
            const value = ref.current!.value;
            if (value.length === 0) {
                snackbarStore.addSnack({
                    type: SnackType.Failure,
                    message: "Title cannot be empty"
                });
                return false;
            }
            return true;
        };

        const onConfirm = () => {
            const title = ref.current!.value;
            resolve({command: createFileSystem({title})});
        };

        addStandardDialog({
            title: "Create new shared file system",
            message: (
                <form onSubmit={e => {
                    e.preventDefault();
                    onConfirm();
                    dialogStore.popDialog();
                }}>
                    <Label htmlFor={"sharedFsTitle"}>Title</Label>
                    <Input autoFocus id={"sharedFsTitle"} ref={ref} placeholder={"Spark FS"}/>
                </form>
            ),
            onConfirm,
            onCancel: () => {
            },
            validator
        })
    });
}

const MountDialogStep1: React.FunctionComponent<{
    isOpen: boolean,
    selectedMounts: SharedFileSystemMount[],
    currentPage: APICallState<Page<SharedFileSystem>>,
    onPageChanged: (page: number) => void,
    resolve: (data: { sharedFileSystem?: SharedFileSystem }) => void
}> = props => {
    return <ReactModal
        isOpen={props.isOpen}
        shouldCloseOnEsc={true}
        onRequestClose={() => props.resolve({})}
        ariaHideApp={false}
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
        <>
            <Box>
                <Heading.h3>Shared File Systems</Heading.h3>
                <Divider/>
            </Box>
            <Pagination.List
                loading={props.currentPage.loading}
                page={props.currentPage.data}
                onPageChanged={(page: number) => props.onPageChanged(page)}
                pageRenderer={page => {
                    return <Box>
                        {
                            props.currentPage.data.items.map((fs, idx) => {
                                return <Flex alignItems={"center"} mb={8} key={fs.id}>
                                    <TextP mr={8}>
                                        {fs.title} <br/>
                                        Created at: {dateToString(fs.createdAt)}
                                    </TextP>
                                    <Box ml={"auto"}/>
                                    <Button
                                        type={"button"}
                                        ml={8}
                                        onClick={() => {
                                            props.resolve({sharedFileSystem: fs});
                                        }}
                                    >Mount</Button>
                                </Flex>;
                            })
                        }
                    </Box>
                }}
            />
        </>
    </ReactModal>;
};

async function mountDialog(
    selectedMounts: SharedFileSystemMount[],
    sharedFileSystem?: SharedFileSystem
): Promise<{ mount?: SharedFileSystemMount }> {
    if (sharedFileSystem === undefined) return {};
    if (selectedMounts.find(it => it.sharedFileSystem.id === sharedFileSystem.id) !== undefined) {
        snackbarStore.addSnack({
            type: SnackType.Failure,
            message: "File system has already been mounted"
        });
        return {};
    }
    const {mountedAt} = await mountDialogStep2(sharedFileSystem.title, selectedMounts);
    if (mountedAt === undefined) return {};
    return {mount: {sharedFileSystem, mountedAt}};
}

function mountDialogStep2(
    mountTitle: string,
    selectedMounts: SharedFileSystemMount[]
): Promise<{ mountedAt?: string }> {
    const ref = createRef<HTMLInputElement>();
    return new Promise(resolve => {
        const onConfirm = () => resolve({mountedAt: ref.current!.value});
        const validator = () => {
            const location = ref.current!.value;
            console.log("Running the validator with this value", location);
            if (blacklistLocations.indexOf(normalize(location)) !== -1 ||
                location.indexOf("/") !== 0) {
                snackbarStore.addSnack({
                    message: `Invalid mount location: ${location}`,
                    type: SnackType.Failure
                });

                return false;
            }

            if (selectedMounts.find(it => normalize(it.mountedAt) === normalize(location)) !== undefined) {
                snackbarStore.addSnack({
                    message: `Another file system is already mounted at this location: ${location}`,
                    type: SnackType.Failure
                });

                return false;
            }

            return true;
        };

        addStandardDialog({
            title: `Where to mount ${mountTitle}?`,
            confirmText: "Mount",
            message: (
                <form onSubmit={e => {
                    e.preventDefault();
                    if (validator()) {
                        onConfirm();
                        dialogStore.popDialog();
                    }
                }}>
                    <Input autoFocus ref={ref} placeholder={"/mnt/shared"}/>
                </form>
            ),

            onConfirm,
            onCancel: () => resolve({}),
            validator
        })
    });
}

export default Management;


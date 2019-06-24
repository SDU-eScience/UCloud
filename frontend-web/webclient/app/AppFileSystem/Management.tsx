import * as React from "react";
import {createRef, useState} from "react";
import {useCloudAPI} from "Authentication/DataHook";
import {listFileSystems, SharedFileSystem} from "AppFileSystem/index";
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
import * as Text from "ui-components/Text";
import Icon from "ui-components/Icon";

interface ManagementProps {
    onMountsChange?: (mounts: SharedFileSystemMount[]) => void
}

interface SharedFileSystemMount {
    sharedFileSystem: SharedFileSystem,
    mountedAt: string
}

const Management: React.FunctionComponent<ManagementProps> = (
    {
        onMountsChange = (mounts) => 42
    }: ManagementProps
) => {
    const [showMountRules, setShowMountRules] = useState(false);
    const [selectedMounts, setSelectedMounts] = useState<SharedFileSystemMount[]>([]);

    const [currentPage, setListParams] = useCloudAPI<Page<SharedFileSystem>>(
        listFileSystems({}),
        emptyPage
    );

    return <Box>
        <Box mb={16}>
            Shared file systems can be mounted by multiple running applications.
            Changes from one applications is immediately visible by others.

            {!showMountRules ? null :
                <Box mt={16}>
                    Mount locations must follow these rules:

                    <ul>
                        <li>Paths must start with '/'</li>
                        <li>Must not be a common top-level directory (Invalid examples: /etc, /var, /usr, /tmp)</li>
                    </ul>
                </Box>
            }
        </Box>

        {selectedMounts.length === 0 ? null :
            <Text.TextP bold>Mounted file systems</Text.TextP>
        }

        {
            selectedMounts.map(m =>
                <Flex mt={16}>
                    <Box flexGrow={1}>
                        {m.sharedFileSystem.id} at <code>{m.mountedAt}</code>
                    </Box>

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

        <Pagination.List
            loading={currentPage.loading}
            page={currentPage.data}
            onPageChanged={(page: number) => setListParams(listFileSystems({page}))}
            pageRenderer={page => {
                const filteredItems = page.items.filter(fs =>
                    selectedMounts.find(m => m.sharedFileSystem.id == fs.id) === undefined);

                return <Box>
                    {selectedMounts.length === 0 ? null :
                        <Text.TextP bold mt={16}>Available file systems</Text.TextP>
                    }

                    {filteredItems.length !== 0 ? null :
                        <Text.TextP>No items</Text.TextP>
                    }

                    <Button type={"button"} onClick={() => {
                        addStandardDialog({
                            title: "Hi",
                            onConfirm: () => 42,
                            onCancel: () => 42,
                            message: (
                                <Button type={"button"} onClick={() => {
                                    addStandardDialog({
                                        title: "Hi",
                                        onConfirm: () => 42,
                                        onCancel: () => 42,
                                        message: "Hello!"
                                    })
                                }}>Nesting</Button>
                            )
                        })
                    }}>Hi!</Button>

                    {
                        filteredItems.map((fs, idx) => {
                            const isSelected = selectedMounts.find(m => m.sharedFileSystem.id == fs.id) !== undefined;
                            if (isSelected) return null;

                            return <Flex>
                                <Box mb={16} key={fs.id} flexGrow={1}>{fs.id}</Box>

                                <Button type={"button"} color={"green"} ml={8}
                                        onClick={async () => {
                                            const location = (await mountDialog(fs.id)).mountLocation;

                                            if (location !== undefined) {
                                                if (blacklistLocations.indexOf(normalize(location)) !== -1 ||
                                                    location.indexOf("/") !== 0) {
                                                    snackbarStore.addSnack({
                                                        message: `Invalid mount location: ${location}`,
                                                        type: SnackType.Failure
                                                    });

                                                    setShowMountRules(true);
                                                    return;
                                                }

                                                let newSelectedMounts = selectedMounts.concat(
                                                    [{
                                                        sharedFileSystem: fs,
                                                        mountedAt: location
                                                    }]
                                                );
                                                setSelectedMounts(newSelectedMounts);
                                                onMountsChange(newSelectedMounts);
                                            }
                                        }}>
                                    Mount
                                </Button>
                            </Flex>;
                        })
                    }
                </Box>
            }}
        />
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

function mountDialog(mountTitle: string): Promise<{ mountLocation?: string }> {
    const ref = createRef<HTMLInputElement>();
    return new Promise(resolve => {
        const onConfirm = () => resolve({mountLocation: ref.current!.value});
        addStandardDialog({
            title: `Where to mount ${mountTitle}?`,
            confirmText: "Mount",
            message: (
                <form onSubmit={e => {
                    onConfirm();
                    dialogStore.popDialog();
                }}>
                    <Input autoFocus ref={ref} placeholder={"/mnt/shared"}/>
                </form>
            ),

            onConfirm,
            onCancel: () => resolve({}),
        })
    });
}

export default Management;


import * as React from "react";
import {useHistory} from "react-router";
import {useRef, useReducer, useCallback, useEffect, useMemo, useState} from "react";
import {AppToolLogo} from "@/Applications/AppToolLogo";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {ItemRenderer, ItemRow} from "@/ui-components/Browse";
import {default as ReactModal} from "react-modal";
import {useToggleSet} from "@/Utilities/ToggleSet";
import {BrowseType} from "@/Resource/BrowseType";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {SidebarPages, useSidebarPage} from "@/ui-components/Sidebar";
import {Label, Input, Image, Box, Flex, Tooltip, NoSelect, Icon, Text, Button, ExternalLink, FtIcon, 
         List} from "@/ui-components";
import MainContainer from "@/MainContainer/MainContainer";
import HighlightedCard from "@/ui-components/HighlightedCard";
import styled from "styled-components";
import {History} from "history";
import * as Heading from "@/ui-components/Heading";
import {SyncthingConfig, SyncthingDevice, SyncthingFolder} from "./api";
import * as Sync from "./api";
import {Job} from "@/UCloud/JobsApi";
import {removePrefixFrom} from "@/Utilities/TextUtilities";
import {usePrettyFilePath} from "@/Files/FilePath";
import {fileName} from "@/Utilities/FileUtilities";
import {ListRowStat} from "@/ui-components/List";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {Operation} from "@/ui-components/Operation";
import {deepCopy} from "@/Utilities/CollectionUtilities";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {dialogStore} from "@/Dialog/DialogStore";
import {FilesBrowse} from "@/Files/Files";
import {api as FilesApi} from "@/UCloud/FilesApi";
import {randomUUID, doNothing, removeTrailingSlash, useEffectSkipMount, copyToClipboard} from "@/UtilityFunctions";
import Spinner from "@/LoadingIcon/LoadingIcon";
import {buildQueryString} from "@/Utilities/URIUtilities";
import {callAPI, callAPIWithErrorHandler} from "@/Authentication/DataHook";

import syncthingScreen1 from "@/Assets/Images/syncthing/syncthing-1.png";
import syncthingScreen2 from "@/Assets/Images/syncthing/syncthing-2.png";
import syncthingScreen3 from "@/Assets/Images/syncthing/syncthing-3.png";
import syncthingScreen4 from "@/Assets/Images/syncthing/syncthing-4.png";
import {snackbarStore} from "@/Snackbar/SnackbarStore";

// UI state management
// ================================================================================
type UIAction =
    ReloadConfig | ReloadServers | ReloadDeviceWizard |
    RemoveDevice | RemoveFolder |
    ResetAll |
    AddDevice | AddFolder |
    ExpectServerUpdate | ExpectServerPause;

interface ReloadConfig {
    type: "ReloadConfig";
    config: SyncthingConfig;
}

interface ReloadServers {
    type: "ReloadServers";
    servers: Job[];
}

interface ReloadDeviceWizard {
    type: "ReloadDeviceWizard";
    visible: boolean;
}

interface RemoveDevice {
    type: "RemoveDevice";
    deviceId: string;
}

interface RemoveFolder {
    type: "RemoveFolder";
    folderPath: string;
}

interface ResetAll {
    type: "ResetAll";
}

interface AddDevice {
    type: "AddDevice";
    device: SyncthingDevice;
}

interface AddFolder {
    type: "AddFolder";
    folderPath: string;
}

interface ExpectServerUpdate {
    type: "ExpectServerUpdate";
}

interface ExpectServerPause {
    type: "ExpectServerPause";
    serverId: string;
}

interface UIState {
    // NOTE(Dan): These are all potentially `undefined`. This makes it easier to define the default state. A value of
    // `undefined` should generally be interpreted as "not yet loaded".
    devices?: SyncthingDevice[];
    folders?: SyncthingFolder[];
    servers?: Job[];
    showDeviceWizard?: boolean;
    didAddFolder?: boolean;
}

function uiReducer(state: UIState, action: UIAction): UIState {
    const copy = deepCopy(state);
    switch (action.type) {
        case "ReloadServers": {
            copy.servers = action.servers;
            return copy;
        }

        case "ReloadConfig": {
            copy.devices = action.config.devices;
            copy.folders = action.config.folders;
            return copy;
        }

        case "ReloadDeviceWizard": {
            copy.showDeviceWizard = action.visible;
            return copy;
        }

        case "RemoveDevice": {
            const devices = copy.devices ?? [];
            copy.devices = devices.filter(it => it.deviceId !== action.deviceId);
            return copy;
        }

        case "RemoveFolder": {
            const folders = copy.folders ?? [];
            copy.folders = folders.filter(it => it.ucloudPath !== action.folderPath);
            return copy;
        }

        case "AddDevice": {
            const devices = copy.devices ?? [];
            devices.push(action.device);
            copy.devices = devices;
            return copy;
        }

        case "AddFolder": {
            const folders = copy.folders ?? [];
            if (folders.map(it => it.ucloudPath).includes(action.folderPath)) {
                snackbarStore.addFailure("Folder is already added to synchronization", false);
            } else {
                folders.push({id: randomUUID(), ucloudPath: action.folderPath});
                copy.folders = folders;
                copy.didAddFolder = true;
            }
            return copy;
        }

        case "ExpectServerUpdate": {
            const servers = copy.servers ?? [];
            servers.forEach(server => {
                if (server.status.state !== "SUCCESS" && server.status.state !== "FAILURE") {
                    server.status.state = "IN_QUEUE";
                }
            });
            copy.servers = servers;
            return copy;
        }

        case "ExpectServerPause": {
            const servers = copy.servers ?? [];
            servers.forEach(server => {
                if (action.serverId === server.id) {
                    server.status.state = "SUCCESS";
                }
            });
            copy.servers = servers;
            return copy;
        }

        case "ResetAll": {
            copy.servers = [];
            copy.devices = [];
            copy.folders = [];
            return copy;
        }
    }
}

async function onAction(_: UIState, action: UIAction, cb: ActionCallbacks): Promise<void> {
    switch (action.type) {
        case "RemoveFolder":
        case "RemoveDevice":
        case "AddFolder":
        case "AddDevice": {
            cb.pureDispatch({type: "ExpectServerUpdate"});
            cb.requestJobReloader();
            break;
        }

        case "ExpectServerUpdate": {
            cb.requestJobReloader();
            break;
        }

        case "ResetAll": {
            callAPIWithErrorHandler(Sync.api.resetConfiguration({providerId: "ucloud"}));
            break;
        }
    }
}

interface ActionCallbacks {
    history: History;
    pureDispatch: (action: UIAction) => void;
    requestReload: () => void; // NOTE(Dan): use when it is difficult to rollback a change
    requestJobReloader: () => void;
}

interface OperationCallbacks {
    history: History;
    dispatch: (action: UIAction) => void;
    requestReload: () => void;
    permissionProblems: string[];
}

// Primary user interface
// ================================================================================
export const Overview: React.FunctionComponent = () => {
    // Input "parameters"
    const history = useHistory();

    // UI state
    const [uiState, pureDispatch] = useReducer(uiReducer, {});
    const folderToggleSet = useToggleSet([]);
    const serverToggleSet = useToggleSet([]);
    const deviceToggleSet = useToggleSet([]);
    const didUnmount = useDidUnmount();
    const jobReloaderTimeout = useRef(-1);

    const devices = uiState?.devices ?? [];
    const folders = uiState?.folders ?? [];
    const servers = uiState?.servers ?? [];

    // UI callbacks and state manipulation
    const reload = useCallback(() => {
        Sync.fetchConfig().then(config => {
            if (didUnmount.current) return;
            pureDispatch({type: "ReloadConfig", config});
        });

        Sync.fetchServers().then(servers => {
            if (didUnmount.current) return;
            pureDispatch({type: "ReloadServers", servers});
        });
    }, [pureDispatch]);

    const requestJobReloader = useCallback((awaitUpdatingAttemptsRemaining: number = 40) => {
        if (jobReloaderTimeout.current !== -1) return;
        Sync.fetchServers().then(servers => {
            if (didUnmount.current) return;

            let isUpdating = false;
            for (const server of servers) {
                if (server.status.state !== "RUNNING") {
                    isUpdating = true;
                }
            }

            if (isUpdating || awaitUpdatingAttemptsRemaining > 0) {
                jobReloaderTimeout.current = window.setTimeout(() => {
                    if (didUnmount.current) return;
                    jobReloaderTimeout.current = -1;

                    requestJobReloader(isUpdating ? 0 : awaitUpdatingAttemptsRemaining - 1);
                }, awaitUpdatingAttemptsRemaining > 0 ? 500 : 3000);
            } else {
                pureDispatch({type: "ReloadServers", servers});
            }
        });
    }, []);

    const [permissionProblems, setPermissionProblems] = useState<string[]>([]);
    React.useEffect(() => {
        if (folders.length === 0) return;
        Promise.allSettled(folders.filter(it => it.ucloudPath != null).map(f => 
            callAPI(FilesApi.browse({path: f!.ucloudPath, itemsPerPage: 250}))
        )).then(promises => {
            const result: string[] = [];
            promises.forEach((p, index) => {
                if (p.status === "fulfilled") {
                    if (p.value.items.some(f => f.status.unixOwner !== 11042)) {
                        result.push(folders[index].id);
                    }
                }
            });
            setPermissionProblems(result);
        });
    }, [folders.length]);

    const actionCb: ActionCallbacks = useMemo(() => ({
        history,
        pureDispatch,
        requestReload: reload,
        requestJobReloader,
    }), [history, pureDispatch, reload]);

    const dispatch = useCallback((action: UIAction) => {
        onAction(uiState, action, actionCb);
        pureDispatch(action);
    }, [uiState, pureDispatch, actionCb]);

    const operationCb: OperationCallbacks = useMemo(() => ({
        history,
        dispatch,
        requestReload: reload,
        permissionProblems,
    }), [history, dispatch, reload, permissionProblems]);

    const openWizard = useCallback(() => {
        pureDispatch({type: "ReloadDeviceWizard", visible: true});
    }, [pureDispatch]);

    const closeWizard = useCallback(() => {
        pureDispatch({type: "ReloadDeviceWizard", visible: false});
    }, [pureDispatch]);

    const onDeviceAdded = useCallback((device: SyncthingDevice) => {
        dispatch({type: "AddDevice", device});
    }, [dispatch]);

    const openFileSelector = useCallback(() => {
        const pathRef = {current: ""};
        dialogStore.addDialog(
            <FilesBrowse
                browseType={BrowseType.Embedded}
                pathRef={pathRef}
                onSelectRestriction={file => file.status.type === "DIRECTORY" && file.specification.product.id !== "share"}
                onSelect={async (res) => {
                    const target = removeTrailingSlash(res.id === "" ? pathRef.current : res.id);
                    dispatch({type: "AddFolder", folderPath: target});
                    dialogStore.success();
                }}
            />,
            doNothing,
            true,
            FilesApi.fileSelectorModalStyle
        );
    }, [dispatch]);

    // Effects
    useEffect(() => reload(), [reload]);

    useEffect(() => {
        folderToggleSet.uncheckAll();
    }, [folders]);

    useEffect(() => {
        serverToggleSet.uncheckAll();
    }, [servers]);

    useEffect(() => {
        deviceToggleSet.uncheckAll();
    }, [devices]);

    useEffectSkipMount(() => {
        callAPI(Sync.api.updateConfiguration({
            providerId: "ucloud",
            config: {devices, folders}
        })).catch(() => reload());
    }, [folders.length, devices.length]);

    useTitle("File Synchronization");
    useRefreshFunction(reload);
    useSidebarPage(SidebarPages.Files);

    let main: JSX.Element;
    if (uiState.devices !== undefined && uiState.devices.length === 0) {
        main = <AddDeviceWizard onDeviceAdded={onDeviceAdded} onWizardClose={closeWizard}/>;
    } else {
        main = <OverviewStyle>
            {uiState.showDeviceWizard !== true ? null :
                <ReactModal
                    isOpen={true}
                    style={largeModalStyle}
                    shouldCloseOnEsc
                    ariaHideApp={false}
                    onRequestClose={closeWizard}
                >
                    <AddDeviceWizard onDeviceAdded={onDeviceAdded} onWizardClose={closeWizard}/>
                </ReactModal>
            }

            <TwoPanelLayout>
                <HighlightedCard
                    icon="hdd"
                    title="My Devices"
                    color="blue"
                    className="devices"
                    subtitle={<Flex>
                        <ExternalLink href="https://syncthing.net/downloads/" mr="8px">
                            <Button><Icon name="open" mr="4px" size="14px"/> Download Syncthing</Button>
                        </ExternalLink>
                        <Button onClick={openWizard}>Add Device</Button>
                    </Flex>}
                >
                    <Text color="darkGray" fontSize={1}>
                        UCloud can synchronize files to any of your devices which run Syncthing.
                        Download and install Syncthing to add one of your devices here.
                    </Text>

                    <List mt="16px">
                        {devices.map(it =>
                            <ItemRow
                                item={it}
                                key={it.deviceId}
                                browseType={BrowseType.Embedded}
                                renderer={DeviceRenderer}
                                operations={deviceOperations}
                                callbacks={operationCb}
                                toggleSet={deviceToggleSet}
                                itemTitle={"Device"}
                            />
                        )}
                    </List>
                </HighlightedCard>

                {uiState.folders !== undefined && folders.length === 0 ? null :
                    <HighlightedCard
                        className="servers"
                        icon="globeEuropeSolid"
                        title="Syncthing Servers"
                        color="blue"
                    >
                        <Text color="darkGray" fontSize={1}>
                            We synchronize your files from this server. Monitor the health of your servers here.
                        </Text>

                        {servers.length === 0 ? <>
                            <Box mt="16px">
                                <b>We are currently starting a Syncthing server for you. </b><br />
                                If nothing happens, then you should try reloading this page.
                            </Box>
                        </> : <>
                            <List mt="16px">
                                {servers.map(it =>
                                    <ItemRow
                                        item={it}
                                        key={it.id}
                                        browseType={BrowseType.Embedded}
                                        renderer={ServerRenderer}
                                        toggleSet={serverToggleSet}
                                        operations={serverOperations}
                                        callbacks={operationCb}
                                        itemTitle={"Server"}
                                    />
                                )}
                            </List>
                        </>}
                    </HighlightedCard>
                }
            </TwoPanelLayout>

            <HighlightedCard
                className="folders"
                icon="ftFolder"
                title="Synchronized Folders"
                color="blue"
                subtitle={<Button onClick={openFileSelector}>Add Folder</Button>}
            >
                <Text color="darkGray" fontSize={1}>
                    These are the files which will be synchronized to your devices.
                    Add a new folder to start synchronizing data.
                </Text>

                {uiState.folders !== undefined && folders.length === 0 ?
                    <EmptyFolders onAddFolder={openFileSelector}/> :
                    <>
                        {uiState.didAddFolder ? <EmptyFolders didAdd onAddFolder={openFileSelector}/> : null}
                        <List mt="16px">
                            {folders.map(it =>
                                <ItemRow
                                    item={it}
                                    key={it.ucloudPath}
                                    browseType={BrowseType.Embedded}
                                    renderer={FolderRenderer}
                                    toggleSet={folderToggleSet}
                                    operations={folderOperations}
                                    callbacks={operationCb}
                                    itemTitle={"Folder"}
                                    disableSelection
                                />
                            )}
                        </List>
                    </>
                }
            </HighlightedCard>
        </OverviewStyle>;
    }

    return <MainContainer main={main}/>;
};

// Secondary interface
// ================================================================================
const DeviceRenderer: ItemRenderer<SyncthingDevice> = {
    Icon: ({size}) => {
        return <Icon name="cubeSolid" size={size} />
    },

    MainTitle: ({resource}) => {
        if (!resource) return null;
        return <>{resource.label}</>;
    },

    ImportantStats: ({resource}) => {
        if (!resource) return null;

        const doCopyId = useCallback((e: React.SyntheticEvent) => {
            e.stopPropagation();
            copyToClipboard({ value: resource.deviceId, message: "Device ID copied to clipboard!" });
        }, [resource.deviceId]);

        const trigger = <DeviceBox onClick={doCopyId}><code>{resource.deviceId.split("-")[0]}</code></DeviceBox>;
        return <Tooltip trigger={trigger}>Copy to clipboard</Tooltip>;
    }
};

const deviceOperations: Operation<SyncthingDevice, OperationCallbacks>[] = [
    {
        text: "Copy device ID",
        icon: "id",
        enabled: selected => selected.length === 1,
        onClick: ([device]) => {
            copyToClipboard({ value: device.deviceId, message: "Device ID copied to clipboard!" });
        },
    },
    {
        text: "Remove",
        icon: "trash",
        color: "red",
        confirm: true,
        enabled: selected => selected.length > 0,
        onClick: (selected, cb) => {
            for (const device of selected) {
                cb.dispatch({type: "RemoveDevice", deviceId: device.deviceId});
            }
        },
    }
];

const FolderRenderer: ItemRenderer<SyncthingFolder> = {
    Icon: ({size}) => {
        return <FtIcon fileIcon={{type: "DIRECTORY", ext: ""}} size={size}/>;
    },

    MainTitle: ({resource, callbacks}) => {
        if (!resource) return null;
        const prettyPath = usePrettyFilePath(resource?.ucloudPath ?? "/");
        return <Text cursor="pointer" onClick={() => {
            const path = resource.ucloudPath;
            callbacks.history.push(buildQueryString("/files", {path}));
        }}>{fileName(prettyPath)}</Text>;
    },

    Stats: ({resource}) => {
        // TODO Caching is currently triggering way too many requests. We should fix this in usePrettyFilePath!
        const prettyPath = usePrettyFilePath(resource?.ucloudPath ?? "/");
        if (!resource) return null;
        return <>
            <ListRowStat>{prettyPath}</ListRowStat>
        </>
    },

    ImportantStats: ({resource, callbacks}) => {
        if (resource == null) return null;
        if (!callbacks.permissionProblems.includes(resource.id)) return null;

        const prettyPath = usePrettyFilePath(resource?.ucloudPath ?? "/");
        return <>
            <Tooltip tooltipContentWidth="200px" trigger={
                <Icon name="warning" color="red" />
            }>
                Some files in {prettyPath} might not be synchronized due to lack of permissions.
            </Tooltip>
        </>
    }
};

const folderOperations: Operation<SyncthingFolder, OperationCallbacks>[] = [
    {
        text: "Remove from Sync",
        icon: "trash",
        color: "red",
        confirm: true,
        enabled: selected => selected.length >= 1,
        onClick: (selected, cb) => {
            for (const file of selected) {
                cb.dispatch({type: "RemoveFolder", folderPath: file.ucloudPath});
            }
        }
    }
];

const ServerRenderer: ItemRenderer<Job> = {
    Icon: ({resource, size}) => {
        if (!resource) return null;
        return <AppToolLogo type="APPLICATION" name={resource.specification.application.name} size={size}/>;
    },

    MainTitle: ({resource}) => {
        if (!resource) return null;
        const rawTitle = resource.specification.name ?? `Server (${resource.id})`;
        const title = removePrefixFrom("Syncthing ", rawTitle);
        return <>{title}</>
    },

    ImportantStats: ({resource}) => {
        if (!resource) return null;
        const status = resource.status.state ?? "IN_QUEUE";
        switch (status) {
            case "IN_QUEUE":
            case "EXPIRED":
            case "SUSPENDED":
            case "CANCELING": {
                return <Flex alignItems="center">
                    <Spinner/>
                    <Box ml="8px">Updating...</Box>
                </Flex>;
            }

            case "RUNNING": {
                return <Flex alignItems="center">
                    <Icon name="check" color="green"/>
                    <Box ml="8px">Running</Box>
                </Flex>
            }

            case "SUCCESS":
            case "FAILURE": {
                return <Flex alignItems="center">
                    <Icon name="pauseSolid" color="purple"/>
                    <Box ml="8px">Paused</Box>
                </Flex>;
            }
        }
    }
};

const serverOperations: Operation<Job, OperationCallbacks>[] = [
    {
        text: "Open interface",
        icon: "open",
        enabled: selected => selected.length === 1,
        onClick: ([job]) => {
            const element = document.createElement("a");
            element.setAttribute("href", `/app/applications/web/${job.id}/0?hide-frame`);
            element.setAttribute("target", "_blank");
            element.style.display = "none";
            document.body.appendChild(element);
            element.click();
            document.body.removeChild(element);
        }
    },
    {
        text: "Show device ID",
        icon: "id",
        enabled: selected => selected.length === 1,
        onClick: ([job], cb) => {
            const path = job.specification.parameters["stateFolder"]?.["path"];
            if (path && typeof path === "string") {
                cb.history.push(`/files/properties/${encodeURIComponent(`${path}/ucloud_device_id.txt`)}`);
            }
        }
    },
    {
        text: "View logs",
        icon: "fileSignatureSolid",
        enabled: selected => selected.length === 1,
        onClick: ([job], cb) => {
            cb.history.push(`/jobs/properties/${job.id}`);
        }
    },
    {
        text: "Restart",
        icon: "refresh",
        enabled: selected => selected.length === 1,
        onClick: (_, cb) => {
            cb.dispatch({type: "ExpectServerUpdate"});
            callAPIWithErrorHandler(Sync.api.restart({providerId: "ucloud"}));
        }
    },
    {
        text: "Factory reset",
        icon: "trash",
        confirm: true,
        color: "red",
        enabled: selected => selected.length === 1,
        onClick: (_, cb) => {
            dialogStore.addDialog(
                <Box maxWidth={600}>
                    <Heading.h3>Factory reset Syncthing server?</Heading.h3>
                    <p>
                        This will reset the configuration of the Syncthing server completely.
                        You probably only want to do this if Syncthing is not working, and/or your configuration is broken.
                    </p>
                    <p>
                         All folders and devices will be removed from the Syncthing server.
                    </p>
                    <p>
                         The device ID will no longer be available and should be removed from your local Syncthing devices.
                         A new device ID will be generated if you decide to set up Synchronization again.
                    </p>
                    <Button mr="5px" onClick={() => dialogStore.success()}>Cancel</Button>
                    <Button color="red" onClick={() => {
                        cb.dispatch({type: "ResetAll"});
                        dialogStore.success();
                    }}>Confirm</Button>
                </Box>, () => undefined, true);
        }
    },
];

const EmptyFolders: React.FunctionComponent<{
    didAdd?: boolean;
    onAddFolder: () => void;
}> = ({onAddFolder, didAdd}) => {
    return <>
        {didAdd ? null :
            <p>
                Now that UCloud knows about your device, you will be able to add folders on UCloud to
                synchronization.
            </p>
        }

        {!didAdd ? null :
            <p>
                We are now applying your changes. To finish the configuration, you must accept the share from the
                Syncthing application installed on your device.
            </p>
        }

        <TutorialList>
            {didAdd ? null :
                <li>
                    <p><b>Mark a folder for synchronization</b></p>

                    <Flex justifyContent="center">
                        <Button onClick={onAddFolder}>Add Folder</Button>
                    </Flex>
                </li>
            }

            <li>
                <Flex>
                    <Box flexGrow={1}>
                        <p><b>Open Syncthing</b></p>
                        <p>
                            A pop-up will appear, saying that UCloud wants to connect.
                            Click the <i>Add device</i> button, then <i>Save</i> in the window that appears.
                        </p>
                    </Box>
                    <Box pl={40}>
                        <Screenshot src={syncthingScreen2}/>
                    </Box>
                </Flex>
            </li>

            <li>
                <Flex>
                    <Box flexGrow={1}>
                        <p><b>A new pop-up will appear</b></p>

                        <p>
                            This states that UCloud wants to share a folder with you. Click <i>Add</i>, then select
                            where you want Syncthing to synchronize the files to on your machine by changing{" "}
                            <i>Folder Path</i>, then press <i>Save</i>.
                        </p>
                    </Box>

                    <Box pl={40}>
                        <Screenshot src={syncthingScreen3}/>
                    </Box>
                </Flex>
            </li>

            <li>
                <p><b>Synchronization should start within a few seconds of clicking <i>Add</i></b></p>
            </li>
        </TutorialList>

        <p>
            For more details see the{" "}
            <ExternalLink href="https://docs.cloud.sdu.dk/guide/syncthing.html">
                UCloud documentation
            </ExternalLink>.
        </p>
    </>
};

const AddDeviceWizard: React.FunctionComponent<{
    onDeviceAdded: (device: SyncthingDevice) => void;
    onWizardClose: () => void;
}> = (props) => {
    const STEP_INTRO = 0;
    const STEP_ADD_DEVICE = 1;

    const STEP_LAST = STEP_ADD_DEVICE;

    const [tutorialStep, setTutorialStep] = useState(0);

    const deviceNameRef = useRef<HTMLInputElement>(null);
    const [deviceNameError, setDeviceNameError] = useState<string | null>(null);
    const deviceIdRef = useRef<HTMLInputElement>(null);
    const [deviceIdError, setDeviceIdError] = useState<string | null>(null);

    const tutorialNext = useCallback((e?: React.SyntheticEvent) => {
        e?.preventDefault();
        let hasErrors = false;

        if (tutorialStep === STEP_ADD_DEVICE) {
            const deviceName = (deviceNameRef.current?.value ?? "").trim();
            const deviceId = (deviceIdRef.current?.value ?? "").trim();

            if (deviceName.length === 0) {
                setDeviceNameError(
                    "You need to set a device name. This can any name to help you remember which device this is. " +
                    "For example: 'Work Phone'."
                );
                hasErrors = true;
            } else {
                setDeviceNameError(null);
            }

            {
                let deviceIdValid = true;
                const deviceSplit = deviceId.split("-");
                if (deviceSplit.length !== 8) {
                    deviceIdValid = false;
                }

                if (!deviceSplit.every(chunk => chunk.length === 7)) {
                    deviceIdValid = false;
                }

                if (!deviceIdValid) {
                    setDeviceIdError(
                        "The device ID you specified doesn't look valid. " +
                        "Make sure you follow the steps described above to locate your device ID."
                    );
                    hasErrors = true;
                } else {
                    setDeviceIdError(null);
                }
            }

            if (!hasErrors) {
                props.onDeviceAdded({deviceId, label: deviceName});
                setTutorialStep(prev => prev + 1);
            }
        }

        if (!hasErrors) {
            if (tutorialStep === STEP_LAST) {
                props.onWizardClose();
            } else {
                setTutorialStep(prev => prev + 1);
            }
        }
    }, [tutorialStep, props.onDeviceAdded, props.onWizardClose]);

    const tutorialPrevious = useCallback(() => {
        setTutorialStep(prev => prev - 1);
    }, []);

    let tutorialContent: JSX.Element = <></>;
    switch (tutorialStep) {
        case STEP_INTRO: {
            tutorialContent = (
                <>
                    <Heading.h3>Installing Syncthing</Heading.h3>
                    <Box borderRadius="6px" backgroundColor="orange" color="white" p={16} mt={16}>
                        The synchronization feature is experimental. Please report any errors through the Support Form.
                    </Box>
                    <p>
                        The synchronization feature allows you to synchronize folders between UCloud <i>and</i> your
                        devices. Changes you make on UCloud are automatically synchronized to your devices and vice
                        versa.
                    </p>
                    <p>
                        For synchronization to work, you need to install the 3rd-party tool{" "}
                        <ExternalLink href="https://syncthing.net">Syncthing</ExternalLink>.
                    </p>
                    <TutorialList>
                        <li>
                            <div><b>Download and install Syncthing for your platform</b></div>

                            <Flex justifyContent="center" mt="8px">
                                <ExternalLink href="https://syncthing.net/downloads/">
                                    <Button><Icon name="open" mr="4px" size="14px"/> Download Syncthing</Button>
                                </ExternalLink>
                            </Flex>
                        </li>
                        <li>
                            <b>Open the Syncthing application</b><br/><br/>

                            If you are using a desktop PC/laptop then your window should now look like this:<br/>

                            <Flex justifyContent="center" mt="8px">
                                <Screenshot src={syncthingScreen4}/>
                            </Flex>
                        </li>
                    </TutorialList>
                </>
            );
            break;
        }

        case STEP_ADD_DEVICE: {
            tutorialContent = (
                <>
                    <Heading.h3>Adding devices</Heading.h3>

                    <p>
                        Now that Syncthing is installed on your device, UCloud need to know the Device ID generated
                        by Syncthing to be able to synchronize your files.
                    </p>

                    <Flex>
                        <TutorialList>
                            <li>Open Syncthing</li>
                            <li>
                                In the <i>Actions</i> menu in the top-right corner, click the <i>Show ID</i> button:
                            </li>
                            <li>
                                A window containing your Device ID, as well as a QR code will appear.<br/>
                                Copy the Device ID and paste it into the field below:
                            </li>
                        </TutorialList>

                        <Box ml={"auto"}>
                            <Screenshot src={syncthingScreen1}/>
                        </Box>
                    </Flex>

                    <form onSubmit={tutorialNext}>
                        <Label>
                            Device Name
                            <Input ref={deviceNameRef} placeholder={"My phone"} error={deviceNameError !== null}/>
                            {!deviceNameError ?
                                <Text color="gray">
                                    A name to help you remember which device this is. For example: "Work phone".
                                </Text> :
                                <Text color="red">{deviceNameError}</Text>
                            }
                        </Label>

                        <Label mt="8px">
                            My Device ID
                            <Input
                                ref={deviceIdRef}
                                placeholder="XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX"
                                error={deviceIdError !== null}
                            />
                            {!deviceIdError ? null :
                                <Text color="red">{deviceIdError}</Text>
                            }
                        </Label>

                        {/*
                            NOTE(Dan): I am pretty sure this isn't actually needed, but I can't make it work without
                            atm.
                        */}
                        <button type={"submit"} style={{display: "none"}}>Next step</button>
                    </form>
                </>
            );
            break;
        }
    }

    return <Flex flexDirection="column" height="100%">
        <Box flexGrow={1}>{tutorialContent}</Box>
        <Flex mt={30}>
            <Box marginLeft={"auto"}>
                {tutorialStep < 1 ? null : (
                    <Button onClick={tutorialPrevious}>Previous step</Button>
                )}
                <Button marginLeft={10} onClick={tutorialNext}>
                    {tutorialStep === 2 ? "Done" : "Next step"}
                </Button>
            </Box>
        </Flex>
    </Flex>;
};

// Styling
// ================================================================================
const OverviewStyle = styled.div`
    .row-left {
        max-width: unset;
    }
`;

const TwoPanelLayout = styled.div`
  display: flex;
  flex-flow: row wrap;

  margin-bottom: 16px;
  gap: 16px;

  & > * {
    flex-basis: 100%;
  }

  @media all and (min-width: 1000px) {
    & > * {
      flex-basis: 400px;
    }

    .devices {
      order: 1;
      flex-grow: 2;
    }

    .servers {
      order: 2;
      flex-grow: 1;
    }
  }
`;

const TutorialList = styled.ol`
  padding-top: 0.5em;

  & > li {
    padding: 0 0 1.5em 0.5em;
  }
`;

const Screenshot = styled(Image)`
    border: 3px solid var(--gray);
    max-height: 250px;
`;

const DeviceBox = styled(NoSelect)`
    cursor: pointer;
`;

export default Overview;

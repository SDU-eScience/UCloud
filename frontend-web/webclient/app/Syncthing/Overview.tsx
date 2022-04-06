import * as React from "react";
import { useHistory } from "react-router";
import { useRef, useReducer, useCallback, useEffect, useMemo, useState } from "react";
import { AppToolLogo } from "@/Applications/AppToolLogo";
import { useTitle, useLoading } from "@/Navigation/Redux/StatusActions";
import { ItemRenderer, ItemRow } from "@/ui-components/Browse";
import { default as ReactModal } from "react-modal";
import { useToggleSet } from "@/Utilities/ToggleSet";
import { BrowseType } from "@/Resource/BrowseType";
import { useRefreshFunction } from "@/Navigation/Redux/HeaderActions";
import { SidebarPages, useSidebarPage } from "@/ui-components/Sidebar";
import { Label, Input, Image, Box, Flex, Grid, Icon, Text, Button, ExternalLink, FtIcon, List } from "@/ui-components";
import MainContainer from "@/MainContainer/MainContainer";
import HighlightedCard from "@/ui-components/HighlightedCard";
import styled from "styled-components";
import { History } from "history";
import * as Heading from "@/ui-components/Heading";
import { SyncthingConfig, SyncthingDevice, SyncthingFolder } from "./api";
import * as Sync from "./api";
import { Job } from "@/UCloud/JobsApi";
import { removePrefixFrom } from "@/Utilities/TextUtilities";
import { usePrettyFilePath } from "@/Files/FilePath";
import { fileName } from "@/Utilities/FileUtilities";
import { ListRowStat } from "@/ui-components/List";
import { useDidUnmount } from "@/Utilities/ReactUtilities";
import { Operation } from "@/ui-components/Operation";
import { deepCopy } from "@/Utilities/CollectionUtilities";
import { largeModalStyle } from "@/Utilities/ModalUtilities";
import { dialogStore } from "@/Dialog/DialogStore";
import { FilesBrowse } from "@/Files/Files";
import { api as FilesApi } from "@/UCloud/FilesApi";
import { randomUUID, doNothing, removeTrailingSlash, useEffectSkipMount } from "@/UtilityFunctions";
import Spinner from "@/LoadingIcon/LoadingIcon";

import syncthingScreen1 from "@/Assets/Images/syncthing/syncthing-1.png";
import syncthingScreen2 from "@/Assets/Images/syncthing/syncthing-2.png";
import syncthingScreen3 from "@/Assets/Images/syncthing/syncthing-3.png";
import {buildQueryString} from "@/Utilities/URIUtilities";
import {callAPI} from "@/Authentication/DataHook";

// UI state management
// ================================================================================
type UIAction = 
    ReloadConfig | ReloadServers | ReloadDeviceWizard |
    RemoveDevice | RemoveFolder |
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
    devices?: SyncthingDevice[];
    folders?: SyncthingFolder[];
    servers?: Job[];
    showDeviceWizard?: boolean;
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
            folders.push({ id: randomUUID(), ucloudPath: action.folderPath });
            copy.folders = folders;
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
    }
}

async function onAction(state: UIState, action: UIAction, cb: ActionCallbacks): Promise<void> {
    state; // TODO(Dan): I forgot how to supress the unused warning

    switch(action.type) {
        case "RemoveFolder":
        case "RemoveDevice":
        case "AddFolder":
        case "AddDevice": {
            cb.pureDispatch({ type: "ExpectServerUpdate" });
            break;
        }

    }
}

interface ActionCallbacks {
    history: History;
    pureDispatch: (action: UIAction) => void;
    requestReload: () => void; // NOTE(Dan): use when it is difficult to rollback a change
}

interface OperationCallbacks {
    history: History;
    dispatch: (action: UIAction) => void;
    requestReload: () => void;
}

// Primary user interface
// ================================================================================
export const Overview: React.FunctionComponent = () => {
    // Input "parameters"
    const history = useHistory();

    // UI state
    let loading = false;
    const [uiState, pureDispatch] = useReducer(uiReducer, {});
    const folderToggleSet = useToggleSet([]);
    const serverToggleSet = useToggleSet([]);
    const didUnmount = useDidUnmount();

    const devices = uiState?.devices ?? [];
    const folders = uiState?.folders ?? [];
    const servers = uiState?.servers ?? [];

    // UI callbacks and state manipulation
    const reload = useCallback(() => {
        Sync.fetchConfig().then(config => {
            if (didUnmount.current) return;
            pureDispatch({ type: "ReloadConfig", config });
        });

        Sync.fetchServers().then(servers => {
            if (didUnmount.current) return;
            pureDispatch({ type: "ReloadServers", servers });
        });
    }, [pureDispatch]);

    const actionCb: ActionCallbacks = useMemo(() => ({
        history,
        pureDispatch,
        requestReload: reload
    }), [history, pureDispatch, reload]);

    const dispatch = useCallback((action: UIAction) => {
        onAction(uiState, action, actionCb);
        pureDispatch(action);
    }, [uiState, pureDispatch, actionCb]);

    const operationCb: OperationCallbacks = useMemo(() => ({
        history,
        dispatch,
        requestReload: reload
    }), [history, dispatch, reload]);

    const openWizard = useCallback(() => {
        pureDispatch({ type: "ReloadDeviceWizard", visible: true });
    }, [pureDispatch]);

    const closeWizard = useCallback(() => {
        pureDispatch({ type: "ReloadDeviceWizard", visible: false });
    }, [pureDispatch]);

    const onDeviceAdded = useCallback((device: SyncthingDevice) => {
        dispatch({ type: "AddDevice", device });
    }, [dispatch]);

    const onRemoveDevice = useCallback((deviceId: string) => {
        dispatch({ type: "RemoveDevice", deviceId });
    }, [dispatch]);
    
    const openFileSelector = useCallback(() => {
        const pathRef = { current: "" };
        dialogStore.addDialog(
            <FilesBrowse
                browseType={BrowseType.Embedded}
                pathRef={pathRef}
                onSelectRestriction={file => file.status.type === "DIRECTORY"}
                onSelect={async (res) => {
                    const target = removeTrailingSlash(res.id === "" ? pathRef.current : res.id);
                    dispatch({ type: "AddFolder", folderPath: target });
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

    useEffectSkipMount(() => {
        callAPI(Sync.api.updateConfiguration({
            providerId: "ucloud",
            config: { devices, folders }
        })).catch(() => reload());
    }, [folders.length, devices.length]);

    useTitle("File Synchronization");
    useRefreshFunction(reload);
    useSidebarPage(SidebarPages.Files);
    useLoading(loading);

    return <MainContainer
        main={<>
            {uiState.showDeviceWizard !== true ? null :
                <ReactModal
                    isOpen={true}
                    style={largeModalStyle}
                    shouldCloseOnEsc
                    ariaHideApp={false}
                    onRequestClose={closeWizard}
                >
                    <AddDeviceWizard onDeviceAdded={onDeviceAdded} onWizardClose={closeWizard} />
                </ReactModal>
            }

            <HighlightedCard
                icon="hdd"
                title="My Devices"
                color="blue"
                subtitle={<Flex>
                    <ExternalLink href="https://syncthing.net/downloads/" mr="8px">
                        <Button><Icon name="open" mr="4px" size="14px" /> Download Syncthing</Button>
                    </ExternalLink>
                    <Button onClick={openWizard}>New Device</Button>
                </Flex>}
            >
                <Text color="darkGray" fontSize={1}>
                    UCloud can synchronize files to any of your devices which run Syncthing.
                    Download and install Syncthing to add one of your devices here.
                </Text>

                <Grid gridTemplateColumns={"repeat(auto-fit, 300px)"} gridGap="16px" mt="16px" mb="16px">
                    {devices.map(it => <Device device={it} onRemove={onRemoveDevice} key={it.deviceId}/>)}
                </Grid>
            </HighlightedCard>

            <TwoPanelLayout>
                <HighlightedCard
                    className="servers"
                    icon="globeEuropeSolid"
                    title="Syncthing Servers"
                    color="blue"
                >
                    <Text color="darkGray" fontSize={1}>
                        We synchronize your files from this server. Monitor the health of your servers here.
                    </Text>

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

                </HighlightedCard>

                <HighlightedCard
                    className="folders"
                    icon="ftFolder"
                    title="Synchronized Folders"
                    color="blue"
                    subtitle={<>
                        <Button onClick={openFileSelector}>New Folder</Button>
                    </>}
                >
                    <Text color="darkGray" fontSize={1}>
                        These are the files which will be synchronized to your devices.
                        Add a new folder to start synchronizing data.
                    </Text>

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
                            />
                        )}
                    </List>
                </HighlightedCard>
            </TwoPanelLayout>

        </>}
    />;
};

// Secondary interface
// ================================================================================
const Device: React.FunctionComponent<{
    device: SyncthingDevice;
    onRemove: (deviceId: string) => void;
}> = ({device, onRemove}) => {
    const deviceIdComponents = device.deviceId.split("-");
    const deviceIdPretty: string[] = [];
    const cachedOnRemove = useCallback(() => {
        onRemove(device.deviceId);
    }, [onRemove, device.deviceId]);

    for (let i = 0; i < deviceIdComponents.length; i += 2) {
        const thisPart = deviceIdComponents[i];
        const nextPart = (i + 1) < deviceIdComponents.length ? deviceIdComponents[i + 1] : null;
        if (nextPart) {
            deviceIdPretty.push(`${thisPart}-${nextPart}`);
        } else {
            deviceIdPretty.push(thisPart);
        }
    }

    return <DeviceWrapper>
        <HighlightedCard color="blue">
            <div className="device-spacer">
                <Heading.h3>{device.label}</Heading.h3>
                <Icon name="trash" color="red" cursor="pointer" onClick={cachedOnRemove} />
            </div>

            <Text color="darkGray" fontSize={1} className="device-id">
                {deviceIdPretty.map((part, idx) => 
                    <React.Fragment key={idx}>
                        {part}
                        <br />
                    </React.Fragment>
                )}
            </Text>
        </HighlightedCard>
    </DeviceWrapper>;
};

const FolderRenderer: ItemRenderer<SyncthingFolder> = {
    Icon: ({size}) => {
        return <FtIcon fileIcon={{type: "DIRECTORY", ext: ""}} size={size} />;
    },

    MainTitle: ({resource}) => {
        if (!resource) return null;
        return <>{fileName(resource.ucloudPath)}</>;
    },

    Stats: ({resource}) => {
        // TODO Caching is currently triggering way too many requests. We should fix this in usePrettyFilePath!
        const prettyPath = usePrettyFilePath(resource?.ucloudPath ?? "/");
        if (!resource) return null;
        return <>
            <ListRowStat>{prettyPath}</ListRowStat>
        </>
    }
};

const folderOperations: Operation<SyncthingFolder, OperationCallbacks>[] = [
    {
        text: "Open folder",
        icon: "open",
        enabled: selected => selected.length === 1,
        onClick: ([file], cb) => {
            const path = file.ucloudPath;
            cb.history.push(buildQueryString("/files", { path }));
        }
    },
    {
        text: "Remove",
        icon: "trash",
        color: "red",
        confirm: true,
        enabled: selected => selected.length >= 1,
        onClick: (selected, cb) => {
            for (const file of selected) {
                cb.dispatch({ type: "RemoveFolder", folderPath: file.ucloudPath });
            }
        }
    }
];

const ServerRenderer: ItemRenderer<Job> = {
    Icon: ({resource, size}) => {
        if (!resource) return null;
        return <AppToolLogo type="APPLICATION" name={resource.specification.application.name} size={size} />;
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
            case "CANCELING": {
                return <Flex alignItems="center">
                    <Spinner />
                    <Box ml="8px">Updating...</Box>
                </Flex>;
            }

            case "RUNNING": {
                return <Flex alignItems="center">
                    <Icon name="check" color="green" />
                    <Box ml="8px">Running</Box>
                </Flex>
            }

            case "SUCCESS":
            case "FAILURE": {
                return <Flex alignItems="center">
                    <Icon name="pauseSolid" color="purple" />
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
        onClick: doNothing
    },
    {
        text: "Show device ID",
        icon: "id",
        enabled: selected => selected.length === 1,
        onClick: doNothing
    },
    {
        text: "View logs",
        icon: "fileSignatureSolid",
        enabled: selected => selected.length === 1,
        onClick: doNothing
    },
    {
        text: "Restart",
        icon: "refresh",
        enabled: selected => selected.length === 1,
        onClick: (_, cb) => {
            cb.dispatch({ type: "ExpectServerUpdate" });
        }
    },
    {
        text: "Pause",
        icon: "pauseSolid",
        enabled: selected => selected.length === 1 && 
            selected[0].status.state !== "SUCCESS" &&
            selected[0].status.state !== "FAILURE",
        onClick: (selected, cb) => {
            for (const server of selected) {
                cb.dispatch({ type: "ExpectServerPause", serverId: server.id });
            }
        }
    },
    {
        text: "Factory reset",
        icon: "trash",
        confirm: true,
        color: "red",
        enabled: selected => selected.length === 1,
        onClick: doNothing
    },
];

const AddDeviceWizard: React.FunctionComponent<{
    onDeviceAdded: (device: SyncthingDevice) => void;
    onWizardClose: () => void;
}> = (props) => {
    const STEP_INTRO = 0;
    const STEP_ADD_DEVICE = 1;
    const STEP_ACCEPT_DEVICE = 2;

    const STEP_LAST = STEP_ACCEPT_DEVICE;

    const [tutorialStep, setTutorialStep] = useState(0);

    const deviceNameRef = useRef<HTMLInputElement>(null);
    const [deviceNameError, setDeviceNameError] = useState<string | null>(null);
    const deviceIdRef = useRef<HTMLInputElement>(null);
    const [deviceIdError, setDeviceIdError] = useState<string | null>(null);

    const tutorialNext = useCallback(() => {
        if (tutorialStep === STEP_ADD_DEVICE) {
            let hasErrors = false;
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
                props.onDeviceAdded({ deviceId, label: deviceName });
                setTutorialStep(prev => prev + 1);
            }
        } else if (tutorialStep === STEP_LAST) {
            props.onWizardClose();
        } else {
            setTutorialStep(prev => prev + 1);
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
                    <Heading.h3>Synchronization Tutorial - Installing Syncthing</Heading.h3>
                    <Box borderRadius="6px" backgroundColor="orange" color="white" p={16} mt={16}>
                        The synchronization feature is experimental. Please report any errors through the Support Form.
                    </Box>
                    <p>
                        The synchronization feature allows you to synchronize folders between UCloud and your devices.
                    </p>
                    <p>
                        For synchronization to work, you need to install the 3rd-party tool{" "}
                        <ExternalLink href="https://syncthing.net">Syncthing</ExternalLink>.
                    </p>
                    <TutorialList>
                        <li>
                            Download Syncthing for your platform from{" "}
                            <ExternalLink href="https://syncthing.net/downloads/">
                                https://syncthing.net/downloads/
                            </ExternalLink>{" "}
                            or from the package manager on your system.
                        </li>
                        <li>
                            Install Syncthing on your device. See the{" "}
                            <ExternalLink href="https://docs.cloud.sdu.dk/guide/syncthing.html">
                                UCloud documentation
                            </ExternalLink>{" "}
                            for details.
                        </li>
                        <li>
                            Once Syncthing is installed and running on your device, you should be able to access the 
                            Syncthing user interface from{" "}
                            <ExternalLink href="http://localhost:8384/">http://localhost:8384/</ExternalLink>.
                        </li>
                    </TutorialList>
                </>
            );
            break;
        }

        case STEP_ADD_DEVICE: {
            tutorialContent = (
                <>
                    <Heading.h3>Synchronization Tutorial - Adding devices</Heading.h3>

                    <p>
                        Now that Syncthing is installed on your device, UCloud need to know the Device ID generated 
                        by Syncthing to be able to synchronize your files.
                    </p>

                    <Flex>
                        <TutorialList>
                            <li>
                                Go to the Syncthing user interface:{" "}
                                <ExternalLink href="http://localhost:8384/">http://localhost:8384/</ExternalLink>.
                            </li>
                            <li>
                                In the <i>Actions</i> menu in the top-right corner, click the <i>Show ID</i> button:
                            </li>
                            <li>
                                A window containing your Device ID, as well as a QR code will appear.<br />
                                Copy the Device ID and paste it into the field below:
                            </li>
                        </TutorialList>

                        <Box ml={"auto"}>
                            <Image src={syncthingScreen1} />
                        </Box>
                    </Flex>

                    <Label>
                        Device Name
                        <Input ref={deviceNameRef} placeholder={"My phone"} error={deviceNameError !== null} />
                        {!deviceNameError ? null : 
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
                </>
            );
            break;
        }

        case STEP_ACCEPT_DEVICE: {
            tutorialContent = (
                <>
                    <Heading.h3>Synchronization Tutorial - Synchronize folder</Heading.h3>
                    <p>
                        Now that UCloud knows about your device, you will be able to add folders on UCloud to 
                        synchronization.
                    </p>

                    <TutorialList>
                        <li>
                            Toggle synchronization of your folder
                        </li>
                        <li>
                            <Flex>
                                <p>
                                    Go to the Syncthing User Interface
                                    at <ExternalLink href="http://localhost:8384/">http://localhost:8384/</ExternalLink>.
                                    A pop-up will appear, saying that UCloud wants to connect. Click the 
                                    <i>Add device</i> button, then <i>Save</i> in the window that appears.
                                </p>
                                <Box ml={"auto"} pl={40} width="80%">
                                    <Image src={syncthingScreen2} />
                                </Box>
                            </Flex>
                        </li>

                        <li>
                            <Flex>
                                <p>
                                    A new pop-up will appear, saying that UCloud wants to
                                    share a folder with you. Click <i>Add</i>, then select
                                    where you want Syncthing to synchronize the files to
                                    on your machine by changing <i>Folder Path</i>, then
                                    press <i>Save</i>.
                                </p>
                                <Box ml={"auto"} pl={40} width="100%">
                                    <Image src={syncthingScreen3} />
                                </Box>
                            </Flex>
                        </li>
                    </TutorialList>

                    <p>
                        Synchronization should start within a few seconds and changes to files in the folder will be 
                        synchronized automatically.
                    </p>

                    <p>
                        For more details see the{" "}
                        <ExternalLink href="https://docs.cloud.sdu.dk/guide/syncthing.html">
                            UCloud documentation
                        </ExternalLink>.
                    </p>
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
const DeviceWrapper = styled.div`
    width: 300px;
    text-align: center;

    .device-spacer {
        display: flex;
    }

    h3 {
        flex-grow: 1;
    }

    .device-id {
        font-family: "Jetbrains Mono";
        width: calc(100% - 32px);
    }

    svg {
        display: absolute;
    }
`;
// TODO(Dan): Why do we not have a way of referencing the monospace font of the theme?

const TwoPanelLayout = styled.div`
    display: flex;
    flex-flow: row wrap;

    margin-top: 16px;
    gap: 16px;

    & > * {
        flex-basis: 100%;
    }
    
    @media all and (min-width: 1000px) {
        & > * {
            flex-basis: 400px;
        }

        .folders {
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
        max-width: 1000px;
    }
`;

export default Overview;


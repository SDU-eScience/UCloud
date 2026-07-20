import * as React from "react";
import {useLocation, useNavigate} from "react-router-dom";
import {useRef, useReducer, useCallback, useEffect, useState} from "react";
import {usePage} from "@/Navigation/Redux";
import {default as ReactModal} from "react-modal";
import {Label, Input, Image, Icon, Text, Button, ExternalLink, Box, Flex} from "@/ui-components";
import {IconButton} from "@/ui-components/IconButton";
import {CopyButton, IconActionButton} from "@/ui-components/CopyButton";
import {TooltipV2} from "@/ui-components/Tooltip";
import MainContainer from "@/ui-components/MainContainer";
import {SyncthingConfig, SyncthingDevice, SyncthingFolder} from "./api";
import * as Sync from "./api";
import JobsApi, {JobState} from "@/UCloud/JobsApi";
import {prettyFilePath} from "@/Files/FilePath";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {deepCopy} from "@/Utilities/CollectionUtilities";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {dialogStore} from "@/Dialog/DialogStore";
import {api as FilesApi, downloadFileContent, findSensitivity} from "@/UCloud/FilesApi";
import {randomUUID, doNothing, removeTrailingSlash, copyToClipboard} from "@/UtilityFunctions";
import {getQueryParam} from "@/Utilities/URIUtilities";
import {callAPI, callAPIWithErrorHandler} from "@/Authentication/DataHook";
import * as UCloud from "@/UCloud";
import syncthingScreen1 from "@/Assets/Images/syncthing/syncthing-1.png";
import syncthingScreen4 from "@/Assets/Images/syncthing/syncthing-4.png";
import syncthingLogo from "@/Assets/Images/syncthing/logo.png";

import {injectStyle, injectStyleSimple} from "@/Unstyled";
import FileBrowse from "@/Files/FileBrowse";
import {CardClass} from "@/ui-components/Card";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import AppRoutes from "@/Routes";
import FileCollectionsApi, {FileCollection} from "@/UCloud/FileCollectionsApi";
import {useProjectId} from "@/Project/Api";
import {Client} from "@/Authentication/HttpClientInstance";
import {PageV2} from "@/UCloud";
import {addStandardDialog} from "@/UtilityComponents";
import {sendFailureNotification, sendSuccessNotification} from "@/Notifications";
import {DocumentTypography} from "@/ui-components/Markdown";
import UcxView, {UcxFunctionRegistry} from "@/UCX/UcxView";
import {Value, ValueKind, valueToPlain} from "@/UCX/protocol";
import {ServiceProviderSelector} from "@/Applications/ApiTokens/Add";
import HexSpin from "@/LoadingIcon/LoadingIcon";

// UI state management
// ================================================================================
type UIAction =
    | ReloadConfig
    | ResetConfig
    | ReloadDeviceWizard
    | RemoveDevice
    | RemoveFolder
    | AddDevice
    | AddFolder
    ;

interface ReloadConfig {
    type: "ReloadConfig";
    config: SyncthingConfig;
    etag: string;
}

interface ResetConfig {
    type: "ResetConfig";
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

interface UIState {
    // NOTE(Dan): These are all potentially `undefined`. This makes it easier to define the default state. A value of
    // `undefined` should generally be interpreted as "not yet loaded".
    devices?: SyncthingDevice[];
    folders?: SyncthingFolder[];
    etag?: string;
    showDeviceWizard?: boolean;
    updateCount?: number;
}

interface SyncthingLiveSnapshot {
    generatedAt: string;
    health: SyncthingHealthState;
    telemetry: SyncthingTelemetryState;
    folders: SyncthingFolderState[];
    devices: SyncthingDeviceState[];
}

interface SyncthingHealthState {
    state: "healthy" | "temporarily_failing" | "restart_required" | string;
    checkedAt: string;
    lastSuccessAt: string;
    error: string;
}

interface SyncthingTelemetryState {
    stale: boolean;
    error: string;
}

interface SyncthingFolderState {
    id: string;
    label: string;
    state: string;
    lastScan: string;
    outOfSyncItems: number;
    outOfSyncBytes: number;
    errorCount: number;
    error: string;
    watchError: string;
    errors: Array<{path: string; message: string}>;
}

interface SyncthingDeviceState {
    id: string;
    label: string;
    online: boolean;
    paused: boolean;
    lastSeen: string;
    sendBytesPerSecond: number;
    receiveBytesPerSecond: number;
    rateKnown: boolean;
    completion: {
        known: boolean;
        percent: number;
        error: string;
    };
    lastDisconnectReason: string;
    connectionError: string;
}

function uiReducer(state: UIState, action: UIAction): UIState {
    const copy = deepCopy(state);
    switch (action.type) {
        case "ResetConfig": {
            return {};
        }

        case "ReloadConfig": {
            copy.devices = action.config.devices;
            copy.folders = action.config.folders;
            copy.etag = action.etag;
            return copy;
        }

        case "ReloadDeviceWizard": {
            copy.showDeviceWizard = action.visible;
            return copy;
        }

        case "RemoveDevice": {
            const devices = copy.devices ?? [];
            copy.devices = devices.filter(it => it.deviceId !== action.deviceId);
            copy.updateCount = (copy.updateCount ?? 0) + 1;
            return copy;
        }

        case "RemoveFolder": {
            const folders = copy.folders ?? [];
            copy.folders = folders.filter(it => it.ucloudPath !== action.folderPath);
            copy.updateCount = (copy.updateCount ?? 0) + 1;
            return copy;
        }

        case "AddDevice": {
            const devices = copy.devices ?? [];
            devices.push(action.device);
            copy.devices = devices;
            copy.updateCount = (copy.updateCount ?? 0) + 1;
            return copy;
        }

        case "AddFolder": {
            const folders = copy.folders ?? [];
            if (folders.map(it => it.ucloudPath).includes(action.folderPath)) {
                sendFailureNotification("Folder is already added to synchronization");
            } else {
                folders.push({id: randomUUID(), ucloudPath: action.folderPath});
                copy.folders = folders;
                copy.updateCount = (copy.updateCount ?? 0) + 1;
            }
            return copy;
        }

    }
}

// Primary user interface
// ================================================================================
export const Overview: React.FunctionComponent = () => {
    return <NewOverview />;
};

const NewOverview: React.FunctionComponent = () => {
    const navigate = useNavigate();
    const location = useLocation();
    const [uiState, pureDispatch] = useReducer(uiReducer, {});
    const didUnmount = useDidUnmount();

    const devices = uiState?.devices ?? [];
    const folders = uiState?.folders ?? [];

    const [selectedProduct, setSelectedProduct] = useState<UCloud.compute.ComputeProductSupportResolved | null>(null);
    const [liveSnapshot, setLiveSnapshot] = useState<SyncthingLiveSnapshot | null>(null);
    const [rescanFolder, setRescanFolder] = useState<((folderId: string) => void) | null>(null);
    const [providers, setProviders] = useState<string[] | null>(null);
    const projectId = useProjectId();

    const updateRescanFolder = useCallback((handler: ((folderId: string) => void) | null) => {
        setRescanFolder(() => handler);
    }, []);

    const requestedProvider = getQueryParam(location.search, "provider");
    const provider = requestedProvider !== null && providers?.includes(requestedProvider) ? requestedProvider : null;
    const providerRef = useRef(provider);
    providerRef.current = provider;

    const selectProvider = useCallback((nextProvider: string, replace = false) => {
        const query = new URLSearchParams(location.search);
        query.set("provider", nextProvider);
        navigate(`${AppRoutes.syncthing.syncthing()}?${query.toString()}`, {replace});
    }, [location.search, navigate]);

    useEffect(() => {
        let cancelled = false;
        setProviders(null);
        Sync.fetchProviders().then(result => {
            if (!cancelled) setProviders(result);
        });
        return () => {
            cancelled = true;
        };
    }, [projectId]);

    useEffect(() => {
        if (providers === null) return;
        if (providers.length === 0) {
            navigate("/drives", {replace: true});
        } else if (provider === null) {
            selectProvider(providers[0], true);
        }
    }, [provider, providers, navigate, selectProvider]);

    useEffect(() => {
        pureDispatch({type: "ResetConfig"});
        setSelectedProduct(null);
        setLiveSnapshot(null);
        setRescanFolder(null);
    }, [provider]);

    // UI callbacks and state manipulation
    const reload = useCallback(() => {
        if (!provider) return;
        Sync.fetchConfigAndEtag(provider).then(([config, etag]) => {
            if (didUnmount.current || providerRef.current !== provider) return;
            pureDispatch({type: "ReloadConfig", config, etag});
        });

        Sync.fetchProducts(provider).then(product => {
            if (providerRef.current === provider && product.length > 0) {
                setSelectedProduct(product[0]);
            }
        });
    }, [provider, didUnmount]);

    const dispatch = useCallback((action: UIAction) => {
        pureDispatch(action);
    }, [pureDispatch]);

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
            <FileBrowse
                opts={{
                    isModal: true,
                    initialPath: "",
                    managesLocalProject: true,
                    selection: {
                        text: "Sync",
                        show(file) {
                            if (file.status.type !== "DIRECTORY") return false;
                            if (file.specification.product.id === "share") return false;
                            if (file.specification.product.provider != provider) return false;
                            if (file.permissions.myself.indexOf("EDIT") === -1 && file.permissions.myself.indexOf("ADMIN") === -1) return false;

                            return true;
                        },
                        async onClick(res) {
                            if (res.specification.product.provider != provider) {
                                sendFailureNotification("Only folders hosted at the same provider as the Syncthing server can be added");
                                return;
                            }

                            const sensitivity = await findSensitivity(res);
                            if (sensitivity == "SENSITIVE" || sensitivity == "CONFIDENTIAL") {
                                sendFailureNotification("Sensitive or confidential folders cannot be added to Syncthing");
                                return;
                            }
                            const target = removeTrailingSlash(res.id === "" ? pathRef.current : res.id);
                            dispatch({type: "AddFolder", folderPath: target});
                            dialogStore.success();
                        },
                    }
                }}
            />,
            doNothing,
            true,
            FilesApi.fileSelectorModalStyle
        );
    }, [dispatch, provider]);

    // Effects
    useEffect(() => reload(), [reload]);

    const eTagRef = useRef(uiState.etag);
    React.useEffect(() => {
        eTagRef.current = uiState.etag;
    }, [uiState.etag])

    useEffect(() => {
        let didCancel = false;
        (async () => {
            if (provider === null) return;
            if (selectedProduct === null) return;

            if (uiState.updateCount !== undefined && uiState.updateCount > 0) {
                await callAPI(Sync.api.updateConfiguration({
                    productId: selectedProduct.product.name,
                    provider: provider,
                    expectedETag: eTagRef.current,
                    config: {
                        devices: devices,
                        folders: folders,
                    }
                }));

                if (!didCancel) reload();
            }
        })()

        return () => {
            didCancel = true;
        };
    }, [uiState.updateCount, selectedProduct]);

    usePage("File synchronization", SidebarTabId.FILES);
    useSetRefreshFunction(reload);

    let main: React.ReactNode;
    if (uiState.devices !== undefined && uiState.devices.length === 0) {
        main = <AddDeviceWizard onDeviceAdded={onDeviceAdded} onWizardClose={closeWizard}
            provider={provider ?? undefined} providers={providers ?? []} onProviderChanged={selectProvider} />;
    } else {
        main = <div className={SyncthingMainClass}>
            {uiState.showDeviceWizard !== true ? null :
                <ReactModal
                    isOpen={true}
                    style={{...largeModalStyle, content: {...largeModalStyle.content, overflow: "hidden", padding: 0}}}
                    shouldCloseOnEsc
                    ariaHideApp={false}
                    onRequestClose={closeWizard}
                    className={CardClass}
                >
                    <AddDeviceWizard modal onDeviceAdded={onDeviceAdded} onWizardClose={closeWizard} />
                </ReactModal>
            }

            <header className="sync-page-header">
                <Image src={syncthingLogo} alt="Syncthing logo" />
                <div className="sync-page-heading">
                    <h1>File synchronization</h1>
                    <p>Synchronize your files between UCloud and your devices using Syncthing</p>
                </div>
                <ExternalLink href="https://docs.cloud.sdu.dk/guide/synch.html">
                    <Button>
                        <Icon name="heroArrowTopRightOnSquare" color="primaryContrast" mr={8} />
                        Documentation
                    </Button>
                </ExternalLink>
            </header>

            <section className="sync-section">
                <div className="sync-section-header">
                    <div className="sync-section-title"><h2>Devices</h2></div>
                    <div className="sync-device-actions">
                        <IconButton tooltip="Add device" onClick={openWizard} icon="heroPlus" />
                    </div>
                </div>
                <p className="sync-description">
                    UCloud can synchronize files to any of your devices which run Syncthing.
                    Download and install Syncthing to add one of your devices here.
                </p>

                <DeviceRows devices={devices} dispatch={dispatch} liveSnapshot={liveSnapshot} />
            </section>

            <section className="sync-section">
                <div className="sync-section-header">
                    <div className="sync-section-title"><h2>Folders</h2></div>
                    <IconButton tooltip="Add folder" onClick={openFileSelector} icon="heroPlus" />
                </div>
                <p className="sync-description">
                    These are the files which will be synchronized to your devices.
                    Add a new folder to start synchronizing data.
                </p>

                {uiState.folders?.length === 0 ?
                    <EmptyFolders onAddFolder={openFileSelector} /> :
                    <FolderRows folders={folders} dispatch={dispatch} liveSnapshot={liveSnapshot} onRescanFolder={rescanFolder} />
                }
            </section>

            {folders.length > 0 && devices.length > 0 ?
                <ServerStatus productId={selectedProduct?.product.name ?? ""} providerId={provider ?? ""}
                    liveSnapshot={liveSnapshot} onLiveSnapshot={setLiveSnapshot}
                    onRescanFolderChange={updateRescanFolder} />
                : null
            }

            <SyncthingSettings productId={selectedProduct?.product.name ?? ""} provider={provider ?? ""}
                providers={providers ?? []} onProviderChanged={selectProvider} reload={reload} />
        </div>;
    }

    if (providers === null || provider === null) {
        main = <Flex height="100%" alignItems="center" justifyContent="center"><HexSpin /></Flex>;
    }

    return <MainContainer main={main} />;
};

function useHomeDrive(providerId: string, projectOverride?: string): string | null {
    const realProjectId = useProjectId();
    const projectId = projectOverride ?? realProjectId;
    const [driveId, setDriveId] = useState<string | null>(null);
    const didCancel = useDidUnmount();

    useEffect(() => {
        (async () => {
            const page = await callAPI<PageV2<FileCollection>>(
                {
                    ...FileCollectionsApi.browse({filterProvider: providerId, itemsPerPage: 250}),
                    projectOverride: projectId,
                }
            );
            const username = Client.username ?? "";
            const memberFilesTitle = "Member files: " + username;
            let result: string | null = null;
            for (const drive of page.items) {
                if (drive.status.preferredDrive === true) {
                    result = drive.id;
                    break;
                }
            }
            if (result == null) {
                for (const drive of page.items) {
                    if (drive.specification.title === "Home") {
                        result = drive.id;
                        break;
                    } else if (drive.specification.title === memberFilesTitle) {
                        result = drive.id;
                        break;
                    }
                }
            }

            if (result == null) {
                for (const drive of page.items) {
                    if (drive.specification.title.toLowerCase().indexOf("home") !== -1) {
                        result = drive.id;
                        break;
                    }
                }
            }

            if (didCancel.current === false) {
                setDriveId(result);
            }
        })()
    }, [providerId, projectId]);

    return driveId;
}

function useTimedRefresh(intervalMs: number): number {
    const [cacheBust, setCacheBust] = useState(0);

    useEffect(() => {
        const id = setInterval(() => {
            setCacheBust(prev => prev + 1);
        }, intervalMs);

        return () => {
            clearInterval(id);
        }
    }, []);

    return cacheBust;
}

interface ServerStatusInfo {
    deviceId: string;
    jobId: string;
    state: JobState;
}

const ServerStatus: React.FunctionComponent<{
    productId: string,
    providerId: string,
    liveSnapshot: SyncthingLiveSnapshot | null,
    onLiveSnapshot: (snapshot: SyncthingLiveSnapshot | null) => void,
    onRescanFolderChange: (handler: ((folderId: string) => void) | null) => void,
}> = (props) => {
    const homeDriveId = useHomeDrive(props.providerId, "");
    const cacheBust = useTimedRefresh(5000);
    const [status, setStatus] = useState<ServerStatusInfo | null>(null);
    const [restartRequested, setRestartRequested] = useState(false);

    const doRestart = useCallback(() => {
        setRestartRequested(true);
        if (status !== null) {
            callAPIWithErrorHandler(Sync.api.restart({provider: props.providerId, productId: props.productId}))
                .then(doNothing);
        }
    }, [props.providerId, props.productId, status])

    useEffect(() => {
        setRestartRequested(false);
    }, [status?.state]);

    useEffect(() => {
        if (status?.state !== "RUNNING" || restartRequested) props.onLiveSnapshot(null);
    }, [status?.state, restartRequested, props.onLiveSnapshot]);

    useEffect(() => () => props.onLiveSnapshot(null), [props.onLiveSnapshot]);

    useEffect(() => {
        let didCancel = false;
        if (homeDriveId !== null) {
            const basePath = `/${homeDriveId}/Syncthing/`;
            const deviceIdPromise = downloadFileContent(basePath + "ucloud_device_id.txt").then(it => it.text());
            const jobIdPromise = downloadFileContent(basePath + "job_id.txt").then(it => it.text());
            Promise.all([deviceIdPromise, jobIdPromise]).then(([deviceId, jobId]) => {
                callAPI(JobsApi.retrieve({id: jobId})).then(job => {
                    if (!didCancel) {
                        setStatus({
                            deviceId,
                            jobId,
                            state: job.status.state,
                        });
                    }
                });
            });
        }
        return () => {
            didCancel = true;
        }
    }, [homeDriveId, cacheBust]);

    const running = status !== null && !restartRequested && status.state === "RUNNING";
    const statusText = running ? "Running" : restartRequested ? "Restarting..." : "Starting...";

    return <>
        <section className="sync-section sync-status">
            <div className="sync-section-header">
                <h2>Server status</h2>
                <Flex gap={"16px"}>
                    {running && status !== null ?
                        <UcxView
                            key={status.jobId}
                            url={Client.computeURL("/api", "/hpc/apps/ucx/connectJob")
                                .replace("http://", "ws://").replace("https://", "wss://")}
                            authToken={async () => `${await Client.receiveAccessTokenOrRefreshIt()}\n`}
                            sysHello={() => JSON.stringify({jobId: status.jobId})}
                            onModelChange={model => props.onLiveSnapshot(parseLiveSnapshot(model))}
                            renderFrame={({connected, fn}) => <SyncthingUcxFrame connected={connected} snapshot={props.liveSnapshot}
                                fn={fn} onRescanFolderChange={props.onRescanFolderChange} />}
                        /> :
                        <span className="sync-connection-status" data-transitioning="true">
                            <span className="sync-status-dot" />
                            {statusText}
                        </span>}
                    <IconButton tooltip={"Restart"} icon={"heroArrowPath"} onClick={doRestart}/>
                </Flex>
            </div>
            {status === null ? <p className="sync-description">Syncthing is starting. This can take a few minutes.</p> :
                <>
                    <div className="sync-server-meta">
                        <div className="sync-server-meta-item">
                            <span>Job ID</span>
                            <ExternalLink href={AppRoutes.prefix + AppRoutes.jobs.view(status.jobId)} target="_blank">
                                {status.jobId} <Icon name="open" size={10}/>
                            </ExternalLink>
                        </div>
                        <div className="sync-server-meta-item sync-server-id">
                            <span>Server ID</span>
                            <div>
                                <code>{status.deviceId}</code>
                                <CopyButton onClick={() => copyToClipboard(status.deviceId)}/>
                            </div>
                        </div>
                    </div>
                </>
            }
        </section>

    </>
}

function SyncthingSettings(props: {
    productId: string;
    provider: string;
    providers: string[];
    onProviderChanged: (provider: string) => void;
    reload: () => void;
}): React.ReactNode {
    const doFactoryReset = useCallback(async () => {
        await callAPI(Sync.api.resetConfiguration({provider: props.provider, productId: props.productId}));
        props.reload();
    }, [props.provider, props.productId, props.reload]);

    const requestFactoryReset = useCallback(() => {
        addStandardDialog({
            title: "Factory reset Syncthing?",
            message: "This will reset the server state. Synchronization status of all folders will be removed and all registered devices will be removed. No data will be deleted.",
            confirmText: "Factory reset",
            confirmButtonColor: "errorMain",
            cancelButtonColor: "primaryMain",
            onConfirm: doFactoryReset,
        });
    }, [doFactoryReset]);

    return <section className="sync-section">
        <div className="sync-section-header">
            <h2>Settings</h2>
        </div>
        <div className="sync-server-actions">
            <Flex width={"100%"} flexWrap={"wrap"} gap={"16px"} alignItems="end">
                <Box flexGrow={1}>
                    <div><strong>Service provider</strong></div>
                    <Box color={"textSecondary"} maxWidth={"50ch"}>
                        Choose where Syncthing runs. Only folders hosted by this provider can be synchronized.
                    </Box>
                </Box>
                <ProviderSelector provider={props.provider} providers={props.providers}
                    onProviderChanged={props.onProviderChanged} />
            </Flex>
        </div>
        <div className="sync-server-actions">
            <Flex width={"100%"} flexWrap={"wrap"} gap={"16px"}>
                <Box flexGrow={1}>
                    <div><strong>Factory reset</strong></div>
                    <Box color={"textSecondary"} maxWidth={"50ch"}>
                        Having issues? Try a factory reset.
                        No data will be deleted, but you will have to redo the setup.
                    </Box>
                </Box>
                <Button color="errorMain" onClick={requestFactoryReset} alignSelf={"end"} flexShrink={0}
                    disabled={!props.productId}>
                    <Icon name="heroTrash" size={15} mr="6px" color="errorContrast"/> Factory reset
                </Button>
            </Flex>
        </div>
    </section>;
}

function ProviderSelector(props: {
    provider: string;
    providers: string[];
    onProviderChanged: (provider: string) => void;
}): React.ReactNode {
    return <div className="sync-provider-selector">
        <ServiceProviderSelector serviceProvider={props.provider}
            serviceProviders={props.providers.map(key => ({key}))}
            onSelect={provider => props.onProviderChanged(provider.key)}
            showLabel={false} />
    </div>;
}

function SyncthingStatusIndicator({connected, snapshot}: {connected: boolean; snapshot: SyncthingLiveSnapshot | null}): React.ReactNode {
    if (!connected || snapshot === null) {
        return <span className="sync-connection-status" data-transitioning="true">
            <span className="sync-status-dot" />
            Running - Waiting for status...
        </span>;
    }

    const healthy = snapshot.health.state === "healthy";
    const indicator = <span className="sync-connection-status" data-transitioning={!healthy} data-error={snapshot.health.state === "restart_required"}>
        <span className="sync-status-dot" />
        Running - {healthLabel(snapshot.health.state)}
    </span>;
    if (!healthy) return indicator;

    return <TooltipV2 tooltip={`Last observed: ${formatObservedTime(snapshot.health.checkedAt)}`} contentWidth={220}
        triggerStyle={{display: "flex"}}>
        {indicator}
    </TooltipV2>;
}

function SyncthingUcxFrame({connected, snapshot, fn, onRescanFolderChange}: {
    connected: boolean;
    snapshot: SyncthingLiveSnapshot | null;
    fn?: UcxFunctionRegistry;
    onRescanFolderChange: (handler: ((folderId: string) => void) | null) => void;
}): React.ReactNode {
    useEffect(() => {
        if (!connected || fn === undefined) {
            onRescanFolderChange(null);
            return;
        }

        onRescanFolderChange(folderId => {
            fn.sendUiEvent("syncthing.rescanFolder", "click", {kind: ValueKind.String, string: folderId});
        });
        return () => onRescanFolderChange(null);
    }, [connected, fn, onRescanFolderChange]);

    return <SyncthingStatusIndicator connected={connected} snapshot={snapshot} />;
}

function parseLiveSnapshot(model: Record<string, Value>): SyncthingLiveSnapshot | null {
    const schemaVersion = plainModelValue<number>(model, "state.schemaVersion");
    const folders = plainModelValue<SyncthingFolderState[]>(model, "state.folders");
    const devices = plainModelValue<SyncthingDeviceState[]>(model, "state.devices");
    if (schemaVersion !== 1 || !Array.isArray(folders) || !Array.isArray(devices)) return null;

    return {
        generatedAt: plainModelValue<string>(model, "state.generatedAt") ?? "",
        health: {
            state: plainModelValue<string>(model, "state.health.state") ?? "",
            checkedAt: plainModelValue<string>(model, "state.health.checkedAt") ?? "",
            lastSuccessAt: plainModelValue<string>(model, "state.health.lastSuccessAt") ?? "",
            error: plainModelValue<string>(model, "state.health.error") ?? "",
        },
        telemetry: {
            stale: plainModelValue<boolean>(model, "state.telemetry.stale") ?? true,
            error: plainModelValue<string>(model, "state.telemetry.error") ?? "",
        },
        folders,
        devices,
    };
}

function plainModelValue<T>(model: Record<string, Value>, path: string): T | undefined {
    const value = model[path];
    return value === undefined ? undefined : valueToPlain(value) as T;
}

function DeviceRows({devices, dispatch, liveSnapshot}: {
    devices: SyncthingDevice[];
    dispatch: (action: UIAction) => void;
    liveSnapshot: SyncthingLiveSnapshot | null;
}): React.ReactNode {
    const removeDevice = useCallback((device: SyncthingDevice) => {
        addStandardDialog({
            title: "Remove device?",
            message: `UCloud will stop synchronizing files with ${device.label}.`,
            confirmText: "Remove",
            confirmButtonColor: "errorMain",
            cancelButtonColor: "primaryMain",
            onConfirm: () => dispatch({type: "RemoveDevice", deviceId: device.deviceId}),
        });
    }, [dispatch]);

    const liveDevices = new Map(liveSnapshot?.devices.map(device => [device.id, device]));
    return <div className="sync-rows">
        {devices.map(device => {
            const live = liveDevices.get(device.deviceId);
            const error = live?.connectionError || live?.completion.error || live?.lastDisconnectReason;
            const badge = live ? <LiveBadge text={live.paused ? "Paused" : live.online ? "Online" : "Offline"}
                tone={live.paused ? "muted" : live.online ? "ok" : "error"} /> : null;
            const shortName = device.deviceId.split("-")[0];
            return <div className="sync-row" key={device.deviceId}>
            <div className="sync-row-icon"><Icon name="heroComputerDesktop" size={20} color="textPrimary" /></div>
            <div className="sync-row-content">
                <div className="sync-row-heading">
                    <strong>{device.label} ({shortName})</strong>
                    {!live || live.online || !error ? badge : <TooltipV2 tooltip={error} contentWidth={320} triggerClassName="sync-offline-tooltip">
                        {badge}
                    </TooltipV2>}
                </div>
                {live ? <div className="sync-live-details">
                    <span>Last seen: {formatObservedTime(live.lastSeen)}</span>
                    <span>Send: {live.rateKnown ? `${formatBytes(live.sendBytesPerSecond)}/s` : "Unavailable"}</span>
                    <span>Receive: {live.rateKnown ? `${formatBytes(live.receiveBytesPerSecond)}/s` : "Unavailable"}</span>
                    <span>Completion: {live.completion.known ? `${formatPercent(live.completion.percent)}%` : "Unavailable"}</span>
                    {live.online && error ? <span className="sync-live-error" title={error}>Error: {error}</span> : null}
                </div> : <div className="sync-live-details">
                    <span>Last seen: Unknown</span>
                    <span>Send: Unknown</span>
                    <span>Receive: Unknown</span>
                    <span>Completion: Unknown</span>
                </div>}
            </div>
            <div className="sync-row-actions">
                <CopyButton tooltip={"Copy device ID to clipboard"} onClick={() => copyToClipboard(device.deviceId)} />
                <IconButton tooltip="Remove device" icon="heroTrash" color="errorMain" onClick={() => removeDevice(device)} />
            </div>
        </div>})}
    </div>;
}

function FolderRows({folders, dispatch, liveSnapshot, onRescanFolder}: {
    folders: SyncthingFolder[];
    dispatch: (action: UIAction) => void;
    liveSnapshot: SyncthingLiveSnapshot | null;
    onRescanFolder: ((folderId: string) => void) | null;
}): React.ReactNode {
    const navigate = useNavigate();
    const [prettyPaths, setPrettyPaths] = useState<Record<string, string>>({});

    useEffect(() => {
        let cancelled = false;
        Promise.all(folders.map(async folder => {
            const path = await prettyFilePath(folder.ucloudPath).catch(() => folder.ucloudPath);
            return [folder.id, path] as const;
        })).then(entries => {
            if (!cancelled) setPrettyPaths(Object.fromEntries(entries));
        });

        return () => { cancelled = true; };
    }, [folders]);

    const removeFolder = useCallback((folder: SyncthingFolder) => {
        addStandardDialog({
            title: "Stop synchronizing folder?",
            message: "The folder remains in UCloud, but Syncthing will stop synchronizing it to your devices.",
            confirmText: "Stop synchronizing",
            confirmButtonColor: "errorMain",
            cancelButtonColor: "primaryMain",
            onConfirm: () => dispatch({type: "RemoveFolder", folderPath: folder.ucloudPath}),
        });
    }, [dispatch]);

    const liveFolders = new Map(liveSnapshot?.folders.map(folder => [folder.id, folder]));
    return <div className="sync-rows">
        {folders.map(folder => {
            const live = liveFolders.get(folder.id);
            const errors = live ? [live.error, live.watchError, ...live.errors.map(error => `${error.path}: ${error.message}`)].filter(Boolean) : [];
            return <div className="sync-row" key={folder.id}>
            <div className="sync-row-icon sync-folder-icon"><Icon name="heroFolder" size={20} color="FtFolderColor" /></div>
            <div className="sync-row-content">
                <button className="sync-row-link sync-row-heading" onClick={() => navigate(AppRoutes.files.path(folder.ucloudPath))}>
                    <strong>{prettyPaths[folder.id] ?? folder.ucloudPath}</strong>
                    {live ? <LiveBadge text={stateLabel(live.state)} tone={folderStateTone(live.state)} /> : null}
                </button>
                {live ? <div className="sync-live-details">
                    <span>Last scan: {formatObservedTime(live.lastScan)}</span>
                    <span>Out of sync: {live.outOfSyncItems.toLocaleString()} items ({formatBytes(live.outOfSyncBytes)})</span>
                    {live.errorCount > 0 || errors.length > 0 ? <span className="sync-live-error" title={errors.join("\n") || "Syncthing reports folder errors"}>
                        {live.errorCount || errors.length} error(s){errors[0] ? `: ${errors[0]}` : ""}
                    </span> : null}
                </div> : <div className="sync-live-details">
                    <span>Last scan: Unknown</span>
                    <span>Out of sync: Unknown</span>
                </div>}
            </div>
            <div className="sync-row-actions">
                {live && onRescanFolder ? <IconActionButton tooltip="Rescan folder" icon="heroArrowPath" onClick={() => {
                    onRescanFolder(folder.id);
                }} /> : null}
                <IconButton tooltip="Open folder" icon="heroFolderOpen" onClick={() => navigate(AppRoutes.files.path(folder.ucloudPath))} />
                <IconButton tooltip="Stop synchronizing" icon="heroTrash" color="errorMain" onClick={() => removeFolder(folder)} />
            </div>
        </div>})}
    </div>;
}

function LiveBadge({text, tone}: {text: string; tone: "ok" | "active" | "error" | "muted"}): React.ReactNode {
    return <span className="sync-live-badge" data-tone={tone}>{text}</span>;
}

function stateLabel(state: string): string {
    if (!state) return "Unknown";
    return state.split("-").map(part => part.charAt(0).toUpperCase() + part.slice(1)).join(" ");
}

function folderStateTone(state: string): "ok" | "active" | "error" | "muted" {
    if (state === "idle") return "ok";
    if (state === "error") return "error";
    if (state.includes("scan") || state.includes("sync")) return "active";
    return "muted";
}

function healthLabel(state?: string): string {
    if (state === "healthy") return "Healthy";
    if (state === "temporarily_failing") return "Temporarily failing";
    if (state === "restart_required") return "Restart required";
    return "Waiting for health result";
}

function formatObservedTime(value?: string): string {
    if (!value) return "Never";
    const timestamp = Date.parse(value);
    return Number.isFinite(timestamp) ? new Date(timestamp).toLocaleString() : "Unknown";
}

function formatBytes(value: number): string {
    if (!Number.isFinite(value) || value < 0) return "Unavailable";
    const units = ["B", "KiB", "MiB", "GiB", "TiB"];
    let amount = value;
    let unit = 0;
    while (amount >= 1024 && unit < units.length - 1) {
        amount /= 1024;
        unit++;
    }
    const digits = amount >= 100 || unit === 0 ? 0 : amount >= 10 ? 1 : 2;
    return `${amount.toFixed(digits)} ${units[unit]}`;
}

function formatPercent(value: number): string {
    if (!Number.isFinite(value)) return "0";
    return Math.max(0, Math.min(100, value)).toFixed(value >= 99.95 ? 0 : 1);
}

const AddDeviceWizard: React.FunctionComponent<{
    onDeviceAdded: (device: SyncthingDevice) => void;
    onWizardClose: () => void;
    modal?: boolean;
    provider?: string;
    providers?: string[];
    onProviderChanged?: (provider: string) => void;
}> = (props) => {
    const STEP_INTRO = 0;
    const STEP_ADD_DEVICE = 1;
    const STEP_LAST = STEP_ADD_DEVICE;

    const [tutorialStep, setTutorialStep] = useState(0);
    const deviceNameRef = useRef<HTMLInputElement>(null);
    const [deviceNameError, setDeviceNameError] = useState<string | null>(null);
    const deviceIdRef = useRef<HTMLInputElement>(null);
    const [deviceIdError, setDeviceIdError] = useState<string | null>(null);
    const [showInstructions, setShowInstructions] = useState(false);

    const addDevice = useCallback((e?: React.SyntheticEvent) => {
        e?.preventDefault();
        const deviceName = (deviceNameRef.current?.value ?? "").trim();
        const deviceId = (deviceIdRef.current?.value ?? "").trim();
        const deviceIdParts = deviceId.split("-");
        const deviceIdValid = deviceIdParts.length === 8 && deviceIdParts.every(part => part.length === 7);

        setDeviceNameError(deviceName ? null : "Enter a name that helps you recognize this device.");
        setDeviceIdError(deviceIdValid ? null : "Enter the Device ID shown by Syncthing.");

        if (deviceName && deviceIdValid) {
            props.onDeviceAdded({deviceId, label: deviceName});
            props.onWizardClose();
        }
    }, [props.onDeviceAdded, props.onWizardClose]);

    const tutorialNext = useCallback((e?: React.SyntheticEvent) => {
        e?.preventDefault();
        let hasErrors = false;

        if (tutorialStep === STEP_ADD_DEVICE) {
            const deviceName = (deviceNameRef.current?.value ?? "").trim();
            const deviceId = (deviceIdRef.current?.value ?? "").trim();

            if (deviceName.length === 0) {
                setDeviceNameError("Enter a name that will help you remember which device this is. For example: 'Work phone'.");
                hasErrors = true;
            } else {
                setDeviceNameError(null);
            }

            const deviceSplit = deviceId.split("-");
            const deviceIdValid = deviceSplit.length === 8 && deviceSplit.every(chunk => chunk.length === 7);
            if (!deviceIdValid) {
                setDeviceIdError(
                    "The device ID you specified doesn't look valid. " +
                    "Make sure you follow the steps described above to locate your device ID."
                );
                hasErrors = true;
            } else {
                setDeviceIdError(null);
            }

            if (!hasErrors) props.onDeviceAdded({deviceId, label: deviceName});
        }

        if (!hasErrors) {
            if (tutorialStep === STEP_LAST) {
                props.onWizardClose();
            } else {
                setTutorialStep(previous => previous + 1);
            }
        }
    }, [tutorialStep, props.onDeviceAdded, props.onWizardClose]);

    const tutorialPrevious = useCallback(() => {
        setTutorialStep(previous => previous - 1);
    }, []);

    if (!props.modal) {
        let tutorialContent: React.ReactNode;
        if (tutorialStep === STEP_INTRO) {
            tutorialContent = <>
                <h2>Install Syncthing</h2>
                <p>
                    Synchronize folders between UCloud and your devices. Changes made in either place are
                    automatically synchronized to the other.
                </p>

                <div className="tutorial-notice">
                    <Icon name="warning" size={20} color="warningMain" />
                    <span>
                        The synchronization feature is experimental. Please report any errors through the Support Form.
                    </span>
                </div>

                <section>
                    <ol>
                        {props.provider && props.providers && props.onProviderChanged ?
                            <li>
                                <div className="tutorial-step-copy">
                                    <b>Choose a service provider</b>
                                    <span>
                                        Syncthing can only synchronize folders hosted by the selected provider.
                                    </span>
                                    <ProviderSelector provider={props.provider} providers={props.providers}
                                        onProviderChanged={props.onProviderChanged} />
                                    <Box mb={"8px"} />
                                </div>
                            </li> : null}
                        <li>
                            <div className="tutorial-step-copy">
                                <b>Download and install Syncthing for your platform</b>
                                <ExternalLink href="https://syncthing.net/downloads/">
                                    <Button mb={16}><Icon name="open" mr="4px" size="14px" /> Download Syncthing</Button>
                                </ExternalLink>
                            </div>
                        </li>
                        <li>
                            <b>Open the Syncthing application</b>
                            <p>On a desktop or laptop, the application should look like this:</p>
                            <Screenshot src={syncthingScreen4} alt="The Syncthing application after installation." />
                        </li>
                    </ol>
                </section>
            </>;
        } else {
            tutorialContent = <>
                <h2>Add your device</h2>
                <p>UCloud needs the Device ID generated by Syncthing before it can synchronize your files.</p>

                <section className="tutorial-device-guide">
                    <h3>Find your Device ID</h3>
                    <div className="tutorial-device-instructions">
                        <ol>
                            <li>Open Syncthing.</li>
                            <li>Open the <i>Actions</i> menu in the top-right corner and select <i>Show ID</i>.</li>
                            <li>
                                A window with your Device ID and a QR code appears. Copy the Device ID and paste it
                                into the field below.
                            </li>
                        </ol>
                        <Screenshot src={syncthingScreen1} alt="The Show ID option in Syncthing's Actions menu." />
                    </div>
                </section>

                <form className="tutorial-form" onSubmit={tutorialNext}>
                    <h2>Enter device details</h2>
                    <div className="tutorial-fields">
                        <Label>
                            Device name
                            <Input inputRef={deviceNameRef} placeholder="My phone" error={deviceNameError !== null} />
                            {!deviceNameError ?
                                <Text color="textSecondary">
                                    A name to help you remember this device. For example: "Work phone".
                                </Text> :
                                <Text color="errorMain">{deviceNameError}</Text>}
                        </Label>
                        <Label>
                            My device ID
                            <Input
                                inputRef={deviceIdRef}
                                placeholder="XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX"
                                error={deviceIdError !== null}
                            />
                            {!deviceIdError ? null : <Text color="errorMain">{deviceIdError}</Text>}
                        </Label>
                    </div>
                    <button type="submit" style={{display: "none"}}>Add device</button>
                </form>
            </>;
        }

        return <div className={TutorialWizardClass}>
            <DocumentTypography className="tutorial-content">{tutorialContent}</DocumentTypography>
            <footer className="tutorial-actions">
                <div className="tutorial-progress" aria-label={`Step ${tutorialStep + 1} of ${STEP_LAST + 1}`}>
                    <span>Step {tutorialStep + 1} of {STEP_LAST + 1}</span>
                    <div className="tutorial-progress-track" aria-hidden="true">
                        <div style={{width: `${((tutorialStep + 1) / (STEP_LAST + 1)) * 100}%`}} />
                    </div>
                </div>
                <Box flexGrow={1} />
                {tutorialStep === STEP_INTRO ? null :
                    <Button color="secondaryMain" onClick={tutorialPrevious}>Previous step</Button>}
                <Button onClick={tutorialNext}>{tutorialStep === STEP_LAST ? "Add device" : "Next step"}</Button>
            </footer>
        </div>;
    }

    return <div className={TutorialWizardClass} data-modal={props.modal === true}>
        <DocumentTypography className="tutorial-content">
            <h2 className={"tutorial-header"}>
                Connect a device
                <ExternalLink href="https://syncthing.net/downloads/">
                    <Button><Icon name="open" mr="4px" size="14px" /> Download Syncthing</Button>
                </ExternalLink>
            </h2>
            <p>Enter the Device ID from Syncthing to synchronize files with UCloud.</p>

            <form className="tutorial-form" onSubmit={addDevice}>
                <div className="tutorial-fields">
                    <Label>
                        Device name
                        <Input inputRef={deviceNameRef} placeholder="My phone" error={deviceNameError !== null} />
                        {deviceNameError ? <Text color="errorMain">{deviceNameError}</Text> :
                            <Text color="textSecondary">For example: "Work phone".</Text>}
                    </Label>
                    <Label>
                        Device ID
                        <Input
                            inputRef={deviceIdRef}
                            placeholder="XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX"
                            error={deviceIdError !== null}
                        />
                        {deviceIdError ? <Text color="errorMain">{deviceIdError}</Text> : null}
                        <Text color="textSecondary">
                            Need help finding your Device ID?{" "}
                            <button type="button" className="tutorial-help-link" onClick={() => setShowInstructions(current => !current)}>
                                {showInstructions ? "Hide the setup instructions." : "View the setup instructions."}
                            </button>
                        </Text>
                    </Label>
                </div>
                <button type="submit" style={{display: "none"}}>Add device</button>
            </form>

            {showInstructions ?
                <div className="device-setup-grid">
                    <section>
                        <h3>1. Open Syncthing</h3>
                        <p>Install the downloaded application, then open Syncthing on the device you want to connect.</p>
                        <Screenshot src={syncthingScreen4} alt="The Syncthing application after installation." />
                    </section>

                    <section>
                        <h3>2. Copy the Device ID</h3>
                        <p>In Syncthing, open <i>Actions</i>, select <i>Show ID</i>, and copy the Device ID.</p>
                        <Screenshot src={syncthingScreen1} alt="The Show ID option in Syncthing's Actions menu." />
                    </section>
                </div> : null}
        </DocumentTypography>
        <footer className="tutorial-actions">
            <Button color="successMain" onClick={addDevice}>Add device</Button>
        </footer>
    </div>;
}

const EmptyFolders: React.FunctionComponent<{
    onAddFolder: () => void;
}> = ({onAddFolder}) => {
    return <div className="sync-empty">
        <div className="sync-row-icon sync-folder-icon"><Icon name="heroFolder" size={20} color="FtFolderColor" /></div>
        <div>
            <h3>No synchronized folders</h3>
            <p>Select a folder to begin synchronizing it with your devices.</p>
        </div>
        <Button onClick={onAddFolder}>Choose folder</Button>
    </div>
};

const SyncthingMainClass = injectStyle("syncthing-main", k => `
    ${k} {
        width: 100%;
        max-width: 1100px;
        height: calc(100vh - 32px);
        margin: 0 auto;
        padding: 8px 24px 48px;
        overflow-y: auto;
        color: var(--textPrimary);
        font-size: 15px;
        line-height: 1.55;
    }

    ${k} h2, ${k} h3, ${k} p {
        padding: 0;
        border: 0;
    }

    ${k} .sync-page-header {
        display: flex;
        align-items: center;
        gap: 16px;
        padding: 24px 0 20px;
    }

    ${k} .sync-page-header img {
        width: 64px;
        height: 64px;
        flex: 0 0 64px;
    }

    ${k} .sync-page-header h1 {
        margin: 0;
        font-size: 30px;
        font-weight: normal;
        line-height: 1.2;
    }

    ${k} .sync-page-heading {
        min-width: 0;
        flex: 1;
    }

    ${k} .sync-page-heading p {
        margin-top: 3px;
        color: var(--textSecondary);
    }

    ${k} h2 {
        margin: 0;
        font-size: 20px;
        line-height: 1.3;
        letter-spacing: -0.01em;
    }

    ${k} h3 {
        margin: 0 0 4px;
        font-size: 15px;
        line-height: 1.4;
    }

    ${k} p {
        margin: 0;
        line-height: 1.55;
    }

    ${k} .sync-description {
        color: var(--textSecondary);
    }

    ${k} .sync-section {
        padding: 22px 0 30px;
    }

    ${k} .sync-section + .sync-section {
        margin-top: 12px;
    }

    ${k} .sync-section-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 20px;
        margin-bottom: 8px;
    }

    ${k} .sync-description {
        margin-bottom: 32px;
    }

    ${k} .sync-section-title {
        display: flex;
        align-items: center;
        gap: 9px;
    }

    ${k} .sync-device-actions {
        display: flex;
        align-items: center;
        gap: 8px;
    }

    ${k} .sync-rows {
        display: grid;
        gap: 16px;
    }

    ${k} .sync-row {
        min-width: 0;
        min-height: 64px;
        display: flex;
        align-items: center;
        gap: 14px;
        padding: 10px 12px;
        margin: -10px -12px;
        border-radius: 10px;
        background: transparent;
        transition: background-color 120ms ease;
    }

    ${k} .sync-row:hover {
        background: var(--rowHover);
    }

    ${k} .sync-row-icon {
        width: 38px;
        height: 38px;
        flex: 0 0 38px;
        display: grid;
        place-items: center;
        border: 1px solid var(--borderColor);
        border-radius: 10px;
        background: var(--backgroundCard);
    }

    ${k} .sync-folder-icon {
        color: var(--FtFolderColor);
    }

    ${k} .sync-row-content {
        min-width: 0;
        flex: 1;
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 2px;
    }

    ${k} .sync-row-content strong {
        max-width: 100%;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: 14px;
    }

    ${k} .sync-row-content code, ${k} .sync-row-content span {
        max-width: 100%;
        overflow: hidden;
        color: var(--textSecondary);
        font-size: 12px;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    ${k} .sync-row-link {
        padding: 0;
        border: 0;
        background: transparent;
        color: inherit;
        font: inherit;
        text-align: left;
        cursor: pointer;
    }

    ${k} .sync-row-heading {
        width: 100%;
        min-width: 0;
        display: flex;
        align-items: center;
        gap: 8px;
    }

    ${k} .sync-row-heading strong {
        min-width: 0;
    }

    ${k} .sync-live-badge {
        flex: 0 0 auto;
        padding: 1px 7px;
        border-radius: 999px;
        background: var(--rowHover);
        color: var(--textSecondary);
        font-size: 11px;
        font-weight: 600;
        line-height: 18px;
    }

    ${k} .sync-live-badge[data-tone="ok"] {
        background: color-mix(in srgb, var(--green-fg) 14%, transparent);
        color: var(--green-fg);
    }

    ${k} .sync-live-badge[data-tone="active"] {
        background: color-mix(in srgb, var(--primaryMain) 14%, transparent);
        color: var(--primaryMain);
    }

    ${k} .sync-live-badge[data-tone="error"] {
        background: color-mix(in srgb, var(--errorMain) 14%, transparent);
        color: var(--errorMain);
    }

    ${k} .sync-live-details {
        width: 100%;
        display: flex;
        flex-wrap: wrap;
        gap: 2px 18px;
        margin-top: 5px;
    }

    ${k} .sync-live-details > span {
        overflow: visible;
        color: var(--textSecondary);
        font-size: 12px;
        text-overflow: clip;
        white-space: normal;
    }

    ${k} .sync-live-details > .sync-live-error {
        width: 100%;
        overflow: hidden;
        color: var(--errorMain);
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    ${k} .sync-offline-tooltip {
        display: inline-flex;
    }

    ${k} .sync-row-actions, ${k} .sync-server-actions, ${k} .sync-connection-status {
        display: flex;
        align-items: center;
    }

    ${k} .sync-row-actions {
        gap: 2px;
    }

    ${k} .sync-row-actions button {
        --icon-button-hover: var(--rowActive);
    }

    ${k} .sync-server-actions {
        gap: 8px;
        margin-top: 20px;
    }

    ${k} .sync-provider-selector {
        width: min(340px, 100%);
        flex-shrink: 0;
    }

    ${k} .sync-row-warning {
        display: inline-flex;
        color: var(--warningMain);
    }

    ${k} .sync-empty {
        min-height: 76px;
        display: flex;
        align-items: center;
        gap: 14px;
        padding: 12px;
        border-radius: 10px;
        background: var(--rowHover);
    }

    ${k} .sync-empty > div:nth-child(2) {
        min-width: 0;
        flex: 1;
    }

    ${k} .sync-empty p {
        color: var(--textSecondary);
    }

    ${k} .sync-connection-status {
        gap: 8px;
        color: var(--textSecondary);
        font-size: 12px;
    }

    ${k} .sync-status-dot {
        width: 8px;
        height: 8px;
        flex: 0 0 8px;
        border-radius: 50%;
        background: var(--green-fg);
    }

    ${k} .sync-connection-status[data-transitioning="true"] .sync-status-dot {
        background: var(--warningMain);
        animation: syncthing-status-pulse 1.6s ease-in-out infinite;
    }

    ${k} .sync-connection-status[data-error="true"] .sync-status-dot {
        background: var(--errorMain);
    }

    @keyframes syncthing-status-pulse {
        0%, 100% { opacity: 1; transform: scale(1); }
        50% { opacity: .45; transform: scale(.75); }
    }

    ${k} .sync-server-meta {
        display: flex;
        align-items: flex-start;
        gap: 48px;
        margin-top: 16px;
    }

    ${k} .sync-server-meta-item {
        min-width: 180px;
        display: flex;
        flex-direction: column;
        gap: 4px;
    }

    ${k} .sync-server-meta-item > span {
        color: var(--textSecondary);
        font-size: 12px;
    }

    ${k} .sync-server-meta-item > a {
        color: var(--linkColor);
        font-size: 14px;
    }

    ${k} .sync-server-meta-item > a:hover {
        color: var(--linkColorHover);
    }

    ${k} .sync-server-meta-item > div {
        color: var(--textPrimary);
        font-size: 14px;
    }

    ${k} .sync-server-id {
        min-width: 0;
        flex: 1;
    }

    ${k} .sync-server-id > div {
        min-width: 0;
        display: flex;
        align-items: center;
        gap: 8px;
    }

    ${k} .sync-server-id code {
        min-width: 0;
        overflow: hidden;
        color: var(--textPrimary);
        font-family: var(--monospace);
        font-size: 14px;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    @media (max-width: 640px) {
        ${k} {
            padding-right: 16px;
            padding-left: 16px;
        }

        ${k} .sync-page-header {
            align-items: flex-start;
            flex-wrap: wrap;
        }

        ${k} .sync-page-heading {
            flex-basis: calc(100% - 80px);
        }

        ${k} .sync-page-header > a {
            margin-left: 80px;
        }

        ${k} .sync-section-header {
            align-items: flex-start;
        }

        ${k} .sync-server-meta {
            align-items: flex-start;
            flex-direction: column;
        }

        ${k} .sync-server-meta {
            gap: 16px;
        }

        ${k} .sync-server-meta-item {
            width: 100%;
        }

        ${k} .sync-row {
            gap: 10px;
            padding-right: 6px;
        }

        ${k} .sync-empty {
            align-items: flex-start;
            flex-wrap: wrap;
        }

        ${k} .sync-empty > button {
            margin-left: 48px;
        }
    }
`);

const ScreenshotClass = injectStyleSimple("screenshot", `
    border: 3px solid var(--borderColor);
    max-height: 250px;
`);

const TutorialWizardClass = injectStyle("tutorial-wizard", k => `
    ${k} {
        width: 100%;
        max-width: 1080px;
        height: calc(100vh - 48px);
        margin: 0 auto;
        display: flex;
        flex-direction: column;
        background: var(--backgroundDefault);
    }

    ${k}[data-modal="true"] {
        height: 100%;
    }

    ${k} .tutorial-progress {
        display: flex;
        align-items: center;
        gap: 16px;
        color: var(--textSecondary);
        font-size: 13px;
        font-weight: 600;
    }

    ${k} .tutorial-progress-track {
        width: 120px;
        height: 5px;
        overflow: hidden;
        border-radius: 999px;
        background: var(--borderColor);
    }

    ${k} .tutorial-progress-track > div {
        height: 100%;
        border-radius: inherit;
        background: var(--primaryMain);
        transition: width 180ms ease-out;
    }

    ${k} .tutorial-content {
        min-height: 0;
        overflow-y: auto;
        padding: 12px 24px 32px;
        flex-grow: 1;
    }

    ${k} .tutorial-header {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        justify-content: space-between;
        gap: 16px;
        margin: -12px -24px 8px;
        padding: 12px 24px;
        border-bottom: 1px solid var(--borderColor);
    }

    ${k} .tutorial-header > a {
        margin-left: auto;
    }

    ${k} .tutorial-notice {
        display: flex;
        align-items: flex-start;
        gap: 10px;
        margin-bottom: 20px;
        padding: 12px 16px;
        border: 1px solid var(--warningMain);
        border-radius: 10px;
        background: var(--backgroundCard);
    }

    ${k} .tutorial-notice svg {
        flex: 0 0 auto;
        margin-top: 2px;
    }

    ${k} .tutorial-step-copy {
        display: flex;
        align-items: flex-start;
        flex-direction: column;
        gap: 12px;
    }

    ${k} .tutorial-device-guide {
        margin-top: 24px;
    }

    ${k} .tutorial-device-instructions {
        display: grid;
        grid-template-columns: minmax(260px, 0.8fr) minmax(0, 1.2fr);
        align-items: start;
        gap: 28px;
    }

    ${k} .device-setup-grid {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        align-items: start;
        gap: 32px;
        margin-top: 28px;
    }

    ${k} .device-setup-grid section {
        min-width: 0;
    }

    ${k} .device-setup-grid ${ScreenshotClass} {
        margin-top: 18px;
    }

    ${k} .tutorial-fields {
        display: grid;
        grid-template-columns: minmax(180px, 0.7fr) minmax(320px, 1.3fr);
        align-items: start;
        gap: 24px;
    }

    ${k} .tutorial-form label {
        display: block;
        font-weight: 600;
    }

    ${k} .tutorial-form label > div {
        margin-top: 8px;
        font-weight: normal;
    }

    ${k} .tutorial-help-link {
        padding: 0;
        border: 0;
        background: transparent;
        color: var(--linkColor);
        font: inherit;
        cursor: pointer;
    }

    ${k} .tutorial-help-link:hover {
        color: var(--linkColorHover);
        text-decoration: underline;
    }
    
    ${k} .tutorial-form {
        margin-top: 32px;
    }

    ${k}[data-modal="true"] .tutorial-form {
        margin-top: 36px;
    }

    ${k} .tutorial-form label > input {
        height: 42px;
        margin-top: 8px;
    }

    ${k} ${ScreenshotClass} {
        display: block;
        width: 100%;
        max-height: none;
        padding: 4px;
        border: 2px solid var(--borderColorHover);
        border-radius: 10px;
        background: var(--backgroundDefault);
        box-shadow: var(--defaultShadow);
    }

    ${k} .tutorial-actions {
        display: flex;
        gap: 12px;
        height: 68px;
        padding: 16px 0;
        border-top: 1px solid var(--borderColor);
        background: var(--backgroundDefault);
        justify-content: center;
    }

    ${k}[data-modal="true"] .tutorial-actions {
        flex: 0 0 auto;
        height: auto;
        padding: 16px 24px;
        border-top: 0;
        background: var(--dialogToolbar);
        justify-content: flex-end;
    }

    @media (max-width: 760px) {
        ${k} {
            height: calc(100vh - 24px);
            min-height: 0;
        }

        ${k}[data-modal="true"] {
            height: 100%;
        }

        ${k} .tutorial-device-instructions, ${k} .device-setup-grid, ${k} .tutorial-fields {
            grid-template-columns: 1fr;
        }
    }
`);

function Screenshot(props: {src: string; alt?: string}): React.ReactNode {
    return <Image alt={props.alt ?? "Descriptive screenshot showing how to set up Syncthing."} className={ScreenshotClass}
        src={props.src} />
}

export default Overview;

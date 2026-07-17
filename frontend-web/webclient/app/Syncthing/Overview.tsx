import * as React from "react";
import {NavigateFunction, useNavigate} from "react-router-dom";
import {useRef, useReducer, useCallback, useEffect, useMemo, useState, useLayoutEffect} from "react";
import {usePage} from "@/Navigation/Redux";
import {default as ReactModal} from "react-modal";
import {useToggleSet} from "@/Utilities/ToggleSet";
import {Label, Input, Image, Box, Flex, Icon, Text, Button, ExternalLink, List} from "@/ui-components";
import MainContainer from "@/ui-components/MainContainer";
import TitledCard from "@/ui-components/HighlightedCard";
import {SyncthingConfig, SyncthingDevice, SyncthingFolder} from "./api";
import * as Sync from "./api";
import JobsApi, {JobState} from "@/UCloud/JobsApi";
import {prettyFilePath} from "@/Files/FilePath";
import {fileName} from "@/Utilities/FileUtilities";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {Operation, ShortcutKey} from "@/ui-components/Operation";
import {deepCopy} from "@/Utilities/CollectionUtilities";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {dialogStore} from "@/Dialog/DialogStore";
import {api as FilesApi, downloadFileContent, findSensitivity} from "@/UCloud/FilesApi";
import {randomUUID, doNothing, removeTrailingSlash, copyToClipboard, createHTMLElements} from "@/UtilityFunctions";
import {getQueryParam} from "@/Utilities/URIUtilities";
import {callAPI, callAPIWithErrorHandler} from "@/Authentication/DataHook";
import * as UCloud from "@/UCloud";
import syncthingScreen1 from "@/Assets/Images/syncthing/syncthing-1.png";
import syncthingScreen2 from "@/Assets/Images/syncthing/syncthing-2.png";
import syncthingScreen3 from "@/Assets/Images/syncthing/syncthing-3.png";
import syncthingScreen4 from "@/Assets/Images/syncthing/syncthing-4.png";

import {injectStyle, injectStyleSimple} from "@/Unstyled";
import FileBrowse from "@/Files/FileBrowse";
import {CardClass} from "@/ui-components/Card";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {ResourceBrowseFeatures, ResourceBrowser, ResourceBrowserOpts} from "@/ui-components/ResourceBrowser";
import AppRoutes from "@/Routes";
import {HTMLTooltip} from "@/ui-components/Tooltip";
import {divHtml, divText} from "@/Utilities/HTMLUtilities";
import {arrayToPage} from "@/Types";
import FileCollectionsApi, {FileCollection, FileCollectionFlags} from "@/UCloud/FileCollectionsApi";
import {useProjectId} from "@/Project/Api";
import {Client} from "@/Authentication/HttpClientInstance";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import Table, {TableCell, TableHeaderCell, TableRow} from "@/ui-components/Table";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {PageV2} from "@/UCloud";
import {addStandardDialog} from "@/UtilityComponents";
import {sendFailureNotification, sendSuccessNotification} from "@/Notifications";
import {DocumentTypography} from "@/ui-components/Markdown";

let permissionProblems: Record<string, boolean> = {};

// UI state management
// ================================================================================
type UIAction =
    | ReloadConfig
    | ReloadDeviceWizard
    | RemoveDevice
    | RemoveFolder
    | ResetAll
    | AddDevice
    | AddFolder
    ;

interface ReloadConfig {
    type: "ReloadConfig";
    config: SyncthingConfig;
    etag: string;
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

interface UIState {
    // NOTE(Dan): These are all potentially `undefined`. This makes it easier to define the default state. A value of
    // `undefined` should generally be interpreted as "not yet loaded".
    devices?: SyncthingDevice[];
    folders?: SyncthingFolder[];
    etag?: string;
    showDeviceWizard?: boolean;
    didAddFolder?: boolean;
    updateCount?: number;
}

function uiReducer(state: UIState, action: UIAction): UIState {
    const copy = deepCopy(state);
    switch (action.type) {
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
                copy.didAddFolder = true;
                copy.updateCount = (copy.updateCount ?? 0) + 1;
            }
            return copy;
        }

        case "ResetAll": {
            copy.devices = [];
            copy.folders = [];
            return copy;
        }
    }
}

async function onAction(_: UIState, action: UIAction, cb: ActionCallbacks): Promise<void> {
    switch (action.type) {
        case "ResetAll": {
            callAPIWithErrorHandler(Sync.api.resetConfiguration({provider: cb.provider, productId: cb.productId}));
            break;
        }
    }
}

interface ActionCallbacks {
    navigate: NavigateFunction;
    pureDispatch: (action: UIAction) => void;
    requestReload: () => void; // NOTE(Dan): use when it is difficult to rollback a change
    provider: string;
    productId: string;
}

interface OperationCallbacks {
    navigate: NavigateFunction;
    dispatch: (action: UIAction) => void;
    requestReload: () => void;
    permissionProblems: string[];
    provider: string;
    productId: string;
}

// Primary user interface
// ================================================================================
export const Overview: React.FunctionComponent = () => {
    return <NewOverview />;
};

const NewOverview: React.FunctionComponent = () => {
    const navigate = useNavigate();
    const [uiState, pureDispatch] = useReducer(uiReducer, {});
    const folderToggleSet = useToggleSet([]);
    const deviceToggleSet = useToggleSet([]);
    const didUnmount = useDidUnmount();

    const devices = uiState?.devices ?? [];
    const folders = uiState?.folders ?? [];

    const [selectedProduct, setSelectedProduct] = useState<UCloud.compute.ComputeProductSupportResolved | null>(null);

    const provider = getQueryParam(location.search, "provider");

    useEffect(() => {
        if (!provider) {
            navigate("/drives");
        }
    }, []);

    // UI callbacks and state manipulation
    const reload = useCallback(() => {
        if (!provider) return;
        Sync.fetchConfigAndEtag(provider).then(([config, etag]) => {
            if (didUnmount.current) return;
            pureDispatch({type: "ReloadConfig", config, etag});
        });

        Sync.fetchProducts(provider).then(product => {
            if (product.length > 0) {
                setSelectedProduct(product[0]);
            }
        });
    }, [pureDispatch]);

    const actionCb: ActionCallbacks = useMemo(() => ({
        navigate,
        pureDispatch,
        requestReload: reload,
        provider: provider ?? "",
        productId: selectedProduct?.product.name ?? "syncthing"
    }), [navigate, pureDispatch, reload]);

    const dispatch = useCallback((action: UIAction) => {
        onAction(uiState, action, actionCb);
        pureDispatch(action);
    }, [uiState, pureDispatch, actionCb]);

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
                            if (sensitivity == "SENSITIVE") {
                                sendFailureNotification("Folder marked as sensitive cannot be added to Syncthing");
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
    }, [dispatch]);

    // Effects
    useEffect(() => reload(), [reload]);

    useEffect(() => {
        folderToggleSet.uncheckAll();
    }, [uiState.folders]);

    useEffect(() => {
        deviceToggleSet.uncheckAll();
    }, [uiState.devices]);

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
    }, [uiState.updateCount]);

    usePage("File synchronization", SidebarTabId.FILES);
    useSetRefreshFunction(reload);

    let main: React.ReactNode;
    if (uiState.devices !== undefined && uiState.devices.length === 0) {
        main = <AddDeviceWizard onDeviceAdded={onDeviceAdded} onWizardClose={closeWizard} />;
    } else {
        main = <Flex gap={"16px"} flexWrap={"wrap"}>
            {uiState.showDeviceWizard !== true ? null :
                <ReactModal
                    isOpen={true}
                    style={largeModalStyle}
                    shouldCloseOnEsc
                    ariaHideApp={false}
                    onRequestClose={closeWizard}
                    className={CardClass}
                >
                    <AddDeviceWizard onDeviceAdded={onDeviceAdded} onWizardClose={closeWizard} />
                </ReactModal>
            }

            <TitledCard
                icon="heroComputerDesktop"
                title="My devices"
                flexBasis={600}
                flexGrow={1}
                subtitle={<Flex>
                    <ExternalLink href="https://syncthing.net/downloads/" mr="8px">
                        <Button><Icon name="open" mr="4px" size="14px" /> Download Syncthing</Button>
                    </ExternalLink>
                    <Button onClick={openWizard}>Add device</Button>
                </Flex>}
            >
                <Text color="textSecondary">
                    UCloud can synchronize files to any of your devices which run Syncthing.
                    Download and install Syncthing to add one of your devices here.
                </Text>

                <List mt="16px">
                    <DeviceBrowse devices={devices} dispatch={dispatch}
                        opts={{embedded: {disableKeyhandlers: true, hideFilters: false}}} />
                </List>
            </TitledCard>

            <TitledCard
                icon="heroFolder"
                title="Synchronized folders"
                flexBasis={600}
                flexGrow={1}
                subtitle={<Button onClick={openFileSelector}>Add Folder</Button>}
            >
                <Text mb="12px" color="textSecondary">
                    These are the files which will be synchronized to your devices.
                    Add a new folder to start synchronizing data.
                </Text>

                {uiState.folders?.length === 0 ?
                    <EmptyFolders onAddFolder={openFileSelector} /> :
                    <SyncedFolders folders={uiState.folders} dispatch={dispatch}
                        opts={{embedded: {disableKeyhandlers: true, hideFilters: false}}} />
                }
            </TitledCard>

            {folders.length > 0 && devices.length > 0 ?
                <ServerStatus productId={selectedProduct?.product.name ?? ""} providerId={provider ?? ""} reload={reload} />
                : null
            }
        </Flex>;
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
    reload: () => void
}> = (props) => {
    const homeDriveId = useHomeDrive(props.providerId, "");
    const cacheBust = useTimedRefresh(5000);
    const [status, setStatus] = useState<ServerStatusInfo | null>(null);
    const [restartRequested, setRestartRequested] = useState(false);

    const doRestart = useCallback((e: React.SyntheticEvent) => {
        e.preventDefault();
        setRestartRequested(true);
        if (status !== null) {
            callAPIWithErrorHandler(Sync.api.restart({provider: props.providerId, productId: props.productId}))
                .then(doNothing);
        }
    }, [props.providerId, props.productId, status])

    const doFactoryReset = useCallback(async () => {
        await callAPI(
            Sync.api.resetConfiguration({provider: props.providerId, productId: props.productId})
        );

        props.reload();
    }, [props.providerId, props.productId, props.reload]);

    useEffect(() => {
        setRestartRequested(false);
    }, [status?.state]);

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

    return <TitledCard
        icon={"heroArrowsUpDown"}
        title={"Server status"}
        flexBasis={600}
        flexGrow={1}
    >
        {status !== null ? null : <>
            <Flex gap={"16px"} alignItems={"center"} flexDirection={"column"}>
                <div><i>Syncthing is currently starting up. This process may take a few minutes.</i></div>
                <div><HexSpin size={48} /></div>
            </Flex>
        </>}
        {status === null ? null : <>
            <Table tableType={"presentation"}>
                <tbody>
                    <TableRow>
                        <TableHeaderCell width={"200px"}>Status</TableHeaderCell>
                        <TableCell>
                            {!restartRequested && status.state === "RUNNING" ? <>
                                <Icon name={"heroCheck"} color={"successMain"} /> Your Syncthing server is currently running.
                                (<a style={{color: "var(--primaryMain)"}} href={"#"} onClick={doRestart}>Restart</a>)
                            </> : <Flex gap={"8px"} alignItems={"center"}>
                                <HexSpin size={16} margin={"0"} /> <Box flexGrow={1}>Your Syncthing server is currently
                                    restarting.</Box>
                            </Flex>}
                        </TableCell>
                    </TableRow>
                    <TableRow>
                        <TableHeaderCell width={"200px"}>Job ID</TableHeaderCell>
                        <TableCell>
                            <ExternalLink href={AppRoutes.prefix + AppRoutes.jobs.view(status.jobId)} target={"_blank"}>
                                {status.jobId} <Icon name={"open"} size={10} />
                            </ExternalLink>
                        </TableCell>
                    </TableRow>
                    <TableRow>
                        <TableHeaderCell width={"200px"}>Device ID</TableHeaderCell>
                        <TableCell>{status.deviceId}</TableCell>
                    </TableRow>
                    <TableRow>
                        <TableHeaderCell width={"200px"}>Factory reset</TableHeaderCell>
                        <TableCell>
                            <ConfirmationButton
                                icon={"heroTrash"}
                                actionText={"Factory reset"}
                                onAction={doFactoryReset}
                                width={280}
                                mt="8px"
                                mb="4px"
                            />
                        </TableCell>
                    </TableRow>
                </tbody>
            </Table>
        </>}
    </TitledCard>
}

function SyncedFolders({folders, dispatch, opts}: {
    dispatch(action: UIAction): void;
    folders?: SyncthingFolder[];
    opts: ResourceBrowserOpts<SyncthingFolder>;
}): React.ReactNode {
    useEffect(() => {
        return () => {
            permissionProblems = {};
        }
    }, []);

    useEffect(() => {
        const validFolders = folders?.filter(it => it.ucloudPath != null);
        if (!validFolders || validFolders.length === 0) return;
        Promise.allSettled(validFolders.map(f =>
            callAPI(FilesApi.browse({path: f!.ucloudPath, itemsPerPage: 250}))
        )).then(promises => {
            promises.forEach((p, index) => {
                if (p.status === "fulfilled") {
                    if (p.value.items.some(f => f.status.unixOwner !== 11042)) {
                        permissionProblems[validFolders[index].id] = true;
                    }
                }
            });
            browserRef.current?.rerender();
        });
    }, [folders]);

    const mountRef = useRef<HTMLDivElement | null>(null);
    const browserRef = useRef<ResourceBrowser<SyncthingFolder>>(null);
    const navigate = useNavigate();

    const features: ResourceBrowseFeatures = {
        dragToSelect: true,
        showColumnTitles: true,
    };

    useEffect(() => {
        const browser = browserRef.current;
        if (browser && folders) {
            browser.registerPage(arrayToPage(folders), "/", true);
            browser.rerender();
        }
    }, [folders]);

    useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<SyncthingFolder>(mount, "SyncFolders", opts).init(browserRef, features, "/", browser => {
                browser.setColumns([{name: "Folder"}, {name: "", columnWidth: 0}, {
                    name: "",
                    columnWidth: 150
                }, {name: "", columnWidth: 50}]);

                browser.on("open", (oldPath, newPath, resource) => {
                    if (resource) {
                        navigate(AppRoutes.files.path(resource.ucloudPath));
                        return;
                    }
                });

                browser.on("renderRow", (folder, row, dims) => {
                    const {title} = browser.renderDefaultRow(row, folder.ucloudPath, {
                        color: "FtFolderColor",
                        color2: "FtFolderColor2"
                    });
                    prettyFilePath(folder.ucloudPath).then(it => {
                        title.innerText = it;
                    });

                    if (permissionProblems[folder.id]) {
                        const [permissionIcon, setPermissionIcon] = ResourceBrowser.defaultIconRenderer();
                        ResourceBrowser.icons.renderIcon({
                            name: "warning",
                            color: "errorMain",
                            color2: "primaryMain",
                            height: 64,
                            width: 64,
                        }).then(setPermissionIcon);

                        prettyFilePath(folder.ucloudPath).then(prettyPath =>
                            row.stat2.append(HTMLTooltip(permissionIcon, divText(`Some files in '${fileName(prettyPath)}' might not be synchronized due to lack of permissions.`), {tooltipContentWidth: 250}))
                        );
                    }

                    const cb = browser.dispatchMessage("fetchOperationsCallback", fn => fn()) as OperationCallbacks;

                    const trigger = browser.defaultButtonRenderer({
                        onClick: res => {
                            addStandardDialog({
                                title: "Remove from Syncthing?",
                                message: "This will remove the folder from synchronization",
                                onConfirm() {
                                    folderOperations.find(it => it.tag === "UNSYNC")?.onClick([res], cb);
                                },
                                confirmButtonColor: "errorMain",
                                cancelButtonColor: "primaryMain",
                                confirmText: "Remove",
                            })
                        },
                        show: () => true,
                        text: "",
                    }, folder, {
                        color: "errorMain", width: "20px", button: {
                            name: "close", size: 18, color: "fixedWhite", color2: "fixedWhite", ml: "8px"
                        }
                    });

                    if (trigger) {
                        const d = divHtml("Remove from sync");
                        const tooltip = HTMLTooltip(trigger, d, {tooltipContentWidth: 160});
                        row.stat3.append(tooltip);
                    }
                });

                browser.setEmptyIcon("ftFolder");

                browser.on("generateBreadcrumbs", () => {
                    return [];
                });

                browser.on("fetchOperationsCallback", () => ({
                    dispatch
                }));

                browser.on("fetchOperations", () => {
                    const selected = browser.findSelectedEntries();
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", c => c);
                    return folderOperations.filter(op => op.enabled(selected, callbacks as OperationCallbacks, selected));
                });

                browser.on("pathToEntry", syncFolder => syncFolder.id);
            });
        }
        if (opts?.reloadRef) {
            opts.reloadRef.current = () => {
                browserRef.current?.refresh();
            }
        }
    }, []);

    if (!opts?.embedded && !opts?.isModal) {
        useSetRefreshFunction(() => {
            browserRef.current?.refresh();
        });
    }

    return <div ref={mountRef} />;
}

function DeviceBrowse({devices, dispatch, opts}: {
    dispatch(action: UIAction): void;
    devices?: SyncthingDevice[],
    opts: ResourceBrowserOpts<SyncthingDevice>
}): React.ReactNode {
    const mountRef = useRef<HTMLDivElement | null>(null);
    const browserRef = useRef<ResourceBrowser<SyncthingDevice>>(null);

    const features: ResourceBrowseFeatures = {
        dragToSelect: true,
    };

    useEffect(() => {
        const browser = browserRef.current;
        if (browser && devices) {
            browser.registerPage(arrayToPage(devices), "/", true);
            browser.rerender();
        }
    }, [devices]);

    useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<SyncthingDevice>(mount, "Syncthing devices", opts).init(browserRef, features, "/", browser => {
                browser.setColumns([{name: "Folder"}, {name: "", columnWidth: 0}, {
                    name: "",
                    columnWidth: 150
                }, {name: "", columnWidth: 50}]);
                browser.on("renderRow", (device, row, dims) => {
                    browser.renderDefaultRow(row, device.label);

                    const trigger = createHTMLElements({
                        tagType: "div",
                        style: {marginRight: "24px", marginTop: "auto", marginBottom: "auto"},
                        className: DeviceBox,
                        handlers: {
                            onClick: e => {
                                e.stopPropagation();
                                copyToClipboard(device.deviceId);
                                sendSuccessNotification("Device ID copied to clipboard");
                            }
                        },
                        children: [{
                            tagType: "code",
                            innerText: device.deviceId.split("-")[0]
                        }]
                    });
                    row.stat3.append(trigger);
                    HTMLTooltip(trigger, divHtml(`Copy device ID to clipboard`), {tooltipContentWidth: 300});

                    const cb = browser.dispatchMessage("fetchOperationsCallback", fn => fn()) as OperationCallbacks;
                    const buttonTrigger = browser.defaultButtonRenderer({
                        onClick: res => {
                            addStandardDialog({
                                title: "Remove device from Syncthing?",
                                message: "This will remove the device from Syncthing.",
                                onConfirm() {
                                    deviceOperations.find(it => it.tag === "REMOVE")?.onClick([res], cb);
                                },
                                confirmButtonColor: "errorMain",
                                cancelButtonColor: "primaryMain",
                                confirmText: "Remove",
                            })
                        },
                        show: () => true,
                        text: "",
                    }, device, {
                        color: "errorMain", width: "20px", button: {
                            name: "close", size: 18, color: "fixedWhite", color2: "fixedWhite", ml: "8px"
                        }
                    });

                    if (buttonTrigger) {
                        const d = divHtml("Remove device");
                        const tooltip = HTMLTooltip(buttonTrigger, d, {tooltipContentWidth: 160});
                        row.stat3.append(tooltip);
                    }
                });

                browser.setEmptyIcon("heroComputerDesktop");

                browser.on("fetchOperationsCallback", () => ({
                    dispatch
                }));

                browser.on("fetchOperations", () => {
                    const selected = browser.findSelectedEntries();
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", f => f()) as OperationCallbacks;
                    return deviceOperations.filter(it => it.enabled(selected, callbacks, selected));
                });
            });
        }
        if (opts?.reloadRef) {
            opts.reloadRef.current = () => {
                browserRef.current?.refresh();
            }
        }
    }, []);

    if (!opts?.embedded && !opts?.isModal) {
        useSetRefreshFunction(() => {
            browserRef.current?.refresh();
        });
    }

    return <div ref={mountRef} />;
}

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
                    "Enter a name that will help you remember which device this is. For example: 'Work phone'."
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

    let tutorialContent: React.ReactNode = <></>;
    switch (tutorialStep) {
        case STEP_INTRO: {
            tutorialContent = (
                <>
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
                </>
            );
            break;
        }

        case STEP_ADD_DEVICE: {
            tutorialContent = (
                <>
                    <h2>Add your device</h2>
                    <p>
                        UCloud needs the Device ID generated by Syncthing before it can synchronize your files.
                    </p>

                    <section className="tutorial-device-guide">
                        <h3>Find your Device ID</h3>
                        <div className="tutorial-device-instructions">
                            <ol>
                                <li>Open Syncthing.</li>
                                <li>
                                    Open the <i>Actions</i> menu in the top-right corner and select <i>Show ID</i>.
                                </li>
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
                                <Input inputRef={deviceNameRef} placeholder={"My phone"} error={deviceNameError !== null} />
                                {!deviceNameError ?
                                    <Text color="textSecondary">
                                        A name to help you remember this device. For example: "Work phone".
                                    </Text> :
                                    <Text color="errorMain">{deviceNameError}</Text>
                                }
                            </Label>

                            <Label>
                                My device ID
                                <Input
                                    inputRef={deviceIdRef}
                                    placeholder="XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX"
                                    error={deviceIdError !== null}
                                />
                                {!deviceIdError ? null :
                                    <Text color="errorMain">{deviceIdError}</Text>
                                }
                            </Label>
                        </div>

                        <button type={"submit"} style={{display: "none"}}>Add device</button>
                    </form>
                </>
            );
            break;
        }
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
            {tutorialStep < 1 ? null : (
                <Button color="secondaryMain" onClick={tutorialPrevious}>Previous step</Button>
            )}
            <Button onClick={tutorialNext}>
                {tutorialStep === STEP_LAST ? "Add device" : "Next step"}
            </Button>
        </footer>
    </div>;
}

const deviceOperations: Operation<SyncthingDevice, OperationCallbacks>[] = [
    {
        text: "Copy device ID",
        icon: "id",
        enabled: selected => selected.length === 1,
        onClick: ([device]) => {
            copyToClipboard(device.deviceId);
            sendSuccessNotification("Device ID copied to clipboard!");
        },
        shortcut: ShortcutKey.C
    },
    {
        text: "Remove",
        icon: "trash",
        color: "errorMain",
        confirm: true,
        tag: "REMOVE",
        enabled: selected => selected.length > 0,
        onClick: (selected, cb) => {
            for (const device of selected) {
                cb.dispatch({type: "RemoveDevice", deviceId: device.deviceId});
            }
        },
        shortcut: ShortcutKey.R
    }
];

const folderOperations: Operation<SyncthingFolder, OperationCallbacks>[] = [
    {
        text: "Remove from sync",
        icon: "trash",
        color: "errorMain",
        confirm: true,
        tag: "UNSYNC",
        enabled: selected => selected.length >= 1,
        onClick: (selected, cb) => {
            for (const file of selected) {
                cb.dispatch({type: "RemoveFolder", folderPath: file.ucloudPath});
            }
        },
        shortcut: ShortcutKey.R
    }
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
                        <Button onClick={onAddFolder}>Add folder</Button>
                    </Flex>
                </li>
            }

            <li>
                <Flex>
                    <Box flexGrow={1}>
                        <p><b>Open Syncthing</b></p>
                        <p>
                            A pop-up will appear, saying that UCloud wants to connect.
                            Click the <i>Add device</i> button, then <i>Save</i> in the window that appears. <br />
                            <b>Note: it can take a few minutes before the pop-up appears.</b>
                        </p>
                    </Box>
                    <Box pl={40}>
                        <Screenshot src={syncthingScreen2} />
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
                        <Screenshot src={syncthingScreen3} />
                    </Box>
                </Flex>
            </li>

            <li>
                <p><b>Synchronization should start within a few seconds of clicking <i>Add</i></b></p>
            </li>
        </TutorialList>

        <p>
            For more details see the{" "}
            <ExternalLink href="https://docs.cloud.sdu.dk/guide/synch.html">
                UCloud documentation
            </ExternalLink>.
        </p>
    </>
};

function TutorialList(props: React.PropsWithChildren): React.ReactNode {
    return <ol className={TutorialListClass} {...props} />
}

const TutorialListClass = injectStyle("tutorial-list", k => `
    ${k} {
        padding-top: 0.5em;
    }

    ${k} > li {
        padding: 0 0 1.5em 0.5em;
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
    
    ${k} .tutorial-form {
        margin-top: 32px;
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
        box-shadow: 0 10px 28px rgba(0, 0, 0, 0.12);
    }

    ${k} .tutorial-actions {
        display: flex;
        gap: 12px;
        padding: 16px 0;
        border-top: 1px solid var(--borderColor);
        background: var(--backgroundDefault);
        height: 68px;
        justify-content: center;
    }

    @media (max-width: 760px) {
        ${k} {
            height: calc(100vh - 24px);
            min-height: 0;
        }

        ${k} .tutorial-device-instructions, ${k} .tutorial-fields {
            grid-template-columns: 1fr;
        }
    }
`);

function Screenshot(props: {src: string; alt?: string}): React.ReactNode {
    return <Image alt={props.alt ?? "Descriptive screenshot showing how to set up Syncthing."} className={ScreenshotClass}
        src={props.src} />
}

const DeviceBox = injectStyleSimple("device-box", `
    cursor: pointer;
    user-select: none;
    -webkit-user-select: none;
`);


export default Overview;

import * as React from "react";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import * as Heading from "@/ui-components/Heading";
import {Box, Button, Card, Flex, Icon, Input, Link} from "@/ui-components";
import {classConcat, injectStyle} from "@/Unstyled";
import {dateToString} from "@/Utilities/DateUtilities";
import {copyToClipboard, displayErrorMessageOrDefault, doNothing, shortUUID} from "@/UtilityFunctions";
import {bulkRequestOf} from "@/UtilityFunctions";
import {callAPI, useCloudCommand} from "@/Authentication/DataHook";
import {
    api as JobsApi,
    ComputeSupport,
    InteractiveSession,
    Job,
    JobState,
    JobStatus,
    JobUpdate,
} from "@/UCloud/JobsApi";
import {ResolvedSupport} from "@/UCloud/ResourceApi";
import {ProviderTitle} from "@/Providers/ProviderTitle";
import TabbedCard, {TabbedCardTab} from "@/ui-components/TabbedCard";
import {addStandardDialog} from "@/UtilityComponents";
import {SafeLogo} from "@/Applications/AppToolLogo";
import {RichSelect} from "@/ui-components/RichSelect";
import {VirtualMachineFolders} from "./VirtualMachineFolders";
import {BulkResponse, compute} from "@/UCloud";
import {format} from "date-fns";
import * as JobViz from "@/Applications/Jobs/JobViz";
import {useJobVizProperties} from "@/Applications/Jobs/JobViz/StreamProcessor";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {PublicLinkBrowse} from "@/Applications/PublicLinks/PublicLinkBrowse";
import {PrivateNetworkBrowse} from "@/Applications/PrivateNetwork/PrivateNetworkBrowse";
import {NetworkIPBrowse} from "@/Applications/NetworkIP/NetworkIPBrowse";
import {VirtualMachineRestartReminder} from "./VirtualMachineRestartReminder";
import {VirtualMachineIconButton} from "@/Applications/Jobs/VirtualMachineIconButton";
import {HeroHeaderCard, HeroHeaderGrid, HeroMetric} from "@/Applications/Jobs/HeroHeader";
import {SplitDropdownTrigger, VmActionItem, VmActionRow, VmActionSplitButton} from "@/Applications/Jobs/VmActionSplitButton";
import PublicLinkApi, {PublicLink} from "@/UCloud/PublicLinkApi";
import PrivateNetworkApi, {PrivateNetwork} from "@/UCloud/PrivateNetworkApi";
import NetworkIPApi, {NetworkIP} from "@/UCloud/NetworkIPApi";
import ReactModal from "react-modal";
import {CardClass} from "@/ui-components/Card";
import {ResourceBrowseHeaderControls} from "@/ui-components/ResourceBrowser";
import AppParameterValue = compute.AppParameterValue;
import {RefreshButton} from "@/Navigation/UtilityBar";

interface InterfaceTarget {
    rank: number;
    type: "WEB" | "VNC";
    target?: string;
    port?: number;
    link?: string;
}

interface VmLogMessage {
    stdout?: string | null;
    stderr?: string | null;
    channel?: string | null;
}

interface VmUpdatesState {
    current: {
        logQueue: VmLogMessage[];
        subscriptions: (() => void)[];
    };
}

type OptimisticPowerState = "POWERING_ON" | "POWERING_OFF" | "RESTARTING";
function useResourcesById<T>(
    params: AppParameterValue[],
    retrieve: (id: string) => Promise<T>,
    setter: (newValue: Record<string, T>) => void,
) {
    useEffect(() => {
        let didCancel = false;

        (async () => {
            const entries = await Promise.all(Array.from(new Set(params.map(it => it["id"] as string))).map(async id => {
                try {
                    const link = await retrieve(id);
                    return [id, link] as const;
                } catch {
                    return [id, undefined] as const;
                }
            }));

            const result: Record<string, T> = {};
            for (const [id, resc] of entries) {
                if (resc) result[id] = resc;
            }

            if (!didCancel) setter(result);
        })();

        return () => {
            didCancel = true;
        }
    }, [params]);
}

export const VirtualMachineStatus: React.FunctionComponent<{
    job: Job;
    status: JobStatus;
    interfaceTargets: InterfaceTarget[];
    defaultInterfaceName?: string;
    updates: JobUpdate[];
    updatesState: VmUpdatesState;
}> = ({job, status, interfaceTargets, defaultInterfaceName, updates, updatesState}) => {
    const [loading, invokeCommand] = useCloudCommand();
    const [, invokeBackgroundCommand] = useCloudCommand();
    const [optimisticPowerState, setOptimisticPowerState] = useState<OptimisticPowerState | null>(null);
    const [statusDotCount, setStatusDotCount] = useState(0);
    const [publicLinkTargets, setPublicLinkTargets] = useState<InterfaceTarget[]>([]);
    const restartObservedNonRunningState = useRef(false);
    const [hasPendingFolderRestart, setHasPendingFolderRestart] = useState(false);
    const [hasPendingAccessRestart, setHasPendingAccessRestart] = useState(false);
    const stream = useMemo(() => new JobViz.StreamProcessor(), [job.id]);
    const streamProperties = useJobVizProperties(stream);

    const support = job.status.resolvedSupport
        ? (job.status.resolvedSupport as ResolvedSupport<never, ComputeSupport>).support
        : undefined;

    const supportsTerminal = support?.virtualMachine.terminal === true;
    const supportsSuspension = support?.virtualMachine.suspension === true;
    const supportsVnc = support?.virtualMachine.vnc === true;

    const resources = job.specification.resources ?? [];
    const ingresses = useMemo(() => {
        return resources.filter(it => it.type === "ingress") as compute.AppParameterValueNS.Ingress[];
    }, [resources]);
    const privateNetworks = useMemo(() => {
        return resources.filter(it => it.type === "private_network") as compute.AppParameterValueNS.PrivateNetwork[];
    }, [resources]);
    const publicIps = useMemo(() => {
        return resources.filter(it => it.type === "network") as compute.AppParameterValueNS.Network[];
    }, [resources]);
    const [accessIngresses, setAccessIngresses] = useState<compute.AppParameterValueNS.Ingress[]>(ingresses);
    const [accessPrivateNetworks, setAccessPrivateNetworks] = useState<compute.AppParameterValueNS.PrivateNetwork[]>(privateNetworks);
    const [accessPublicIps, setAccessPublicIps] = useState<compute.AppParameterValueNS.Network[]>(publicIps);
    const [publicLinksById, setPublicLinksById] = useState<Record<string, PublicLink>>({});
    const [privateNetworksById, setPrivateNetworksById] = useState<Record<string, PrivateNetwork>>({});
    const [publicIpsById, setPublicIpsById] = useState<Record<string, NetworkIP>>({});
    const [accessDialog, setAccessDialog] = useState<"ingress" | "private_network" | "network" | null>(null);

    useEffect(() => {
        setAccessIngresses(ingresses);
    }, [job.id, ingresses]);

    useEffect(() => {
        setAccessPrivateNetworks(privateNetworks);
    }, [job.id, privateNetworks]);

    useEffect(() => {
        setAccessPublicIps(publicIps);
    }, [job.id, publicIps]);

    useResourcesById(accessIngresses, id => callAPI(PublicLinkApi.retrieve({id})), setPublicLinksById);
    useResourcesById(accessPrivateNetworks, id => callAPI(PrivateNetworkApi.retrieve({id})), setPrivateNetworksById);
    useResourcesById(accessPublicIps, id => callAPI(NetworkIPApi.retrieve({id})), setPublicIpsById);

    const combinedInterfaceTargets = useMemo(() => {
        const all = [...interfaceTargets, ...publicLinkTargets];
        const seen = new Set<string>();
        return all.filter(it => {
            const key = `${it.link ?? ""}::${it.rank}::${it.target ?? ""}`;
            if (seen.has(key)) return false;
            seen.add(key);
            return true;
        });
    }, [interfaceTargets, publicLinkTargets]);

    const desktopTarget = useMemo(() => {
        let defaultInterfaceId = combinedInterfaceTargets.findIndex(link => !link.target && link.rank === 0);
        if (defaultInterfaceId < 0) {
            defaultInterfaceId = combinedInterfaceTargets.findIndex(it => !it.target);
            if (defaultInterfaceId < 0) defaultInterfaceId = 0;
        }
        return combinedInterfaceTargets[defaultInterfaceId];
    }, [combinedInterfaceTargets]);

    const sshCommand = useMemo(() => parseSshCommand(updates), [updates]);

    const resolvedProduct = job.status.resolvedProduct as {
        cpu?: number;
        memoryInGigs?: number;
        gpu?: number;
        cpuModel?: string;
        memoryModel?: string;
        gpuModel?: string;
    } | undefined;

    const hardware = {
        cpu: resolvedProduct?.cpu ?? 1,
        memoryInGigs: resolvedProduct?.memoryInGigs ?? 1,
        gpu: resolvedProduct?.gpu ?? 0,
        cpuModel: resolvedProduct?.cpuModel,
        memoryModel: resolvedProduct?.memoryModel,
        gpuModel: resolvedProduct?.gpuModel,
    };

    const activity = useMemo(() => {
        return updates
            .filter(update => {
                if (!update.status && !update.state) return false;
                if (update.status?.startsWith("Target:")) return false;
                if (update.status?.startsWith("SSH:")) return false;
                return true;
            })
            .slice(-12)
            .reverse();
    }, [updates]);

    const suspend = useCallback(async () => {
        setOptimisticPowerState("POWERING_OFF");
        try {
            await invokeCommand(JobsApi.suspend(bulkRequestOf({id: job.id})));
        } catch (e) {
            setOptimisticPowerState(null);
            displayErrorMessageOrDefault(e, "Failed to power off VM.");
        }
    }, [invokeCommand, job.id]);

    const confirmSuspend = useCallback(() => {
        addStandardDialog({
            title: "Power off virtual machine?",
            message: "The VM will be suspended and can be powered on again.",
            confirmText: "Power off",
            confirmButtonColor: "warningMain",
            cancelButtonColor: "secondaryMain",
            onConfirm: suspend,
        });
    }, [suspend]);

    const unsuspend = useCallback(async () => {
        setOptimisticPowerState("POWERING_ON");
        try {
            await invokeCommand(JobsApi.unsuspend(bulkRequestOf({id: job.id})));
        } catch (e) {
            setOptimisticPowerState(null);
            displayErrorMessageOrDefault(e, "Failed to power on VM.");
        }
    }, [invokeCommand, job.id]);

    const terminate = useCallback(async () => {
        setOptimisticPowerState("POWERING_OFF");
        try {
            await invokeCommand(JobsApi.terminate(bulkRequestOf({id: job.id})));
        } catch (e) {
            setOptimisticPowerState(null);
            displayErrorMessageOrDefault(e, "Failed to stop VM.");
        }
    }, [invokeCommand, job.id]);

    const restart = useCallback(async () => {
        setOptimisticPowerState("RESTARTING");
        restartObservedNonRunningState.current = false;
        try {
            await invokeCommand(JobsApi.suspend(bulkRequestOf({id: job.id})));
            await invokeCommand(JobsApi.unsuspend(bulkRequestOf({id: job.id})));
            setHasPendingFolderRestart(false);
            setHasPendingAccessRestart(false);
        } catch (e) {
            setOptimisticPowerState(null);
            displayErrorMessageOrDefault(e, "Failed to restart VM.");
        }
    }, [invokeCommand, job.id]);

    const confirmRestart = useCallback(() => {
        addStandardDialog({
            title: "Restart virtual machine?",
            message: "This will power off and then power on the VM.",
            confirmText: "Restart",
            confirmButtonColor: "warningMain",
            cancelButtonColor: "secondaryMain",
            onConfirm: restart,
        });
    }, [restart]);

    const confirmTerminate = useCallback(() => {
        addStandardDialog({
            title: "Stop and delete virtual machine?",
            message: "This will terminate the VM and cannot be undone.",
            confirmText: "Delete VM",
            confirmButtonColor: "errorMain",
            cancelButtonColor: "secondaryMain",
            onConfirm: terminate,
        });
    }, [terminate]);

    const isTerminalState = status.state === "SUCCESS" || status.state === "FAILURE" || status.state === "EXPIRED";
    const isSuspended = status.state === "SUSPENDED";
    const isPowerTransitioning = optimisticPowerState != null;
    const powerActionDisabled = loading || isPowerTransitioning || isTerminalState;
    const showRuntimePanels = !isTerminalState;
    const effectiveState: JobState | OptimisticPowerState = optimisticPowerState ?? status.state;
    const appTitle = job.specification.name ?? job.status.resolvedApplication?.metadata?.title ?? "Virtual machine";
    const appVersion = job.status.resolvedApplication?.metadata?.version ?? "";
    const alternativeInterfaces = useMemo(() => {
        return combinedInterfaceTargets.filter(it => it !== desktopTarget && it.link);
    }, [combinedInterfaceTargets, desktopTarget]);
    const hasLaunchMenu = alternativeInterfaces.length > 0 || supportsTerminal;

    const launchMenuItems = useMemo<VmActionItem[]>(() => {
        const result: VmActionItem[] = alternativeInterfaces.map(it => ({
            key: `iface:${it.link}`,
            value: it.target ?? ((defaultInterfaceName ?? "Open interface") + ` (Node ${it.rank + 1})`),
            icon: "heroArrowTopRightOnSquare",
            color: "textPrimary",
        }));
        if (supportsTerminal) {
            result.push({key: "terminal", value: "Open terminal", icon: "heroCommandLine", color: "textPrimary"});
        }
        return result;
    }, [alternativeInterfaces, defaultInterfaceName, supportsTerminal]);

    const dangerMenuItems = useMemo<VmActionItem[]>(() => {
        const items: VmActionItem[] = [];
        if (supportsSuspension && !isTerminalState && !isSuspended) {
            items.push({key: "restart", value: "Restart", icon: "heroArrowPath", color: "warningMain"});
        }

        items.push({key: "delete", value: "Stop and delete VM", icon: "trash", color: "errorMain"});
        return items;
    }, [isSuspended, isTerminalState, supportsSuspension]);

    const onSelectLaunchMenuItem = useCallback((item: VmActionItem) => {
        if (item.key === "terminal") {
            window.open(`/app/applications/shell/${job.id}/0?hide-frame`, "_blank");
            return;
        }
        if (item.key.startsWith("iface:")) {
            window.open(item.key.substring("iface:".length), "_blank");
        }
    }, [job.id]);

    const onSelectDangerMenuItem = useCallback((item: VmActionItem) => {
        switch (item.key) {
            case "restart":
                confirmRestart();
                break;
            case "delete":
                confirmTerminate();
                break;
        }
    }, [confirmRestart, confirmTerminate]);

    const onFolderAdded = useCallback(async (newFolders: compute.AppParameterValueNS.File[], addedFolder: compute.AppParameterValueNS.File) => {
        try {
            await invokeCommand(JobsApi.attachResource({
                jobId: job.id,
                resource: addedFolder,
            }));
            setHasPendingFolderRestart(true);
        } catch (e) {
            displayErrorMessageOrDefault(e, "Failed to attach folder.");
            throw e;
        }
    }, [invokeCommand, job.id]);

    const onFolderRemoved = useCallback(async (newFolders: compute.AppParameterValueNS.File[], removedFolder: compute.AppParameterValueNS.File) => {
        try {
            await invokeCommand(JobsApi.detachResource({
                jobId: job.id,
                resource: removedFolder,
            }));
            setHasPendingFolderRestart(true);
        } catch (e) {
            displayErrorMessageOrDefault(e, "Failed to detach folder.");
            throw e;
        }
    }, [invokeCommand, job.id]);

    const setAccessResourcesByType = useCallback((resource: compute.AppParameterValue, action: "attach" | "detach") => {
        const key = vmAccessResourceKey(resource);
        const update = <T extends compute.AppParameterValue>(items: T[]): T[] => {
            if (action === "attach") {
                if (items.some(item => vmAccessResourceKey(item) === key)) return items;
                return [...items, resource as T];
            } else {
                return items.filter(item => vmAccessResourceKey(item) !== key);
            }
        };

        if (resource.type === "ingress") setAccessIngresses(prev => update(prev));
        if (resource.type === "private_network") setAccessPrivateNetworks(prev => update(prev));
        if (resource.type === "network") setAccessPublicIps(prev => update(prev));
    }, []);

    const attachAccessResource = useCallback(async (resource: compute.AppParameterValue, extra?: string) => {
        setAccessResourcesByType(resource, "attach");
        try {
            if (resource.type === "ingress" && extra) {
                resource.port = parseInt(extra);
            }

            await invokeCommand(JobsApi.attachResource({jobId: job.id, resource}));
            setHasPendingAccessRestart(true);
        } catch (e) {
            setAccessResourcesByType(resource, "detach");
            displayErrorMessageOrDefault(e, "Failed to attach resource.");
            throw e;
        }
    }, [invokeCommand, job.id, setAccessResourcesByType]);

    const detachAccessResource = useCallback(async (resource: compute.AppParameterValue) => {
        setAccessResourcesByType(resource, "detach");
        try {
            await invokeCommand(JobsApi.detachResource({jobId: job.id, resource}));
            setHasPendingAccessRestart(true);
        } catch (e) {
            setAccessResourcesByType(resource, "attach");
            displayErrorMessageOrDefault(e, "Failed to detach resource.");
        }
    }, [invokeCommand, job.id, setAccessResourcesByType]);

    const publicLinkSelector = useMemo(() => {
        return (
            onSelect: (resource: compute.AppParameterValue, title: string) => void,
            headerControls?: ResourceBrowseHeaderControls,
        ) => {
            return <PublicLinkBrowse
                headerControls={headerControls}
                opts={{
                    selection: {
                        text: "Attach",
                        onClick: (link) => {
                            onSelect({type: "ingress", id: link.id}, link.specification.domain);
                        },
                        show: res => {
                            if (res.specification.product.provider !== job.specification.product.provider) {
                                return "Public link must be on the same provider as this VM";
                            }

                            const alreadyAttached = accessIngresses.some(it => it.id === res.id);
                            if (alreadyAttached) return "Public link is already attached";

                            return res.status.boundTo.length === 0 || "This public link is already in use";
                        },
                    },
                    embedded: {
                        hideFilters: true,
                        disableKeyhandlers: true,
                    },
                    isModal: true,
                    additionalFilters: {
                        filterProvider: job.specification.product.provider,
                        filterState: "READY",
                    },
                }}
            />;
        };
    }, [accessIngresses]);

    const openPublicLinksManager = useCallback(() => {
        setAccessDialog("ingress");
    }, []);

    const privateNetworkSelector = useMemo(() => {
        return (
            onSelect: (resource: compute.AppParameterValue, title: string) => void,
            headerControls?: ResourceBrowseHeaderControls,
        ) => {
            return <PrivateNetworkBrowse
                headerControls={headerControls}
                opts={{
                    selection: {
                        text: "Attach",
                        onClick: network => {
                            onSelect({type: "private_network", id: network.id}, network.specification.name);
                        },
                        show: res => {
                            if (res.specification.product.provider !== job.specification.product.provider) {
                                return "Network must be on the same provider as this VM";
                            }

                            const alreadyAttached = accessPrivateNetworks.some(it => it.id === res.id);
                            if (alreadyAttached) return "Network is already attached";

                            return true;
                        },
                    },
                    embedded: {
                        hideFilters: true,
                        disableKeyhandlers: true,
                    },
                    isModal: true,
                    additionalFilters: {
                        filterProvider: job.specification.product.provider,
                    },
                }}
            />;
        };
    }, [accessPrivateNetworks]);

    const openPrivateNetworksManager = useCallback(() => {
        setAccessDialog("private_network");
    }, []);

    const publicIpSelector = useMemo(() => {
        return (
            onSelect: (resource: compute.AppParameterValue, title: string) => void,
            headerControls?: ResourceBrowseHeaderControls,
        ) => {
            return <NetworkIPBrowse
                headerControls={headerControls}
                opts={{
                    selection: {
                        text: "Attach",
                        onClick: ip => {
                            onSelect({type: "network", id: ip.id}, ip.status.ipAddress ?? "");
                        },
                        show: res => {
                            if (res.specification.product.provider !== job.specification.product.provider) {
                                return "Public IP must be on the same provider as this VM";
                            }

                            const alreadyAttached = accessPublicIps.some(it => it.id === res.id);
                            if (alreadyAttached) return "Public IP is already attached";

                            return res.status.boundTo.length === 0 || "This public IP is already in use";
                        },
                    },
                    embedded: {
                        hideFilters: true,
                        disableKeyhandlers: true,
                    },
                    isModal: true,
                    additionalFilters: {
                        filterProvider: job.specification.product.provider,
                        filterState: "READY",
                    },
                }}
            />;
        };
    }, [accessPublicIps]);

    const openPublicIpsManager = useCallback(() => {
        setAccessDialog("network");
    }, []);

    const interfaceDisabled = !desktopTarget?.link || isTerminalState;

    const powerTone: "success" | "warning" | "neutral" = !supportsSuspension || isTerminalState
        ? "neutral"
        : isSuspended
            ? "success"
            : "warning";
    const powerButtonColor = powerTone === "success" ? "successMain" : "warningMain";

    useEffect(() => {
        if (status.state !== "RUNNING" || ingresses.length === 0) {
            setPublicLinkTargets([]);
            return;
        }

        const requests = ingresses.map(ingress => ({
            sessionType: "WEB" as const,
            id: job.id,
            rank: 0,
            port: ingress.port,
        }));

        invokeBackgroundCommand<BulkResponse<InteractiveSession>>(
            JobsApi.openInteractiveSession(bulkRequestOf(...requests)),
            {defaultErrorHandler: false}
        )
            .then(result => {
                const responses = result?.responses ?? [];
                const newTargets: InterfaceTarget[] = [];

                for (let i = 0; i < responses.length; i++) {
                    const session = responses[i]?.session;
                    const ingress = ingresses[i];
                    if (!session || !ingress || !("redirectClientTo" in session)) continue;

                    newTargets.push({
                        rank: session.rank,
                        type: "WEB",
                        port: ingress.port,
                        link: session.redirectClientTo,
                    });
                }

                setPublicLinkTargets(newTargets);
            })
            .catch(() => {
                setPublicLinkTargets([]);
            });
    }, [status.state, job.id, ingresses, invokeBackgroundCommand]);

    useEffect(() => {
        if (!isPowerTransitioning) {
            setStatusDotCount(0);
            return;
        }

        const timer = window.setInterval(() => {
            setStatusDotCount(prev => ((prev + 1) % 4));
        }, 450);

        return () => {
            window.clearInterval(timer);
        };
    }, [isPowerTransitioning]);

    useEffect(() => {
        if (optimisticPowerState === "POWERING_ON" && status.state === "RUNNING") {
            setOptimisticPowerState(null);
            return;
        }

        if (optimisticPowerState === "RESTARTING") {
            if (status.state !== "RUNNING") {
                restartObservedNonRunningState.current = true;
                return;
            }

            if (restartObservedNonRunningState.current) {
                setOptimisticPowerState(null);
                restartObservedNonRunningState.current = false;
                return;
            }
        }

        if (optimisticPowerState === "POWERING_OFF" && status.state === "SUSPENDED") {
            setOptimisticPowerState(null);
            return;
        }

        if (["FAILURE", "SUCCESS", "EXPIRED"].includes(status.state)) {
            setOptimisticPowerState(null);
        }
    }, [optimisticPowerState, status.state]);

    useEffect(() => {
        const logListener = () => {
            const s = updatesState.current;
            if (!s) return;

            const newLogQueue: VmLogMessage[] = [];
            for (const l of s.logQueue) {
                if (l.channel === "ui" || l.channel === "data") {
                    stream.accept(l.stdout ?? l.stderr ?? "");
                } else if (l.channel != null) {
                    stream.acceptGenericData(l.stdout ?? l.stderr ?? "", l.channel);
                } else {
                    newLogQueue.push(l);
                }
            }

            s.logQueue = newLogQueue;
        };

        updatesState.current?.subscriptions?.push(logListener);
        logListener();

        return () => {
            const subscriptions = updatesState.current?.subscriptions;
            if (!subscriptions) return;
            const idx = subscriptions.indexOf(logListener);
            if (idx >= 0) subscriptions.splice(idx, 1);
        };
    }, [job.id, stream, updatesState]);

    useEffect(() => {
        if (updates.length > 0) {
            const hasBeenRunningBefore = updates.some(it => it.state === "RUNNING");
            if (!hasBeenRunningBefore) {
                setOptimisticPowerState("POWERING_ON");
            }
        }
    }, [status.state, updates]);

    return <div className={VmLayout}>
        <ReactModal
            isOpen={accessDialog != null}
            ariaHideApp={false}
            style={largeModalStyle}
            shouldCloseOnEsc
            shouldCloseOnOverlayClick
            onRequestClose={() => setAccessDialog(null)}
            className={CardClass}
        >
            {accessDialog !== "ingress" ? null :
                <VmAccessResourceManagerDialog
                    title="Manage public links"
                    selectTitle="Select public link"
                    attached={accessIngresses}
                    emptyMessage="No public links are currently attached to this VM."
                    renderSelector={publicLinkSelector}
                    onAttach={attachAccessResource}
                    onRemove={detachAccessResource}
                    labelForResource={resource => vmAccessResourceLabel(resource, publicLinksById, privateNetworksById, publicIpsById)}
                    inlineCreationLabel={"Port"}
                />
            }
            {accessDialog !== "private_network" ? null :
                <VmAccessResourceManagerDialog
                    title="Manage connected networks"
                    selectTitle="Select private network"
                    attached={accessPrivateNetworks}
                    emptyMessage="No private networks are currently attached to this VM."
                    renderSelector={privateNetworkSelector}
                    onAttach={attachAccessResource}
                    onRemove={detachAccessResource}
                    labelForResource={resource => vmAccessResourceLabel(resource, publicLinksById, privateNetworksById, publicIpsById)}
                />
            }
            {accessDialog !== "network" ? null :
                <VmAccessResourceManagerDialog
                    title="Manage public IPs"
                    selectTitle="Select public IP"
                    attached={accessPublicIps}
                    emptyMessage="No public IPs are currently attached to this VM."
                    renderSelector={publicIpSelector}
                    onAttach={attachAccessResource}
                    onRemove={detachAccessResource}
                    labelForResource={resource => vmAccessResourceLabel(resource, publicLinksById, privateNetworksById, publicIpsById)}
                />
            }
        </ReactModal>
        <Card className={classConcat(HeroHeaderCard, effectiveState)} p="24px">
            <Flex alignItems="center" gap="16px" flexWrap="wrap">
                <Box>
                    <Flex gap={"8px"} alignItems={"center"}>

                        <SafeLogo name={job?.specification?.application?.name ?? "unknown"}
                                  type={"APPLICATION"}
                                  size={"32px"}/>

                        <Heading.h2>{appTitle} {appVersion}</Heading.h2>
                    </Flex>
                </Box>

                <Box flexGrow={1}/>

                <Flex flexDirection="row" alignItems="center" gap="10px">
                    <Flex>
                        <Link to={desktopTarget?.link ?? ""} target="_blank" aria-disabled={interfaceDisabled}>
                            <Button disabled={interfaceDisabled} attachedLeft={hasLaunchMenu}>
                                <Icon name="heroComputerDesktop" mr="8px"/>
                                Open {desktopTarget?.target ?? defaultInterfaceName ?? (supportsVnc ? "desktop" : "interface")}
                            </Button>
                        </Link>

                        {!hasLaunchMenu ? null : (
                            <RichSelect
                                items={launchMenuItems}
                                keys={["value"]}
                                RenderRow={VmActionRow}
                                onSelect={onSelectLaunchMenuItem}
                                showSearchField={false}
                                dropdownWidth="300px"
                                matchTriggerWidth={false}
                                trigger={
                                    <div className={SplitDropdownTrigger} data-disabled={interfaceDisabled}>
                                        <Icon name="heroChevronDown"/>
                                    </div>
                                }
                            />
                        )}
                    </Flex>

                    <VmActionSplitButton
                        tone={powerTone}
                        disabled={powerActionDisabled}
                        buttonColor={supportsSuspension && !isTerminalState ? powerButtonColor : "primaryMain"}
                        buttonIcon={supportsSuspension && !isTerminalState ? "heroPower" : "heroCog6Tooth"}
                        buttonText={supportsSuspension && !isTerminalState
                            ? (isSuspended
                                ? (optimisticPowerState === "POWERING_ON" ? "Powering on..." : "Power on")
                                : (optimisticPowerState === "POWERING_OFF" ? "Powering off..." : "Power off"))
                            : "Actions"
                        }
                        onButtonClick={supportsSuspension && !isTerminalState
                            ? (isSuspended ? unsuspend : confirmSuspend)
                            : doNothing
                        }
                        menuItems={dangerMenuItems}
                        onSelectMenuItem={onSelectDangerMenuItem}
                    />
                </Flex>

                <div className={StatusBadge} data-state={effectiveState}>
                    <span>{stateToTitle(effectiveState)}</span>
                    {!isPowerTransitioning ? null : (
                        <span className={StatusDots}>{".".repeat(statusDotCount)}</span>
                    )}
                </div>
            </Flex>

            <div className={HeroHeaderGrid}>
                <HeroMetric title={"ID"}>{shortUUID(job.id)}</HeroMetric>
                <HeroMetric title="Provider">
                    <ProviderTitle providerId={job.specification.product.provider}/>
                </HeroMetric>
                <HeroMetric title="Machine type">{job.specification.product.id}</HeroMetric>
                <HeroMetric title="Launched by">{job.owner.createdBy}</HeroMetric>
                <HeroMetric title="Created at">{dateToString(job.createdAt)}</HeroMetric>
                <HeroMetric title="Started at">{status.startedAt ? dateToString(status.startedAt) : "Pending"}</HeroMetric>
            </div>
        </Card>

        <div className={VmContentGrid}>
            {!showRuntimePanels ? null : (
                <TabbedCard
                    style={{minHeight: "240px"}}
                    rightControls={!hasPendingAccessRestart ? undefined : (
                        <VirtualMachineRestartReminder
                            tooltip="Restart the machine for changes to take effect"
                            ariaLabel="Restart required for changes"
                            onClick={confirmRestart}
                        />
                    )}
                >
                    <TabbedCardTab icon="heroSignal" name="Access">
                        <div className={VmTabBody}>
                            <div className={VmDetails}>
                                <dt>SSH access</dt>
                                <dd className={VmDetailRowWithAction}>
                                    <code
                                        style={{cursor: "pointer"}}
                                        onClick={() => sshCommand ? copyToClipboard(sshCommand) : undefined}
                                        title={sshCommand ?? undefined}
                                    >
                                        {sshCommand ?? "Not announced by provider yet"}
                                    </code>
                                    <VirtualMachineIconButton
                                        tooltip={"Copy to clipboard"}
                                        onClick={() => sshCommand ? copyToClipboard(sshCommand) : undefined}
                                        icon={"heroDocumentDuplicate"}
                                    />
                                </dd>

                                <dt>Public links</dt>
                                <dd className={VmDetailRowWithAction}>
                                    <span>{accessIngresses.length > 0 ? accessIngresses.length : "None"}</span>
                                    <VirtualMachineIconButton tooltip={"Manage"} onClick={openPublicLinksManager} icon={"heroWrenchScrewdriver"} />
                                </dd>

                                <dt>Public IPs</dt>
                                <dd className={VmDetailRowWithAction}>
                                    <span>{accessPublicIps.length > 0 ? accessPublicIps.length : "None"}</span>
                                    <VirtualMachineIconButton tooltip={"Manage"} onClick={openPublicIpsManager} icon={"heroWrenchScrewdriver"} />
                                </dd>

                                <dt>Connected networks</dt>
                                <dd className={VmDetailRowWithAction}>
                                    <span>{accessPrivateNetworks.length > 0 ? accessPrivateNetworks.length : "None"}</span>
                                    <VirtualMachineIconButton tooltip={"Manage"} onClick={openPrivateNetworksManager} icon={"heroWrenchScrewdriver"} />
                                </dd>
                            </div>
                        </div>
                    </TabbedCardTab>
                </TabbedCard>
            )}

            {!showRuntimePanels ? null : (
                <TabbedCard style={{minHeight: "240px"}}>
                    <TabbedCardTab icon="heroCpuChip" name="System information">
                        <div className={VmTabBody}>
                            <div className={VmDetails}>
                                <dt>Specs</dt>
                                <dd>
                                    {hardware.cpu}x {hardware.cpuModel ?? "vCPU"}
                                    {" | "}{hardware.memoryInGigs} GB {hardware.memoryModel ?? "RAM"}
                                    {hardware.gpu === 0 ? null : <>
                                        {" | "}{hardware.gpu}x {hardware.gpuModel ?? "GPUs"}
                                    </>}
                                </dd>

                                <dt>Private IP</dt>
                                <dd>{streamProperties["PrivateIPAddress"] ?? "Unknown"}</dd>

                                <dt>Operating system</dt>
                                <dd>{streamProperties["OperatingSystem"] ?? "Unknown"}</dd>

                                <dt>Kernel</dt>
                                <dd>{streamProperties["KernelVersion"] ?? "Unknown"}</dd>
                            </div>
                        </div>
                    </TabbedCardTab>
                </TabbedCard>
            )}

            {!showRuntimePanels ? null :
                <VirtualMachineFolders
                    jobId={job.id}
                    providerId={job.specification.product.provider}
                    parameters={job.specification.parameters}
                    resources={job.specification.resources}
                    onFolderAdded={onFolderAdded}
                    onFolderRemoved={onFolderRemoved}
                    showRestartIndicator={hasPendingFolderRestart}
                    onRestartRequested={confirmRestart}
                />
            }

            <TabbedCard style={{minHeight: "240px"}}>
                <TabbedCardTab icon="heroClock" name="Lifecycle">
                    <div className={classConcat(VmTabBody, VmLifecycleBody)}>
                        {activity.length === 0 ? (
                            <Box color="textSecondary">No lifecycle updates yet.</Box>
                        ) : (
                            <div className={VmTimeline}>
                                {activity.map((entry, idx) => (
                                    <div className={VmTimelineRow} key={`${entry.timestamp}-${idx}`}>
                                        <span>{format(entry.timestamp, "HH:mm:ss dd/MM/yy")}</span>
                                        <span>{renderUpdate(entry)}</span>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                </TabbedCardTab>
            </TabbedCard>

            {!showRuntimePanels ? null :
                <div className={VmMetricsRow}>
                    <TabbedCard style={{minHeight: "240px"}}>
                        <JobViz.Renderer
                            processor={stream}
                            windows={[
                                JobViz.WidgetWindow.WidgetWindowMain,
                                JobViz.WidgetWindow.WidgetWindowAux1,
                                JobViz.WidgetWindow.WidgetWindowAux2,
                            ]}
                            tabsOnly={true}
                        />
                    </TabbedCard>
                </div>
            }
        </div>
    </div>;
};

function renderUpdate(update: JobUpdate): string {
    if (update.state) return stateToMessage[update.state];
    return update.status ?? "Status updated";
}

const VmAccessResourceManagerDialog: React.FunctionComponent<{
    title: string;
    selectTitle: string;
    attached: compute.AppParameterValue[];
    emptyMessage: string;
    renderSelector: (
        onSelect: (resource: compute.AppParameterValue, title: string) => void,
        headerControls?: ResourceBrowseHeaderControls,
    ) => React.ReactNode;
    onAttach: (resource: compute.AppParameterValue, inlineCreationValue?: string) => Promise<void>;
    onRemove: (resource: compute.AppParameterValue) => Promise<void>;
    labelForResource: (resource: compute.AppParameterValue) => string;
    inlineCreationLabel?: string;
}> = ({title, selectTitle, attached, emptyMessage, renderSelector, onAttach, onRemove, labelForResource, inlineCreationLabel}) => {
    const [isSelecting, setIsSelecting] = useState(false);
    const [inlineCreationValue, setInlineCreationValue] = useState("");
    const [inlineResourceBeingCreated, setInlineResourceBeingCreated] = useState<compute.AppParameterValue | null>(null);
    const [inlineTitle, setInlineTitle] = useState<string | null>(null);
    const [selectorRefresh, setSelectorRefresh] = useState<(() => void) | undefined>(undefined);
    const [selectorProjectSwitcherTarget, setSelectorProjectSwitcherTarget] = useState<HTMLDivElement | null>(null);

    const selectorHeaderControls = useMemo<ResourceBrowseHeaderControls>(() => ({
        setRefresh: refresh => setSelectorRefresh(() => refresh),
        projectSwitcherTarget: selectorProjectSwitcherTarget,
    }), [selectorProjectSwitcherTarget]);

    const closeInlineCreation = useCallback(() => {
        setInlineCreationValue("");
        setInlineResourceBeingCreated(null);
        setInlineTitle(null);
    }, []);

    const onInlineCreationConfirm = useCallback((e?: React.SyntheticEvent) => {
        e?.preventDefault();
        if (inlineResourceBeingCreated) {
            onAttach(inlineResourceBeingCreated, inlineCreationValue);
        }
        closeInlineCreation();
    }, [inlineResourceBeingCreated, inlineCreationValue, closeInlineCreation]);

    const onAddResource = useCallback(() => {
        setSelectorRefresh(undefined);
        setIsSelecting(true);
    }, []);

    const onSelectFromBrowse = useCallback((resource: compute.AppParameterValue, title: string) => {
        setSelectorRefresh(undefined);
        setIsSelecting(false);
        if (inlineCreationLabel) {
            setInlineResourceBeingCreated(resource);
            setInlineTitle(title);
        } else {
            onAttach(resource);
        }
    }, [onAttach, inlineCreationLabel]);

    const inlineCreationOnChange = useCallback((e: React.SyntheticEvent) => {
        setInlineCreationValue((e.target as HTMLInputElement).value);
    }, []);

    const onBackToManage = useCallback(() => {
        setSelectorRefresh(undefined);
        setIsSelecting(false);
    }, []);

    const onRemoveResource = useCallback((resource: compute.AppParameterValue) => {
        onRemove(resource);
    }, [onRemove]);

    if (isSelecting) {
        return <div className={VmAccessManagerDialogBody}>
            <div className={VmAccessManagerHeader}>
                <Heading.h3>
                    <Flex gap={"8px"} alignItems={"center"}>
                        <VirtualMachineIconButton tooltip={"Back"} onClick={onBackToManage} icon={"heroArrowLeft"} />
                        {selectTitle}
                    </Flex>
                </Heading.h3>
                <div className={VmAccessManagerControls}>
                    {selectorRefresh == null ? null : (
                        <RefreshButton loading={false} refresh={selectorRefresh} />
                    )}
                    <div ref={setSelectorProjectSwitcherTarget} />
                </div>
            </div>

            <div>{renderSelector(onSelectFromBrowse, selectorHeaderControls)}</div>
        </div>;
    }

    const isEmpty = inlineResourceBeingCreated === null && attached.length === 0;

    return <div className={VmAccessManagerDialogBody}>
        <div className={VmAccessManagerHeader}>
            <Heading.h3>{title}</Heading.h3>
            <div className={VmAccessManagerControls}>
                <VirtualMachineIconButton tooltip={"Attach resource"} onClick={onAddResource} icon={"heroPlus"} />
            </div>
        </div>

        {isEmpty ? (
            <Box color="textSecondary">{emptyMessage}</Box>
        ) : (
            <div className={VmAccessManagerList}>
                {inlineResourceBeingCreated === null ? null :
                    <Flex gap={"8px"} alignItems={"center"}>
                        <Box flexGrow={1}>{inlineTitle}</Box>
                        <form onSubmit={onInlineCreationConfirm}>
                            <Input autoFocus={true} width={"150px"} placeholder={inlineCreationLabel} value={inlineCreationValue} onChange={inlineCreationOnChange} />
                        </form>
                        <VirtualMachineIconButton
                            tooltip={"Confirm"}
                            onClick={onInlineCreationConfirm}
                            icon={"heroCheck"}
                            color={"successMain"}
                        />
                        <VirtualMachineIconButton
                            tooltip={"Cancel"}
                            onClick={closeInlineCreation}
                            icon={"heroMinus"}
                            color={"errorMain"}
                        />
                    </Flex>
                }
                {attached.map((resource, idx) => (
                    <Flex key={`${vmAccessResourceKey(resource)}-${idx}`} gap={"8px"}>
                        <Box flexGrow={1}>{labelForResource(resource)}</Box>
                        <VirtualMachineIconButton
                            tooltip={"Remove resource"}
                            onClick={() => onRemoveResource(resource)}
                            icon={"heroMinus"}
                        />
                    </Flex>
                ))}
            </div>
        )}
    </div>;
};

function vmAccessResourceKey(resource: compute.AppParameterValue): string {
    if (resource.type === "ingress") return `${resource.type}:${resource.id}`;
    if (resource.type === "private_network") return `${resource.type}:${resource.id}`;
    if (resource.type === "network") return `${resource.type}:${resource.id}`;
    return resource.type;
}

function vmAccessResourceLabel(
    resource: compute.AppParameterValue,
    publicLinksById: Record<string, PublicLink>,
    privateNetworksById: Record<string, PrivateNetwork>,
    publicIpsById: Record<string, NetworkIP>,
): string {
    if (resource.type === "ingress") {
        const link = publicLinksById[resource.id];
        const domain = link?.specification.domain ?? resource.id;
        return resource.port ? `${domain} (port ${resource.port})` : domain;
    }

    if (resource.type === "private_network") {
        const network = privateNetworksById[resource.id];
        return network?.specification.name || network?.specification.subdomain || resource.id;
    }

    if (resource.type === "network") {
        const ip = publicIpsById[resource.id];
        return ip?.status.ipAddress ?? resource.id;
    }

    return resource.type;
}

function parseSshCommand(updates: JobUpdate[]): string | null {
    for (let i = updates.length - 1; i >= 0; i--) {
        const status = updates[i].status;
        if (!status) continue;

        const lines = status.split("\n");
        for (const line of lines) {
            const aauPrefix = "SSH Access: ";
            if (line.startsWith(aauPrefix)) {
                return line.substring(aauPrefix.length);
            }

            if (line.startsWith("SSH:") && line.includes("Available at:")) {
                const idx = line.indexOf("Available at:");
                return line.substring(idx + "Available at:".length).trim();
            }
        }
    }

    return null;
}

const stateToMessage: Record<JobState | OptimisticPowerState, string> = {
    "POWERING_ON": "Your machine is starting up",
    "POWERING_OFF": "Your machine is shutting down",
    "RESTARTING": "Your machine is restarting",
    "IN_QUEUE": "Your machine is currently waiting in the queue",
    "RUNNING": "Your machine is now running",
    "SUSPENDED": "Your machine is powered off",
    "CANCELING": "Your machine is shutting down for deletion",
    "SUCCESS": "Your machine has been deleted",
    "FAILURE": "Your machine has been deleted",
    "EXPIRED": "Your machine has been deleted",
};

function stateToTitle(state: JobState | OptimisticPowerState): string {
    switch (state) {
        case "POWERING_ON":
            return "Powering on";
        case "POWERING_OFF":
            return "Powering off";
        case "RESTARTING":
            return "Restarting";
        case "IN_QUEUE":
            return "Starting";
        case "RUNNING":
            return "Running";
        case "SUSPENDED":
            return "Powered off";
        case "CANCELING":
            return "Stopping";
        case "SUCCESS":
        case "FAILURE":
        case "EXPIRED":
            return "Terminated";
        default:
            return "Unknown";
    }
}

const VmLayout = injectStyle("vm-layout", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        gap: 16px;
        margin-top: 20px;
    }
`);

const StatusBadge = injectStyle("vm-status-badge", k => `
    ${k} {
        display: inline-flex;
        align-items: center;
        border-radius: 999px;
        font-weight: 600;
        padding: 6px 12px;
        background: color-mix(in srgb, var(--primaryMain) 20%, transparent);
    }

    ${k}[data-state="RUNNING"] {
        background: color-mix(in srgb, var(--successMain) 24%, transparent);
    }

    ${k}[data-state="POWERING_ON"] {
        background: color-mix(in srgb, var(--successMain) 24%, transparent);
    }

    ${k}[data-state="POWERING_OFF"],
    ${k}[data-state="RESTARTING"],
    ${k}[data-state="SUSPENDED"] {
        background: color-mix(in srgb, var(--warningMain) 24%, transparent);
    }

    ${k}[data-state="SUCCESS"],
    ${k}[data-state="EXPIRED"],
    ${k}[data-state="FAILURE"] {
        background: color-mix(in srgb, var(--errorMain) 24%, transparent);
    }
`);

const StatusDots = injectStyle("vm-status-dots", k => `
    ${k} {
        display: inline-block;
        width: 2ch;
        text-align: left;
        white-space: pre;
    }
`);

const VmContentGrid = injectStyle("vm-content-grid", k => `
    ${k} {
        display: grid;
        gap: 16px;
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    @media (max-width: 1100px) {
        ${k} {
            grid-template-columns: 1fr;
        }
    }
`);

const VmMetricsRow = injectStyle("vm-metrics-row", k => `
    ${k} {
        grid-column: 1 / -1;
    }
`);

const VmTabBody = injectStyle("vm-tab-body", k => `
    ${k} {
        min-height: 165px;
        padding-right: 4px;
        display: flex;
        flex-direction: column;
        gap: 10px;
    }
`);

const VmDetails = injectStyle("vm-details", k => `
    ${k} {
        margin-top: 10px;
        display: grid;
        grid-template-columns: minmax(150px, 190px) 1fr;
        gap: 10px;
        align-items: start;
    }

    ${k} dt {
        color: var(--textSecondary);
    }

    ${k} dd {
        margin: 0;
        overflow-wrap: anywhere;
    }
`);

const VmDetailRowWithAction = injectStyle("vm-detail-row-with-action", k => `
    ${k} {
        display: flex;
        align-items: center;
        gap: 10px;
    }

    ${k} > :first-child {
        min-width: 0;
        flex: 1;
    }
`);

const VmAccessManagerDialogBody = injectStyle("vm-access-manager-dialog-body", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        gap: 10px;
        min-height: 260px;
    }
`);

const VmAccessManagerHeader = injectStyle("vm-access-manager-header", k => `
    ${k} {
        display: flex;
        align-items: center;
    }
`);

const VmAccessManagerControls = injectStyle("vm-access-manager-controls", k => `
    ${k} {
        margin-left: auto;
        display: inline-flex;
        align-items: center;
        gap: 16px;
        flex-wrap: wrap;
    }
`);

const VmAccessManagerList = injectStyle("vm-access-manager-list", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        gap: 8px;
        max-height: 320px;
        overflow-y: auto;
    }
`);

const VmTimeline = injectStyle("vm-timeline", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        gap: 8px;
    }
`);

const VmLifecycleBody = injectStyle("vm-lifecycle-body", k => `
    ${k} {
        max-height: 210px;
        overflow-y: auto;
    }
`);

const VmTimelineRow = injectStyle("vm-timeline-row", k => `
    ${k} {
        display: grid;
        grid-template-columns: 140px 1fr;
        gap: 10px;
        padding: 8px 10px;
        border-radius: 8px;
        background: color-mix(in srgb, var(--primaryMain) 8%, transparent);
    }

    @media (max-width: 700px) {
        ${k} {
            grid-template-columns: 1fr;
        }
    }
`);

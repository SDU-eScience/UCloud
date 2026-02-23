import * as React from "react";
import {useCallback, useEffect, useMemo, useState} from "react";
import * as Heading from "@/ui-components/Heading";
import {Box, Button, Card, Flex, Icon, Link} from "@/ui-components";
import {classConcat, injectStyle} from "@/Unstyled";
import {dateToString} from "@/Utilities/DateUtilities";
import {displayErrorMessageOrDefault, shortUUID} from "@/UtilityFunctions";
import {bulkRequestOf} from "@/UtilityFunctions";
import {useCloudCommand} from "@/Authentication/DataHook";
import {
    api as JobsApi,
    ComputeSupport,
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
import {RichSelect, RichSelectChildComponent} from "@/ui-components/RichSelect";
import {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";
import {VirtualMachineFolders} from "./VirtualMachineFolders";
import {compute} from "@/UCloud";
import {format} from "date-fns";
import * as JobViz from "@/Applications/Jobs/JobViz";
import {useJobVizProperties} from "@/Applications/Jobs/JobViz/StreamProcessor";

interface InterfaceTarget {
    rank: number;
    type: "WEB" | "VNC";
    target?: string;
    port?: number;
    link?: string;
}

interface VmActionItem {
    key: string;
    value: string;
    icon: IconName;
    color: ThemeColor;
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

type OptimisticPowerState = "POWERING_ON" | "POWERING_OFF" | null;
type PowerTone = "success" | "warning" | "neutral";

const VmActionRow: RichSelectChildComponent<VmActionItem> = ({element, onSelect, dataProps}) => {
    if (!element) return null;
    return <Box p="8px" onClick={onSelect} {...dataProps}>
        <Flex gap="8px" alignItems="center">
            <Icon name={element.icon} color={element.color}/>
            <span>{element.value}</span>
        </Flex>
    </Box>;
};

export const VirtualMachineStatus: React.FunctionComponent<{
    job: Job;
    status: JobStatus;
    interfaceTargets: InterfaceTarget[];
    defaultInterfaceName?: string;
    updates: JobUpdate[];
    updatesState: VmUpdatesState;
}> = ({job, status, interfaceTargets, defaultInterfaceName, updates, updatesState}) => {
    const [loading, invokeCommand] = useCloudCommand();
    const [optimisticPowerState, setOptimisticPowerState] = useState<OptimisticPowerState>(null);
    const [statusDotCount, setStatusDotCount] = useState(0);
    const stream = useMemo(() => new JobViz.StreamProcessor(), [job.id]);
    const streamProperties = useJobVizProperties(stream);

    const support = job.status.resolvedSupport
        ? (job.status.resolvedSupport as ResolvedSupport<never, ComputeSupport>).support
        : undefined;

    const supportsTerminal = support?.virtualMachine.terminal === true;
    const supportsSuspension = support?.virtualMachine.suspension === true;
    const supportsVnc = support?.virtualMachine.vnc === true;

    const desktopTarget = useMemo(() => {
        let defaultInterfaceId = interfaceTargets.findIndex(link => !link.target && link.rank === 0);
        if (defaultInterfaceId < 0) {
            defaultInterfaceId = interfaceTargets.findIndex(it => !it.target);
            if (defaultInterfaceId < 0) defaultInterfaceId = 0;
        }
        return interfaceTargets[defaultInterfaceId];
    }, [interfaceTargets]);

    const resources = job.specification.resources ?? [];
    const peers = resources.filter(it => it.type === "peer") as { hostname: string; jobId: string; }[];
    const ingresses = resources.filter(it => it.type === "ingress") as { id: string; }[];

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
    const effectiveState: JobState | "POWERING_ON" | "POWERING_OFF" = optimisticPowerState ?? status.state;
    const appTitle = job.specification.name ?? job.status.resolvedApplication?.metadata?.title ?? "Virtual machine";
    const appVersion = job.status.resolvedApplication?.metadata?.version ?? "";
    const alternativeInterfaces = useMemo(() => {
        return interfaceTargets.filter(it => it !== desktopTarget && it.link);
    }, [interfaceTargets, desktopTarget]);
    const hasLaunchMenu = alternativeInterfaces.length > 0 || supportsTerminal;

    const launchMenuItems = useMemo<VmActionItem[]>(() => {
        const result: VmActionItem[] = alternativeInterfaces.map(it => ({
            key: `iface:${it.link}`,
            value: (it.target ?? defaultInterfaceName ?? "Open interface") + ` (Node ${it.rank + 1})`,
            icon: "heroArrowTopRightOnSquare",
            color: "textPrimary",
        }));
        if (supportsTerminal) {
            result.push({key: "terminal", value: "Open serial console", icon: "heroCommandLine", color: "textPrimary"});
        }
        return result;
    }, [alternativeInterfaces, defaultInterfaceName, supportsTerminal]);

    const dangerMenuItems = useMemo<VmActionItem[]>(() => [
        {key: "delete", value: "Stop and delete VM", icon: "trash", color: "errorMain"},
    ], []);

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
        if (item.key === "delete") confirmTerminate();
    }, [confirmTerminate]);

    const onFolderAdded = useCallback((newFolders: compute.AppParameterValueNS.File[], addedFolder: compute.AppParameterValueNS.File) => {
        // TODO: Persist folder attachment updates once backend API is available.
        void newFolders;
        void addedFolder;
    }, []);

    const onFolderRemoved = useCallback((newFolders: compute.AppParameterValueNS.File[], removedFolder: compute.AppParameterValueNS.File) => {
        // TODO: Persist folder attachment updates once backend API is available.
        void newFolders;
        void removedFolder;
    }, []);

    const interfaceDisabled = !desktopTarget?.link || isTerminalState;

    const powerTone: PowerTone = !supportsSuspension || isTerminalState
        ? "neutral"
        : isSuspended
            ? "success"
            : "warning";
    const powerButtonColor = powerTone === "success" ? "successMain" : "warningMain";
    const powerDropdownClass =
        powerTone === "success"
            ? classConcat(SplitDropdownTrigger, SuccessSplitDropdownTrigger)
            : powerTone === "warning"
                ? classConcat(SplitDropdownTrigger, DangerSplitDropdownTrigger)
                : SplitDropdownTrigger;

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
        <Card className={classConcat(VmHero, status.state ?? optimisticPowerState)} p="24px">
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
                                placeholder="More"
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

                    <Flex>
                        {supportsSuspension && !isTerminalState ? (
                            isSuspended ?
                                <Button color={powerButtonColor} onClick={unsuspend} disabled={powerActionDisabled}
                                        attachedLeft>
                                    <Icon name="heroPower" mr="8px"/>
                                    {optimisticPowerState === "POWERING_ON" ? "Powering on..." : "Power on"}
                                </Button> :
                                <Button color={powerButtonColor} onClick={confirmSuspend} disabled={powerActionDisabled}
                                        attachedLeft>
                                    <Icon name="heroPower" mr="8px"/>
                                    {optimisticPowerState === "POWERING_OFF" ? "Powering off..." : "Power off"}
                                </Button>
                        ) : (
                            <Button attachedLeft disabled={powerActionDisabled}>
                                <Icon name="heroCog6Tooth" mr="8px"/>
                                Actions
                            </Button>
                        )}

                        <RichSelect
                            items={dangerMenuItems}
                            keys={["value"]}
                            RenderRow={VmActionRow}
                            onSelect={onSelectDangerMenuItem}
                            placeholder="More"
                            dropdownWidth="260px"
                            matchTriggerWidth={false}
                            disabled={powerActionDisabled}
                            trigger={
                                <div className={powerDropdownClass} data-disabled={powerActionDisabled}>
                                    <Icon name="heroChevronDown"/>
                                </div>
                            }
                        />
                    </Flex>
                </Flex>

                <div className={StatusBadge} data-state={effectiveState}>
                    <span>{stateToTitle(effectiveState)}</span>
                    {!isPowerTransitioning ? null : (
                        <span className={StatusDots}>{".".repeat(statusDotCount)}</span>
                    )}
                </div>
            </Flex>

            <div className={VmHeroGrid}>
                <Metric title={"ID"}>{shortUUID(job.id)}</Metric>
                <Metric title="Provider">
                    <ProviderTitle providerId={job.specification.product.provider}/>
                </Metric>
                <Metric title="Machine type">{job.specification.product.id}</Metric>
                <Metric title="Launched by">{job.owner.createdBy}</Metric>
                <Metric title="Created at">{dateToString(job.createdAt)}</Metric>
                <Metric title="Started at">{status.startedAt ? dateToString(status.startedAt) : "Pending"}</Metric>
            </div>
        </Card>

        <div className={VmContentGrid}>
            {!showRuntimePanels ? null : (
                <TabbedCard style={{minHeight: "240px"}}>
                    <TabbedCardTab icon="heroSignal" name="Access">
                        <div className={VmTabBody}>
                            <div className={VmDetails}>
                                <dt>SSH access</dt>
                                <dd>{sshCommand ?? "Not announced by provider yet"}</dd>

                                <dt>Public links</dt>
                                <dd>{ingresses.length > 0 ? ingresses.length : "None"}</dd>

                                <dt>Connected peers</dt>
                                <dd>{peers.length > 0 ? peers.length : "None"}</dd>
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
                    onFolderAdded={onFolderAdded}
                    onFolderRemoved={onFolderRemoved}
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
    if (update.state) return `State changed to ${stateToTitle(update.state)}`;
    return update.status ?? "Status updated";
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

function stateToTitle(state: JobState | "POWERING_ON" | "POWERING_OFF"): string {
    switch (state) {
        case "POWERING_ON":
            return "Powering on";
        case "POWERING_OFF":
            return "Powering off";
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

const Metric: React.FunctionComponent<React.PropsWithChildren<{ title: string; }>> = ({title, children}) => {
    return <div>
        <div className={VmMetricTitle}>{title}</div>
        <div>{children}</div>
    </div>;
};

const VmLayout = injectStyle("vm-layout", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        gap: 16px;
        margin-top: 20px;
    }
`);

const VmHero = injectStyle("vm-hero", k => `
    ${k} {
        border: 1px solid color-mix(in srgb, var(--primaryMain) 25%, transparent);
        --jobStateColor1: var(--successLight);
        --jobStateColor2: var(--successDark);
        
        background: linear-gradient(115deg,
            color-mix(in srgb, var(--jobStateColor1) 8%, transparent),
            color-mix(in srgb, var(--jobStateColor2) 12%, transparent)
        );
    }

    ${k}.POWERING_ON,
    ${k}.RUNNING {
        border-color: color-mix(in srgb, var(--successMain) 40%, transparent);
    }

    ${k}.IN_QUEUE,
    ${k}.POWERING_OFF,
    ${k}.SUSPENDED {
        border-color: color-mix(in srgb, var(--warningMain) 45%, transparent);
        --jobStateColor1: var(--warningLight);
        --jobStateColor2: var(--warningDark);
    }

    ${k}.SUCCESS,
    ${k}.EXPIRED,
    ${k}.FAILURE {
        --jobStateColor1: var(--errorLight);
        --jobStateColor2: var(--errorDark);
        border-color: color-mix(in srgb, var(--errorMain) 45%, transparent);
    }
`);

const VmHeroGrid = injectStyle("vm-hero-grid", k => `
    ${k} {
        margin-top: 32px;
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
        gap: 10px 16px;
    }
`);

const VmMetricTitle = injectStyle("vm-metric-title", k => `
    ${k} {
        color: var(--textSecondary);
        font-size: 12px;
        margin-bottom: 3px;
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

    ${k}[data-state="SUSPENDED"] {
        background: color-mix(in srgb, var(--warningMain) 24%, transparent);
    }

    ${k}[data-state="POWERING_OFF"] {
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

const SplitDropdownTrigger = injectStyle("split-dropdown-trigger", k => `
    ${k} {
        position: relative;
        width: 35px;
        height: 35px;
        border-radius: 8px;
        user-select: none;
        -webkit-user-select: none;
        background: var(--primaryMain);
        box-shadow: inset 0 .0625em .125em rgba(10,10,10,.05);
        padding: 6px;
        border-top-left-radius: 0;
        border-bottom-left-radius: 0;
        cursor: pointer;
    }

    ${k}:hover {
        background: var(--primaryDark);
    }

    ${k}[data-disabled="true"] {
        opacity: 0.25;
        cursor: not-allowed;
    }

    ${k}[data-disabled="true"]:hover {
        background: var(--primaryMain);
    }

    ${k} > svg {
        color: var(--primaryContrast);
        position: absolute;
        bottom: 9px;
        right: 10px;
        height: 16px;
    }
`);

const DangerSplitDropdownTrigger = injectStyle("danger-split-dropdown-trigger", k => `
    ${k} {
        background: var(--warningMain);
    }

    ${k}:hover {
        background: var(--warningDark);
    }

    ${k}[data-disabled="true"]:hover {
        background: var(--warningMain);
    }

    ${k} > svg {
        color: var(--warningContrast);
    }
`);

const SuccessSplitDropdownTrigger = injectStyle("success-split-dropdown-trigger", k => `
    ${k} {
        background: var(--successMain);
    }

    ${k}:hover {
        background: var(--successDark);
    }

    ${k}[data-disabled="true"]:hover {
        background: var(--successMain);
    }

    ${k} > svg {
        color: var(--successContrast);
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

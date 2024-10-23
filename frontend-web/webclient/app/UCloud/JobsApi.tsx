import * as React from "react";
import {
    DELETE_TAG,
    ProductSupport,
    Resource,
    ResourceApi, ResourceBrowseCallbacks,
    ResourceIncludeFlags,
    ResourceSpecification, ResourceStatus,
    ResourceUpdate
} from "@/UCloud/ResourceApi";
import {BulkRequest, BulkResponse, compute, FindByStringId} from "@/UCloud/index";
import AppParameterValue = compute.AppParameterValue;
import SimpleDuration = compute.SimpleDuration;
import {SafeLogo} from "@/Applications/AppToolLogo";
import {stateToTitle} from "@/Applications/Jobs";
import {Box, Flex, Icon, Text} from "@/ui-components";
import {IconName} from "@/ui-components/Icon";
import {View} from "@/Applications/Jobs/View";
import {ItemRenderer} from "@/ui-components/Browse";
import {ProductCompute} from "@/Accounting";
import {Operation, ShortcutKey} from "@/ui-components/Operation";
import {bulkRequestOf} from "@/UtilityFunctions";
import {BrowseType} from "@/Resource/BrowseType";
import {formatDistanceToNow} from "date-fns";
import {ListRowStat} from "@/ui-components/List";
import {apiRetrieve, apiUpdate} from "@/Authentication/DataHook";
import AppRoutes from "@/Routes";
import {ThemeColor} from "@/ui-components/theme";
import {Application, ApplicationParameter, NameAndVersion} from "@/Applications/AppStoreApi";

export interface DynamicParameters {
    parametersByProvider: Record<string, ApplicationParameter[]>;
}

export interface JobBinding {
    kind: "BIND" | "UNBIND";
    job: string;
}

export interface JobSpecification extends ResourceSpecification {
    application: NameAndVersion;
    name?: string;
    replicas: number;
    allowDuplicateJob?: boolean;
    parameters: Record<string, AppParameterValue>;
    resources: AppParameterValue[];
    timeAllocation?: SimpleDuration;
    openedFile?: string;
    sshEnabled?: boolean;
}

export type JobState = "IN_QUEUE" | "RUNNING" | "CANCELING" | "SUCCESS" | "FAILURE" | "EXPIRED" | "SUSPENDED";
export function isJobStateFinal(state: JobState): boolean {
    switch (state) {
        case "SUCCESS":
        case "FAILURE":
        case "EXPIRED":
            return true;
        default:
            return false;
    }
}

export interface JobUpdate extends ResourceUpdate {
    state?: JobState;
    expectedState?: JobState;
    expectedDifferentState?: boolean;
}

export interface JobOutput {
    outputFolder: string;
}

export interface JobFlags extends ResourceIncludeFlags {
    filterApplication?: string;
    filterState?: JobState;
    includeApplication?: boolean;
    includeParameters?: boolean;
}

export interface JobStatus extends ResourceStatus {
    state: JobState;
    startedAt?: number;
    expiresAt?: number;
    resolvedApplication?: Application;
    jobParametersJson: any;
}

export interface Job extends Resource<JobUpdate, JobStatus, JobSpecification> {
    output?: JobOutput;
}

export interface ComputeSupport extends ProductSupport {
    docker: DockerSupport;
    virtualMachine: VirtualMachineSupport;
    native: NativeSupport;
}

export interface NativeSupport {
    enabled?: boolean;
    web?: boolean;
    vnc?: boolean;
    logs?: boolean;
    terminal?: boolean;
    timeExtension?: boolean;
    utilization?: boolean;
}

export interface DockerSupport {
    enabled?: boolean;
    web?: boolean;
    vnc?: boolean;
    logs?: boolean;
    terminal?: boolean;
    peers?: boolean;
    timeExtension?: boolean;
    utilization?: boolean;
}

export interface VirtualMachineSupport {
    enabled?: boolean;
    logs?: boolean;
    vnc?: boolean;
    terminal?: boolean;
    timeExtension?: boolean;
    suspension?: boolean;
    utilization?: boolean;
}

export interface CpuAndMemory {
    cpu: number;
    memory: number;
}

export interface QueueStatus {
    running: number;
    pending: number;
}

export interface ComputeUtilization {
    capacity: CpuAndMemory;
    usedCapacity: CpuAndMemory;
    queueStatus: QueueStatus;
}

export interface ExtendRequest {
    jobId: string;
    requestedTime: SimpleDuration;
}

export type ResumeRequest = FindByStringId;
export type SuspendRequest = FindByStringId;
export interface OpenInteractiveSessionRequest {
    id: string;
    rank: number;
    sessionType: "WEB" | "VNC" | "SHELL";
}

export interface OpenTerminalInFolderRequest {
    folder: string;
}

export interface InteractiveSession {
    providerDomain: string;
    providerId: string;
    session: SessionData;
}

export type SessionData = SessionDataShell | SessionDataWeb | SessionDataVnc;

export interface SessionDataShell {
    type: "shell";
    jobId: string;
    rank: number;
    sessionIdentifier: string;
}

export interface SessionDataWeb {
    type: "web";
    jobId: string;
    rank: number;
    redirectClientTo: string;
}

export interface SessionDataVnc {
    type: "vnc";
    jobId: string;
    rank: number;
    url: string;
    password?: string;
}

export function jobStateToIconAndColor(state: JobState): [IconName, ThemeColor] {
    let color: ThemeColor = "iconColor";
    let icon: IconName;
    switch (state) {
        case "IN_QUEUE":
            icon = "calendar";
            break;
        case "RUNNING":
            icon = "chrono";
            break;
        case "SUCCESS":
            icon = "check";
            color = "successMain";
            break;
        case "FAILURE":
            icon = "close";
            color = "errorMain";
            break;
        case "EXPIRED":
            icon = "chrono";
            color = "warningMain";
            break;
        case "SUSPENDED":
            icon = "pauseSolid";
            break;
        default:
            icon = "ellipsis";
            break;
    }
    return [icon, color];
}

class JobApi extends ResourceApi<Job, ProductCompute, JobSpecification, JobUpdate, JobFlags,
    JobStatus, ComputeSupport> {
    routingNamespace = "jobs";
    title = "Run";
    productType = "COMPUTE" as const;
    defaultSortDirection = "descending" as const;

    renderer: ItemRenderer<Job> = {
        MainTitle({resource}) {return <>{resource?.specification?.name ?? resource?.id ?? ""}</>},
        Icon({resource, size, browseType}) {
            if (browseType === BrowseType.Card) {
                const job = resource as Job;
                const [icon, color] = jobStateToIconAndColor(job.status.state);
                return <Icon name={icon} color={color} mr={"8px"} />;
            }
            return <SafeLogo name={resource?.specification?.application?.name ?? ""} type={"APPLICATION"} size={size} />
        },
        Stats({resource, browseType}) {
            if (resource == null || browseType !== BrowseType.Card) return null;
            return (
                <ListRowStat>
                    {resource.status.resolvedApplication?.metadata?.title ?? resource.specification.application.name}
                    {" "}
                    {resource.specification.application.version}
                </ListRowStat>
            )
        },
        ImportantStats({resource, browseType}) {
            if (browseType === BrowseType.Embedded) {
                return null;
            }

            if (browseType === BrowseType.Card) {
                return <Text mr="-40px" fontSize="14px" color="textSecondary">{formatDistanceToNow(resource?.createdAt ?? 0)}</Text>
            }

            const job = resource as Job;
            const [icon, color] = jobStateToIconAndColor(job.status.state);
            return <Flex width={"140px"} mt="4px" height={"27px"}><Icon name={icon} color={color} mr={"8px"} />
                <Box mt={"-2px"}>{stateToTitle(job.status.state)}</Box>
            </Flex>
        }
    };

    Properties = props => <View embedded={props.embedded} id={props?.resource?.id} />;

    constructor() {
        super("jobs");
    }

    retrieveOperations(): Operation<Job, ResourceBrowseCallbacks<Job> & {isModal: boolean}>[] {
        const baseOperations = super.retrieveOperations();
        const deleteOperation = baseOperations.find(it => it.tag === DELETE_TAG)!;
        deleteOperation.text = "Stop";
        deleteOperation.onClick = async (selected, cb) => {
            await cb.invokeCommand(this.terminate(bulkRequestOf(...selected.map(it => ({id: it.id})))))
            cb.reload();
        };
        const originalEnabled = deleteOperation.enabled;
        deleteOperation.enabled = (selected, cb) => {
            const orig = originalEnabled(selected, cb);
            if (orig !== true) return orig;
            if (selected.every(it => isJobStateFinal(it.status.state))) {
                if (selected.length === 1) return false;
                return "All jobs have already terminated";
            }
            return true;
        };

        const ourOps: Operation<Job, ResourceBrowseCallbacks<Job> & {isModal: boolean}>[] = [{
            // Re-run app
            enabled: (selected, cb) => {
                const isSyncthing = isSyncthingApp(selected[0]);
                return !cb.isModal && selected.length === 1 && !isSyncthing;
            },
            onClick: ([{specification, id}], cb) =>
                cb.navigate(AppRoutes.jobs.create(specification.application.name, specification.application.version, id)),
            icon: "play",
            text: "Run application again",
            shortcut: ShortcutKey.P
        }];

        return ourOps.concat(baseOperations);
    }

    terminate(request: BulkRequest<FindByStringId>): APICallParameters<BulkRequest<FindByStringId>, BulkResponse<any | null>> {
        return apiUpdate(request, this.baseContext, "terminate");
    }

    /* Untested */
    retrieveUtilization(request: {jobId: string}): APICallParameters<{jobId: string}, ComputeUtilization> {
        console.log("RETRIEVE_UTILIZATION");
        return apiRetrieve(request, this.baseContext, "utilization");
    }

    extend(request: BulkRequest<ExtendRequest>): APICallParameters<BulkRequest<ExtendRequest>, BulkResponse<any | null>> {
        return apiUpdate(request, this.baseContext, "extend");
    }

    /* Untested */
    suspend(request: BulkRequest<SuspendRequest>): APICallParameters<BulkRequest<SuspendRequest>, BulkResponse<any | null>> {
        console.log("SUSPEND");
        return apiUpdate(request, this.baseContext, "suspend");
    }

    /* Untested */
    unsuspend(request: BulkRequest<ResumeRequest>): APICallParameters {
        console.log("UNSUSPEND");
        return apiUpdate(request, this.baseContext, "unsuspend")
    }

    openInteractiveSession(
        request: BulkRequest<OpenInteractiveSessionRequest>
    ): APICallParameters<BulkRequest<OpenInteractiveSessionRequest>, BulkResponse<InteractiveSession>> {
        return apiUpdate(request, this.baseContext, "interactiveSession");
    }

    openTerminalInFolder(
        request: BulkRequest<OpenTerminalInFolderRequest>
    ): APICallParameters<BulkRequest<OpenTerminalInFolderRequest>, BulkResponse<InteractiveSession>> {
        return apiUpdate(request, this.baseContext, "openTerminalInFolder");
    }

    requestDynamicParameters(req: {
        application: NameAndVersion
    }): APICallParameters {
        return apiUpdate(req, this.baseContext, "requestDynamicParameters")
    }
}

export function isSyncthingApp(app?: Job) {
    return app?.specification.application.name === "syncthing"
}

export const api = new JobApi()

export default api;

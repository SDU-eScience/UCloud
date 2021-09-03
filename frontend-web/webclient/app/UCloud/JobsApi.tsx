import * as React from "react";
import {
    ProductSupport,
    Resource,
    ResourceApi,
    ResourceIncludeFlags,
    ResourceSpecification, ResourceStatus,
    ResourceUpdate
} from "UCloud/ResourceApi";
import {accounting, BulkRequest, BulkResponse, compute, FindByStringId} from "UCloud/index";
import NameAndVersion = compute.NameAndVersion;
import AppParameterValue = compute.AppParameterValue;
import SimpleDuration = compute.SimpleDuration;
import {SidebarPages} from "ui-components/Sidebar";
import {AppToolLogo} from "Applications/AppToolLogo";
import {EnumFilter} from "Resource/Filter";
import Application = compute.Application;
import {buildQueryString} from "Utilities/URIUtilities";
import {stateToTitle} from "Applications/Jobs";
import {Box, Flex, Icon} from "ui-components";
import {IconName} from "ui-components/Icon";
import View from "Applications/Jobs/View";
import {ItemRenderer} from "ui-components/Browse";
import {ProductCompute} from "Accounting";

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
}

export type JobState = "IN_QUEUE" | "RUNNING" | "CANCELING" | "SUCCESS" | "FAILURE" | "EXPIRED";
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
}

export interface JobStatus extends ResourceStatus {
    state: JobState;
    startedAt?: number;
    expiresAt?: number;
    resolvedApplication?: Application;
}

export interface Job extends Resource<JobUpdate, JobStatus, JobSpecification> {
    output?: JobOutput;
}

export interface ComputeSupport extends ProductSupport {
    docker: DockerSupport;
    virtualMachine: VirtualMachineSupport;
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

export type SuspendRequest = FindByStringId
export interface OpenInteractiveSessionRequest {
    id: string;
    rank: number;
    sessionType: "WEB" | "VNC" | "SHELL";
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

function jobStateToIconAndColor(state: JobState): [IconName, string] {
    let color = "iconColor";
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
            color = "green";
            break;
        case "FAILURE":
            icon = "close";
            color = "red";
            break;
        case "EXPIRED":
            icon = "chrono";
            color = "orange";
            break;
        default:
            icon = "ellipsis";
            break;
    }
    return [icon, color];
}

class JobApi extends ResourceApi<Job, ProductCompute, JobSpecification, JobUpdate, JobFlags,
    JobStatus, ComputeSupport>  {
    routingNamespace = "jobs";
    title = "Run";
    page = SidebarPages.Runs;
    productType = "COMPUTE" as const;

    renderer: ItemRenderer<Job> = {
        MainTitle({resource}) {return <>{resource?.specification?.name ?? resource?.id ?? ""}</>},
        Icon({resource, size}) {
            return <AppToolLogo name={resource?.specification?.application?.name ?? ""} type={"APPLICATION"} size={size} />
        },
        ImportantStats({resource}) {
            const job = resource as Job;
            const [icon, color] = jobStateToIconAndColor(job.status.state);
            return <Flex width={"120px"} height={"27px"}><Icon name={icon} color={color} mr={"8px"} />
                <Box mt={"-3px"}>{stateToTitle(job.status.state)}</Box>
            </Flex>
        }
    };

    Properties = View;

    constructor() {
        super("jobs");

        this.registerFilter(EnumFilter(
            "radioEmpty",
            "filterState",
            "Status",
            [
                {title: "In queue", value: "IN_QUEUE", icon: "hashtag"},
                {title: "Running", value: "RUNNING", icon: "hashtag"},
                {title: "Success", value: "SUCCESS", icon: "check"},
                {title: "Failure", value: "FAILURE", icon: "close"},
                {title: "Expired", value: "EXPIRED", icon: "chrono"},
            ]
        ));

        this.sortEntries.push({
            icon: "apps",
            title: "Application",
            column: "application",
            helpText: "The name of the application, e.g. 'Terminal'"
        });

        this.sortEntries.push({
            icon: "radioEmpty",
            title: "Status",
            column: "state",
            helpText: "The current status of the job, e.g. 'Running'"
        });
    }

    terminate(request: BulkRequest<FindByStringId>): APICallParameters<BulkRequest<FindByStringId>, BulkResponse<any | null>> {
        return {
            context: "",
            method: "POST",
            path: this.baseContext + "terminate",
            payload: request,
            parameters: request
        };
    }

    retrieveUtilization(request: {jobId: string}): APICallParameters<{jobId: string}, ComputeUtilization> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString(this.baseContext + "retrieveUtilization", request),
            parameters: request
        };
    }

    extend(request: BulkRequest<ExtendRequest>): APICallParameters<BulkRequest<ExtendRequest>, BulkResponse<any | null>> {
        return {
            context: "",
            method: "POST",
            path: this.baseContext + "extend",
            payload: request,
            parameters: request
        };
    }

    suspend(request: BulkRequest<SuspendRequest>): APICallParameters<BulkRequest<SuspendRequest>, BulkResponse<any | null>> {
        return {
            context: "",
            method: "POST",
            path: this.baseContext + "suspend",
            payload: request,
            parameters: request
        };
    }

    openInteractiveSession(
        request: BulkRequest<OpenInteractiveSessionRequest>
    ): APICallParameters<BulkRequest<OpenInteractiveSessionRequest>, BulkResponse<InteractiveSession>> {
        return {
            context: "",
            method: "POST",
            path: this.baseContext + "interactiveSession",
            payload: request,
            parameters: request
        };
    }
}

export default new JobApi();

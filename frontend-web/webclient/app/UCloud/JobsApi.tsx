import * as React from "react";
import {
    ProductSupport,
    Resource,
    ResourceApi,
    ResourceIncludeFlags,
    ResourceSpecification, ResourceStatus,
    ResourceUpdate
} from "UCloud/ResourceApi";
import {accounting, compute} from "UCloud/index";
import NameAndVersion = compute.NameAndVersion;
import AppParameterValue = compute.AppParameterValue;
import SimpleDuration = compute.SimpleDuration;
import ProductNS = accounting.ProductNS;
import {SidebarPages} from "ui-components/Sidebar";
import {AppToolLogo} from "Applications/AppToolLogo";
import {EnumFilter} from "Resource/Filter";
import Application = compute.Application;

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

class JobApi extends ResourceApi<Job, ProductNS.Compute, JobSpecification, JobUpdate, JobFlags,
    JobStatus, ComputeSupport>  {
    routingNamespace = "jobs";
    title = "Run";
    page = SidebarPages.Runs;

    TitleRenderer = ({resource}) => resource.specification.name ?? resource.id;
    IconRenderer = ({resource, size}) =>
        <AppToolLogo name={resource.specification.application.name} type={"APPLICATION"} size={size}/>;

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
        ))
    }
}

export default new JobApi();

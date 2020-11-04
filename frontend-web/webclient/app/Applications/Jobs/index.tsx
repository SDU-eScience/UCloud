import {buildQueryString} from "Utilities/URIUtilities";
import {View} from "./View" ;

export interface FindByIdRequest {
    id: string,
}

export type Job = FindByIdResponse;

export interface FindByIdResponse {
    createdAt: number,
    creditsCharged?: number,
    expiresAt?: number,
    failedState?: JobState,
    jobId: string,
    maxTime: number,
    metadata: ApplicationMetadata,
    modifiedAt: number,
    name?: string,
    outputFolder?: string,
    owner: string,
    state: JobState,
    status: string,
}

export interface ApplicationMetadata {
    authors: string[],
    description: string,
    isPublic: boolean,
    name: string,
    title: string,
    version: string,
    website?: string,
}

export function findById(
    request: FindByIdRequest
): APICallParameters<FindByIdRequest> {
    return {
        method: "GET",
        path: `/hpc/jobs/${request.id}`,
        parameters: request,
        reloadId: Math.random(),
        payload: undefined
    };
}

export interface ListRecentRequest {
    application?: string,
    filter?: JobState,
    itemsPerPage?: number,
    maxTimestamp?: number,
    minTimestamp?: number,
    order: SortOrder,
    page?: number,
    sortBy: JobSortBy,
    version?: string,
}

export enum JobState {
    IN_QUEUE = "IN_QUEUE",
    RUNNING = "RUNNING",
    CANCELING = "CANCELING",
    SUCCESS = "SUCCESS",
    FAILURE = "FAILURE",
}

export enum SortOrder {
    ASCENDING = "ASCENDING",
    DESCENDING = "DESCENDING",
}

export enum JobSortBy {
    NAME = "NAME",
    STATE = "STATE",
    APPLICATION = "APPLICATION",
    STARTED_AT = "STARTED_AT",
    LAST_UPDATE = "LAST_UPDATE",
    CREATED_AT = "CREATED_AT",
}

export interface JobWithStatus {
    createdAt: number,
    creditsCharged?: number,
    expiresAt?: number,
    failedState?: JobState,
    jobId: string,
    maxTime: number,
    metadata: ApplicationMetadata,
    modifiedAt: number,
    name?: string,
    outputFolder?: string,
    owner: string,
    state: JobState,
    status: string,
}

export interface ApplicationMetadata {
    authors: string[],
    description: string,
    isPublic: boolean,
    name: string,
    title: string,
    version: string,
    website?: string,
}

export function listRecent(
    request: ListRecentRequest
): APICallParameters<ListRecentRequest> {
    return {
        method: "GET",
        path: buildQueryString("/hpc/jobs/", request),
        parameters: request,
        reloadId: Math.random(),
        payload: undefined
    };
}

export interface StartRequest {
    acceptSameDataRetry: boolean,
    application: NameAndVersion,
    archiveInCollection?: string,
    backend?: string,
    maxTime?: SimpleDuration,
    mounts: any[],
    name?: string,
    numberOfNodes?: number,
    parameters: any,
    peers: ApplicationPeer[],
    reservation: string,
    url?: string,
}

export interface StartResponse {
    jobId: string,
}

export interface SimpleDuration {
    hours: number,
    minutes: number,
    seconds: number,
}

export interface ApplicationPeer {
    jobId: string,
    name: string,
}

export function start(
    request: StartRequest
): APICallParameters<StartRequest> {
    return {
        method: "POST",
        path: "/hpc/jobs/",
        parameters: request,
        reloadId: Math.random(),
        payload: request
    };
}

export interface CancelRequest {
    jobId: string,
}

export function cancel(
    request: CancelRequest
): APICallParameters<CancelRequest> {
    return {
        method: "DELETE",
        path: "/hpc/jobs/",
        parameters: request,
        reloadId: Math.random(),
        payload: request
    };
}

export interface FollowRequest {
    jobId: string,
    stderrLineStart: number,
    stderrMaxLines: number,
    stdoutLineStart: number,
    stdoutMaxLines: number,
}

export interface NameAndVersion {
    name: string;
    version: string;
}

export interface FollowResponse {
    application: NameAndVersion,
    complete: boolean,
    failedState?: JobState,
    id: string,
    metadata: ApplicationMetadata,
    name?: string,
    outputFolder?: string,
    state: JobState,
    status: string,
    stderr: string,
    stderrNextLine: number,
    stdout: string,
    stdoutNextLine: number,
    timeLeft?: number,
}

export function follow(
    request: FollowRequest
): APICallParameters<FollowRequest> {
    return {
        method: "GET",
        path: buildQueryString(`/hpc/jobs/follow/${request.jobId}`, request),
        parameters: request,
        reloadId: Math.random(),
        payload: undefined
    };
}

export interface QueryVncParametersRequest {
    jobId: string,
}

export interface QueryVncParametersResponse {
    password?: string,
    path: string,
}

export function queryVncParameters(
    request: QueryVncParametersRequest
): APICallParameters<QueryVncParametersRequest> {
    return {
        method: "GET",
        path: `/hpc/jobs/query-vnc/${request.jobId}`,
        parameters: request,
        reloadId: Math.random(),
        payload: undefined
    };
}

export interface QueryWebParametersRequest {
    jobId: string,
}

export interface QueryWebParametersResponse {
    path: string,
}

export function queryWebParameters(
    request: QueryWebParametersRequest
): APICallParameters<QueryWebParametersRequest> {
    return {
        method: "GET",
        path: `/hpc/jobs/query-web/${request.jobId}`,
        parameters: request,
        reloadId: Math.random(),
        payload: undefined
    };
}

export interface QueryShellParametersRequest {
    jobId: string,
    rank: number,
}

export interface QueryShellParametersResponse {
    path: string,
}

export function queryShellParameters(
    request: QueryShellParametersRequest
): APICallParameters<QueryShellParametersRequest> {
    return {
        method: "GET",
        path: buildQueryString(`/hpc/jobs/query-shell/${request.jobId}`, request),
        parameters: request,
        reloadId: Math.random(),
        payload: undefined
    };
}

export interface ExtendDurationRequest {
    extendWith: SimpleDuration,
    jobId: string,
}

export interface SimpleDuration {
    hours: number,
    minutes: number,
    seconds: number,
}

export function extendDuration(
    request: ExtendDurationRequest
): APICallParameters<ExtendDurationRequest> {
    return {
        method: "POST",
        path: "/hpc/jobs/extend-duration",
        parameters: request,
        reloadId: Math.random(),
        payload: request
    };
}

export {View};

export function isJobStateTerminal(state: JobState): boolean {
    return state === JobState.SUCCESS || state === JobState.FAILURE;
}

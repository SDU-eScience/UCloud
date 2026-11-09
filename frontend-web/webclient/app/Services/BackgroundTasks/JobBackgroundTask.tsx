import * as React from "react";
import {BulkResponse, compute, FindByStringId} from "@/UCloud";
import {WSFactory, Client} from "@/Authentication/HttpClientInstance";
import {WebSocketConnection, WebsocketResponse} from "@/Authentication/ws";
import {callAPI} from "@/Authentication/DataHook";
import JobsApi, {Job, JobState, isJobStateFinal} from "@/UCloud/JobsApi";
import {bulkRequestOf, displayErrorMessageOrDefault} from "@/UtilityFunctions";
import {sendFailureNotification, sendSuccessNotification} from "@/Notifications";
import {reportBackgroundTaskChange} from "@/Services/BackgroundTasks/BackgroundTaskChanges";
import {IconName} from "@/ui-components/Icon";

const STORAGE_KEY = "job-backed-background-tasks";

export interface JobBackgroundTaskDisplay {
    icon: IconName;
    title: string;
    runningTitle: string;
    body?: string;
    cancelTitle: string;
    cancelMessage: string;
    startingMessage?: string;
    successNotification?: string;
    failureNotification?: string;
    stateMessages?: Partial<Record<JobState, string>>;
}

export interface JobBackgroundTask {
    jobId: string;
    projectId: string | null;
    owner: string;
    display: JobBackgroundTaskDisplay;
    createdAt: number;
    state: JobState;
    progress: string;
    notificationSent: boolean;
    percentage100?: number;
    onSuccess?: () => void;
}

interface JobsFollowResponse {
    updates?: compute.JobUpdate[];
    newStatus?: {state: JobState};
    log: LogMessage[];
    initialJob?: Job | null;
}

interface LogMessage {
    rank: number;
    stdout?: string | null;
    stderr?: string | null;
    channel?: string | null;
}

interface JobBackgroundTaskSnapshot {
    inProgress: JobBackgroundTask[];
    finished: JobBackgroundTask[];
}

const listeners = new Set<() => void>();
const connections = new Map<string, WebSocketConnection>();
const retryTimers = new Map<string, number>();
let tasks: JobBackgroundTask[] = [];
let loadedForOwner: string | null = null;
let snapshot: JobBackgroundTaskSnapshot = {inProgress: [], finished: []};
let followingEnabled = false;

function currentOwner(): string {
    return Client.activeUsername ?? "";
}

function storageKey(owner: string = currentOwner()): string {
    return `${STORAGE_KEY}:${owner}`;
}

function isTerminal(state: JobState): boolean {
    return isJobStateFinal(state);
}

function ensureLoaded(): void {
    const owner = currentOwner();
    if (loadedForOwner === owner) return;

    for (const connection of connections.values()) connection.close();
    connections.clear();
    for (const timer of retryTimers.values()) window.clearTimeout(timer);
    retryTimers.clear();
    loadedForOwner = owner;
    tasks = [];

    try {
        const stored = JSON.parse(sessionStorage.getItem(storageKey(owner)) ?? "[]") as JobBackgroundTask[];
        tasks = stored.filter(task => task.owner === owner && task.display != null);
    } catch (_) {
        sessionStorage.removeItem(storageKey(owner));
    }
    updateSnapshot();
}

function updateSnapshot(): void {
    snapshot = {
        inProgress: tasks.filter(task => !isTerminal(task.state)).sort((a, b) => a.createdAt - b.createdAt),
        finished: tasks.filter(task => isTerminal(task.state)).sort((a, b) => a.createdAt - b.createdAt)
    };
}

function persistAndEmit(): void {
    sessionStorage.setItem(storageKey(), JSON.stringify(tasks));
    updateSnapshot();
    for (const listener of listeners) listener();
}

function latestUpdate(updates?: compute.JobUpdate[]): compute.JobUpdate | undefined {
    return updates?.reduce((latest, update) => !latest || update.timestamp > latest.timestamp ? update : latest, undefined as compute.JobUpdate | undefined);
}

function defaultProgress(task: JobBackgroundTask, state: JobState): string {
    return task.display.stateMessages?.[state] ?? state;
}

function applyFollowResponse(task: JobBackgroundTask, response: JobsFollowResponse): void {
    const previousState = task.state;
    const previousIndeterminate = task.percentage100 === undefined && !isTerminal(previousState);
    const initialUpdate = latestUpdate(response.initialJob?.updates);
    const update = latestUpdate(response.updates);
    const state = response.newStatus?.state ?? update?.state ?? response.initialJob?.status.state ?? initialUpdate?.state;
    const progressState = update?.state ?? initialUpdate?.state;
    const reportedProgress = update?.status ?? initialUpdate?.status;
    const progress = state && isTerminal(state) && progressState !== state ? undefined : reportedProgress;

    if (state) {
        task.state = state;
        task.progress = progress ?? defaultProgress(task, state);
    } else if (progress) {
        task.progress = progress;
    }

    for (const log of response.log) {
        if (log.channel != null) continue;
        const message = log.stdout ?? log.stderr ?? "";
        if (message.startsWith("Progress: ")) {
            const progress = parseFloat(message.replaceAll("Progress:", "").replaceAll("%", "").trim());
            if (!isNaN(progress) && progress >= 0 && progress <= 100) {
                task.percentage100 = progress;
            }
        }
    }

    if (isTerminal(task.state) && !task.notificationSent) {
        task.notificationSent = true;
        if (task.state === "SUCCESS") {
            sendSuccessNotification(task.display.successNotification ?? `${task.display.title} is ready`);
            task.onSuccess?.();
        } else {
            sendFailureNotification(task.display.failureNotification ?? `Could not complete ${task.display.title}`);
        }
    }

    const indeterminate = task.percentage100 === undefined && !isTerminal(task.state);
    if (!isTerminal(previousState) && isTerminal(task.state)) {
        reportBackgroundTaskChange(`${task.display.title} has been completed`);
    } else if (previousIndeterminate !== indeterminate) {
        reportBackgroundTaskChange(indeterminate ?
            `${task.display.title} has been updated` :
            `${task.display.title} has started`);
    }

    persistAndEmit();
}

function retryFollow(task: JobBackgroundTask): void {
    if (isTerminal(task.state) || retryTimers.has(task.jobId)) return;
    const timer = window.setTimeout(() => {
        retryTimers.delete(task.jobId);
        followTask(task);
    }, 2000);
    retryTimers.set(task.jobId, timer);
}

function followTask(task: JobBackgroundTask): void {
    if (isTerminal(task.state) || connections.has(task.jobId)) return;

    const connection = WSFactory.open("/jobs", {
        reconnect: false,
        init: conn => {
            void conn.subscribe({
                call: "jobs.follow",
                payload: {id: task.jobId},
                projectOverride: task.projectId,
                handler: (message: WebsocketResponse) => {
                    if (message.type === "message" && message.payload) {
                        applyFollowResponse(task, message.payload as JobsFollowResponse);
                    }
                    if (message.type === "response" || isTerminal(task.state)) {
                        connections.delete(task.jobId);
                        conn.close();
                        if (!isTerminal(task.state)) {
                            const permanentFailure = message.type === "response" && message.status != null &&
                                message.status >= 400 && message.status < 500 && message.status !== 408 && message.status !== 429;
                            if (permanentFailure) {
                                task.state = "FAILURE";
                                task.progress = "Unable to follow task";
                                task.notificationSent = true;
                                reportBackgroundTaskChange(`${task.display.title} has been completed`);
                                persistAndEmit();
                                sendFailureNotification(`Could not track ${task.display.title}`);
                            } else {
                                task.progress = "Unable to follow job. Retrying";
                                persistAndEmit();
                                retryFollow(task);
                            }
                        }
                    }
                }
            });
        },
        onClose: () => {
            connections.delete(task.jobId);
            if (followingEnabled && tasks.includes(task) && !isTerminal(task.state)) retryFollow(task);
        }
    });
    connections.set(task.jobId, connection);
}

export function registerJobBackgroundTask(input: {
    jobId: string;
    projectId: string | null;
    display: JobBackgroundTaskDisplay;
    onSuccess?: () => void;
}): void {
    ensureLoaded();
    if (tasks.some(task => task.jobId === input.jobId)) return;

    const task: JobBackgroundTask = {
        ...input,
        owner: currentOwner(),
        createdAt: Date.now(),
        state: "IN_QUEUE",
        progress: input.display.stateMessages?.IN_QUEUE ?? "Waiting for task",
        notificationSent: false,
        onSuccess: input.onSuccess,
    };
    tasks.push(task);
    reportBackgroundTaskChange(task.display.startingMessage ?? `Starting ${task.display.title}...`);
    persistAndEmit();
    followTask(task);
}

export function startFollowingJobBackgroundTasks(): () => void {
    ensureLoaded();
    followingEnabled = true;
    for (const task of tasks) followTask(task);
    return () => {
        followingEnabled = false;
        for (const connection of connections.values()) connection.close();
        connections.clear();
        for (const timer of retryTimers.values()) window.clearTimeout(timer);
        retryTimers.clear();
    };
}

export async function cancelJobBackgroundTask(task: JobBackgroundTask): Promise<void> {
    const previousState = task.state;
    const previousProgress = task.progress;
    task.state = "CANCELING";
    task.progress = task.display.stateMessages?.CANCELING ?? "Stopping task";
    persistAndEmit();

    try {
        await callAPI<BulkResponse<FindByStringId | null>>({
            ...JobsApi.terminate(bulkRequestOf({id: task.jobId})),
            projectOverride: task.projectId ?? ""
        });
    } catch (error) {
        if (task.state === "CANCELING") {
            task.state = previousState;
            task.progress = previousProgress;
            persistAndEmit();
        }
        displayErrorMessageOrDefault(error, "Failed to stop task.");
    }
}

export function removeJobBackgroundTask(task: JobBackgroundTask): void {
    ensureLoaded();
    tasks = tasks.filter(candidate => candidate.jobId !== task.jobId);
    connections.get(task.jobId)?.close();
    connections.delete(task.jobId);
    const timer = retryTimers.get(task.jobId);
    if (timer !== undefined) window.clearTimeout(timer);
    retryTimers.delete(task.jobId);
    persistAndEmit();
}

export function clearFinishedJobBackgroundTasks(): void {
    ensureLoaded();
    tasks = tasks.filter(task => !isTerminal(task.state));
    persistAndEmit();
}

export function subscribeToJobBackgroundTasks(listener: () => void): () => void {
    ensureLoaded();
    listeners.add(listener);
    return () => listeners.delete(listener);
}

export function getJobBackgroundTaskSnapshot(): JobBackgroundTaskSnapshot {
    ensureLoaded();
    return snapshot;
}

export function useJobBackgroundTasks(): JobBackgroundTaskSnapshot {
    React.useEffect(startFollowingJobBackgroundTasks, []);
    return React.useSyncExternalStore(subscribeToJobBackgroundTasks, getJobBackgroundTaskSnapshot);
}

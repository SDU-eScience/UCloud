import * as React from "react";
import {useEffect, useMemo} from "react";
import {Box, Card, Icon} from "@/ui-components";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import Flex from "@/ui-components/Flex";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {Upload, UploadState, uploadStore, useUploads} from "@/Files/Upload";
import {classConcat, injectStyle, injectStyleSimple, makeKeyframe} from "@/Unstyled";
import {stopPropagation} from "@/UtilityFunctions";
import {ExternalStoreBase} from "@/Utilities/ReduxUtilities";
import {WebSocketConnection} from "@/Authentication/ws";
import {IconName} from "@/ui-components/Icon";
import {TaskRow, UploadCallback, UploaderRow, uploadIsTerminal} from "@/Files/Uploader";
import {addStandardDialog} from "@/UtilityComponents";
import {TooltipV2} from "@/ui-components/Tooltip";
import {Client, WSFactory} from "@/Authentication/HttpClientInstance";
import {ThemeColor} from "@/ui-components/theme";
import * as icons from "@/ui-components/icons";
import {Feature, hasFeature} from "@/Features";

const iconNames = Object.keys(icons) as IconName[];

function onBeforeUnload(): boolean {
    snackbarStore.addInformation(
        "You currently have uploads in progress. Are you sure you want to leave UCloud?",
        true
    );
    return false;
}

const SpinAnimation = makeKeyframe("spin", `
    0% {
        transform: rotate(0deg);
    }
    100% {
        transform: rotate(360deg);
    }    
`)

enum TaskState {
    IN_QUEUE = "IN_QUEUE",
    SUSPENDED = "SUSPENDED",
    RUNNING = "RUNNING",
    CANCELLED = "CANCELLED",
    FAILURE = "FAILURE",
    SUCCESS = "SUCCESS",
};

interface Status {
    state: TaskState;
    title: string;
    body?: string;
    progress: string;
    progressPercentage: number;
}

interface Specification {
    canPause: boolean;
    canCancel: boolean;
}

interface BackgroundTask {
    taskId: number; // number? Also, no need to show end-user.
    createdAt: number;
    modifiedAt: number;
    createdBy: string;
    provider: string;
    status: Status;
    specification: Specification;
    icon?: IconName;
}

export const taskStore = new class extends ExternalStoreBase {
    public addTask(task: BackgroundTask) {
        if (TaskOperations.isTaskTerminal(task)) {
            taskStore.addFinishedTask(task);
        } else {
            taskStore.addInProgressTask(task);
        }
    }

    public addTaskList(tasks: BackgroundTask[]) {
        for (const t of tasks) {
            this.addTask(t);
        }
    }

    public removeFinishedTask(task: BackgroundTask) {
        delete this.finishedTasks[task.taskId];
        // TODO(Jonas): Mark as read. Needs backend support.
        this.emitChange();
    }

    public inProgress: Record<number, BackgroundTask> = {};
    private addInProgressTask(task: BackgroundTask) {
        this.inProgress[task.taskId] = task;
        this.emitChange();
    }

    public finishedTasks: Record<number, BackgroundTask> = {};
    private addFinishedTask(task: BackgroundTask) {
        delete this.inProgress[task.taskId];
        this.finishedTasks[task.taskId] = task;
        this.emitChange();
    }
}();

const DEFAULT_ICON: IconName = "heroRectangleStack";

function TaskItem({task, ws}: {task: BackgroundTask; ws: WebSocketConnection}): React.JSX.Element {

    const isFinished = TaskOperations.isTaskTerminal(task);

    const operations: React.ReactNode[] = [];
    if (!isFinished) {
        if (task.specification.canPause) {
            if (task.status.state === TaskState.SUSPENDED) {
                operations.push(<Icon
                    onClick={() => ws.call(TaskOperations.calls.pauseOrCancel(task.taskId, TaskState.RUNNING))}
                    cursor="pointer"
                    name="play"
                    color="primaryMain"
                />);
            } else {
                operations.push(<Icon
                    onClick={() => ws.call(TaskOperations.calls.pauseOrCancel(task.taskId, TaskState.SUSPENDED))}
                    cursor="pointer"
                    name="pauseSolid"
                    color="primaryMain"
                />);
            }
        }

        if (task.specification.canCancel) {
            operations.push(<Icon name="close" cursor="pointer" ml="8px" color="errorMain" onClick={() => promptCancel(task, ws)} />);
        }
    } else {
        operations.push(
            <TooltipV2 tooltip="Clear task" contentWidth={100}>
                <Icon name="close" cursor="pointer" color="primaryMain" onClick={() => taskStore.removeFinishedTask(task)} />
            </TooltipV2>)
    }

    const icon = task.icon && iconNames.indexOf(task.icon) !== -1 ? task.icon : DEFAULT_ICON;

    return <TaskRow
        icon={<Icon name={icon} size={16} />}
        title={task.status.title}
        body={task.status.body}
        progress={task.status.progress}
        operations={operations}
        error={TaskOperations.taskError(task)}
        progressInfo={{
            indeterminate: TaskOperations.isIndeterminate(task),
            stopped: isFinished,
            limit: 100,
            progress: task.status.progressPercentage,
        }}
    />
}

const INDETERMINATE_VALUES = {
    successes: 1,
    failures: 0,
    total: 10
}

const IndeterminateSpinClass = injectStyleSimple("indeterminate-spinner", `
    animation: ${SpinAnimation} 2s linear infinite;
`);

const ANIMATION_SPEED = ".3s";
const FailureExpandAnimation = makeKeyframe("failure-expand", `
    from {
        stroke-dasharray: var(--arrayFrom) var(--arrayTo);
    }
    to {
        stroke-dasharray: var(--arrayTo) var(--arrayFrom);
    }
`);

const FailureExpandInterpolate = injectStyleSimple("interpolate", `
    animation: ${FailureExpandAnimation} ${ANIMATION_SPEED} ease-out;
`);

const FailureMoveAnimation = makeKeyframe("failure-move", `
    from {
        stroke-dashoffset: var(--offsetStart);
    }
    to {
        stroke-dashoffset: var(--offsetEnd);
    }
`);

const FailureMoveInterpolate = injectStyleSimple("interpolate", `
    animation: ${FailureMoveAnimation} ${ANIMATION_SPEED} ease-out;
`);


const SuccessAnimation = makeKeyframe("interpolation-fill", `
    from {
        stroke-dasharray: var(--from) var(--to);
    }
    to {
        stroke-dasharray: var(--to) var(--from);
    }
`);

const SuccessInterpolate = injectStyleSimple("interpolate", `
    animation: ${SuccessAnimation} ${ANIMATION_SPEED} ease-out;
`);


const RADIUS = 61.5;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

function toDashArray(current: number, total: number): number {
    const angle = current / total;
    return angle * CIRCUMFERENCE;
}

function toStrokeDashArray(dashArray: number): string {
    return `${dashArray} ${CIRCUMFERENCE - dashArray}`
}

export function ProgressCircle({
    pendingColor,
    finishedColor,
    indeterminate,
    size,
    ...stats
}: {successes: number; failures: number; total: number; size: number; pendingColor: ThemeColor; finishedColor: ThemeColor; indeterminate: boolean;}): React.ReactNode {
    const progressValues = indeterminate ? INDETERMINATE_VALUES : stats;

    const oldValues = React.useRef({successes: progressValues.successes, failures: progressValues.failures});

    const successDashArray = toDashArray(progressValues.successes, progressValues.total);
    const successStrokeDasharray = toStrokeDashArray(successDashArray);
    const failureStrokeDasharray = toStrokeDashArray(toDashArray(progressValues.failures, progressValues.total));

    const oldSuccess = oldValues.current.successes;
    const successStyle: React.CSSProperties = {};
    const oldSuccessDashArray = toDashArray(oldSuccess, progressValues.total)
    successStyle["--from"] = toStrokeDashArray(oldSuccessDashArray);
    successStyle["--to"] = successStrokeDasharray;
    oldValues.current.successes = progressValues.successes;

    const successRef = React.useRef<SVGCircleElement>(null);
    const failureRef = React.useRef<SVGCircleElement>(null);
    React.useEffect(() => {
        successRef.current?.classList.add(SuccessInterpolate);
        failureRef.current?.classList.add(FailureMoveInterpolate);
    }, [progressValues.successes]);

    React.useEffect(() => {
        failureRef.current?.classList.add(FailureExpandInterpolate);
    }, [progressValues.failures]);

    const failureOffset = CIRCUMFERENCE - successDashArray;

    const oldFailures = oldValues.current.failures;
    const oldFailureDashArray = toDashArray(oldFailures, progressValues.total)
    const failuresStyle: React.CSSProperties = {};
    failuresStyle["--arrayFrom"] = failureStrokeDasharray;
    failuresStyle["--arrayTo"] = toStrokeDashArray(oldFailureDashArray);
    failuresStyle["--offsetStart"] = CIRCUMFERENCE - oldFailureDashArray;
    failuresStyle["--offsetEnd"] = failureOffset;
    oldValues.current.failures = progressValues.failures;

    return (<svg className={indeterminate ? IndeterminateSpinClass : undefined} width={size.toString()} height={"auto"} viewBox="-17.875 -17.875 178.75 178.75" version="1.1" xmlns="http://www.w3.org/2000/svg" style={{transform: "rotate(-90deg)"}}>
        <circle r={RADIUS} cx="71.5" cy="71.5" fill="transparent" stroke={`var(--${pendingColor})`} strokeWidth="20" strokeDasharray="386.22px" strokeDashoffset=""></circle>
        <circle ref={failureRef} style={failuresStyle} onAnimationEnd={e => {
            if (e.animationName === FailureMoveAnimation) {
                e.currentTarget.classList.remove(FailureMoveInterpolate);
            } else {
                e.currentTarget.classList.remove(FailureExpandInterpolate);
            }
        }} r={RADIUS} cx="71.5" cy="71.5" stroke={`var(--errorMain)`} strokeWidth="20" strokeLinecap="butt" strokeDashoffset={failureOffset} fill="transparent" strokeDasharray={failureStrokeDasharray}></circle>
        <circle ref={successRef} style={successStyle} onAnimationEnd={e => e.currentTarget.classList.remove(SuccessInterpolate)} r={RADIUS} cx="71.5" cy="71.5" stroke={`var(--${finishedColor})`} strokeWidth="20" strokeLinecap="butt" strokeDashoffset={0} fill="transparent" strokeDasharray={successStrokeDasharray}></circle>
    </svg>)
}

function promptCancel(task: BackgroundTask, ws: WebSocketConnection) {
    addStandardDialog({
        title: "Cancel task?",
        message: "This will cancel the task, and will have to be restarted to finish.",
        onConfirm: () => {
            ws.call(TaskOperations.calls.pauseOrCancel(task.taskId, TaskState.CANCELLED))
        },
        cancelText: "Back",
        confirmText: "Cancel task",
        onCancel: () => void 0,
        cancelButtonColor: "successMain",
        confirmButtonColor: "errorMain",
        stopEvents: true,
    });
}

export function TaskList(): React.ReactNode {
    const [uploads] = useUploads();

    const [websocket, setWebsocket] = React.useState<WebSocketConnection>();

    const fileUploads = React.useMemo(() => {
        const uploadGrouping = Object.groupBy(uploads, t => uploadIsTerminal(t) ? "finished" : "uploading")
        if (uploadGrouping.finished == null) uploadGrouping.finished = [];
        if (uploadGrouping.uploading == null) uploadGrouping.uploading = [];
        return uploadGrouping as Record<"finished" | "uploading", Upload[]>;
    }, [uploads]);

    React.useEffect(() => {
        if (websocket) return;
        const ws = WSFactory.open(
            "/tasks",
            {
                init: async conn => {
                    try {
                        const page: PageV2<BackgroundTask> = (await ws.call(
                            TaskOperations.calls.browse({
                                itemsPerPage: 250,
                            } as any)
                        )).payload;

                        for (const item of page.items) {
                            taskStore.addTask(item);
                        }
                    } catch (e) {
                        console.warn(e);
                    }

                    try {
                        await conn.subscribe({
                            call: "tasks.listen",
                            payload: {},
                            handler: message => {
                                if (message.type === "message") {
                                    if (message.payload) {
                                        const task: BackgroundTask = message.payload;
                                        taskStore.addTask(task);
                                    }
                                }
                            }
                        });
                    } catch (e) {
                        console.warn(e);
                    }
                }
            }
        );

        setWebsocket(ws);
    }, [Client.isLoggedIn, websocket]);

    const inProgressTasks = React.useSyncExternalStore(s => taskStore.subscribe(s), () => taskStore.inProgress);
    const inProgressTaskList = Object.values(inProgressTasks).sort((a, b) => a.createdAt - b.createdAt);

    const finishedTasks = React.useSyncExternalStore(s => taskStore.subscribe(s), () => taskStore.finishedTasks);
    const finishedTaskList = Object.values(finishedTasks).sort((a, b) => a.createdAt - b.createdAt);;

    const anyFinished = finishedTaskList.length + fileUploads.finished.length > 0;
    const uploadCallbacks: UploadCallback = React.useMemo(() => ({
        startUploads: () => void 0, // Doesn't make sense in this context
        clearUploads: b => uploadStore.clearUploads(b, () => void 0),
        pauseUploads: b => uploadStore.pauseUploads(b),
        resumeUploads: b => uploadStore.resumeUploads(b, () => void 0),
        stopUploads: b => uploadStore.stopUploads(b)
    }), []);

    const activeUploadCount = useMemo(() => {
        return uploads.filter(it => it.state === UploadState.UPLOADING).length;
    }, [uploads]);


    useEffect(() => {
        window.removeEventListener("beforeunload", onBeforeUnload);
        if (activeUploadCount > 0) {
            window.addEventListener("beforeunload", onBeforeUnload);
        }

        return () => {
            window.removeEventListener("beforeunload", onBeforeUnload);
        };
    }, [activeUploadCount]);

    const rippleColoring: React.CSSProperties = {};

    const anyFailed = finishedTaskList.find(it => it.status.state === TaskState.FAILURE) != null;

    if (anyFailed)
        rippleColoring["--ringColor"] = "var(--errorMain)";
    else {
        rippleColoring["--ringColor"] = "#fff";
    }

    const inProgressCount = Object.values(inProgressTasks).length + activeUploadCount;

    const rippleRef = React.useRef<HTMLDivElement>(null);

    if (rippleRef.current) {
        if (inProgressCount) {
            rippleRef.current.classList.add(RippleEffect);
            rippleRef.current.classList.remove(StaticCircle);
        } else {
            rippleRef.current.classList.remove(RippleEffect);
            rippleRef.current.classList.add(StaticCircle);
        }
    }

    if (!websocket || !hasFeature(Feature.NEW_TASKS)) return null;
    return (
        <ClickableDropdown
            left="50px"
            bottom="-168px"
            colorOnHover={false}
            trigger={<div ref={rippleRef} className={RippleCenter} style={rippleColoring} />}
        >
            <Card cursor="default" onClick={stopPropagation} width="450px" maxHeight={"566px"} style={{paddingTop: "20px", paddingBottom: "20px"}}>
                <Box height={"526px"} overflowY="auto">
                    {fileUploads.uploading.length + inProgressTaskList.length ? <h4>Tasks in progress</h4> : null}
                    {fileUploads.uploading.map(u => <UploaderRow key={u.name} upload={u} callbacks={uploadCallbacks} />)}
                    {inProgressTaskList.map(t => <TaskItem key={t.taskId} task={t} ws={websocket} />)}
                    {anyFinished ? <Flex>
                        <h4 style={{marginBottom: "4px"}}>Finished tasks</h4>
                        <Box ml="auto">
                            <TooltipV2 contentWidth={190} tooltip={"Remove finished tasks"}>
                                <Icon name="close" onClick={() => {
                                    taskStore.finishedTasks = {};
                                    uploadStore.clearUploads(fileUploads.finished, () => void 0);
                                }} />
                            </TooltipV2>
                        </Box>
                    </Flex> : null}
                    {fileUploads.finished.map(u => <UploaderRow key={u.name} upload={u} callbacks={uploadCallbacks} />)}
                    {finishedTaskList.map(t => <TaskItem key={t.taskId} task={t} ws={websocket} />)}
                </Box>
            </Card>
        </ClickableDropdown>
    );
}

const RippleAnimation = makeKeyframe("ripple-animation", `
    0% {
        box-shadow: 0 0 0 0px var(--sidebarColor), 0 0 0 0px var(--sidebarColor), 0 0 0 0px var(--sidebarColor);
    }
    100% {
        box-shadow: 0 0 0 4px var(--sidebarColor), 0 0 0 6px var(--sidebarColor), 0 0 0 8px var(--ringColor);
    }
`);

const PULSE_ANIMATION_SPEED = "1.2s";

const RippleCenter = injectStyleSimple("ripple-center", `
    margin-top: 32px;
    margin-bottom: 32px;
    margin-left: 16px;
    margin-right: 16px;
    background-color: #FFF;
    width: 8px;
    height: 8px;
    border-radius: 50%;
`);

const RippleEffect = injectStyle("ripple", k => `
        ${k} {
            -webkit-animation: ${RippleAnimation} ${PULSE_ANIMATION_SPEED} alternate infinite;
            animation: ${RippleAnimation} ${PULSE_ANIMATION_SPEED} alternate infinite;
    }
`);

const StaticCircle = injectStyle(`static-circle`, k => `
        ${k} {
            box-shadow: 0 0 0 4px var(--sidebarColor), 0 0 0 6px var(--sidebarColor), 0 0 0 8px var(--ringColor);
    }
`);

const baseContext = "tasks";
const TaskOperations = new class {
    public isIndeterminate(task: BackgroundTask): boolean {
        return task.status.progressPercentage < 0;
    }


    public isTaskTerminal(task: BackgroundTask): boolean {
        return [TaskState.CANCELLED, TaskState.FAILURE, TaskState.SUCCESS].includes(task.status.state);
    }

    public taskError(task: BackgroundTask): string | undefined {
        if (task.status.state === TaskState.FAILURE) return task.status.progress;
        return undefined;
    }

    public calls = {
        browse(pageProps: PaginationRequestV2) {
            return {call: baseContext + ".browse", payload: pageProps};
        },
        retrieve(id: number) {
            return {call: baseContext + ".retrieve", payload: {id}};
        },
        listen() {
            return baseContext + ".listen";
        },
        pauseOrCancel(id: number, requestedState: TaskState) {
            return {call: baseContext + ".pauseOrCancel", payload: {id, requestedState}};
        }
    }
};

export default TaskList;

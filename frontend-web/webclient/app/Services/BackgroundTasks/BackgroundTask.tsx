import * as React from "react";
import {useEffect, useMemo} from "react";
import {Box, Card, Icon} from "@/ui-components";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import Flex from "@/ui-components/Flex";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {Upload, UploadState, uploadStore, useUploads} from "@/Files/Upload";
import {injectStyleSimple, makeKeyframe} from "@/Unstyled";
import {inDevEnvironment, stopPropagation} from "@/UtilityFunctions";
import {ExternalStoreBase} from "@/Utilities/ReduxUtilities";
import {WebSocketConnection} from "@/Authentication/ws";
import {IconName} from "@/ui-components/Icon";
import {TaskRow, UploadCallback, UploaderRow, uploadIsTerminal} from "@/Files/Uploader";
import {addStandardDialog} from "@/UtilityComponents";
import {TooltipV2} from "@/ui-components/Tooltip";
import {Client, WSFactory} from "@/Authentication/HttpClientInstance";
import {ThemeColor} from "@/ui-components/theme";
import {buildQueryString} from "@/Utilities/URIUtilities";
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

const ActiveTasksIcon = <Icon color="fixedWhite" color2="fixedWhite" height="24px" width="24px" mb={"16px"} name="heroQueueList" />;

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
    }


    const icon = task.icon && iconNames.indexOf(task.icon) !== -1 ? task.icon : DEFAULT_ICON;

    return <TaskRow
        icon={<Icon onClick={() => {
            console.log("TODO(Jonas): Remove me");
            ws.call(TaskOperations.calls.retrieve(task.taskId));
        }} name={icon} size={16} />}
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

export function ProgressCircle({
    pendingColor,
    finishedColor,
    indeterminate,
    size,
    ...stats
}: {successes: number; failures: number; total: number; size: number; pendingColor: ThemeColor; finishedColor: ThemeColor; indeterminate: boolean;}): React.ReactNode {
    // inspired/reworked from https://codepen.io/mjurczyk/pen/wvBKOvP
    const progressValues = indeterminate ? INDETERMINATE_VALUES : stats;

    const successAngle = progressValues.successes / progressValues.total;
    const failureAngle = (progressValues.failures + progressValues.successes) / progressValues.total;
    const radius = 61.5;
    const circumference = 2 * Math.PI * radius;
    const successDashArray = successAngle * circumference;
    const failureDashArray = failureAngle * circumference;
    const successStrokeDasharray = `${successDashArray} ${circumference - successDashArray}`;
    const failureStrokeDasharray = `${failureDashArray} ${circumference - failureDashArray}`;

    return (<svg className={indeterminate ? IndeterminateSpinClass : undefined} width={size.toString()} height={"auto"} viewBox="-17.875 -17.875 178.75 178.75" version="1.1" xmlns="http://www.w3.org/2000/svg" style={{transform: "rotate(-90deg)"}}>
        <circle r={radius} cx="71.5" cy="71.5" fill="transparent" stroke={`var(--${pendingColor})`} strokeWidth="20" strokeDasharray="386.22px" strokeDashoffset=""></circle>
        <circle r={radius} cx="71.5" cy="71.5" stroke={`var(--errorMain)`} strokeWidth="20" strokeLinecap="butt" strokeDashoffset={0} fill="transparent" strokeDasharray={failureStrokeDasharray}></circle>
        <circle r={radius} cx="71.5" cy="71.5" stroke={`var(--${finishedColor})`} strokeWidth="20" strokeLinecap="butt" strokeDashoffset={0} fill="transparent" strokeDasharray={successStrokeDasharray}></circle>
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
                        // TODO(Jonas): This is really not viable
                        ws.call(TaskOperations.calls.browse({itemsPerPage: 250, itemsToSkip: 0, next: null, consistency: "PREFER"} as any)).then(console.log)
                    } catch (e) {
                        console.warn(e);
                    }

                    try {
                        await conn.subscribe({
                            call: "tasks.listen",
                            payload: {},
                            handler: message => {
                                console.log("message", message)
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
    const finishedTaskList = Object.values(finishedTasks);

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

    const inProgressCount = Object.values(inProgressTasks).length + activeUploadCount;
    if (inProgressCount + Object.values(finishedTaskList).length === 0 || !websocket || !hasFeature(Feature.NEW_TASKS)) return null;
    return (
        <ClickableDropdown
            left="50px"
            bottom="-168px"
            colorOnHover={false}
            trigger={<Flex>{ActiveTasksIcon}</Flex>}
        >
            <Card onClick={stopPropagation} width="450px" maxHeight={"566px"} style={{paddingTop: "20px", paddingBottom: "20px"}}>
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


const baseContext = "tasks";
const TaskOperations = new class {
    public isIndeterminate(task: BackgroundTask): boolean {
        return task.status.progressPercentage < 0;
    }

    // TODO(Jonas): not in use. delete? 
    public didTaskNotFinish(task: BackgroundTask): boolean {
        return [TaskState.CANCELLED, TaskState.FAILURE].includes(task.status.state);
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

import * as React from "react";
import {useEffect, useMemo} from "react";
import {Box, Card, Icon} from "@/ui-components";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import Flex from "@/ui-components/Flex";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {Upload, UploadState, uploadStore, useUploads} from "@/Files/Upload";
import {injectStyle} from "@/Unstyled";
import {inDevEnvironment, prettierString, stopPropagation} from "@/UtilityFunctions";
import {ExternalStoreBase} from "@/Utilities/ReduxUtilities";
import {WebSocketConnection, WebSocketFactory} from "@/Authentication/ws";
import {IconName} from "@/ui-components/Icon";
import {PrettyFilePath} from "@/Files/FilePath";
import {sizeToString} from "@/Utilities/FileUtilities";
import {TaskRow, UploadCallback, UploaderRow, uploadIsTerminal} from "@/Files/Uploader";
import {addStandardDialog} from "@/UtilityComponents";
import {TooltipV2} from "@/ui-components/Tooltip";
import {Client} from "@/Authentication/HttpClientInstance";

function onBeforeUnload(): boolean {
    snackbarStore.addInformation(
        "You currently have uploads in progress. Are you sure you want to leave UCloud?",
        true
    );
    return false;
}

const TasksIconBase = injectStyle("task-icon-base", k => `
    @keyframes spin {
        0% {
           transform: rotate(0deg);
        }
        100% {
            transform: rotate(360deg);
        }
    }

    ${k} {
        animation: spin 2s linear infinite;
        margin-bottom: 16px;
    }
`);

const TasksIcon = <Icon color="fixedWhite" color2="fixedWhite" height="20px" width="20px" className={TasksIconBase} name="notchedCircle" />;

let mockMaxUpdateEntries = -1;
const MOCKING = {
    id: 0,
    mockTask(kind: Task["kind"]): Task {
        switch (kind) {
            case "COPY": {
                return MOCKING.mockCopyTask();
            }
            case "EMPTY_TRASH": {
                return MOCKING.mockEmptyTrashTask();
            }
            case "TRANSFER": {
                return MOCKING.mockTransferTask();
            }
        }
    },

    mockCopyTask(): CopyFiles {
        return {
            id: ++MOCKING.id,
            status: TaskState.PENDING,
            createdAt: new Date().getTime(),
            updatedAt: new Date().getTime(),
            kind: "COPY",
            destination: "/SomePosition" + MOCKING.id,
            source: "/file" + MOCKING.id,
            expectedEnd: new Date().getTime() + 100_000_000 * Math.random(),
        }
    },

    mockEmptyTrashTask(): EmptyTrash {
        return {
            id: ++MOCKING.id,
            status: TaskState.PENDING,
            createdAt: new Date().getTime(),
            updatedAt: new Date().getTime(),
            kind: "EMPTY_TRASH",
            path: `/67126/Trash` + MOCKING.id
        }
    },

    mockTransferTask(): TransferFiles {
        return {
            id: ++MOCKING.id,
            status: TaskState.PENDING,
            createdAt: new Date().getTime(),
            updatedAt: new Date().getTime(),
            kind: "TRANSFER",
            source: "/file" + MOCKING.id,
            destination: "Thing/" + MOCKING.id,
        }
    },

    mockUpdateEntries() {
        const index = Math.floor(Math.random() * Object.values(taskStore.inProgress).length);
        const entry = taskStore.inProgress[index];
        if (entry && entry.status !== TaskState.PAUSED) {
            const newEntry = {...entry};

            if (entry.fileSizeProgress != null && entry.fileSizeTotal != null) {
                newEntry.fileSizeProgress = Math.min(Math.max(1000, entry.fileSizeProgress * 2.5), entry.fileSizeTotal);
            } else {
                newEntry.fileSizeProgress = 0;
                newEntry.fileSizeTotal = (Math.random() * 100000) | 0;
            }
            let state = TaskState.IN_PROGRESS;
            if (newEntry.fileSizeProgress === newEntry.fileSizeTotal) {
                state = TaskState.SUCCEEDED;
            } else if (Math.random() < 0.05) {
                state = TaskState.FAILED;
                newEntry.error = "Failed due to threshold!"
            }
            newEntry.status = state;
            newEntry.updatedAt = new Date().getTime();

            if ([TaskState.CANCELLED, TaskState.FAILED, TaskState.SUCCEEDED].includes(newEntry.status)) {
                delete taskStore.inProgress[index];
                taskStore.finishedTasks[index] = newEntry;
            } else {
                taskStore.inProgress[index] = newEntry;
            }

            taskStore.emitChange();
        }

        if (mockMaxUpdateEntries !== -1) {
            if (--mockMaxUpdateEntries === 0) {
                return console.log("maxUpdateEntries reached");
            }
        }

        if (Object.values(taskStore.inProgress).length > 0) {
            window.setTimeout(MOCKING.mockUpdateEntries, 200 + Math.random() * 50);
        } else {
            console.log("Not setting timeout.");
        }
    },

    mockPause(task: Task) {
        MOCKING.mockNewState(task, TaskState.PAUSED);
    },

    mockResume(task: Task) {
        MOCKING.mockNewState(task, TaskState.IN_PROGRESS);
    },

    mockCancel(task: Task) {
        MOCKING.mockNewState(task, TaskState.CANCELLED);
        const updatedTask = taskStore.inProgress[task.id];
        delete taskStore.inProgress[task.id];
        taskStore.finishedTasks[task.id] = updatedTask;
        taskStore.emitChange();
    },

    mockNewState(task: Task, state: TaskState) {
        const newTask = {...task};
        newTask.status = state;
        newTask.updatedAt = new Date().getTime();
        taskStore.inProgress[task.id] = newTask;
    }
}

enum TaskState {
    PENDING, // Needed? Could just be if updates.length === 0
    PAUSED,
    IN_PROGRESS,
    CANCELLED,
    FAILED,
    SUCCEEDED
};

interface TaskBase {
    id: number; // number? Also, no need to show end-user.
    updatedAt: number;
    createdAt: number;
    status: TaskState;
    expectedEnd?: number;
    fileSizeTotal?: number;
    fileSizeProgress?: number;
    error?: string;
}

type Task = CopyFiles | TransferFiles | EmptyTrash; // Upload is handled differently

interface CopyFiles extends TaskBase {
    kind: "COPY";
    source: string;
    destination: string;
}

interface TransferFiles extends TaskBase {
    kind: "TRANSFER";
    source: string;
    destination: string;
}

interface EmptyTrash extends TaskBase {
    kind: "EMPTY_TRASH";
    path: string; // Path to trash could be neat. User could be deleting multiple trash-folder contents, or just change drive/project during
}

export const taskStore = new class extends ExternalStoreBase {
    ws: WebSocketConnection;
    inProgress: Record<number, Task> = {};
    finishedTasks: Record<number, Task> = {};

    async initConnection(): Promise<void> {}

    mockInitConnection(): void {
        const kinds: Task["kind"][] = ["COPY", "EMPTY_TRASH", "TRANSFER"];
        for (let i = 0; i < 8; i++) {
            const t = MOCKING.mockTask(kinds[i % kinds.length]);
            this.inProgress[t.id] = t;
        }

        setTimeout(MOCKING.mockUpdateEntries, 1000);
    }
}();

function isTaskFinished(task: Task): boolean {
    const s = task.status;
    return s === TaskState.FAILED || s === TaskState.SUCCEEDED || s === TaskState.CANCELLED;
}

function didTaskNotFinish(task: Task): boolean {
    return [TaskState.CANCELLED, TaskState.FAILED].includes(task.status);
}

function iconNameFromTaskType(task: Task): IconName {
    switch (task.kind) {
        case "COPY":
            return "copy";
        case "EMPTY_TRASH":
            return "trash";
        case "TRANSFER":
            return "move";
    }
}

function TaskItem({task}: {task: Task}): React.JSX.Element {

    let taskSpecificContent: React.ReactNode = null;
    const errorText = [TaskState.CANCELLED, TaskState.FAILED].includes(task.status) ? <b>[{prettierString(TaskState[task.status])}]</b> : "";
    switch (task.kind) {
        case "COPY": {
            taskSpecificContent = <>{errorText} {isTaskFinished(task) ? "Copied" : "Copying"} <b>{task.source}</b> to <b>{task.destination}</b></>
            break;
        }
        case "EMPTY_TRASH": {
            taskSpecificContent = <>{errorText} {isTaskFinished(task) ? "Emptied" : "Emptying"} trash <b><PrettyFilePath path={task.path} /></b></>
            break;
        }
        case "TRANSFER": {
            taskSpecificContent = <>{errorText} {isTaskFinished(task) ? "Transferred" : "Transferring"} <b>{task.source}</b> to <b>{task.destination}</b></>;
            break;
        }
    }

    let operations: React.ReactNode = null;
    switch (task.status) {
        case TaskState.FAILED:
        case TaskState.CANCELLED:
        case TaskState.SUCCEEDED:
            break;
        case TaskState.PENDING:
            operations = <Icon name="close" cursor="pointer" ml="8px" color="errorMain" onClick={() => promptCancel(task)} />;
            break;
        case TaskState.IN_PROGRESS: {
            operations = <>
                <Icon cursor="pointer" onClick={() => MOCKING.mockPause(task)} name="pauseSolid" color="primaryMain" />
                <Icon name="close" cursor="pointer" ml="8px" color="errorMain" onClick={() => promptCancel(task)} />
            </>
            break;
        }
        case TaskState.PAUSED: {
            operations = <>
                <Icon cursor="pointer" onClick={() => MOCKING.mockResume(task)} name="play" color="primaryMain" />
                <Icon name="close" cursor="pointer" ml="8px" color="errorMain" onClick={() => promptCancel(task)} />
            </>
            break;
        }
    }

    const progressText = task.fileSizeProgress != null && task.fileSizeTotal != null ? `${sizeToString(task.fileSizeProgress)} / ${sizeToString(task.fileSizeTotal)}` : "";

    return <TaskRow
        icon={<Icon name={iconNameFromTaskType(task)} size={24} />}
        left={taskSpecificContent as unknown as string}
        right={progressText}
        operations={operations}
        error={task.error}
        progressInfo={{
            stopped: didTaskNotFinish(task),
            limit: task.fileSizeTotal ?? 100,
            progress: task.fileSizeProgress ?? 0
        }}
    />
}

function promptCancel(task: Task) {
    addStandardDialog({
        title: "Cancel task?",
        message: "This will cancel the task, and will have to be restarted to finish.",
        onConfirm: () => MOCKING.mockCancel(task),
        cancelText: "Back",
        confirmText: "Cancel task",
        onCancel: () => void 0,
        cancelButtonColor: "successMain",
        confirmButtonColor: "errorMain",
        stopEvents: true,
    })
}

export function TaskList(): React.ReactNode {
    const [uploads] = useUploads();
    const fileUploads = React.useMemo(() => {
        const uploadGrouping = Object.groupBy(uploads, t => uploadIsTerminal(t) ? "finished" : "uploading")
        if (uploadGrouping.finished == null) uploadGrouping.finished = [];
        if (uploadGrouping.uploading == null) uploadGrouping.uploading = [];
        return uploadGrouping as Record<"finished" | "uploading", Upload[]>;
    }, [uploads]);

    React.useEffect(() => {
        /* if (Client.isLoggedIn) {
            const conn = WSFactory.open(
                "/tasks", {
                init: async conn => {
                    await conn.subscribe({
                        call: "tasks.follow",
                        payload: {},
                        handler: message => {
                            // TODO
                        }
                    });
                    conn.close();
                },
            });
        } */
        taskStore.mockInitConnection();
    }, [Client.isLoggedIn]);

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
        let activeCount = 0;
        for (let i = 0; i < uploads.length; i++) {
            if (uploads[i].state === UploadState.UPLOADING) {
                activeCount++;
            }
        }
        return activeCount;
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

    if ((Object.values(inProgressTasks).length + activeUploadCount) === 0 || !inDevEnvironment()) return null;
    return (
        <ClickableDropdown
            left="50px"
            bottom="-168px"
            trigger={<Flex justifyContent="center">{TasksIcon}</Flex>}
        >
            <Card onClick={stopPropagation} width="450px" maxHeight={"566px"} style={{paddingTop: "20px", paddingBottom: "20px"}}>
                <Box height={"526px"} overflowY="scroll">
                    {fileUploads.uploading.length + inProgressTaskList.length ? <h4>Tasks in progress</h4> : null}
                    {fileUploads.uploading.map(u => <UploaderRow key={u.name} upload={u} callbacks={uploadCallbacks} />)}
                    {inProgressTaskList.map(t => <TaskItem key={t.id} task={t} />)}
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
                    {finishedTaskList.map(t => <TaskItem key={t.id} task={t} />)}
                </Box>
            </Card>
        </ClickableDropdown>
    );


}

export default TaskList;

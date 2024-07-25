import {MainContainer} from "@/ui-components/MainContainer";
import * as React from "react";
import {useEffect} from "react";
import Icon, {EveryIcon, IconName} from "@/ui-components/Icon";
import {Box, Card, Flex, Text} from "@/ui-components";
import {ThemeColor} from "@/ui-components/theme";
import {api as ProjectApi, useProjectId} from "@/Project/Api";
import {useCloudAPI} from "@/Authentication/DataHook";
import * as icons from "@/ui-components/icons";
import {Project} from "@/Project";
import {NewAndImprovedProgress} from "@/ui-components/Progress";
import {Upload, UploadState, uploadStore, useUploads} from "@/Files/Upload";
import {TaskRow, UploadCallback, UploaderRow, uploadIsTerminal} from "@/Files/Uploader";
import {useGlobal} from "@/Utilities/ReduxHooks";
import {formatDistance} from "date-fns";
import {ExternalStoreBase} from "@/Utilities/ReduxUtilities";
import {WebSocketConnection} from "@/Authentication/ws";
import {PrettyFilePath} from "@/Files/FilePath";
import {sizeToString} from "@/Utilities/FileUtilities";
import {addStandardDialog} from "@/UtilityComponents";
import {prettierString, stopPropagation} from "@/UtilityFunctions";
import {TooltipV2} from "@/ui-components/Tooltip";

let maxUpdateEntries = -1;
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

        if (maxUpdateEntries !== -1) {
            if (--maxUpdateEntries === 0) {
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

const iconsNames = Object.keys(icons) as IconName[];

export const taskStore = new class extends ExternalStoreBase {
    ws: WebSocketConnection;
    inProgress: Record<number, Task> = {};
    finishedTasks: Record<number, Task> = {};

    async fetch(): Promise<void> {
        const kinds: Task["kind"][] = ["COPY", "EMPTY_TRASH", "TRANSFER"];
        for (let i = 0; i < 8; i++) {
            const t = MOCKING.mockTask(kinds[i % kinds.length]);
            this.inProgress[t.id] = t;
        }

        setTimeout(MOCKING.mockUpdateEntries, 1000);

        // const result = await callAPI(({
        //     method: "GET",
        //     path: buildQueryString("/tasks", {itemsPerPage: 100, page: 0})
        // }));
    }
}();

taskStore.fetch();


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
        case TaskState.PENDING:
        case TaskState.CANCELLED:
        case TaskState.SUCCEEDED:
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
        return uploadGrouping;
    }, [uploads]) as Record<"finished" | "uploading", Upload[]>;

    const inProgressTasks = React.useSyncExternalStore(s => taskStore.subscribe(s), () => taskStore.inProgress);
    const inProgressTaskList = Object.values(inProgressTasks).sort((a, b) => a.createdAt - b.createdAt);

    const finishedTasks = React.useSyncExternalStore(s => taskStore.subscribe(s), () => taskStore.finishedTasks);
    const finishedTaskList = Object.values(finishedTasks);

    const anyFinished = finishedTaskList.length + fileUploads.finished.length > 0;

    const taskNumbers = React.useMemo(() => {
        let successes = 0;
        let failures = 0;
        for (const task of finishedTaskList) {
            const state = task.status;
            if (state === TaskState.FAILED) failures++;
            else if (state === TaskState.SUCCEEDED) successes++;
        }
        return {successes, failures};
    }, [Object.values(finishedTasks).length]);


    const uploadCallbacks: UploadCallback = React.useMemo(() => ({
        startUploads: () => void 0, // Doesn't make sense in this context
        clearUploads: b => uploadStore.clearUploads(b, () => void 0),
        pauseUploads: b => uploadStore.pauseUploads(b),
        resumeUploads: b => uploadStore.resumeUploads(b, () => void 0),
        stopUploads: b => uploadStore.stopUploads(b)
    }), []);

    return (<Card onClick={stopPropagation} width="450px" maxHeight={"566px"} style={{paddingTop: "20px", paddingBottom: "20px"}}>
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
    </Card>)
}

const Playground: React.FunctionComponent = () => {

    const main = (
        <>
            <TaskList />
            <Box mb="60px" />

            {/* <NewAndImprovedProgress limitPercentage={20} label="Twenty!" percentage={30} />
            <NewAndImprovedProgress limitPercentage={40} label="Forty!" percentage={30} />
            <NewAndImprovedProgress limitPercentage={60} label="Sixty!" percentage={30} />
            <NewAndImprovedProgress limitPercentage={80} label="Eighty!" percentage={30} />
            <NewAndImprovedProgress limitPercentage={100} label="Hundred!" percentage={30} />
            <NewAndImprovedProgress limitPercentage={120} label="Above!!" percentage={30} />
            <NewAndImprovedProgress limitPercentage={120} label="OY!" percentage={110} />
            <NewAndImprovedProgress limitPercentage={100} label="OY!" percentage={130} withWarning /> */}
            <PaletteColors />
            <Colors />
            <EveryIcon />
            {/*
            <Button onClick={() => {
                messageTest();
            }}>UCloud message test</Button>
            <Button onClick={() => {
                function useAllocator<R>(block: (allocator: BinaryAllocator) => R): R {
                    const allocator = BinaryAllocator.create(1024 * 512, 128)
                    return block(allocator);
                }

                const encoded = useAllocator(alloc => {
                    const root = Wrapper.create(
                        alloc,
                        AppParameterFile.create(
                            alloc,
                            "/home/dan/.vimrc"
                        )
                    );
                    alloc.updateRoot(root);
                    return alloc.slicedBuffer();
                });

                console.log(encoded);

                {
                    const value = loadMessage(Wrapper, encoded).wrapThis;

                    if (value instanceof AppParameterFile) {
                        console.log(value.path, value.encodeToJson());
                    } else {
                        console.log("Not a file. It must be something else...", value);
                    }
                }
            }}>UCloud message test2</Button>
            <ProductSelectorPlayground />
            <Button onClick={() => {
                const now = timestampUnixMs();
                for (let i = 0; i < 50; i++) {
                    sendNotification({
                        icon: "bug",
                        title: `Notification ${i}`,
                        body: "This is a test notification",
                        isPinned: false,
                        uniqueId: `${now}-${i}`,
                    });
                }
            }}>Trigger 50 notifications</Button>
            <Button onClick={() => {
                sendNotification({
                    icon: "logoSdu",
                    title: `This is a really long notification title which probably shouldn't be this long`,
                    body: "This is some text which maybe is slightly longer than it should be but who really cares.",
                    isPinned: false,
                    uniqueId: `${timestampUnixMs()}`,
                });
            }}>Trigger notification</Button>

            <Button onClick={() => {
                sendNotification({
                    icon: "key",
                    title: `Connection required`,
                    body: <>
                        You must <BaseLink href="#">re-connect</BaseLink> with 'Hippo' to continue
                        using it.
                    </>,
                    isPinned: true,
                    // NOTE(Dan): This is static such that we can test the snooze functionality. You will need to
                    // clear local storage for this to start appearing again after dismissing it enough times.
                    uniqueId: `playground-notification`,
                });
            }}>Trigger pinned notification</Button>

            <Button onClick={() => snackbarStore.addSuccess("Hello. This is a success.", false, 5000)}>Add success notification</Button>
            <Button onClick={() => snackbarStore.addInformation("Hello. This is THE information.", false, 5000)}>Add info notification</Button>
            <Button onClick={() => snackbarStore.addFailure("Hello. This is a failure.", false, 5000)}>Add failure notification</Button>
            <Button onClick={() => snackbarStore.addSnack({
                message: "Hello. This is a custom one with a text that's pretty long.",
                addAsNotification: false,
                icon: iconsNames.at(Math.floor(Math.random() * iconsNames.length))!,
                type: SnackType.Custom
            })}>Add custom notification</Button>

            <Grid gridTemplateColumns={"repeat(5, 1fr)"} mb={"32px"}>
                <EveryIcon />
            </Grid>

            <Grid
                gridTemplateColumns="repeat(10, 1fr)"
                style={{overflowY: "auto"}}
                mb={"32px"}
            >
                {colors.map((c: ThemeColor) => (
                    <div
                        title={`${c}, var(${c})`}
                        key={c}
                        style={{color: "black", backgroundColor: `var(--${c})`, height: "100%", width: "100%"}}
                    >
                        {c} {getCssPropertyValue(c)}
                    </div>
                ))}
            </Grid>
            <ConfirmationButton icon={"trash"} actionText={"Delete"} color={"errorMain"} />

            <TabbedCard>
                <TabbedCardTab icon={"heroChatBubbleBottomCenter"} name={"Messages"}>
                    These are the messages!
                </TabbedCardTab>

                <TabbedCardTab icon={"heroGlobeEuropeAfrica"} name={"Public links"}>
                    Public links go here!
                </TabbedCardTab>

                <TabbedCardTab icon={"heroServerStack"} name={"Connected jobs"}>
                    Connections!
                </TabbedCardTab>
            </TabbedCard>
            */}
        </>
    );
    return <MainContainer main={main} />;
};

const ProjectPlayground: React.FunctionComponent = () => {
    const projectId = useProjectId();
    const [project, fetchProject] = useCloudAPI<Project | null>({noop: true}, null);
    useEffect(() => {
        fetchProject(ProjectApi.retrieve({id: projectId ?? "", includeMembers: true, includeGroups: true, includeFavorite: true}));
    }, [projectId]);

    if (project.data) {
        return <>Title: {project.data.specification.title}</>;
    } else {
        return <>Project is still loading...</>;
    }
}

const colors: ThemeColor[] = [
    "primaryMain",
    "primaryLight",
    "primaryDark",
    "primaryContrast",

    "secondaryMain",
    "secondaryLight",
    "secondaryDark",
    "secondaryContrast",

    "errorMain",
    "errorLight",
    "errorDark",
    "errorContrast",

    "warningMain",
    "warningLight",
    "warningDark",
    "warningContrast",

    "infoMain",
    "infoLight",
    "infoDark",
    "infoContrast",

    "successMain",
    "successLight",
    "successDark",
    "successContrast",

    "backgroundDefault",
    "backgroundCard",

    "textPrimary",
    "textSecondary",
    "textDisabled",

    "iconColor",
    "iconColor2",

    "fixedWhite",
    "fixedBlack",

    "wayfGreen",
];


const paletteColors = ["purple", "red", "orange", "yellow", "green", "gray", "blue"];
const numbers = [5, 10, 20, 30, 40, 50, 60, 70, 80, 90];

function CSSPaletteColorVar({color, num}: {color: string, num: number}) {
    const style: React.CSSProperties = {
        backgroundColor: `var(--${color}-${num})`,
        color: num >= 60 ? "white" : "black",
        width: "150px",
        height: "100px",
        paddingTop: "38px",
        paddingLeft: "32px",
    }
    return <div style={style}>--{color}-{num}</div>
}

function Colors(): React.ReactNode {
    return <Flex>
        {colors.map(color => {
            const style: React.CSSProperties = {
                backgroundColor: `var(--${color})`,
                color: "teal",
                width: "150px",
                height: "100px",
                paddingTop: "38px",
                paddingLeft: "32px",
            }
            return <div style={style}>--{color}</div>
        })}
    </Flex>
}

function PaletteColors(): React.ReactNode {
    return <Flex>
        {paletteColors.map(color => <div>{numbers.map(number => <CSSPaletteColorVar color={color} num={number} />)}</div>)}
    </Flex>
}

export default Playground;



import {WSFactory} from "@/Authentication/HttpClientInstance";
import {Progress, Speed, Task, TaskUpdate} from "@/Services/BackgroundTasks/api";
import DetailedTask from "@/Services/BackgroundTasks/DetailedTask";
import * as React from "react";
import {useCallback, useEffect, useMemo, useState} from "react";
import {default as ReactModal} from "react-modal";
import {Icon} from "@/ui-components";
import Box from "@/ui-components/Box";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import Flex from "@/ui-components/Flex";
import IndeterminateProgressBar from "@/ui-components/IndeterminateProgress";
import ProgressBar from "@/ui-components/Progress";
import {defaultModalStyle} from "@/Utilities/ModalUtilities";
import {useCloudAPI} from "@/Authentication/DataHook";
import {buildQueryString} from "@/Utilities/URIUtilities";
import {associateBy, takeLast} from "@/Utilities/CollectionUtilities";
import {useGlobal} from "@/Utilities/ReduxHooks";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {UploadState, useUploads} from "@/Files/Upload";
import {CardClass} from "@/ui-components/Card";
import {injectStyle} from "@/Unstyled";
import {emptyPage} from "@/Utilities/PageUtilities";

function insertTimestamps(speeds: Speed[]): Speed[] {
    return speeds.map(it => {
        if (it.clientTimestamp) {
            return it;
        } else {
            return {...it, clientTimestamp: Date.now()};
        }
    });
}

const BackgroundTasks: React.FunctionComponent = () => {
    const [initialTasks, fetchInitialTasks] = useCloudAPI<Page<Task>>({noop: true}, emptyPage);
    const [taskInFocus, setTaskInFocus] = useState<string | null>(null);
    const [tasks, setTasks] = useState<Record<string, TaskUpdate>>({});
    const [uploads] = useUploads();
    const [, setUploaderVisible] = useGlobal("uploaderVisible", false);

    const openUploader = useCallback(() => {
        setUploaderVisible(true);
    }, [setUploaderVisible]);

    const handleTaskUpdate = useCallback((update: TaskUpdate) => {
        setTasks(oldTasks => {
            const newTasks: Record<string, TaskUpdate> = {...oldTasks};
            const existingTask = newTasks[update.jobId];
            if (update.complete) {
                delete newTasks[update.jobId];
            } else if (!existingTask) {
                newTasks[update.jobId] = {
                    ...update,
                    speeds: insertTimestamps(update.speeds)
                };
            } else {
                const currentMessage = existingTask.messageToAppend ? existingTask.messageToAppend : "";
                const messageToAdd = update.messageToAppend ? update.messageToAppend : "";
                const newMessage = currentMessage + messageToAdd;

                const newStatus = update.newStatus ? update.newStatus : existingTask.newStatus;
                const newTitle = update.newTitle ? update.newTitle : existingTask.newTitle;
                const newProgress = update.progress ? update.progress : existingTask.progress;
                const newSpeed = takeLast((existingTask.speeds || []).concat(update.speeds || []), 500);
                const newComplete = update.complete ? update.complete : existingTask.complete;

                newTasks[update.jobId] = {
                    ...existingTask,
                    messageToAppend: newMessage,
                    progress: newProgress,
                    speeds: insertTimestamps(newSpeed),
                    complete: newComplete,
                    newStatus,
                    newTitle
                };
            }
            return newTasks;
        });
    }, [setTasks]);

    useEffect(() => {
        fetchInitialTasks({
            method: "GET",
            path: buildQueryString("/tasks", {itemsPerPage: 100, page: 0})
        });
    }, []);

    useEffect(() => {
        setTasks(associateBy(
            initialTasks.data.items.map(it => ({
                jobId: it.jobId,
                speeds: [],
                messageToAppend: null,
                progress: null,
                newTitle: it.title,
                complete: false,
                newStatus: it.status
            }),
            ),
            it => it.jobId
        ));
    }, [initialTasks.data]);

    useEffect(() => {
        const wsConnection = WSFactory.open("/tasks", {
            init: async conn => {
                await conn.subscribe({
                    call: "task.listen",
                    payload: {},
                    handler: message => {
                        if (message.type === "message") {
                            const payload = message.payload as TaskUpdate;
                            handleTaskUpdate(payload);
                        }
                    }
                });
            }
        });

        return () => wsConnection.close();
    }, []);

    const onDetailedClose = useCallback(() => {
        setTaskInFocus(null);
    }, []);

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
        if (activeUploadCount > 0) {
            window.onbeforeunload = () => {
                snackbarStore.addInformation(
                    "You currently have uploads in progress. Are you sure you want to leave UCloud?",
                    true
                );
                return false;
            };
        }

        return () => {
            window.onbeforeunload = null;
        };
    }, [uploads]);

    const hasTaskInFocus = taskInFocus && (tasks && tasks[taskInFocus]);
    const numberOfTasks = Object.keys(tasks).length + activeUploadCount;
    if (numberOfTasks === 0) return null;
    return (
        <>
            <ClickableDropdown
                width="600px"
                left="-400px"
                top="37px"
                trigger={<Flex justifyContent="center"><TasksIcon /></Flex>}
            >
                {!tasks ? null :
                    <>
                        {uploads.length === 0 ? null :
                            <TaskComponent title={"File uploads"} jobId={"uploads"} onClick={openUploader} />
                        }
                        {Object.values(tasks).map(update => (
                            <TaskComponent
                                key={update.jobId}
                                jobId={update.jobId}
                                onClick={setTaskInFocus}
                                title={update.newTitle ?? ""}
                                speed={!!update.speeds ? update.speeds[update.speeds.length - 1] : undefined}
                                progress={update.progress ? update.progress : undefined}
                            />
                        ))}
                    </>
                }
            </ClickableDropdown>

            <ReactModal
                style={defaultModalStyle}
                isOpen={!!hasTaskInFocus}
                onRequestClose={onDetailedClose}
                ariaHideApp={false}
                className={CardClass}
            >
                {!hasTaskInFocus ? null : <DetailedTask task={tasks[taskInFocus!]!} />}
            </ReactModal>
        </>
    );
};

interface TaskComponentProps {
    title: string;
    progress?: Progress;
    speed?: Speed;
    onClick?: (jobId: string) => void;
    jobId?: string;
}

const TaskComponent: React.FunctionComponent<TaskComponentProps> = props => {
    const label = props.speed?.asText ?? "";
    const onClickHandler = useCallback(
        () => {
            if (props.onClick && props.jobId) {
                props.onClick(props.jobId);
            }
        },
        [props.jobId, props.onClick]
    );

    return (
        <Flex className={TaskContainer} onClick={onClickHandler}>
            <Box mr={16}>
                <b>{props.title}</b>
            </Box>

            <Box flexGrow={1}>
                {!props.progress ?
                    <IndeterminateProgressBar color="successMain" label={label} /> :

                    (
                        <ProgressBar
                            active={true}
                            color="successMain"
                            label={label}
                            percent={(props.progress.current / props.progress.maximum) * 100}
                        />
                    )
                }
                <Icon name="activity" className="foo" />
            </Box>
        </Flex>
    );
};

const TaskContainer = injectStyle("task-container", k => `
    ${k} {
        padding: 16px;
        cursor: pointer;
    }

    ${k} > * {
        cursor: inherit;
    }
`);

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
        margin-right: 8px;
    }
`);

const TasksIcon = (): React.JSX.Element => <Icon className={TasksIconBase} name="notchedCircle" />;

export default BackgroundTasks;

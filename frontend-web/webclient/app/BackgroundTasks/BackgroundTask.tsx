import {Cloud, WSFactory} from "Authentication/SDUCloudObject";
import {Progress, Speed, Task, TaskUpdate} from "BackgroundTasks/api";
import DetailedTask from "BackgroundTasks/DetailedTask";
import {taskLoadAction, taskUpdateAction} from "BackgroundTasks/redux";
import {ReduxObject} from "DefaultObjects";
import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import * as ReactModal from "react-modal";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import styled from "styled-components";
import {Dictionary, Page} from "Types";
import {Icon} from "ui-components";
import Box from "ui-components/Box";
import ClickableDropdown from "ui-components/ClickableDropdown";
import Flex from "ui-components/Flex";
import IndeterminateProgressBar from "ui-components/IndeterminateProgress";
import ProgressBar from "ui-components/Progress";
import {Upload} from "Uploader";
import {setUploaderVisible} from "Uploader/Redux/UploaderActions";
import {calculateUploadSpeed} from "Uploader/Uploader";
import {sizeToHumanReadableWithUnit} from "Utilities/FileUtilities";
import {defaultModalStyle} from "Utilities/ModalUtilities";
import {buildQueryString} from "Utilities/URIUtilities";

interface BackgroundTaskProps {
    activeUploads: number;
    uploads: Upload[];
    showUploader: () => void;
    tasks?: Dictionary<TaskUpdate>;
    onTaskUpdate: (update: TaskUpdate) => void;
    loadInitialTasks: () => void;
}

const BackgroundTasks = (props: BackgroundTaskProps) => {
    const [taskInFocus, setTaskInFocus] = useState<string | null>(null);

    useEffect(() => {
        props.loadInitialTasks();
    }, []);

    useEffect(() => {
        const wsConnection = WSFactory.open("/tasks", {
            init: async conn => {
                await conn.subscribe({
                    call: "task.listen",
                    payload: {},
                    handler: message => {
                        if (message.type === "message") {
                            const payload = message.payload as TaskUpdate;
                            props.onTaskUpdate(payload);
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

    let speedSum = 0;
    let uploadedSize = 0;
    let targetUploadSize = 0;

    props.uploads.forEach(upload => {
        if (upload.isUploading) {
            speedSum += calculateUploadSpeed(upload);
            targetUploadSize += upload.uploadSize;
            if (upload.uploadEvents.length > 0) {
                uploadedSize += upload.uploadEvents[upload.uploadEvents.length - 1].progressInBytes;
            }
        }
    });

    const humanReadable = sizeToHumanReadableWithUnit(speedSum);
    const uploadTask: TaskComponentProps = {
        title: "File uploads",
        progress: {
            title: "Bytes uploaded",
            maximum: targetUploadSize,
            current: uploadedSize
        },
        speed: {
            title: "Transfer speed",
            speed: humanReadable.size,
            unit: humanReadable.unit + "/s",
            asText: `${humanReadable.size} ${humanReadable.unit}/s`
        }
    };

    if (props.activeUploads <= 0 && (props.tasks === undefined || (Object.keys(props.tasks).length === 0))) {
        return null;
    }

    const hasTaskInFocus = taskInFocus && (props.tasks && props.tasks[taskInFocus]);
    return (
        <>
            <ClickableDropdown
                width={"600px"}
                left={"-400px"}
                top={"37px"}
                trigger={<TasksIcon />}
            >
                {props.activeUploads <= 0 ? null : <TaskComponent {...uploadTask} />}
                {!props.tasks ? null :
                    Object.values(props.tasks).map(update => {
                        return (
                            <TaskComponent
                                key={update.jobId}
                                jobId={update.jobId}
                                onClick={setTaskInFocus}
                                title={update.newTitle || ""}
                                speed={!!update.speeds ? update.speeds[update.speeds.length - 1] : undefined}
                                progress={update.progress ? update.progress : undefined}
                            />
                        );
                    })
                }
            </ClickableDropdown>

            <ReactModal
                style={defaultModalStyle}
                isOpen={!!hasTaskInFocus}
                onRequestClose={onDetailedClose}
                ariaHideApp={false}
            >
                {!hasTaskInFocus ? null : <DetailedTask taskId={taskInFocus!} />}
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
    const label = !props.speed ? "" : props.speed.asText;
    const onClickHandler = useCallback(
        () => {
            if (props.onClick && props.jobId) {
                props.onClick(props.jobId);
            }
        },
        [props.jobId, props.onClick]
    );

    return (
        <TaskContainer onClick={onClickHandler}>
            <Box mr={16}>
                <b>{props.title}</b>
            </Box>

            <Box flexGrow={1}>
                {!props.progress ?
                    <IndeterminateProgressBar color={"green"} label={label} /> :

                    (
                        <ProgressBar
                            active={true}
                            color={"green"}
                            label={label}
                            percent={(props.progress.current / props.progress.maximum) * 100}
                        />
                    )
                }
            </Box>
        </TaskContainer>
    );
};

const TaskContainer = styled(Flex)`
    padding: 16px;
    cursor: pointer;

    > * {
        cursor: inherit;
    }
`;

const TasksIconBase = styled(Icon)`
    animation: spin 2s linear infinite;
    margin-right: 8px;

    @keyframes spin {
      0% { transform: rotate(0deg); }
      100% { transform: rotate(360deg); }
    }
`;

const mapStateToProps = (state: ReduxObject) => ({
    uploads: state.uploader.uploads,
    activeUploads: state.uploader.uploads.filter(it => it.uploadXHR &&
        it.uploadXHR.readyState > XMLHttpRequest.UNSENT && it.uploadXHR.readyState < XMLHttpRequest.DONE).length,
    tasks: state.tasks
});

const mapDispatchToProps = (dispatch: Dispatch) => ({
    showUploader: () => dispatch(setUploaderVisible(true, Cloud.homeFolder)),
    onTaskUpdate: (update: TaskUpdate) => dispatch(taskUpdateAction(update)),
    loadInitialTasks: async () => {
        const result: Page<Task> = (await Cloud.get(buildQueryString("/tasks", {itemsPerPage: 100, page: 0}))).response;
        dispatch(taskLoadAction(result));
    }
});

const TasksIcon = () => <TasksIconBase name="notchedCircle" />;

export default connect(mapStateToProps, mapDispatchToProps)(BackgroundTasks);

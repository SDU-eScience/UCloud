import {Cloud, WSFactory} from "Authentication/SDUCloudObject";
import {Progress, Speed, Task, TaskUpdate} from "BackgroundTasks/api";
import DetailedTask from "BackgroundTasks/DetailedTask";
import {taskUpdateAction} from "BackgroundTasks/redux";
import {ReduxObject} from "DefaultObjects";
import * as React from "react";
import {useEffect, useState} from "react";
import * as ReactModal from "react-modal";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import styled from "styled-components";
import {Dictionary} from "Types";
import {Button, Icon} from "ui-components";
import Box from "ui-components/Box";
import ClickableDropdown from "ui-components/ClickableDropdown";
import Flex from "ui-components/Flex";
import IndeterminateProgressBar from "ui-components/IndeterminateProgress";
import ProgressBar from "ui-components/Progress";
import {Upload} from "Uploader";
import {setUploaderVisible} from "Uploader/Redux/UploaderActions";
import {calculateUploadSpeed} from "Uploader/Uploader";
import {sizeToHumanReadableWithUnit} from "Utilities/FileUtilities";

interface BackgroundTaskProps {
    activeUploads: number;
    uploads: Upload[];
    showUploader: () => void;
    tasks?: Dictionary<TaskUpdate>;
    onTaskUpdate: (update: TaskUpdate) => void;
}

const BackgroundTasks = (props: BackgroundTaskProps) => {
    let speedSum = 0;
    let uploadedSize = 0;
    let targetUploadSize = 0;

    const [taskInFocus, setTaskInFocus] = useState<string | null>(null);

    useEffect(() => {
        const wsConnection = WSFactory.open("/tasks", {
            init: conn => {
                conn.subscribe({
                    call: "task.listen",
                    payload: {},
                    handler: message => {
                        if (message.type === "message") {
                            const payload = message.payload as TaskUpdate;
                            props.onTaskUpdate(payload);
                        }
                        console.log(message);
                    }
                });
            }
        });

        return () => wsConnection.close();
    }, []);

    props.uploads.forEach(upload => {
        if (upload.isUploading) {
            speedSum += calculateUploadSpeed(upload);
            uploadedSize += upload.uploadSize;
            if (upload.uploadEvents.length > 0) {
                targetUploadSize += upload.uploadEvents[upload.uploadEvents.length - 1].progressInBytes;
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
            unit: humanReadable.unit + "/s"
        }
    };

    return <>
        <ClickableDropdown
            width="600px"
            left="-400px"
            top="37px"
            trigger={<TasksIcon/>}
        >
            {" "}
            {props.activeUploads <= 0 ? null : <TaskComponent {...uploadTask} />}
            {!props.tasks ? null :
                Object.values(props.tasks).map(update => {
                    return <TaskComponent
                        onClick={() => setTaskInFocus(update.jobId)}
                        title={update.newTitle ? update.newTitle : ""}
                        speed={!!update.speeds ? update.speeds[0] : undefined}
                        progress={update.progress ? update.progress : undefined}
                    />;
                })
            }
        </ClickableDropdown>

        <ReactModal isOpen={taskInFocus !== null} onRequestClose={() => setTaskInFocus(null)}>
            {!taskInFocus ? null : <DetailedTask taskId={taskInFocus}/>}
        </ReactModal>
    </>;
};

interface TaskComponentProps {
    title: string;
    progress?: Progress;
    speed?: Speed;
    onClick?: () => void;
}

const TaskComponent: React.FunctionComponent<TaskComponentProps> = props => {
    const label = !props.speed ? "" : `${props.speed.speed} ${props.speed.unit}`;
    return <Flex p={16} onClick={props.onClick}>
        <Box mr={16}>
            <b>{props.title}</b>
        </Box>

        <Box flexGrow={1}>
            {!props.progress ?
                <IndeterminateProgressBar color={"green"} label={label}/> :

                <ProgressBar
                    active
                    color={"green"}
                    label={label}
                    percent={(props.progress.current / props.progress.maximum) * 100}
                />
            }
        </Box>
    </Flex>;
};

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
});

const TasksIcon = () => <TasksIconBase name="notchedCircle"/>;

export default connect(mapStateToProps, mapDispatchToProps)(BackgroundTasks);

import {Cloud} from "Authentication/SDUCloudObject";
import {Progress, Speed, Task, TaskUpdate} from "BackgroundTasks/api";
import {ReduxObject} from "DefaultObjects";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import styled from "styled-components";
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

const BackgroundTasks = (props: { activeUploads: number, uploads: Upload[], showUploader: () => void }) => {
    let speedSum = 0;
    let uploadedSize = 0;
    let targetUploadSize = 0;
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

    return (
        <ClickableDropdown
            width="600px"
            left="-400px"
            top="37px"
            trigger={<TasksIcon/>}
        >
            {" "}
            {props.activeUploads <= 0 ? null : <TaskComponent {...uploadTask} />}
        </ClickableDropdown>
    );
};

interface TaskComponentProps {
    title: string;
    progress?: Progress;
    speed?: Speed;
}

const TaskComponent: React.FunctionComponent<TaskComponentProps> = props => {
    const label = !props.speed ? "" : `${props.speed.speed} ${props.speed.unit}`;
    return <Flex p={16}>
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

const mapStateToProps = ({uploader}: ReduxObject) => ({
    uploads: uploader.uploads,
    activeUploads: uploader.uploads.filter(it => it.uploadXHR &&
        it.uploadXHR.readyState > XMLHttpRequest.UNSENT && it.uploadXHR.readyState < XMLHttpRequest.DONE).length
});

const mapDispatchToProps = (dispatch: Dispatch) => ({
    showUploader: () => dispatch(setUploaderVisible(true, Cloud.homeFolder))
});

const TasksIcon = () => <TasksIconBase name="notchedCircle"/>;

export default connect(mapStateToProps, mapDispatchToProps)(BackgroundTasks);

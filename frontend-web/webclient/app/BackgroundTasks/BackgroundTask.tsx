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

const BackgroundTasks = (props: { activeUploads: number, uploads: Upload[], showUploader: () => void }) => {
    const uploadsCount = props.activeUploads;

    const uploads = props.uploads.length > 0 ? (
        <>
            <Button color="green"
                    fullWidth
                    onClick={() => props.showUploader()}
            >
                {`${uploadsCount} active upload${uploadsCount > 1 ? "s" : ""} in progress.`}
            </Button>
        </>
    ) : null;

    return (
        <ClickableDropdown
            width="600px"
            left="-400px"
            top="37px"
            trigger={<TasksIcon/>}
        >
            <TaskComponent title={"Simple"}/>
            <TaskComponent
                title={"Upload"}
                progress={{current: 1000, maximum: 10000, title: "Bytes uploaded"}}
                speed={{speed: 100, title: "Upload speed", unit: "B/s"}}
            />
            <TaskComponent title={"Simple"}/>
            {uploads}
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
    animation: spin 8s linear infinite;
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

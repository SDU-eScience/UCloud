import * as React from "react";
import {Button, Icon} from "ui-components";
import styled from "styled-components";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {connect} from "react-redux";
import {ReduxObject} from "DefaultObjects";
import {Upload} from "Uploader";
import {Dispatch} from "redux";
import {setUploaderVisible} from "Uploader/Redux/UploaderActions";
import {Cloud} from "Authentication/SDUCloudObject";

const BackgroundTasks = (props: {activeUploads: number, uploads: Upload[], showUploader: () => void}) => {
    const uploadsCount = props.activeUploads;
    if (uploadsCount === 0) return null;
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
            width="200px"
            left="-80px"
            top="37px"
            trigger={<TasksIcon />}
        >
            {uploads}
        </ClickableDropdown>
    )
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

const TasksIcon = () => <TasksIconBase name="notchedCircle" />;

export default connect(mapStateToProps, mapDispatchToProps)(BackgroundTasks);
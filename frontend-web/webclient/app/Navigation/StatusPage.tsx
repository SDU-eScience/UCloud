import {MainContainer} from "MainContainer/MainContainer";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {updatePageTitle} from "./Redux/StatusActions";

const Status = ({status, updatePageTitle}) => {
    updatePageTitle();
    return (
        <MainContainer
            main={(
                <>
                    <div>{status.title}</div>
                    <div>{status.body}</div>
                </>
            )}
        />
    );
};

const mapDispatchToProps = (dispatch: Dispatch) => ({
    updatePageTitle: () => dispatch(updatePageTitle("System Status"))
});
const mapStateToProps = ({status}) => ({status: status.status});
export default connect(mapStateToProps, mapDispatchToProps)(Status);

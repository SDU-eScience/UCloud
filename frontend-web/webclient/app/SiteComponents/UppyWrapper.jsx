import React from "react";
import { connect } from "react-redux";
import { changeUppyOpen } from "../Actions/UppyActions";
import {DashboardModal} from "uppy/lib/react";

const UppyWrapper = ({ uppy, uppyOpen, dispatch }) => {
    return (<DashboardModal uppy={uppy} open={uppyOpen} 
                            closeModalOnClickOutside onRequestClose={() => dispatch(changeUppyOpen(false))}/>);
}

const mapStateToProps = (state) => {
    return { uppy, uppyOpen } = state.uppy;
}

export default connect(mapStateToProps)(UppyWrapper);
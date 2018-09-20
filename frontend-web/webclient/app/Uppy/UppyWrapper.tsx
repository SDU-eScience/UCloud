import * as React from "react";
import { connect } from "react-redux";
import { closeUppy } from "./Redux/UppyActions";
import { DashboardModal } from "uppy/lib/react";

const UppyWrapper = ({ uppy, uppyOpen, dispatch }) => {
    if (!uppyOpen) { return null; }
    return (<DashboardModal uppy={uppy} open={uppyOpen} closeModalOnClickOutside onRequestClose={() => dispatch(closeUppy(uppy))} />);
}

const mapStateToProps = (state) => {
    const { uppy, uppyOpen } = state.uppy;
    return { uppy, uppyOpen };
}

export default connect(mapStateToProps)(UppyWrapper);
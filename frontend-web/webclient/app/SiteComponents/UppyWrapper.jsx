import React from "react";
import { connect } from "react-redux";
import { closeUppy } from "../Actions/UppyActions";
import {DashboardModal} from "uppy/lib/react";
import PropTypes from "prop-types";

const UppyWrapper = ({ uppy, uppyOpen, dispatch }) => {
    if (!uppyOpen) {return null;}
    return (<DashboardModal uppy={uppy} open={uppyOpen} closeModalOnClickOutside onRequestClose={() => dispatch(closeUppy())}/>);
}

UppyWrapper.propTypes = {
    uppy: PropTypes.object.isRequired,
    uppyOpen: PropTypes.bool.isRequired,
}


const mapStateToProps = (state) => {
    const { uppyFiles, uppyFilesOpen, uppyRunApp, uppyRunAppOpen } = state.uppy;
    let uppy = uppyFiles;
    let uppyOpen = uppyFilesOpen;
    if (uppyRunAppOpen) {
        uppy = uppyRunApp;
        uppyOpen = true;
    }
    return { uppy, uppyOpen}

}

export default connect(mapStateToProps)(UppyWrapper);
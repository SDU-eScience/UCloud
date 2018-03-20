import React from "react";
import { connect } from "react-redux";
import { changeUppyOpen } from "../Actions/UppyActions";
import {DashboardModal} from "uppy/lib/react";
import PropTypes from "prop-types";

const UppyWrapper = ({ uppy, uppyOpen, restrictions, dispatch }) => {
    return (<DashboardModal uppy={uppy} open={uppyOpen} 
                            closeModalOnClickOutside onRequestClose={() => dispatch(changeUppyOpen(false))}/>);
}

UppyWrapper.propTypes = {
    uppy: PropTypes.object.isRequired,
    uppyOpen: PropTypes.bool.isRequired,
    restrictions: PropTypes.object.isRequired
}


const mapStateToProps = (state) => {
    const { uppy, uppyOpen } = state.uppy;
    return { uppy, uppyOpen, restrictions: uppy.opts.restrictions }

}

export default connect(mapStateToProps)(UppyWrapper);
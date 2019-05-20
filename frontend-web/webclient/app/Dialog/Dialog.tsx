import * as React from "react";
import { Dispatch } from "redux";
import { connect } from "react-redux";
import { DialogOperations, DialogState } from "Dialog";
import { setVisible, setNode } from "./Redux/DialogActions";
import * as ReactModal from "react-modal";
import { ReduxObject } from "DefaultObjects";

function Dialog(props: DialogOperations & DialogState) {
    if (!props.visible) return null;
    return <ReactModal isOpen={props.visible}>{props.node}</ReactModal>;
}

const mapDispatchToProps = (dispatch: Dispatch): DialogOperations => ({
    setVisible: visible => dispatch(setVisible(visible)),
    setNode: node => dispatch(setNode(node))
});

const mapStateToProps = ({ }: ReduxObject): DialogState => ({ visible: false, node: undefined });

export default connect(mapStateToProps, mapDispatchToProps)(Dialog);
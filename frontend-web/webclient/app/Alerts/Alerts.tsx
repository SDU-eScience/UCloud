import * as React from "react";
import { Dispatch } from "redux";
import { connect } from "react-redux";
import { AlertOperations, AlertState } from "Alerts";
import { setVisible, setNode } from "./Redux/AlertsActions";
import * as ReactModal from "react-modal";
import { ReduxObject } from "DefaultObjects";

function Alert(props: AlertOperations & AlertState) {
    if (!props.visible) return null;
    return <ReactModal isOpen={props.visible}>{props.node}</ReactModal>;
}

const mapDispatchToProps = (dispatch: Dispatch): AlertOperations => ({
    setVisible: visible => dispatch(setVisible(visible)),
    setNode: node => dispatch(setNode(node))
});

const mapStateToProps = ({ }: ReduxObject): AlertState => ({ visible: false, node: undefined });

export default connect(mapStateToProps, mapDispatchToProps)(Alert);
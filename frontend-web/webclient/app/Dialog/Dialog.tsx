import * as React from "react";
import { Dispatch } from "redux";
import { connect } from "react-redux";
import { DialogOperations, DialogState } from "Dialog";
import { setNode } from "./Redux/DialogActions";
import * as ReactModal from "react-modal";
import { ReduxObject } from "DefaultObjects";

function Dialog(props: DialogOperations & DialogState) {
    if (!props.node) return null;
    return <ReactModal
        isOpen={!!props.node}
        shouldCloseOnEsc
        ariaHideApp={false}
        onRequestClose={() => props.setNode()}
        onAfterOpen={() => undefined}
        style={{
            content: {
                top: '50%',
                left: '50%',
                right: 'auto',
                bottom: 'auto',
                marginRight: '-50%',
                transform: 'translate(-50%, -50%)'
            }
        }}
    >{props.node}</ReactModal>;
}

const mapDispatchToProps = (dispatch: Dispatch): DialogOperations => ({
    setNode: node => dispatch(setNode(node))
});

const mapStateToProps = ({ dialog }: ReduxObject): DialogState => dialog;

export default connect(mapStateToProps, mapDispatchToProps)(Dialog);
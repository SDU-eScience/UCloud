import * as React from "react";
import { DialogState } from "Dialog";
import * as ReactModal from "react-modal";
import DialogStore from "./DialogStore";
import { connect } from "react-redux";

function Dialog(props: DialogState) {
    const current = props.dialogStore.current;
    if (!current) return null;
    return <ReactModal
        isOpen={!!current}
        shouldCloseOnEsc
        ariaHideApp={false}
        onRequestClose={() => props.dialogStore.popDialog()}
        onAfterOpen={() => undefined}
        style={{
            content: {
                top: "50%",
                left: "50%",
                right: "auto",
                bottom: "auto",
                marginRight: "-50%",
                transform: "translate(-50%, -50%)"
            }
        }}
    >{current}</ReactModal>;
}

/* FIXME: Rerender hack */
const mapDispatchToProps = () => ({
    count: DialogStore.dialogs.length
})

export default connect(mapDispatchToProps)(Dialog);
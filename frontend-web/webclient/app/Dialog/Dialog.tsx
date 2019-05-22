import * as React from "react";
import { DialogState } from "Dialog";
import * as ReactModal from "react-modal";

class Dialog extends React.PureComponent<DialogState> {
    constructor(props: DialogState) {
        super(props);
        props.dialogStore.attach(this);
    }

    render() {
        const current = this.props.dialogStore.current;
        if (!current) return null;
        return <ReactModal
            isOpen={!!current}
            shouldCloseOnEsc
            ariaHideApp={false}
            onRequestClose={() => this.props.dialogStore.popDialog()}
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
}

export default Dialog;
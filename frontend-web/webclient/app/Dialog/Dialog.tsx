import {dialogStore, Dialog as IDialog} from "@/Dialog/DialogStore";
import * as React from "react";
import {useEffect, useState} from "react";
import * as ReactModal from "react-modal";

export const Dialog: React.FunctionComponent = () => {
    const [dialogs, setDialogs] = useState<IDialog[]>([]);

    useEffect(() => {
        const subscription = (dialogs: IDialog[]): void => setDialogs(dialogs);

        dialogStore.subscribe(subscription);
        return () => dialogStore.unsubscribe(subscription);
    }, []);

    const current = dialogs.length > 0 ? dialogs[0] : null;
    return (
        <ReactModal
            isOpen={!!current}
            shouldCloseOnEsc
            ariaHideApp={false}
            onRequestClose={() => dialogStore.failure()}
            onAfterOpen={() => undefined}
            style={current?.style ?? {
                content: {
                    top: "50%",
                    left: "50%",
                    right: "auto",
                    bottom: "auto",
                    marginRight: "-50%",
                    transform: "translate(-50%, -50%)",
                    background: "",
                    overflow: "visible"
                }
            }}
        >
            {current?.element}
        </ReactModal>
    );
};

export default Dialog;

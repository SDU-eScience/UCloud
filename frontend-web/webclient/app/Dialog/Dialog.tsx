import * as React from "react";
import {useEffect, useState} from "react";
import {default as ReactModal} from "react-modal";
import {useLocation} from "react-router";

import {dialogStore, Dialog as IDialog} from "@/Dialog/DialogStore";
import {defaultModalStyle} from "@/Utilities/ModalUtilities";
import {CardClass} from "@/ui-components/Card";

export const Dialog: React.FunctionComponent = (): JSX.Element | null => {
    const [dialogs, setDialogs] = useState<IDialog[]>([]);
    
    const [activePath, setActivePath] = useState(window.location.href);
    const loc = useLocation(); // Note(Jonas): window.location.href change does not re-render.
    const currentLocation = loc.pathname + loc.search + loc.hash;

    useEffect(() => {
        const subscription = (dialogs: IDialog[]): void => setDialogs(dialogs);

        dialogStore.subscribe(subscription);
        return () => dialogStore.unsubscribe(subscription);
    }, []);

    const current = dialogs.length > 0 ? dialogs[0] : null;

    React.useEffect(() => {
        setActivePath(currentLocation);
    }, [current, currentLocation]);

    React.useEffect(() => {
        if (activePath !== currentLocation) {
            dialogStore.failure();
        }
    }, [activePath, currentLocation]);

    return (
        <ReactModal
            isOpen={current != null}
            shouldCloseOnEsc
            ariaHideApp={false}
            onRequestClose={() => dialogStore.failure()}
            onAfterOpen={() => undefined}
            style={current?.style ?? defaultModalStyle}
            className={CardClass}
        >
            {current?.element ?? null}
        </ReactModal>
    );
};

export default Dialog;

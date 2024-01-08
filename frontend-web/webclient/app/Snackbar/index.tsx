import * as React from "react";
import {useEffect, useState} from "react";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {Snackbar} from "@/ui-components/Snackbar";
import {CustomSnack, DefaultSnack, Snack, SnackType} from "@/Snackbar/Snackbars";

const Snackbars: React.FunctionComponent = () => {
    const [activeSnack, setActiveSnack] = useState<Snack | undefined>(undefined);

    useEffect(() => {
        const subscriber = (snack: Snack): void => {
            if (snack?.addAsNotification === true) return;
            setActiveSnack(snack);
        };

        snackbarStore.subscribe(subscriber);
        return () => snackbarStore.unsubscribe(subscriber);
    }, []);

    if (activeSnack === undefined) {
        return null;
    }

    const snackElement = activeSnack.type === SnackType.Custom ?
        <CustomSnack onCancel={onCancellation} snack={activeSnack} /> :
        <DefaultSnack onCancel={onCancellation} snack={activeSnack} />;

    return <Snackbar key={activeSnack.message}>{snackElement}</Snackbar>;

    function onCancellation(): void {
        snackbarStore.requestCancellation();
    }
};

export default Snackbars;

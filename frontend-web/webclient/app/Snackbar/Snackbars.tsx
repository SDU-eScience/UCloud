import * as React from "react";
import {useEffect, useState} from "react";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Flex, Icon} from "ui-components";
import {IconName} from "ui-components/Icon";
import {Snackbar} from "ui-components/Snackbar";
import {ThemeColor} from "ui-components/theme";

interface IconColorAndName {
    name: IconName;
    color: ThemeColor;
    color2: ThemeColor;
}

const iconNameAndColorFromSnack = (type: Exclude<SnackType, SnackType.Custom>): IconColorAndName => {
    switch (type) {
        case SnackType.Success:
            return {name: "check", color: "white", color2: "white"};
        case SnackType.Information:
            return {name: "info", color: "black", color2: "white"};
        case SnackType.Failure:
            return {name: "close", color: "white", color2: "white"};
    }
};

const CustomSnack: React.FunctionComponent<{snack: CustomSnack}> = ({snack}) =>
    <Flex><Icon color="white" color2="white" name={snack.icon} pr="10px" />{snack.message}</Flex>;

const DefaultSnack: React.FunctionComponent<{snack: DefaultSnack}> = ({snack}) => {
    const icon = iconNameAndColorFromSnack(snack.type);
    return <Flex><Icon pr="10px" {...icon} />{snack.message}</Flex>;
};

const Snackbars: React.FunctionComponent = () => {
    const [activeSnack, setActiveSnack] = useState<Snack | undefined>(undefined);

    useEffect(() => {
        const subscriber = (snack: Snack): void => setActiveSnack(snack);
        snackbarStore.subscribe(subscriber);
        return () => snackbarStore.unsubscribe(subscriber);
    }, []);

    if (activeSnack === undefined) {
        return null;
    }

    const snackElement = activeSnack.type === SnackType.Custom ?
        <CustomSnack snack={activeSnack} /> : <DefaultSnack snack={activeSnack} />;

    return <Snackbar onClick={onCancellation} visible={true}>{snackElement}</Snackbar>;

    function onCancellation(): void {
        snackbarStore.requestCancellation();
    }
};

export const enum SnackType {
    Success,
    Information,
    Failure,
    Custom
}

interface DefaultSnack {
    message: string;
    type: SnackType.Success | SnackType.Information | SnackType.Failure;
    id?: number;
    lifetime?: number;
    addAsNotification: boolean;
}

interface CustomSnack {
    message: string;
    type: SnackType.Custom;
    id?: number;
    lifetime?: number;
    icon: IconName;
    addAsNotification: boolean;
}

export type Snack = CustomSnack | DefaultSnack;
export default Snackbars;

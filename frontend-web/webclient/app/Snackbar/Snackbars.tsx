import * as React from "react";
import {IconName} from "ui-components/Icon";
import {Flex, Icon} from "ui-components";
import {ThemeColor} from "ui-components/theme";
import {useEffect, useState} from "react";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Snackbar} from "ui-components/Snackbar";

interface IconColorAndName {
    name: IconName,
    color: ThemeColor,
    color2: ThemeColor
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

const CustomSnack: React.FunctionComponent<{ snack: CustomSnack }> = ({snack}) =>
    <Flex><Icon color="white" color2="white" name={snack.icon} pr="10px"/>{snack.message}</Flex>;

const DefaultSnack: React.FunctionComponent<{ snack: DefaultSnack }> = ({snack}) => {
    const icon = iconNameAndColorFromSnack(snack.type);
    return <Flex><Icon pr="10px" {...icon} />{snack.message}</Flex>
};

const Snackbars: React.FunctionComponent = props => {
    const [activeSnack, setActiveSnack] = useState<Snack | undefined>(undefined);

    useEffect(() => {
        let subscriber = snack => setActiveSnack(snack);
        snackbarStore.subscribe(subscriber);
        return () => snackbarStore.unsubscribe(subscriber);
    }, []);

    if (activeSnack === undefined) {
        return null;
    }

    let snackElement: JSX.Element;
    if (activeSnack.type == SnackType.Custom) {
        snackElement = <CustomSnack snack={activeSnack}/>;
    } else {
        snackElement = <DefaultSnack snack={activeSnack}/>;
    }

    return <Snackbar onClick={e => snackbarStore.requestCancellation()} visible={true}>{snackElement}</Snackbar>;
};

export const enum SnackType {
    Success,
    Information,
    Failure,
    Custom
}

interface DefaultSnack {
    message: string
    type: SnackType.Success | SnackType.Information | SnackType.Failure
    id?: number
    lifetime?: number
}

interface CustomSnack {
    message: string
    type: SnackType.Custom
    id?: number
    lifetime?: number
    icon: IconName
}

export type Snack = CustomSnack | DefaultSnack;
export default Snackbars;

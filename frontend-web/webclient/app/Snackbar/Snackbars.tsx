import * as React from "react";
import {useEffect, useState} from "react";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {Box, Flex, Icon, Text} from "@/ui-components";
import {IconName} from "@/ui-components/Icon";
import {Snackbar} from "@/ui-components/Snackbar";
import {ThemeColor} from "@/ui-components/theme";
import {copyToClipboard} from "@/UtilityFunctions";

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

interface SnackProps<SnackType> {
    snack: SnackType;
    onCancel(): void;
}

const CustomSnack: React.FC<SnackProps<CustomSnack>> = ({snack, onCancel}) => {
    return <SnackBody snack={snack} onCancel={onCancel}>
        <Icon color="white" color2="white" name={snack.icon} pr="10px" />
    </SnackBody>;
}

const DefaultSnack: React.FC<SnackProps<DefaultSnack>> = ({snack, onCancel}) => {
    const icon = iconNameAndColorFromSnack(snack.type);
    return <SnackBody snack={snack} onCancel={onCancel}>
        <Icon pr="10px" {...icon} />
    </SnackBody>
};

const SnackBody: React.FC<SnackProps<Exclude<Snack, "icon">> & {children: React.ReactNode}> = ({
    snack,
    onCancel,
    children
}): JSX.Element => {
    const [didCopy, setDidCopy] = useState(false);
    return <Flex mb="-12px" my="auto">
        {children}
        <div>
            <div>{snack.message}</div>
            <Text cursor="pointer" onClick={() => (copyToClipboard({value: snack.message, message: ""}), setDidCopy(true))} fontSize="8px" color="var(--gray)">{didCopy ? "Copied!" : "Click to copy"}</Text>
        </div>
        <Box ml="auto" />
        <Icon mt="-8px" ml="8px" mr="-8px" size="12px" cursor="pointer" name="close" onClick={onCancel} />
    </Flex>;
}

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

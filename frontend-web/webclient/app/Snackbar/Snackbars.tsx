import * as React from "react";
import {useState} from "react";
import {Box, Flex, Icon, Text} from "@/ui-components";
import {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";
import {copyToClipboard} from "@/UtilityFunctions";

interface IconColorAndName {
    name: IconName;
    color: ThemeColor;
    color2: ThemeColor;
}

const iconNameAndColorFromSnack: Record<Exclude<SnackType, SnackType.Custom>, IconColorAndName> = {
    [SnackType.Success]: {name: "check", color: "textSecondary", color2: "successMain"},
    [SnackType.Information]: {name: "heroInformationCircle", color: "fixedBlack", color2: "fixedWhite"},
    [SnackType.Failure]: {name: "close", color: "textSecondary", color2: "errorMain"},
};

interface SnackProps<SnackType> {
    snack: SnackType;

    onCancel(): void;
}

export const CustomSnack: React.FC<SnackProps<CustomSnack>> = ({snack, onCancel}) => {
    return <SnackBody snack={snack} onCancel={onCancel}>
        <Icon mt="4px" mr="8px" size="14px" color="fixedWhite" color2="fixedWhite" name={snack.icon} />
    </SnackBody>;
}

export const DefaultSnack: React.FC<SnackProps<DefaultSnack>> = ({snack, onCancel}) => {
    const icon = iconNameAndColorFromSnack[snack.type];
    return <SnackBody snack={snack} onCancel={onCancel}>
        <Icon mt="4px" mr="8px" size="18px" {...icon} />
    </SnackBody>
};

const SnackBody: React.FC<SnackProps<Exclude<Snack, "icon">> & {children: React.ReactNode}> = ({
    snack,
    onCancel,
    children
}): JSX.Element => {
    const [didCopy, setDidCopy] = useState(false);
    return <Flex mb="-12px">
        {children}
        <div>
            <div>{snack.message}</div>
            <Text cursor="pointer" onClick={() => {
                copyToClipboard({value: snack.message, message: ""});
                setDidCopy(true)
            }} fontSize="8px" color="var(--textSecondary)">{didCopy ? "Copied!" : "Click to copy"}</Text>
        </div>
        <Box ml="auto" />
        <Icon mt="-8px" ml="8px" mr="-8px" size="12px" cursor="pointer" name="close" onClick={onCancel} />
    </Flex>;
}

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

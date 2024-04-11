import * as React from "react";
import {useState} from "react";
import {Absolute, Flex, Icon, Text} from "@/ui-components";
import {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";
import {copyToClipboard} from "@/UtilityFunctions";

interface IconColorAndName {
    name: IconName;
    color: ThemeColor;
    color2: ThemeColor;
}

const iconNameAndColorFromSnack: Record<Exclude<SnackType, SnackType.Custom>, IconColorAndName> = {
    [SnackType.Success]: {name: "check", color: "successMain", color2: "successMain"},
    [SnackType.Information]: {name: "heroInformationCircle", color: "backgroundDefault", color2: "backgroundDefault"},
    [SnackType.Failure]: {name: "close", color: "errorMain", color2: "errorMain"},
};

interface SnackProps<SnackType> {
    snack: SnackType;

    onCancel(): void;
}

export const CustomSnack: React.FC<SnackProps<CustomSnack>> = ({snack, onCancel}) => {
    return <SnackBody snack={snack} onCancel={onCancel}>
        <Icon mr="8px" size="18px" color="backgroundDefault" color2="backgroundDefault" name={snack.icon} />
    </SnackBody>;
}

export const DefaultSnack: React.FC<SnackProps<DefaultSnack>> = ({snack, onCancel}) => {
    const icon = iconNameAndColorFromSnack[snack.type];
    return <SnackBody snack={snack} onCancel={onCancel}>
        <Icon mr="8pt" size="18px" {...icon} />
    </SnackBody>
};

const SnackBody: React.FC<SnackProps<Exclude<Snack, "icon">> & {children: React.ReactNode}> = ({
    snack,
    onCancel,
    children
}): JSX.Element => {
    const [didCopy, setDidCopy] = useState(false);
    return <> 
        <Flex alignItems={"center"} justifyContent={"center"}>
            {children}
            {snack.message}
        </Flex>
        <Text paddingTop={"4px"}
            cursor="pointer" 
            onClick={() => {
                copyToClipboard({value: snack.message, message: ""});
                setDidCopy(true)
            }} 
            fontSize="8px" 
            color="var(--textSecondary)">
            {didCopy ? "Copied!" : "Click to copy"}
        </Text>
        <Absolute right={"6px"} top={"2px"} >
            <Icon size="12px" cursor="pointer" name="close" onClick={onCancel} />
        </Absolute>
    </>
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

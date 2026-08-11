import * as React from "react";
import Box from "@/ui-components/Box";
import Icon from "@/ui-components/Icon";
import {injectStyleSimple} from "@/Unstyled";
import {IconButton} from "@/ui-components/IconButton";

interface WarningProps {
    clearWarning?: () => void;
    warning?: string;
    children?: React.ReactNode
}

const WarningClass = injectStyleSimple("warning", `
    display: flex;
    align-items: flex-start;
    gap: 10px;
    margin-bottom: 20px;
    padding: 12px 16px;
    border: 1px solid var(--warningMain);
    border-radius: 10px;
    background: var(--backgroundCard);

    & svg {
        flex: 0 0 auto;
        margin-top: 2px;
    }
`);

const Warning: React.FunctionComponent<WarningProps> = props => {
    if (!props.warning && !props.children) return null;

    return (
        <div className={WarningClass}>
            <Icon name="warning" size={20} color="warningMain" />
            <div>{props.warning}</div>
            {props.children}
            {!props.clearWarning ? null : (
                <Box ml={"auto"}>
                    <IconButton icon={"heroXMark"} tooltip={"Close"} onClick={props.clearWarning} />
                </Box>
            )}
        </div>
    );
};

export default Warning;


import * as React from "react";
import Box from "@/ui-components/Box";
import Icon from "@/ui-components/Icon";
import {injectStyleSimple} from "@/Unstyled";
import {IconButton} from "@/ui-components/IconButton";

interface WarningProps {
    variant?: "warning" | "tip";
    clearWarning?: () => void;
    warning?: string;
    children?: React.ReactNode
    mb?: string;
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

    const color = props.variant === "tip" ? "primaryMain" : "warningMain";
    const icon = props.variant === "tip" ? "heroInformationCircle" : "warning";

    return (
        <Box className={WarningClass} mb={props.mb} style={{borderColor: `var(--${color})`}}>
            <Icon name={icon} size={20} color={color} />
            <div className={WarningContentClass}>
                {props.warning ? <div>{props.warning}</div> : null}
                {props.children}
            </div>
            {!props.clearWarning ? null : (
                <Box ml={"auto"}>
                    <IconButton icon={"heroXMark"} tooltip={"Dismiss"} onClick={props.clearWarning} />
                </Box>
            )}
        </Box>
    );
};

const WarningContentClass = injectStyleSimple("warning-content", `
    min-width: 0;
    flex: 1 1 auto;
`);

export default Warning;

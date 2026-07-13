import * as React from "react";
import {injectStyle} from "@/Unstyled";
import Icon, {IconName} from "@/ui-components/Icon";
import {TooltipV2} from "@/ui-components/Tooltip";
import {ThemeColor} from "@/ui-components/theme";

const style = injectStyle("vm-icon-button", k => `
    ${k} {
        width: 32px;
        height: 32px;
        border: 0;
        border-radius: 999px;
        background: transparent;
        color: var(--textPrimary);
        display: inline-flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
        transition: background-color 120ms ease, opacity 120ms ease;
        --icon-button-hover: var(--secondaryMain);
    }
    
    html.dark ${k} {
        --icon-button-hover: #30343a;
    }

    ${k}:hover {
        background: var(--icon-button-hover);
    }

    ${k}:focus-visible {
        outline: 2px solid var(--primaryMain);
        outline-offset: 1px;
    }
`);

export const IconButton: React.FunctionComponent<{
    tooltip: React.ReactNode;
    onClick: () => void;
    icon: IconName;
    color?: ThemeColor;
    noDefaultFill?: boolean;
}> = props => {
    const color = props.color ?? "textSecondary";
    return <TooltipV2 tooltip={props.tooltip}>
        <button type="button" className={style} onClick={props.onClick}>
            <Icon name={props.icon} color={color} noDefaultFill={props.noDefaultFill} />
        </button>
    </TooltipV2>
}
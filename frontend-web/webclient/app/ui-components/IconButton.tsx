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
        --icon-button-hover: var(--rowActive);
    }

    ${k}:hover {
        background: var(--icon-button-hover);
    }

    ${k}[data-compact="true"] {
        width: 28px;
        height: 32px;
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
    ariaExpanded?: boolean;
    compact?: boolean;
    hoverColor?: ThemeColor | `#${string}`;
}> = props => {
    const color = props.color ?? "textSecondary";
    const hoverColor = props.hoverColor === undefined ? undefined :
        props.hoverColor.startsWith("#") ? props.hoverColor : `var(--${props.hoverColor})`;
    return <TooltipV2 tooltip={props.tooltip}>
        <button type="button" className={style} onClick={props.onClick}
            aria-label={typeof props.tooltip === "string" ? props.tooltip : undefined}
            aria-expanded={props.ariaExpanded}
            data-compact={props.compact}
            style={hoverColor ? {"--icon-button-hover": hoverColor} as React.CSSProperties : undefined}>
            <Icon name={props.icon} color={color} noDefaultFill={props.noDefaultFill} />
        </button>
    </TooltipV2>
}

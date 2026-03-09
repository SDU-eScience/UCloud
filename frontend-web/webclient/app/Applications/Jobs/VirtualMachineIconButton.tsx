import * as React from "react";
import {injectStyle} from "@/Unstyled";
import Icon, {IconName} from "@/ui-components/Icon";
import {TooltipV2} from "@/ui-components/Tooltip";

const style = injectStyle("vm-icon-button", k => `
    ${k} {
        border: 0;
        background: transparent;
        color: var(--textSecondary);
        cursor: pointer;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 28px;
        height: 28px;
        border-radius: 6px;
        transition: background-color 120ms ease-in-out, color 120ms ease-in-out;
    }

    ${k}:hover {
        background: color-mix(in srgb, var(--primaryMain) 12%, transparent);
        color: var(--textPrimary);
    }

    ${k}:focus-visible {
        outline: 2px solid var(--primaryMain);
        outline-offset: 1px;
    }
`);

export const VirtualMachineIconButton: React.FunctionComponent<{
    tooltip: string;
    onClick: () => void;
    icon: IconName;
}> = props => {
    return <TooltipV2 tooltip={props.tooltip}>
        <button type="button" className={style} onClick={props.onClick} aria-label={props.tooltip}>
            <Icon name={props.icon} />
        </button>
    </TooltipV2>
}
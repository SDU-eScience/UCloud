import * as React from "react";
import {Icon} from "@/ui-components";
import {injectStyle} from "@/Unstyled";
import {TooltipV2} from "@/ui-components/Tooltip";

export const VirtualMachineRestartReminder: React.FunctionComponent<{
    tooltip: string;
    ariaLabel: string;
    onClick?: () => void;
}> = ({tooltip, ariaLabel, onClick}) => {
    const content = (
        <button
            type="button"
            className={RestartIndicator}
            aria-label={ariaLabel}
            onClick={onClick}
            disabled={!onClick}
        >
            <Icon name="heroArrowPath" color="warningMain" />
        </button>
    );

    return <TooltipV2 tooltip={tooltip}>{content}</TooltipV2>;
};

const RestartIndicator = injectStyle("restart-indicator", k => `
    ${k} {
        border: 0;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 28px;
        height: 28px;
        border-radius: 6px;
        background: color-mix(in srgb, var(--warningMain) 12%, transparent);
        cursor: pointer;
        transition: background-color 120ms ease-in-out;
    }

    ${k}:hover {
        background: color-mix(in srgb, var(--warningMain) 20%, transparent);
    }

    ${k}:focus-visible {
        outline: 2px solid var(--warningMain);
        outline-offset: 1px;
    }

    ${k}:disabled {
        cursor: default;
    }
`);

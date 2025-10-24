import * as React from "react";
import {SpaceProps} from "styled-system";
import * as ReactDOM from "react-dom";
import {useCallback, useRef} from "react";
import {injectStyleSimple} from "@/Unstyled";
import {doNothing} from "@/UtilityFunctions";

interface Tooltip extends SpaceProps {
    children: React.ReactNode;
    trigger: React.ReactNode;
    tooltipContentWidth?: number;
}

const TooltipContent = injectStyleSimple("tooltip-content", `
    padding: 16px;
    border-radius: 8px;
    background: var(--textPrimary);
    color: var(--backgroundDefault);
    position: fixed;
    z-index: 10000;
    transition: opacity ease;
    transition-duration: 0s;
    transition-delay: 0s;
    opacity: 0;
    pointer-events: none;
`);

const TooltipVisible = injectStyleSimple("tooltip-visible", `
    transition-delay: 0.2s;
    transition-duration: .25s;
    opacity: 1;
`);

const TooltipSlim = injectStyleSimple("tooltip-slim", `
    padding: 8px;
    border-radius: 8px;
    text-align: center;
`);

function getPortal(): HTMLElement {
    let portal = document.getElementById(tooltipPortalId);
    if (!portal) {
        const elem = document.createElement("div");
        elem.id = tooltipPortalId;
        const tooltip = document.createElement("div");
        tooltip.id = tooltipElementID
        elem.appendChild(tooltip);
        document.body.appendChild(elem);
        portal = elem;
    }
    return portal;
}

const Tooltip: React.FunctionComponent<Tooltip> = props => {
    const portal = getPortal();

    const width = props.tooltipContentWidth ?? 300;

    const tooltipRef = useRef<HTMLDivElement>(null);
    const onHover = useCallback((ev: React.MouseEvent) => {
        const tooltip = tooltipRef.current;
        if (!tooltip) return;

        tooltip.style.left = ev.clientX + 20 + "px";

        if (ev.clientX + width + 20 > window.innerWidth) {
            tooltip.style.left = ev.clientX - width + "px";
        }

        tooltip.style.top = ev.clientY - tooltip.getBoundingClientRect().height / 2 + "px";
        if (width <= 100) tooltip.classList.add(TooltipSlim);
        tooltip.classList.add(TooltipVisible);
    }, []);

    const onLeave = useCallback(() => {
        const tooltip = tooltipRef.current;
        if (!tooltip) return;
        tooltip.className = TooltipContent;
    }, []);

    return <>
        <div onMouseMove={onHover} onMouseLeave={onLeave}>{props.trigger}</div>
        {
            ReactDOM.createPortal(
                <div className={TooltipContent} ref={tooltipRef} style={{width: `${width}px`}}>{props.children}</div>,
                portal
            )
        }
    </>;
};

export function HTMLTooltip(trigger: HTMLElement, tooltip: HTMLElement, opts?: {tooltipContentWidth: number}): HTMLElement {
    const {moveListener, leaveListener} = HTMLTooltipEx(tooltip, opts);
    trigger.onmousemove = moveListener;
    trigger.onmouseleave = leaveListener;
    return trigger;
}

export function HTMLTooltipEx(tooltip: HTMLElement, opts?: {tooltipContentWidth: number}): {
    moveListener: (ev: MouseEvent) => void;
    leaveListener: () => void;
} {
    getPortal(); // Note(Jonas): Init portal.

    const width = opts?.tooltipContentWidth ?? 200;
    const contentWrapper = document.getElementById(tooltipElementID);
    if (!contentWrapper) {
        return { moveListener: doNothing, leaveListener: doNothing };
    }

    contentWrapper.style.position = "absolute";
    contentWrapper.className = TooltipContent;
    contentWrapper.style.width = `${width}px`;
    contentWrapper.style.display = "block";

    function onHover(ev: MouseEvent) {
        if (!contentWrapper) return;
        contentWrapper.replaceChildren(tooltip);
        contentWrapper.classList.add(TooltipVisible);

        contentWrapper.style.position = "fixed"; // Hack(Jonas): Absolute height of absolute elements are 0, so we briefly modify it.
        contentWrapper.style.position = "absolute"; // Hack(Jonas): Set to absolute again as is intended state.

        contentWrapper.style.left = ev.clientX + 20 + "px";
        if (ev.clientX + width + 20 > window.innerWidth) {
            contentWrapper.style.left = ev.clientX - width + "px";
        }

        contentWrapper.style.top = ev.clientY - contentWrapper.getBoundingClientRect().height / 2 + "px";
    }

    function onLeave() {
        if (!contentWrapper) return;
        contentWrapper.className = TooltipContent;
    }

    tooltip.onmouseleave = onLeave;
    return {
        moveListener: onHover,
        leaveListener: onLeave,
    };
}

export function TooltipV2(props: React.PropsWithChildren<{
    tooltip?: React.ReactNode;
    contentWidth?: number;
}>): React.ReactElement {
    if (props.tooltip === undefined) return <>{props.children}</>;
    return <Tooltip tooltipContentWidth={props.contentWidth} trigger={props.children}>{props.tooltip ?? null}</Tooltip>;
}

const tooltipPortalId = "tooltip-portal";
const tooltipElementID = "tooltip-element";

export default Tooltip;

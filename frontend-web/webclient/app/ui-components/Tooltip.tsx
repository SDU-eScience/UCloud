import * as React from "react";
import {SpaceProps} from "styled-system";
import * as ReactDOM from "react-dom";
import {useCallback, useRef} from "react";
import {injectStyleSimple} from "@/Unstyled";

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

        if (ev.clientX + width > window.innerWidth) {
            tooltip.style.left = ev.clientX - width + "px";
        }

        tooltip.style.top = ev.clientY - tooltip.getBoundingClientRect().height / 2 + "px";
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

const SMALL_OFFSET_IN_PIXELS = 8;
export function HTMLTooltip(trigger: HTMLElement, tooltip: HTMLElement, opts?: {tooltipContentWidth: number}): HTMLElement {
    getPortal(); // Note(Jonas): Init portal.

    const width = opts?.tooltipContentWidth ?? 200;
    const contentWrapper = document.getElementById(tooltipElementID);
    if (!contentWrapper) return trigger;
    contentWrapper.style.position = "absolute";
    contentWrapper.className = TooltipContent;
    contentWrapper.style.width = `${width}px`;
    contentWrapper.style.display = "block";    
    
    function onHover(ev: MouseEvent) {
        if (!contentWrapper) return; 
        contentWrapper.replaceChildren(tooltip);
        contentWrapper.classList.add(TooltipVisible);
        const triggerRect = trigger.getBoundingClientRect();
        const expectedLeft = triggerRect.x + triggerRect.width / 2 - width / 2;
        if (expectedLeft + width > window.innerWidth) {
            contentWrapper.style.left = `${window.innerWidth - width - 24}px`;
        } else {
            contentWrapper.style.left = `${expectedLeft}px`;
        }
        contentWrapper.style.position = "fixed"; // Hack(Jonas): Absolute height of absolute elements are 0, so we briefly modify it.
        const contentWrapperRect = contentWrapper.getBoundingClientRect();
        contentWrapper.style.position = "absolute"; // Hack(Jonas): Set to absolute again as is intended state.
        if (triggerRect.y + triggerRect.height + contentWrapperRect.height + SMALL_OFFSET_IN_PIXELS > window.innerHeight) {
            contentWrapper.style.top = `${triggerRect.y - contentWrapperRect.height - SMALL_OFFSET_IN_PIXELS}px`;
        } else {
            contentWrapper.style.top = `${triggerRect.y + triggerRect.height + SMALL_OFFSET_IN_PIXELS}px`;
        }

    }

    function onLeave() {
        if (!contentWrapper) return;
        contentWrapper.className = TooltipContent;
    }

    trigger.onmouseover = onHover;
    trigger.onmouseleave = tooltip.onmouseleave = onLeave;

    return trigger;
}

export function TooltipV2(props: {
    tooltip?: React.ReactNode;
    children: React.ReactNode;
}): React.ReactElement {
    if (props.tooltip === undefined) return <>{props.children}</>;
    return <Tooltip trigger={props.children}>{props.tooltip ?? null}</Tooltip>;
}

const tooltipPortalId = "tooltip-portal";
const tooltipElementID = "tooltip-element";

export default Tooltip;

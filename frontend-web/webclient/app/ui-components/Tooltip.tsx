import * as React from "react";
import {SpaceProps} from "styled-system";
import * as ReactDOM from "react-dom";
import {useCallback, useRef} from "react";
import {injectStyle} from "@/Unstyled";

interface Tooltip extends SpaceProps {
    children: React.ReactNode;
    trigger: React.ReactNode;
    tooltipContentWidth?: number;
}

const TooltipContent = injectStyle("tooltip-content", k => `
    ${k} {
        padding: 8px;
        border-radius: 8px;
        background: var(--black);
        color: var(--white);
        font-size: 16px;
        position: fixed;
        z-index: 10000;
    }
`);

function getPortal(): HTMLElement {
    let portal = document.getElementById(tooltipPortalId);
    if (!portal) {
        const elem = document.createElement("div");
        elem.id = tooltipPortalId;
        document.body.appendChild(elem);
        portal = elem;
    }
    return portal;
}

const Tooltip: React.FunctionComponent<Tooltip> = props => {
    const portal = getPortal();

    const width = props.tooltipContentWidth ?? 200;

    const tooltipRef: React.MutableRefObject<HTMLDivElement | null> = useRef<HTMLDivElement>(null);
    const onHover = useCallback((ev: React.MouseEvent) => {
        const tooltip = tooltipRef.current;
        if (!tooltip) return;

        tooltip.style.left = ev.clientX + "px";

        if (ev.clientX + width > window.innerWidth) {
            tooltip.style.left = ev.clientX - width + "px";
        }

        tooltip.style.top = ev.clientY + "px";
        tooltip.style.display = "block";
    }, []);

    const onLeave = useCallback(() => {
        const tooltip = tooltipRef.current;
        if (!tooltip) return;
        tooltip.style.display = "none";
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

export function HTMLTooltip(trigger: HTMLElement, tooltip: HTMLElement, opts?: {tooltipContentWidth: number}) {
    const portal = getPortal();

    const width = opts?.tooltipContentWidth ?? 200;
    const contentWrapper = document.createElement("div");
    contentWrapper.append(tooltip);
    contentWrapper.style.position = "absolute";
    contentWrapper.className = TooltipContent;
    contentWrapper.style.width = `${width}px`;

    function onHover(ev: MouseEvent) {
        const wrapperRect = trigger.getBoundingClientRect();
        contentWrapper.style.left = `${wrapperRect.x + wrapperRect.width / 2 - width / 2}px`;
        contentWrapper.style.top = `${wrapperRect.y + wrapperRect.height}px`;
        contentWrapper.style.display = "block";
    }

    function onLeave() {
        contentWrapper.style.display = "none";
    }

    trigger.onmouseover = onHover;
    trigger.onmouseleave = onLeave;

    portal.innerHTML = "";
    portal.append(contentWrapper);

};

const tooltipPortalId = "tooltip-portal";

export default Tooltip;

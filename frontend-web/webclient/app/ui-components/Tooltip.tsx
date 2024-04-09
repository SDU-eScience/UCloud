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
    background: var(--infoDark);
    color: var(--infoContrast);
    // border: 2px solid var(--textSecondary);
    position: fixed;
    z-index: 10000;
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

    const width = props.tooltipContentWidth ?? 300;

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

export function HTMLTooltip(trigger: HTMLElement, tooltip: HTMLElement, opts?: {tooltipContentWidth: number}): HTMLElement {
    const portal = getPortal();

    const width = opts?.tooltipContentWidth ?? 200;
    const contentWrapper = document.createElement("div");
    contentWrapper.append(tooltip);
    contentWrapper.style.position = "absolute";
    contentWrapper.className = TooltipContent;
    contentWrapper.style.width = `${width}px`;
    contentWrapper.style.display = "none";

    function onHover(ev: MouseEvent) {
        portal.append(contentWrapper);
        const wrapperRect = trigger.getBoundingClientRect();
        const expectedLeft = wrapperRect.x + wrapperRect.width / 2 - width / 2;
        if (expectedLeft + width > window.innerWidth) {
            contentWrapper.style.left = `${window.innerWidth - width - 24}px`;
        } else {
            contentWrapper.style.left = `${expectedLeft}px`;
        }
        contentWrapper.style.top = `${wrapperRect.y + wrapperRect.height}px`;
        contentWrapper.style.display = "block";
    }

    function onLeave() {
        portal.innerHTML = "";
        contentWrapper.style.display = "none";
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

export default Tooltip;

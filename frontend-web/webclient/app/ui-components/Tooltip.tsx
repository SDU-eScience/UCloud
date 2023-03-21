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

const Tooltip: React.FunctionComponent<Tooltip> = props => {
    let portal = document.getElementById(tooltipPortalId);
    if (!portal) {
        const elem = document.createElement("div");
        elem.id = tooltipPortalId;
        document.body.appendChild(elem);
        portal = elem;
    }

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

const tooltipPortalId = "tooltip-portal";

export default Tooltip;

import * as React from "react";
import {SpaceProps} from "styled-system";
import * as ReactDOM from "react-dom";
import {useCallback, useEffect, useId, useRef, useState} from "react";
import {arrow, autoUpdate, computePosition, flip, offset, shift} from "@floating-ui/dom";
import {injectStyle, injectStyleSimple} from "@/Unstyled";
import {doNothing} from "@/UtilityFunctions";
import {Feature, hasFeature} from "@/Features";

export type TooltipSide = "top" | "right" | "bottom" | "left";

interface Tooltip extends SpaceProps {
    children: React.ReactNode;
    trigger: React.ReactNode;
    triggerClassName?: string;
    triggerStyle?: React.CSSProperties;
    tooltipContentWidth?: number;
    side?: TooltipSide;
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

const AnchoredTooltipContent = injectStyle("anchored-tooltip-content", k => `
    ${k} {
        box-sizing: border-box;
        width: max-content;
        max-width: min(320px, calc(100vw - 16px));
        padding: 6px 10px;
        border-radius: 6px;
        background: contrast-color(var(--backgroundDefault));
        color: var(--backgroundDefault);
        position: fixed;
        z-index: 10000;
        overflow-wrap: anywhere;
        text-align: center;
        font-size: 12px;
        line-height: 1.4;
        opacity: 0;
        pointer-events: none;
    }

    ${k} *:first-child {
        text-align: center;
    }

    ${k} > * {
        text-align: left;
    }
`);

const AnchoredTooltipVisible = injectStyleSimple("anchored-tooltip-visible", `
    opacity: 1;
    transition: opacity .15s ease .2s;
`);

const AnchoredTooltipImmediate = injectStyleSimple("anchored-tooltip-immediate", `
    transition: none;
`);

const TooltipArrow = injectStyleSimple("tooltip-arrow", `
    width: 8px;
    height: 8px;
    background: inherit;
    position: absolute;
    transform: rotate(45deg);
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

    if (hasFeature(Feature.NEW_TOOLTIPS)) {
        return <AnchoredTooltip {...props} portal={portal} />;
    }

    return <LegacyTooltip {...props} portal={portal} />;
};

function LegacyTooltip(props: Tooltip & {portal: HTMLElement}): React.ReactElement {
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
        <div className={props.triggerClassName} style={props.triggerStyle} onMouseMove={onHover} onMouseLeave={onLeave}>{props.trigger}</div>
        {
            ReactDOM.createPortal(
                <div className={TooltipContent} ref={tooltipRef} style={{width: `${width}px`}}>{props.children}</div>,
                props.portal
            )
        }
    </>;
}

function AnchoredTooltip(props: Tooltip & {portal: HTMLElement}): React.ReactElement {
    const [open, setOpen] = useState(false);
    const [openImmediately, setOpenImmediately] = useState(false);
    const triggerRef = useRef<HTMLDivElement>(null);
    const tooltipRef = useRef<HTMLDivElement>(null);
    const arrowRef = useRef<HTMLDivElement>(null);
    const tooltipId = useId();

    function showTooltip() {
        setOpenImmediately(recentlyClosedTooltip());
        setOpen(true);
    }

    function hideTooltip() {
        tooltipClosedAt = Date.now();
        setOpen(false);
    }

    useEffect(() => {
        const trigger = triggerRef.current;
        const tooltip = tooltipRef.current;
        const arrowElement = arrowRef.current;
        if (!open || !trigger || !tooltip || !arrowElement) return;

        return autoUpdate(trigger, tooltip, () => {
            positionTooltip(trigger, tooltip, arrowElement, props.side ?? "bottom");
        });
    }, [open, props.side]);

    return <>
        <div
            ref={triggerRef}
            className={props.triggerClassName}
            style={props.triggerStyle}
            onMouseEnter={showTooltip}
            onMouseLeave={hideTooltip}
            onFocus={showTooltip}
            onBlur={hideTooltip}
            aria-describedby={open ? tooltipId : undefined}
        >
            {props.trigger}
        </div>
        {ReactDOM.createPortal(
            <div
                id={tooltipId}
                role="tooltip"
                className={`${AnchoredTooltipContent}${open ? ` ${AnchoredTooltipVisible}${openImmediately ? ` ${AnchoredTooltipImmediate}` : ""}` : ""}`}
                ref={tooltipRef}
            >
                {props.children}
                <div className={TooltipArrow} ref={arrowRef} />
            </div>,
            props.portal
        )}
    </>;
}

async function positionTooltip(
    trigger: Element,
    tooltip: HTMLElement,
    arrowElement: HTMLElement,
    side: TooltipSide
): Promise<void> {
    const result = await computePosition(trigger, tooltip, {
        placement: side,
        strategy: "fixed",
        middleware: [
            offset(7),
            flip({padding: 8}),
            shift({padding: 8}),
            arrow({element: arrowElement, padding: 6}),
        ],
    });

    tooltip.style.left = `${result.x}px`;
    tooltip.style.top = `${result.y}px`;

    const arrowPosition = result.middlewareData.arrow;
    const actualSide = result.placement.split("-")[0] as TooltipSide;
    const staticSide: Record<TooltipSide, TooltipSide> = {
        top: "bottom",
        right: "left",
        bottom: "top",
        left: "right",
    };

    arrowElement.style.left = arrowPosition?.x == null ? "" : `${arrowPosition.x}px`;
    arrowElement.style.top = arrowPosition?.y == null ? "" : `${arrowPosition.y}px`;
    arrowElement.style.right = "";
    arrowElement.style.bottom = "";
    arrowElement.style[staticSide[actualSide]] = "-4px";
}

export function HTMLTooltip(trigger: HTMLElement, tooltip: HTMLElement, opts?: {tooltipContentWidth?: number; side?: TooltipSide}): HTMLElement {
    const {moveListener, leaveListener} = HTMLTooltipEx(tooltip, opts);
    trigger.onmousemove = moveListener;
    trigger.onmouseleave = leaveListener;
    return trigger;
}

export function HTMLTooltipEx(tooltip: HTMLElement, opts?: {tooltipContentWidth?: number; side?: TooltipSide}): {
    moveListener: (ev: MouseEvent) => void;
    leaveListener: () => void;
} {
    getPortal(); // Note(Jonas): Init portal.

    if (hasFeature(Feature.NEW_TOOLTIPS)) {
        return anchoredHTMLTooltip(tooltip, opts?.side ?? "bottom");
    }

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

let activeHTMLTooltipCleanup: (() => void) | undefined;
let activeHTMLTooltipTrigger: Element | undefined;
let tooltipClosedAt = 0;
const tooltipSkipTransitionWindow = 300;

function recentlyClosedTooltip(): boolean {
    return Date.now() - tooltipClosedAt < tooltipSkipTransitionWindow;
}

function anchoredHTMLTooltip(tooltip: HTMLElement, side: TooltipSide): {
    moveListener: (ev: MouseEvent) => void;
    leaveListener: () => void;
} {
    const contentWrapper = document.getElementById(tooltipElementID);
    if (!contentWrapper) return {moveListener: doNothing, leaveListener: doNothing};
    const wrapper = contentWrapper;

    const arrowElement = document.createElement("div");
    arrowElement.className = TooltipArrow;

    function onHover(ev: MouseEvent) {
        const trigger = ev.currentTarget;
        if (!(trigger instanceof Element)) return;
        if (activeHTMLTooltipTrigger === trigger && wrapper.contains(tooltip)) return;

        activeHTMLTooltipCleanup?.();
        activeHTMLTooltipTrigger = trigger;
        wrapper.style.position = "";
        wrapper.style.width = "";
        wrapper.replaceChildren(tooltip, arrowElement);
        wrapper.className = `${AnchoredTooltipContent} ${AnchoredTooltipVisible}${recentlyClosedTooltip() ? ` ${AnchoredTooltipImmediate}` : ""}`;
        activeHTMLTooltipCleanup = autoUpdate(trigger, wrapper, () => {
            positionTooltip(trigger, wrapper, arrowElement, side);
        });
    }

    function onLeave() {
        activeHTMLTooltipCleanup?.();
        activeHTMLTooltipCleanup = undefined;
        activeHTMLTooltipTrigger = undefined;
        tooltipClosedAt = Date.now();
        wrapper.className = AnchoredTooltipContent;
    }

    tooltip.onmouseleave = onLeave;
    return {moveListener: onHover, leaveListener: onLeave};
}

export function TooltipV2(props: React.PropsWithChildren<{
    tooltip?: React.ReactNode;
    contentWidth?: number;
    side?: TooltipSide;
    triggerClassName?: string;
    triggerStyle?: React.CSSProperties;
}>): React.ReactElement {
    if (props.tooltip === undefined) return <>{props.children}</>;
    return <Tooltip
        tooltipContentWidth={props.contentWidth}
        trigger={props.children}
        triggerClassName={props.triggerClassName}
        triggerStyle={props.triggerStyle}
        side={props.side}
    >{props.tooltip ?? null}</Tooltip>;
}

const tooltipPortalId = "tooltip-portal";
const tooltipElementID = "tooltip-element";

export default Tooltip;

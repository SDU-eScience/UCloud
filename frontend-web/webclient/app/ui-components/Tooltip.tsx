import * as React from "react";
import styled from "styled-components";
import {SpaceProps} from "styled-system";
import * as ReactDOM from "react-dom";
import {Flex} from "@/ui-components";
import Box, {BoxProps} from "./Box";
import theme from "./theme";
import {useCallback, useRef} from "react";

interface TooltipContentProps extends BoxProps, SpaceProps {
    bg?: any;
    omitPositionBox?: boolean
}

export const TooltipContent = styled(Box) <TooltipContentProps>`
  pointer-events: none;
  box-shadow: ${theme.shadows.sm};
  font-size: 12px;
  border-radius: ${theme.radii[1]}px;
  box-sizing: border-box;
  background: ${p => p.theme.colors[p.bg]};
  position: fixed;
  opacity: 0;
  z-index: 9999999999;
  background: var(--white);
  padding: 8px;
`;

interface Tooltip extends SpaceProps {
    children: React.ReactNode;
    trigger: React.ReactNode;
    bg?: string;
    color?: string;
    bottom?: string;
    top?: string;
    center?: string;
    left?: string;
    right?: string;
    zIndex?: number;
    wrapperOffsetLeft?: string;
    wrapperOffsetTop?: string;
    tooltipContentWidth?: string;
    tooltipContentHeight?: string;
    omitPositionBox?: boolean;
    noDelay?: boolean;
}

const defaultProps = {
    position: "bottom",
    color: "text",
    bg: "white",
    textAlign: "center",
    zIndex: 9999
};

const Tooltip = ({
    children,
    zIndex,
    wrapperOffsetLeft,
    wrapperOffsetTop,
    tooltipContentWidth,
    tooltipContentHeight,
    omitPositionBox,
    noDelay,
    ...props
}: Tooltip): JSX.Element => {
    let portal = document.getElementById(tooltipPortalId);
    if (!portal) {
        const elem = document.createElement("div");
        elem.id = tooltipPortalId;
        document.body.appendChild(elem);
        portal = elem;
    }

    const tooltipRef: React.MutableRefObject<HTMLDivElement | null> = useRef<HTMLDivElement>(null);
    const onHover = useCallback((ev: React.MouseEvent) => {
        const tooltip = tooltipRef.current;
        if (!tooltip) return;

        tooltip.style.left = ev.clientX + "px";

        if (tooltipContentWidth && parseInt(tooltipContentWidth)) {
            const parsedWidth = parseInt(tooltipContentWidth);
            if (ev.clientX + parsedWidth > window.innerWidth) {
                tooltip.style.left = ev.clientX - parsedWidth + "px";
            }
        }

        tooltip.style.top = ev.clientY + "px";
        tooltip.style.opacity = "1";
    }, []);

    const onLeave = useCallback(() => {
        const tooltip = tooltipRef.current;
        if (!tooltip) return;
        tooltip.style.opacity = "0";
    }, []);
    return (
        <>
            <Flex onMouseMove={onHover} onMouseLeave={onLeave}>{props.trigger}</Flex>
            {
                ReactDOM.createPortal(
                    <TooltipContent omitPositionBox={omitPositionBox} height={tooltipContentHeight}
                        width={tooltipContentWidth} p={2} mb={3} mt={2} {...props}
                        ref={tooltipRef}>
                        {children}
                    </TooltipContent>,
                    portal
                )
            }
        </>
    );
};

const tooltipPortalId = "tooltip-portal";

Tooltip.defaultProps = defaultProps;

export default Tooltip;

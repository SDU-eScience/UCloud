import * as React from "react";
import styled from "styled-components";
import {SpaceProps} from "styled-system";
import {Flex, Relative} from "@/ui-components";
import Box, {BoxProps} from "./Box";
import theme from "./theme";
import {useCallback, useRef, useState} from "react";

const arrowShadow = (props: { top?: boolean }) => props.top ?
    {"box-shadow": "-9.66px 9.66px 8px 0 rgba(0,0,0,0.04), -4px 4px 4px 0 rgba(0,0,0,0.08)"} :
    {"box-shadow": "-1.41px 1.41px 1px 0 rgba(0,0,0,0.01), -3.66px 3.66px 8px 0 rgba(0,0,0,0.04)"};

const arrowAlign = (props: { top?: boolean; left?: boolean; center?: boolean }) => props.left ?
    {"left": "16px", "margin-left": props.top ? 0 : "15px"}
    : props.center
        ? {"left": "50%", "margin-left": props.top ? "-7px" : "7px"}
        : {"right": "16px", "margin-right": props.top ? "5px" : "-10px"};

const arrowPosition = (props: { top?: boolean }) => props.top ?
    {
        "transform-origin": "0 0",
        "transform": "rotate(-45deg)",
        "bottom": "-10px"
    }
    : {
        "transform-origin": "0 0",
        "transform": "rotate(-225deg)",
        "top": "0"
    };

const arrow = (props: { top?: boolean }) => {
    return props.top
        ? {
            "transform-origin": "0 0",
            "transform": "rotate(-45deg)"
        } : {
            "transform-origin": "0 0",
            "transform": "rotate(-225deg)"
        };
};

const tooltipPosition = (props: { top?: boolean }) =>
    props.top ? {bottom: "-8px"} : {top: 0};


const tooltipAlign = (props: { right: boolean; center: boolean }) =>
    props.right
        ? {right: 0}
        : props.center
            ? {left: "50%", width: "auto", transform: "translateX(-50%)"}
            : null;

interface TooltipContentProps extends BoxProps, SpaceProps {
    bg?: any;
    omitPositionBox?: boolean
}

const TooltipContent = styled(Box) <TooltipContentProps>`
  pointer-events: none;
  box-shadow: ${theme.shadows.sm};
  font-size: ${theme.fontSizes[0]}px;
  border-radius: ${theme.radii[1]}px;
  box-sizing: border-box;
  background: ${p => p.theme.colors[p.bg]};
  text-align: center;
  position: fixed;
  opacity: 0;
  z-index: 9999999999;
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
    zIndex: 9999
};

const Tooltip = (
    {
        children,
        zIndex,
        wrapperOffsetLeft,
        wrapperOffsetTop,
        tooltipContentWidth,
        tooltipContentHeight,
        omitPositionBox,
        noDelay,
        ...props
    }: Tooltip
): JSX.Element => {
    const tooltipRef = useRef<HTMLDivElement>(null);
    const onHover = useCallback((ev: React.MouseEvent) => {
        const tooltip = tooltipRef.current;
        if (!tooltip) return;

        tooltip.style.left = ev.clientX + "px";
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
            <TooltipContent omitPositionBox={omitPositionBox} height={tooltipContentHeight}
                            width={tooltipContentWidth} p={2} mb={3} mt={2} {...props}
                            ref={tooltipRef}>
                {children}
            </TooltipContent>
        </>
    );
};

Tooltip.defaultProps = defaultProps;

export default Tooltip;

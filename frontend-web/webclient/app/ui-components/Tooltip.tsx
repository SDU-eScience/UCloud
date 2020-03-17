import * as React from "react";
import styled from "styled-components";
import {SpaceProps} from "styled-system";
import {Flex, Relative} from "ui-components";
import Box, {BoxProps} from "./Box";
import theme from "./theme";

const arrowShadow = (props: {top?: boolean}) => props.top ?
    {"box-shadow": "-9.66px 9.66px 8px 0 rgba(0,0,0,0.04), -4px 4px 4px 0 rgba(0,0,0,0.08)"} :
    {"box-shadow": "-1.41px 1.41px 1px 0 rgba(0,0,0,0.01), -3.66px 3.66px 8px 0 rgba(0,0,0,0.04)"};

const arrowAlign = (props: {top?: boolean; left?: boolean; center?: boolean}) => props.left ?
    {"left": "16px", "margin-left": props.top ? 0 : "15px"}
    : props.center
        ? {"left": "50%", "margin-left": props.top ? "-7px" : "7px"}
        : {"right": "16px", "margin-right": props.top ? "5px" : "-10px"};

const arrowPosition = (props: {top?: boolean}) => props.top ?
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

const arrow = (props: {top?: boolean}) => {
    return props.top
        ? {
            "transform-origin": "0 0",
            "transform": "rotate(-45deg)"
        } : {
            "transform-origin": "0 0",
            "transform": "rotate(-225deg)"
        };
};

const tooltipPosition = (props: {top?: boolean}) =>
    props.top ? {bottom: "-8px"} : {top: 0};


const tooltipAlign = (props: {right: boolean; center: boolean}) =>
    props.right
        ? {right: 0}
        : props.center
            ? {left: "50%", width: "auto", transform: "translateX(-50%)"}
            : null;

interface TooltipContentProps extends BoxProps, SpaceProps {
    bg?: any;
}

const sm = "sm";

const TooltipContent = styled(Box) <TooltipContentProps>`
  opacity: 0;
  pointer-events: none;
  box-shadow: ${theme.shadows[sm]};
  font-size: ${theme.fontSizes[0]}px;
  position: absolute;
  border-radius: ${theme.radii[1]}px;
  box-sizing: border-box;
  background: ${p => p.theme.colors[p.bg]};
  text-align: center;

  ${tooltipPosition as any} ${tooltipAlign as any} &::after {
    content: '';
    position: absolute;
    width: 0;
    height: 0;
    border-width: 5px;
    border-style: solid;
    border-color: transparent transparent ${p => p.theme.colors[p.bg]}
      ${p => p.theme.colors[p.bg]};

    ${arrow as any} ${arrowPosition as any} ${arrowAlign as any} ${arrowShadow as any};
  }
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
}

const defaultProps = {
    position: "bottom",
    color: "text",
    bg: "white",
    zIndex: 9999
};

const Tooltip = ({
    children,
    zIndex,
    wrapperOffsetLeft,
    wrapperOffsetTop,
    tooltipContentWidth,
    ...props
}: Tooltip): JSX.Element => (
        <VisibleOnHover>
            <Flex>{props.trigger}</Flex>
            <Relative left={wrapperOffsetLeft} top={wrapperOffsetTop} zIndex={zIndex}>
                <TooltipContent width={tooltipContentWidth} p={2} mb={3} mt={2} {...props}>
                    {children}
                </TooltipContent>
            </Relative>
        </VisibleOnHover>
    );

const VisibleOnHover = styled(Box)`
  & > ${Flex}:hover + ${Relative} > ${TooltipContent} {
    opacity: 1;
    transition: opacity 0s linear 1s;
  }
`;

Tooltip.defaultProps = defaultProps;

export default Tooltip;

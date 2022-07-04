import * as React from "react";
import styled, {keyframes} from "styled-components";
import {fontWeight, FontWeightProps} from "styled-system";
import {ThemeColor} from "./theme";

const animatePositive = keyframes`
    0% { width: 0%; }
`;

const animateNegative = keyframes`
    0% { width: 100%; }
`;

const thresholds: {maxValue: number, color: ThemeColor}[] = [
    {
        maxValue: 79,
        color: "green"
    },
    {
        maxValue: 89,
        color: "lightOrange"
    },
    {
        maxValue: 100,
        color: "red"
    }
];

function getColorFromValue(value: number): string {
    return thresholds.find(it => it.maxValue >= value)?.color ?? "red";
}

const Bar = styled.div<{value: number; width: number | string; fontWeight?: FontWeightProps;}>`
    position: absolute;
    top: 0;
    height: 100%;
    overflow: hidden;
    & > span {
        padding-top: 2px;
        padding-bottom: auto;
        position: absolute;
        display: block;
        width: ${props => props.width};
        min-width: 200px;
        height: 100%;
        text-align: center;
        ${fontWeight}
    }

    &.positive {      
        background: var(--${props => getColorFromValue(props.value)});
        left: 0;
        width: ${props => Math.min(props.value, 100)}%;
        animation: ${animatePositive} 4s;
    }

    &.positive > span {
        left: 0;
        color: var(--white);
    }

    &.negative {
        background: var(--appCard);
        right: 0;
        width: ${props => 100 - Math.min(props.value, 100)}%;
        animation: ${animateNegative} 4s;
    }

    &.negative > span {
        right: 0;
        color: var(--black);
    }
`;

const ProgressBar = styled.div<{value: number; width: number | string; height: number | string;}>`
    border-radius: 4px;
    position: relative;
    width: ${props => props.width};
    min-width: 200px;
    height: ${props => props.height};
    line-height: 15px;
    vertical-align: middle;
    overflow: hidden;
    font-size: 12px;
`;

interface ResourceProgressProps {
    text?: string;
    value: number;
    width?: string;
    height?: string;
    fontWeight?: FontWeightProps;
}

/* https://codepen.io/valiooo/pen/ALXodB */
export function ResourceProgress(
    props: React.PropsWithChildren<ResourceProgressProps>
): JSX.Element | null {
    if (isNaN(props.value)) return null;
    const width = props.width ?? "200px";
    const height = props.height ?? "20px";
    const fontWeight = props.fontWeight ?? {fontWeight: "bold"};
    return (
        <ProgressBar width={width} height={height} value={props.value}>
            <Bar className="positive" width={width} value={props.value} fontWeight={fontWeight}>
                <span>{props.text ?? props.value}</span>
            </Bar>
            <Bar className="negative" width={width} value={props.value} fontWeight={fontWeight}>
                <span>{props.text ?? props.value}</span>
            </Bar>
        </ProgressBar>
    );
}
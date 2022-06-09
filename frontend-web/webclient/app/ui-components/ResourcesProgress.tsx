import * as React from "react";
import styled, {keyframes} from "styled-components";
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
    return thresholds.find(it => it.maxValue >= value)?.color ?? "green"
}

const Bar = styled.div<{value: number; width: number | string;}>`
    position: absolute;
    top: 0;
    height: 100%;
    overflow: hidden;
    & > span {
        position: absolute;
        display: block;
        width: ${props => props.width};
        height: 100%;
        text-align: center;
    }

    &.positive {      
        background: var(--${props => getColorFromValue(props.value)});
        left: 0;
        width: ${props => props.value}%;      
        animation: ${animatePositive} 4s;
    }

    &.positive > span {
        left: 0;
        color: var(--white);
    }

    &.negative {
        background: var(--appCard);
        right: 0;
        width: ${props => 100 - props.value}%;        
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
    height: ${props => props.height};
    line-height: 15px;
    vertical-align: middle;
    overflow: hidden;
    font-size: 12px;
`;

/* https://codepen.io/valiooo/pen/ALXodB */
export function ResourceProgress(
    props: React.PropsWithChildren<{
        value: number;
        width?: string;
        height?: string;
    }>
): JSX.Element {
    const width = props.width ?? "150px";
    const height = props.height ?? "15px";
    return (
        <ProgressBar width={width} height={height} value={props.value}>
            <Bar className="positive" width={width} value={props.value}>
                <span>{props.value}%</span>
            </Bar>
            <Bar className="negative" width={width} value={props.value}>
                <span>{props.value}%</span>
            </Bar>
        </ProgressBar>
    );
}
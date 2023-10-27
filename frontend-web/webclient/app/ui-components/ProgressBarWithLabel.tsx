import * as React from "react";
import {ThemeColor} from "./theme";
import {injectStyle} from "@/Unstyled";
import {CSSProperties} from "react";

const thresholds: { maxValue: number, color: ThemeColor }[] = [
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

const BarClass = injectStyle("resources-bar", k => `
    ${k} {
        position: absolute;
        top: 0;
        height: 100%;
        overflow: hidden;
        
        --barBackground: var(--green);
        --animationLength: 1s;
    }
        
    ${k} > span {
        padding-top: 2px;
        padding-bottom: auto;
        position: absolute;
        display: block;
        min-width: 200px;
        height: 100%;
        text-align: center;
    }
    
    ${k}.positive {
        background: var(--barBackground);
        left: 0;
        width: var(--percentage, 0%);
        animation: animatePositive var(--animationLength);
    }
    
    ${k}.positive > span {
        left: 0;
        color: var(--white);
    }
    
    ${k}.negative {
        background: var(--appCard);
        right: 0;
        animation: animateNegative var(--animationLength);
        width: calc(100% - var(--percentage, 0%));
    }

    ${k}.negative > span {
        right: 0;
        color: var(--black);
    }
    
    @keyframes animatePositive {
        0% { width: 0; }
    }
    
    @keyframes animateNegative {
        0% { width: 100%; }
    }
`);

const ContainerClass = injectStyle("resource-progress-container", k => `
    ${k} {
        border-radius: 4px;
        position: relative;
        min-width: 200px;
        line-height: 15px;
        vertical-align: middle;
        overflow: hidden;
        font-size: 12px;
        border: 1px solid var(--midGray);
    }
`);

interface Props {
    text?: string;
    value: number;
    width?: string;
    height?: string;
}

/* https://codepen.io/valiooo/pen/ALXodB */
export function ProgressBarWithLabel(
    props: React.PropsWithChildren<Props>
): JSX.Element | null {
    if (isNaN(props.value)) return null;
    const width = props.width ?? "200px";
    const height = props.height ?? "20px";

    const style: CSSProperties = {width, height};
    style["--barBackground"] = `var(--${getColorFromValue(props.value)})`
    style["--percentage"] = `${props.value}%`;

    return (
        <div className={ContainerClass} style={style}>
            <div className={`${BarClass} positive`}>
                <span>{props.text ?? props.value}</span>
            </div>
            <div className={`${BarClass} negative`}>
                <span>{props.text ?? props.value}</span>
            </div>
        </div>
    );
}

export default ProgressBarWithLabel;

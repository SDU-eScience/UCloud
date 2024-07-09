import * as React from "react";
import Flex from "./Flex";
import Text from "./Text";
import {ThemeColor} from "./theme";
import {injectStyle} from "@/Unstyled";
import {CSSProperties} from "react";
import {TooltipV2} from "./Tooltip";
import Icon from "./Icon";
import {UNABLE_TO_USE_FULL_ALLOC_MESSAGE} from "@/Accounting";
import {OverallocationLink} from "@/UtilityComponents";

const ProgressBaseClass = injectStyle("progress-base", k => `
    ${k} {
        border-radius: 5px;
        background-color: var(--progressColor, #f00);
        width: 100%;
        
        --progressColor: var(--successMain):
    }
    
    ${k}[data-active="false"] {
        display: none;
    }
    
    ${k}[data-pulse="true"] {
        height: 100%;
        
        /* From semantic-ui-css */
        animation: progress-active 2s ease infinite;
        color: black;
        width: 100%;
    }
    
    @keyframes progress-active {
        0% {
            opacity: 0.3;
            width: 0;
        }
        100% {
            opacity: 0;
            width: 100%;
        }
    }
`);

interface Progress {
    color: ThemeColor;
    percent: number;
    active: boolean;
    label: string;
}

const Progress = ({color, percent, active, label}: Progress): React.ReactNode => {
    const topLevelStyle: CSSProperties = {height: "30px"};
    topLevelStyle["--progressColor"] = `var(--${color})`;

    const secondaryStyle = {...topLevelStyle};
    secondaryStyle.width = `${percent}%`;

    return (
        <>
            <div className={ProgressBaseClass} style={topLevelStyle}>
                <div className={ProgressBaseClass} style={secondaryStyle}>
                    <div className={ProgressBaseClass} data-pulse={"true"} data-active={active} />
                </div>
            </div>
            {label ? <Flex justifyContent="center"><Text>{label}</Text></Flex> : null}
        </>
    );
};

const NewAndImprovedProgressStyle = injectStyle("progress", k => `
    ${k} {
        height: var(--progress-bar-height);
        width: var(--progress-bar-width);
        border-radius: 5px;
        position: relative;
        display: inline-flex;
        background: linear-gradient(
            120deg,
            var(--primaryLight) 0%, var(--primaryDark) var(--percentage),
            var(--secondaryMain) var(--percentage), var(--secondaryDark)
            var(--limit));
    }

    ${k}[data-label] {
        margin-top: 20px;
    }
    
    ${k}:before {
        content: '';
        border-radius: 5px;
        position: absolute;
        top: 0;
        right: 0;
        width: 100%;
        height: 100%;
        background: linear-gradient(
            120deg, #0000 0%, #0000 var(--limit), var(--errorLight) var(--limit), var(--errorDark) 100%);
    }
    
    ${k}[data-label]:after {
        content: attr(data-label);
        font-size: 12px;
        position: absolute;
        color: var(--textPrimary);
        text-align: center;
        top: -1.8em;
        width: 100%;
    }
`)

export function NewAndImprovedProgress({
    label,
    percentage,
    limitPercentage,
    withWarning,
    width = "250px",
    height = "5px",
}: {label?: string; percentage: number; limitPercentage: number; withWarning?: boolean; width?: string, height?: string;}): React.ReactNode {
    const style: CSSProperties = {};
    // for visualization purposes we offset values too small or too close to 100%
    if (percentage > 0.1 && percentage < 2) {
        percentage = 2
    }
    if (limitPercentage < 99.9 && limitPercentage > 98) {
        limitPercentage = 98
    }
    style["--percentage"] = percentage + "%";
    style["--limit"] = limitPercentage + "%";
    style["--progress-bar-width"] = width;
    style["--progress-bar-height"] = height;
    const warning = withWarning ? <OverallocationLink><TooltipV2 tooltip={UNABLE_TO_USE_FULL_ALLOC_MESSAGE}>
        <Icon size={"20px"} mr="8px" name={"heroExclamationTriangle"} color={"warningMain"} />
    </TooltipV2></OverallocationLink> : null;
    return <Flex alignItems={"center"}>{warning}<div className={NewAndImprovedProgressStyle} data-label={label} style={style} /></Flex>
}

export default Progress;

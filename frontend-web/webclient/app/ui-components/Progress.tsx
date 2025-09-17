import * as React from "react";
import Flex from "./Flex";
import Text from "./Text";
import {ThemeColor} from "./theme";
import {injectStyle, makeClassName} from "@/Unstyled";
import {CSSProperties} from "react";
import {TooltipV2} from "./Tooltip";
import Icon from "./Icon";
import {UNABLE_TO_USE_FULL_ALLOC_MESSAGE} from "@/Accounting";
import {OverallocationLink} from "@/UtilityComponents";
import Box from "@/ui-components/Box";

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

    topLevelStyle["background"] = `var(--secondaryLight)`;

    return (
        <>
            <div className={ProgressBaseClass} style={topLevelStyle}>
                <div className={ProgressBaseClass} style={secondaryStyle}/>
            </div>
            {label ? <Flex justifyContent="center"><Text>{label}</Text></Flex> : null}
        </>
    );
};

const progressLimitOverlays = makeClassName("progress-limit-overlay");
const progressLabel = makeClassName("progress-label");

const NewAndImprovedProgressStyle = injectStyle("progress", k => `
    ${k} {
        height: var(--progress-bar-height);
        width: var(--progress-bar-width);
        border-radius: 5px;
        position: relative;
        display: inline-flex;
        margin-top: 20px;
        background: linear-gradient(
            120deg,
            var(--progress-bar-color-start) 0%, var(--progress-bar-color-end) var(--percentage),
            var(--secondaryMain) var(--percentage), var(--secondaryDark) var(--limit));
    }

    ${k} ${progressLimitOverlays.dot} {
        border-radius: 5px;
        position: absolute;
        top: 0;
        right: 0;
        width: 100%;
        height: 100%;
        background: linear-gradient(
            120deg, #0000 0%, #0000 var(--limit), var(--limit-bar-color-start) var(--limit), var(--limit-bar-color-end) 100%);
        pointer-events: none;
    }

    ${k} ${progressLabel.dot} {
        white-space: pre;
        font-size: 12px;
        position: absolute;
        color: var(--textPrimary);
        top: -1.8em;
        width: 100%;
        display: flex;
        justify-content: center;
    }
`);

interface ColorPair {
    start: ThemeColor;
    end: ThemeColor;
}

export function NewAndImprovedProgress({
    label,
    percentage,
    limitPercentage,
    withWarning,
    width = "250px",
    height = "5px",
    progressColor = {start: "primaryLight", end: "primaryDark"},
    limitColor = {start: "errorLight", end: "errorDark"},
}: {
    label?: string;
    percentage: number;
    limitPercentage: number;
    withWarning?: boolean;
    width?: string;
    height?: string;
    progressColor?: ColorPair;
    limitColor?: ColorPair;
}): React.ReactNode {
    const style: CSSProperties = {};

    // clamp percentages for visualization
    if (percentage > 0.1 && percentage < 2) percentage = 2;
    percentage = Math.max(0, percentage);
    if (limitPercentage < 99.9 && limitPercentage > 98) limitPercentage = 98;

    style["--percentage"] = percentage + "%";
    style["--limit"] = limitPercentage + "%";
    style["--progress-bar-width"] = width;
    style["--progress-bar-height"] = height;

    style["--progress-bar-color-start"] = `var(--${progressColor.start})`;
    style["--progress-bar-color-end"] = `var(--${progressColor.end})`;

    style["--limit-bar-color-start"] = `var(--${limitColor.start})`;
    style["--limit-bar-color-end"] = `var(--${limitColor.end})`;

    const warning = withWarning ? (
        <div style={{position: "relative"}}>
            <div style={{position: "absolute", left: "-20px"}}>
                <OverallocationLink>
                    <TooltipV2 tooltip={UNABLE_TO_USE_FULL_ALLOC_MESSAGE}>
                        <Icon size="20px" mr="8px" name="heroExclamationTriangle" color="warningMain"/>
                    </TooltipV2>
                </OverallocationLink>
            </div>
        </div>
    ) : null;

    return (
        <Flex alignItems="center">
            <div className={NewAndImprovedProgressStyle} style={style}>
                <div className={progressLimitOverlays.class}/>
                {label && <span className={progressLabel.class}>
                    <Box flexGrow={1} textAlign={"center"}>{label}</Box>
                    {warning}
                </span>}
            </div>
        </Flex>
    );
}

export default Progress;

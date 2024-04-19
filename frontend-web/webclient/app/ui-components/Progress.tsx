import * as React from "react";
import Flex from "./Flex";
import Text from "./Text";
import {ThemeColor} from "./theme";
import {injectStyle} from "@/Unstyled";
import {CSSProperties} from "react";
import {TooltipV2} from "./Tooltip";
import Icon from "./Icon";
import {UNABLE_TO_USE_FULL_ALLOC_MESSAGE} from "@/Accounting";

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

const Progress = ({color, percent, active, label}: Progress): JSX.Element => {
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
        margin-top: 15px;
        height: 5px;
        width: 250px;
        border-radius: 5px;
        position: relative;
        display: inline-flex;
        background: linear-gradient(
        120deg,
        var(--primaryLight) 0%, var(--primaryDark) var(--percentage),
        var(--secondaryMain) var(--percentage), var(--secondaryDark)  var(--limit)
        );
    }
    
    ${k}:before {
        content: '';
        border-radius: 0 5px 5px 0;
        position: absolute;
        top: 0;
        right: 0;
        width: 100%;
        height: 100%;
        background: linear-gradient(
            120deg, #0000 0%, #0000 var(--limit), var(--errorLight) var(--limit), var(--errorDark) 100%)
    }
    
    ${k}:after {
        content: attr(data-label);
        font-size: 12px;
        position: absolute;
        color: var(--textPrimary);
        text-align: center;
        top: -1.4em;
        width: 100%;
    }
`)

const DEBUGGING_PURPOSES = DEVELOPMENT_ENV;
export function NewAndImprovedProgress({label, percentage, limitPercentage, withWarning}: {label: string; percentage: number; limitPercentage: number; withWarning?: boolean}) {
    React.useEffect(() => {
        if (DEBUGGING_PURPOSES) {
            if (percentage > 100) {
                console.warn("Percentage for", label, "is above 100")
            }

            if (limitPercentage > 100) {
                console.warn("limit for", label, "is above 100")
            }
        }
    }, []);

    const style: CSSProperties = {};
    // for visualization purposes we round values too small or too close to 100%
    if (percentage != 0 && percentage < 2) {
        percentage = 2
    }
    if (limitPercentage != 100 && limitPercentage > 98) {
        limitPercentage = 98
    }
    style["--percentage"] = percentage + "%";
    style["--limit"] = limitPercentage + "%";
    const warning = withWarning ? <TooltipV2 tooltip={UNABLE_TO_USE_FULL_ALLOC_MESSAGE}>
        <Icon mr="4px" name={"heroExclamationTriangle"} color={"warningMain"} />
    </TooltipV2> : null;
    return <Flex>{warning}<div className={NewAndImprovedProgressStyle} data-label={label} style={style} /></Flex>
}

export default Progress;

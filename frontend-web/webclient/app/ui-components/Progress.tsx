import * as React from "react";
import Flex from "./Flex";
import Text from "./Text";
import {ThemeColor} from "./theme";
import {injectStyle} from "@/Unstyled";
import {CSSProperties} from "react";

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
    const topLevelStyle: CSSProperties = { height: "30px" };
    topLevelStyle["--progressColor"] = `var(--${color})`;

    const secondaryStyle = {...topLevelStyle};
    secondaryStyle.width = `${percent}%`;

    return (
        <>
            <div className={ProgressBaseClass} style={topLevelStyle}>
                <div className={ProgressBaseClass} style={secondaryStyle}>
                    <div className={ProgressBaseClass} data-pulse={"true"} data-active={active}/>
                </div>
            </div>
            {label ? <Flex justifyContent="center"><Text>{label}</Text></Flex> : null}
        </>
    );
};

export default Progress;

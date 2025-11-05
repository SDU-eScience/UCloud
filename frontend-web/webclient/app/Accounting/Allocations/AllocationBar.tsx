import * as React from "react";
import {injectStyle, makeClassName} from "@/Unstyled";
import {CSSProperties} from "react";
import Box from "@/ui-components/Box";
import {ThemeColor} from "@/ui-components/theme";
import {Flex} from "@/ui-components";

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
            var(--ok-bar-color-start) 0%, var(--ok-bar-color-end) var(--percentage),
            var(--at-risk-bar-color-start) var(--percentage), var(--at-risk-bar-color-end) var(--limit));
    }

    ${k} ${progressLimitOverlays.dot} {
        border-radius: 5px;
        position: absolute;
        top: 0;
        right: 0;
        width: 100%;
        height: 100%;
        background: linear-gradient(
            120deg, #0000 0%, #0000 var(--limit), var(--underused-bar-color-start) var(--limit), var(--underused-bar-color-end) 100%);
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

interface AllocationBarProps {
    label?: string;
    okPercentage: number;
    atRiskPercentage: number;
    underusedPercentage: number;
    width?: string;
    height?: string;
    okColor?: ColorPair;
    atRiskColor?: ColorPair;
    underusedColor?: ColorPair;
}

export function AllocationBar(
    {
        label,
        okPercentage,
        underusedPercentage,
        width = "250px",
        height = "5px",
        okColor = {start: "successLight", end: "successDark"},
        atRiskColor = {start: "errorLight", end: "errorDark"},
        underusedColor = {start: "warningMain", end: "warningDark"},
    }: AllocationBarProps
): React.ReactNode
{
    const style: CSSProperties = {};

    style["--percentage"] = okPercentage + "%";
    style["--limit"] = (100 - underusedPercentage) + "%";
    style["--progress-bar-width"] = width;
    style["--progress-bar-height"] = height;

    style["--ok-bar-color-start"] = `var(--${okColor.start})`;
    style["--ok-bar-color-end"] = `var(--${okColor.end})`;

    style["--underused-bar-color-start"] = `var(--${underusedColor.start})`;
    style["--underused-bar-color-end"] = `var(--${underusedColor.end})`;

    style["--at-risk-bar-color-start"] = `var(--${atRiskColor.start})`;
    style["--at-risk-bar-color-end"] = `var(--${atRiskColor.end})`;

    return (
        <Flex alignItems="center">
            <div className={NewAndImprovedProgressStyle} style={style}>
                <div className={progressLimitOverlays.class}/>
                {label && <span className={progressLabel.class}>
                    <Box flexGrow={1} textAlign={"center"}>{label}</Box>
                </span>}
            </div>
        </Flex>
    );
}

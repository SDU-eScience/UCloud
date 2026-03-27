import * as React from "react";
import {injectStyle} from "@/Unstyled";

export const HeroHeaderCard = injectStyle("hero-header-card", k => `
    ${k} {
        border: 1px solid color-mix(in srgb, var(--primaryMain) 25%, transparent);
        --jobStateColor1: var(--successLight);
        --jobStateColor2: var(--successDark);

        background: linear-gradient(115deg,
            color-mix(in srgb, var(--jobStateColor1) 8%, transparent),
            color-mix(in srgb, var(--jobStateColor2) 12%, transparent)
        );
    }

    ${k}.POWERING_ON,
    ${k}.RUNNING {
        border-color: color-mix(in srgb, var(--successMain) 40%, transparent);
    }

    ${k}.IN_QUEUE,
    ${k}.POWERING_OFF,
    ${k}.RESTARTING,
    ${k}.SUSPENDED {
        border-color: color-mix(in srgb, var(--warningMain) 45%, transparent);
        --jobStateColor1: var(--warningLight);
        --jobStateColor2: var(--warningDark);
    }

    ${k}.SUCCESS,
    ${k}.EXPIRED,
    ${k}.FAILURE {
        --jobStateColor1: var(--errorLight);
        --jobStateColor2: var(--errorDark);
        border-color: color-mix(in srgb, var(--errorMain) 45%, transparent);
    }
`);

export const HeroHeaderGrid = injectStyle("hero-header-grid", k => `
    ${k} {
        margin-top: 32px;
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
        gap: 10px 16px;
    }
`);

export const HeroMetricTitle = injectStyle("hero-metric-title", k => `
    ${k} {
        color: var(--textSecondary);
        font-size: 12px;
        margin-bottom: 3px;
    }
`);

export const HeroMetric: React.FunctionComponent<React.PropsWithChildren<{title: string}>> = ({title, children}) => {
    return <div>
        <div className={HeroMetricTitle}>{title}</div>
        <div>{children}</div>
    </div>;
};

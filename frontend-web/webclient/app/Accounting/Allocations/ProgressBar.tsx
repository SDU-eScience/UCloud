import {UsageAndQuota} from "@/Accounting";
import {NewAndImprovedProgress} from "@/ui-components/Progress";
import {injectStyle} from "@/Unstyled";
import * as React from "react";

const responsiveProgressStyle = injectStyle("responsive-progress", k => `
    ${k} .progress-label-only {
        font-size: 12px;
        color: var(--textPrimary);
        white-space: nowrap;
        display: none;
    }

    @media (max-width: 1500px) {
        ${k}[data-gpu="true"] .progress-visual {
            display: none;
        }
    }

    @media (max-width: 1200px) {
        ${k} .progress-visual {
            display: none;
        }

        ${k} .progress-label-only {
            display: inline;
        }
    }
`);

function isGpuBar(uq: UsageAndQuota): boolean {
    return uq.raw.unit.toLowerCase().includes("gpu");
}

export function ProgressBar({uq, width, responsive}: {
    uq: UsageAndQuota,
    width?: string;
    responsive?: boolean;
}) {
    const bar = <NewAndImprovedProgress
        limitPercentage={uq.display.maxUsablePercentage}
        label={uq.display.usageAndQuotaPercent}
        percentage={uq.display.percentageUsed}
        withWarning={uq.display.displayOverallocationWarning}
        width={width}
    />;

    if (!responsive) return bar;

    return <div className={responsiveProgressStyle} data-gpu={isGpuBar(uq) ? "true" : "false"}>
        <div className="progress-visual">{bar}</div>
        <span className="progress-label-only">{uq.display.usageAndQuotaPercent}</span>
    </div>;
}

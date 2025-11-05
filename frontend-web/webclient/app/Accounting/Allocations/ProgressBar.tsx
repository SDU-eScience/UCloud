import {UsageAndQuota} from "@/Accounting";
import {NewAndImprovedProgress} from "@/ui-components/Progress";
import * as React from "react";

export function ProgressBar({uq, width}: {
    uq: UsageAndQuota,
    width?: string;
}) {
    return <NewAndImprovedProgress
        limitPercentage={uq.display.maxUsablePercentage}
        label={uq.display.usageAndQuotaPercent}
        percentage={uq.display.percentageUsed}
        withWarning={uq.display.displayOverallocationWarning}
        width={width}
    />;
}

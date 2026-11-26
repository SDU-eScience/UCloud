import * as React from "react";
import type {AccountingModel, FrontendAccountingUnit} from "@/Accounting/index";
import {Icon} from "@/ui-components";
import {TooltipV2} from "@/ui-components/Tooltip";
import {formatNumber} from "@/Utilities/NumberFormatting";
import {
    USAGE_DROP_CRITICAL_DAYS,
    usageDaysUntil,
    usageDropRiskDays,
    usageFormatShortDate,
    usageFormatValue,
} from "./Api";
import type {UsageQuotaDrop} from "./Api";

export function UsageExpiryCell({
    drop,
    nextExpiration,
    accountingModel,
    unit,
    referenceTime,
    activeUsage,
    warningClassName,
    numberClassName,
}: {
    drop?: UsageQuotaDrop | null;
    nextExpiration?: number | null;
    accountingModel: AccountingModel;
    unit: FrontendAccountingUnit;
    referenceTime: number;
    activeUsage?: number;
    warningClassName: string;
    numberClassName: string;
}): React.ReactNode {
    const riskDays = usageDropRiskDays(drop, accountingModel, referenceTime);
    if (drop && riskDays !== null) {
        const isCapacity = accountingModel === "CAPACITY";
        const color = riskDays < USAGE_DROP_CRITICAL_DAYS ? "errorMain" : "warningMain";
        const date = usageFormatShortDate(drop.timestamp);
        const quotaBefore = usageFormatValue(unit, drop.quotaBefore);
        const usageAmount = activeUsage === undefined ? "" : ` of ${usageFormatValue(unit, activeUsage)}`;
        const tooltip = isCapacity
            ? `On ${date}, active quota drops from ${quotaBefore} to ${usageFormatValue(unit, drop.quotaAfter)}, below current usage${usageAmount}.`
            : `On ${date}, active quota of ${quotaBefore} expires with no renewal in place.`;
        return <TooltipV2 tooltip={tooltip}>
            <span className={warningClassName} style={{color: `var(--${color})`}}>
                <Icon name="heroExclamationTriangle" size={14} color={color} />
                <span>{isCapacity ? "Over quota in" : "No quota in"} {formatNumber(riskDays)}d</span>
            </span>
        </TooltipV2>;
    }

    const target = drop?.timestamp ?? nextExpiration;
    if (target == null) return <span className={numberClassName} style={{color: "var(--textSecondary)"}}>—</span>;
    return <TooltipV2 tooltip={usageFormatShortDate(target)}>
        <span className={numberClassName}>{formatNumber(usageDaysUntil(target, referenceTime) ?? 0)}d</span>
    </TooltipV2>;
}

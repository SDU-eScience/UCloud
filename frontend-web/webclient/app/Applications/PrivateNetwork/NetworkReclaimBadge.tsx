import * as React from "react";
import {injectStyleSimple} from "@/Unstyled";
import {NetworkReclaimState, networkReclaimStateLabel} from "./types";

const ReclaimBadgeClass = injectStyleSimple("network-reclaim-badge", `
    display: inline-flex;
    align-items: center;
    border-radius: 999px;
    padding: 2px 8px;
    font-size: 11px;
    font-weight: 600;
    line-height: 1.4;
    white-space: nowrap;
    background: var(--warningMain);
    color: var(--warningContrast);
`);

export function networkReclaimBadgeElement(state: NetworkReclaimState): HTMLElement {
    const element = document.createElement("span");
    element.className = ReclaimBadgeClass;
    element.style.marginLeft = "6px";
    element.textContent = networkReclaimStateLabel(state);
    return element;
}

export function NetworkReclaimBadge({state}: {state: NetworkReclaimState}): React.ReactNode {
    if (state === "active") return null;
    return <span className={ReclaimBadgeClass}>{networkReclaimStateLabel(state)}</span>;
}

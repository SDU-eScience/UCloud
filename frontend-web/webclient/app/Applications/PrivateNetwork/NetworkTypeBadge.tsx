import * as React from "react";
import {classConcat, injectStyleSimple} from "@/Unstyled";
import {NetworkReclaimState, networkReclaimStateLabel, PrivateNetworkType, privateNetworkTypeLabel} from "./types";

const BadgeClass = injectStyleSimple("network-type-badge", `
    display: inline-flex;
    align-items: center;
    border-radius: 999px;
    padding: 2px 8px;
    font-size: 11px;
    font-weight: 600;
    line-height: 1.4;
    white-space: nowrap;
`);

const EthernetClass = injectStyleSimple("network-type-badge-ethernet", `
    background: var(--primaryMain);
    color: var(--primaryContrast);
`);

const InfiniBandClass = injectStyleSimple("network-type-badge-infiniband", `
    background: var(--successMain);
    color: var(--successContrast);
`);

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

export function networkTypeBadgeClassName(type: PrivateNetworkType): string {
    return classConcat(BadgeClass, type === "infiniband" ? InfiniBandClass : EthernetClass);
}

export function networkTypeBadgeElement(type: PrivateNetworkType): HTMLElement {
    const element = document.createElement("span");
    element.className = networkTypeBadgeClassName(type);
    element.textContent = privateNetworkTypeLabel(type);
    return element;
}

export function networkReclaimBadgeElement(state: NetworkReclaimState): HTMLElement {
    const element = document.createElement("span");
    element.className = ReclaimBadgeClass;
    element.style.marginLeft = "6px";
    element.textContent = networkReclaimStateLabel(state);
    return element;
}

export function NetworkTypeBadge({type}: {type: PrivateNetworkType}): React.ReactNode {
    return <span className={networkTypeBadgeClassName(type)}>{privateNetworkTypeLabel(type)}</span>;
}

export function NetworkReclaimBadge({state}: {state: NetworkReclaimState}): React.ReactNode {
    if (state === "active") return null;
    return <span className={ReclaimBadgeClass}>{networkReclaimStateLabel(state)}</span>;
}

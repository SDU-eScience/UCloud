import * as React from "react";
import {classConcat, injectStyleSimple} from "@/Unstyled";
import {PrivateNetworkType, privateNetworkTypeLabel} from "./types";

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

export function networkTypeBadgeClassName(type: PrivateNetworkType): string {
    return classConcat(BadgeClass, type === "infiniband" ? InfiniBandClass : EthernetClass);
}

export function networkTypeBadgeElement(type: PrivateNetworkType): HTMLElement {
    const element = document.createElement("span");
    element.className = networkTypeBadgeClassName(type);
    element.textContent = privateNetworkTypeLabel(type);
    return element;
}

export function NetworkTypeBadge({type}: {type: PrivateNetworkType}): React.ReactNode {
    return <span className={networkTypeBadgeClassName(type)}>{privateNetworkTypeLabel(type)}</span>;
}

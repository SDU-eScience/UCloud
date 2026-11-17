import {explainUnit} from "@/Accounting";
import type {Allocation, AllocationDisplayTreeRecipient, AllocationGroup, ProductCategoryV2, WalletV2} from "@/Accounting";
import type {ProjectInfo} from "@/Project/InfoCache";
import {projectInfoPi, projectInfoTitle} from "@/Project/InfoCache";
import {getShortProviderTitle} from "@/Providers/ProviderTitle";
import {allocationCompareProducts, allocationDisplayUnit} from "./AllocationPresentation";

export interface AllocationDateRange {
    start: Date;
    end: Date;
}

export type AllocationStatus = "active" | "expiring" | "upcoming" | "exhausted" | "expired";
export type AllocationAttribution = "recorded" | "pooled" | "future" | "not-applicable";

export interface AllocationAmounts {
    granted: number;
    used: number;
    activeQuota: number;
    activeUsed: number;
    activeBalance: number;
    retiredUsed: number;
    expiredUnused: number;
    future: number;
    startingSoon: number;
    expiringSoon: number;
}

export interface AllocationModelSlice {
    key: string;
    allocationId: number;
    start: number;
    end: number;
    quota: number;
    rawQuota: number;
    used: number | null;
    attribution: AllocationAttribution;
    retired: boolean;
}

export interface AllocationModelGrant {
    key: string;
    allocationId: number;
    grantId?: number;
    start: number;
    end: number;
    quota: number;
    rawQuota: number;
    used: number | null;
    recordedUsed: number;
    attribution: AllocationAttribution;
    slices: AllocationModelSlice[];
    sliced: boolean;
}

export interface AllocationModelProduct {
    key: string;
    workspaceKey: string;
    owner: AllocationDisplayTreeRecipient["owner"];
    category: ProductCategoryV2;
    unit: string;
    groupIds: number[];
    start: number;
    end: number;
    grants: AllocationModelGrant[];
    raw: AllocationAmounts;
    amounts: AllocationAmounts;
    states: Set<AllocationStatus>;
}

export interface AllocationModel {
    now: number;
    products: AllocationModelProduct[];
    received: {category: ProductCategoryV2; amounts: AllocationAmounts}[];
}

export interface AllocationModelRecipient {
    owner: AllocationModelProduct["owner"];
    products: AllocationModelProduct[];
}

export function allocationModelRecipients(model: AllocationModel, workspaceKeys: ReadonlySet<string>): AllocationModelRecipient[] {
    const recipients = new Map<string, AllocationModelRecipient>();
    for (const product of model.products) {
        if (!workspaceKeys.has(product.workspaceKey)) continue;
        let recipient = recipients.get(product.workspaceKey);
        if (!recipient) {
            recipient = {owner: product.owner, products: []};
            recipients.set(product.workspaceKey, recipient);
        }
        recipient.products.push(product);
    }
    return [...recipients.values()];
}

export interface AllocationProductSelection {
    dateRange?: AllocationDateRange;
    personalWorkspaces: boolean;
    categories?: string[];
    providers?: string[];
    query?: string;
    state?: AllocationStatus | "all";
    projectInfo?: Record<string, ProjectInfo | null>;
}

const allocationMonthMs = 30 * 24 * 60 * 60 * 1000;

export function allocationDateRangeBounds(range?: AllocationDateRange): {start: number | null; end: number | null} {
    if (!range) return {start: null, end: null};
    const first = range.start.getTime() <= range.end.getTime() ? range.start : range.end;
    const last = first === range.start ? range.end : range.start;
    return {
        start: new Date(first.getFullYear(), first.getMonth(), 1).getTime(),
        end: new Date(last.getFullYear(), last.getMonth() + 1, 1).getTime() - 1,
    };
}

export function allocationOverlapsRange(start: number, end: number, range: {start: number | null; end: number | null}): boolean {
    if (range.start !== null && end < range.start) return false;
    if (range.end !== null && start > range.end) return false;
    return true;
}

function allocationEmptyAmounts(): AllocationAmounts {
    return {
        granted: 0,
        used: 0,
        activeQuota: 0,
        activeUsed: 0,
        activeBalance: 0,
        retiredUsed: 0,
        expiredUnused: 0,
        future: 0,
        startingSoon: 0,
        expiringSoon: 0,
    };
}

function allocationGroupAmounts(group: AllocationGroup, category: ProductCategoryV2, now: number): AllocationAmounts {
    const amounts = allocationEmptyAmounts();
    const consumption = category.accountingModel === "CONSUMPTION";
    amounts.used = group.usage;
    for (const allocation of group.allocations) {
        const missingRetirement = allocation.retired && consumption
            && (allocation.retiredUsage == null || allocation.retiredQuota == null);
        if (missingRetirement) throw new Error(`Allocation ${allocation.id} is missing its retirement attribution.`);
        const quota = allocation.retired ? allocation.retiredQuota ?? allocation.quota : allocation.quota;
        amounts.granted += quota;
        if (allocation.retired) {
            if (consumption) {
                const used = allocation.retiredUsage!;
                amounts.retiredUsed += used;
                amounts.expiredUnused += quota - used;
            }
        } else if (allocation.startDate > now) {
            amounts.future += quota;
            if (allocation.startDate <= now + allocationMonthMs) amounts.startingSoon += quota;
        } else if (allocation.endDate > now) {
            amounts.activeQuota += quota;
            if (allocation.endDate <= now + allocationMonthMs) amounts.expiringSoon += quota;
        }
    }
    amounts.activeQuota = group.activeQuota ?? amounts.activeQuota;
    amounts.activeUsed = group.activeUsage ?? amounts.used - amounts.retiredUsed;
    amounts.activeBalance = amounts.activeQuota - amounts.activeUsed;
    return amounts;
}

function allocationAddAmounts(target: AllocationAmounts, source: AllocationAmounts): void {
    for (const key of Object.keys(target) as (keyof AllocationAmounts)[]) target[key] += source[key];
}

function allocationDisplayAmounts(raw: AllocationAmounts, category: ProductCategoryV2): AllocationAmounts {
    const result = {...raw};
    const factor = explainUnit(category).balanceFactor;
    for (const key of Object.keys(result) as (keyof AllocationAmounts)[]) result[key] *= factor;
    return result;
}

function allocationNormalizeSlice(allocation: Allocation, category: ProductCategoryV2, now: number): AllocationModelSlice {
    const consumption = category.accountingModel === "CONSUMPTION";
    const factor = explainUnit(category).balanceFactor;
    const rawQuota = allocation.retired ? allocation.retiredQuota ?? allocation.quota : allocation.quota;
    let attribution: AllocationAttribution = "pooled";
    let used: number | null = null;
    if (allocation.retired) {
        attribution = consumption ? "recorded" : "not-applicable";
        if (consumption) used = (allocation.retiredUsage ?? 0) * factor;
    } else if (allocation.startDate > now) {
        attribution = "future";
        used = 0;
    }
    return {
        key: `allocation:${allocation.id}`,
        allocationId: allocation.id,
        start: allocation.startDate,
        end: allocation.endDate,
        quota: rawQuota * factor,
        rawQuota,
        used,
        attribution,
        retired: allocation.retired === true,
    };
}

function allocationProductStates(product: AllocationModelProduct, now: number): Set<AllocationStatus> {
    const states = new Set<AllocationStatus>();
    const slices = product.grants.flatMap(grant => grant.slices);
    if (product.grants.some(grant => grant.slices.every(slice => slice.retired))) states.add("expired");
    if (product.grants.some(grant => grant.start > now)) states.add("upcoming");
    const active = slices.filter(slice => !slice.retired && slice.start <= now && slice.end > now);
    if (active.length > 0) {
        states.add("active");
        if (active.some(slice => slice.end <= now + allocationMonthMs)) states.add("expiring");
        if (product.raw.activeBalance <= 0) states.add("exhausted");
    }
    return states;
}

export function allocationNormalize(wallets: WalletV2[], now: number): AllocationModel {
    const products = new Map<string, AllocationModelProduct>();
    const received: AllocationModel["received"] = [];
    const seenGroups = new Set<string>();
    for (const wallet of wallets) {
        if (wallet.paysFor.freeToUse) continue;
        const category = wallet.paysFor;
        const incoming = allocationEmptyAmounts();
        for (const parent of wallet.allocationGroups) {
            allocationAddAmounts(incoming, allocationGroupAmounts(parent.group, category, now));
        }
        received.push({category, amounts: allocationDisplayAmounts(incoming, category)});
        for (const child of wallet.children ?? []) {
            const owner: AllocationDisplayTreeRecipient["owner"] = child.child.projectId ? {
                reference: {type: "project", projectId: child.child.projectId},
                title: child.child.projectTitle,
                primaryUsername: child.child.pi ?? "",
            } : {
                reference: {type: "user", username: child.child.projectTitle},
                title: child.child.projectTitle,
                primaryUsername: child.child.projectTitle,
            };
            const workspaceKey = owner.reference.type === "project" ?
                `project:${owner.reference.projectId}` : `user:${owner.reference.username}`;
            const key = `product:${workspaceKey}/${category.provider}/${category.name}`;
            const groupKey = `${key}/${wallet.owner.type === "project" ? wallet.owner.projectId : wallet.owner.username}/${child.group.id ?? child.group.allocations.map(allocation => allocation.id).sort((a, b) => a - b).join(",")}`;
            if (seenGroups.has(groupKey) || child.group.allocations.length === 0) continue;
            seenGroups.add(groupKey);
            let product = products.get(key);
            if (!product) {
                product = {
                    key,
                    workspaceKey,
                    owner,
                    category,
                    unit: allocationDisplayUnit(category),
                    groupIds: [],
                    start: Number.POSITIVE_INFINITY,
                    end: Number.NEGATIVE_INFINITY,
                    grants: [],
                    raw: allocationEmptyAmounts(),
                    amounts: allocationEmptyAmounts(),
                    states: new Set(),
                };
                products.set(key, product);
            }
            if (child.group.id !== undefined) product.groupIds.push(child.group.id);
            allocationAddAmounts(product.raw, allocationGroupAmounts(child.group, category, now));
            const packages = new Map<string, Allocation[]>();
            for (const allocation of child.group.allocations) {
                const grantKey = allocation.allocationPackage && allocation.grantedIn != null ?
                    `package:${groupKey}/${allocation.grantedIn}` : `allocation:${allocation.id}`;
                const entries = packages.get(grantKey) ?? [];
                entries.push(allocation);
                packages.set(grantKey, entries);
            }
            for (const [grantKey, allocations] of packages) {
                const slices = allocations.map(allocation => allocationNormalizeSlice(allocation, category, now))
                    .sort((a, b) => a.start - b.start || a.allocationId - b.allocationId);
                const first = allocations[0];
                const pooled = slices.some(slice => slice.attribution === "pooled");
                const notApplicable = slices.some(slice => slice.attribution === "not-applicable");
                const used = pooled || notApplicable ? null : slices.reduce((sum, slice) => sum + (slice.used ?? 0), 0);
                const grant: AllocationModelGrant = {
                    key: grantKey,
                    allocationId: slices[0].allocationId,
                    grantId: first.grantedIn ?? undefined,
                    start: Math.min(...slices.map(slice => slice.start)),
                    end: Math.max(...slices.map(slice => slice.end)),
                    quota: slices.reduce((sum, slice) => sum + slice.quota, 0),
                    rawQuota: slices.reduce((sum, slice) => sum + slice.rawQuota, 0),
                    used,
                    recordedUsed: slices.reduce((sum, slice) => sum + (slice.attribution === "recorded" ? slice.used ?? 0 : 0), 0),
                    attribution: pooled ? "pooled" : notApplicable ? "not-applicable" : slices.every(slice => slice.attribution === "future") ? "future" : "recorded",
                    slices,
                    sliced: first.allocationPackage === true,
                };
                product.grants.push(grant);
                product.start = Math.min(product.start, grant.start);
                product.end = Math.max(product.end, grant.end);
            }
        }
    }
    for (const product of products.values()) {
        product.grants.sort((a, b) => a.start - b.start || a.allocationId - b.allocationId);
        product.amounts = allocationDisplayAmounts(product.raw, product.category);
        product.states = allocationProductStates(product, now);
    }
    return {now, products: [...products.values()], received};
}

export function allocationWorkspaceInfo(owner: AllocationModelProduct["owner"], projectInfo: Record<string, ProjectInfo | null>) {
    if (owner.reference.type === "user") {
        const username = owner.reference.username;
        return {title: username, pi: username, type: "user" as const, id: username};
    }
    const info = projectInfo[owner.reference.projectId];
    return {
        title: projectInfoTitle(info, owner.title) ?? owner.reference.projectId,
        pi: projectInfoPi(info, owner.primaryUsername) ?? owner.primaryUsername,
        type: "project" as const,
        id: owner.reference.projectId,
    };
}

export function allocationSelectProducts(model: AllocationModel, selection: AllocationProductSelection): AllocationModelProduct[] {
    const range = allocationDateRangeBounds(selection.dateRange);
    const needle = selection.query?.trim().toLowerCase() ?? "";
    return model.products.filter(product => {
        if (!selection.personalWorkspaces && product.owner.reference.type === "user") return false;
        if (selection.categories?.length && !selection.categories.includes(`${product.category.provider}/${product.category.name}`)) return false;
        if (selection.providers?.length && !selection.providers.includes(product.category.provider)) return false;
        if (selection.state && selection.state !== "all" && !product.states.has(selection.state)) return false;
        const overlaps = product.grants.some(grant => grant.slices.some(slice =>
            allocationOverlapsRange(slice.start, slice.end - 1, range)
        ));
        if (!overlaps) return false;
        const workspace = allocationWorkspaceInfo(product.owner, selection.projectInfo ?? {});
        const text = `${workspace.title} ${workspace.pi} ${product.category.name} ${getShortProviderTitle(product.category.provider)}`.toLowerCase();
        return needle === "" || text.includes(needle);
    }).sort((a, b) => {
        const aWorkspace = allocationWorkspaceInfo(a.owner, selection.projectInfo ?? {});
        const bWorkspace = allocationWorkspaceInfo(b.owner, selection.projectInfo ?? {});
        return aWorkspace.title.localeCompare(bWorkspace.title) || allocationCompareProducts(a.category, b.category);
    });
}

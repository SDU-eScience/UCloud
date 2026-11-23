import {explainUnit} from "@/Accounting";
import type {
    Allocation,
    AllocationDisplayTreeRecipient,
    AllocationGroup,
    ProductCategoryV2,
    WalletV2,
} from "@/Accounting";
import type {ProjectInfo} from "@/Project/InfoCache";
import {projectInfoPi, projectInfoTitle} from "@/Project/InfoCache";
import {getShortProviderTitle} from "@/Providers/ProviderTitle";
import {allocationCompareProducts} from "./AllocationPresentation";

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
    estimatedUsed?: number;
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
    maxUsable?: number;
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

export function allocationModelRecipients(
    model: AllocationModel,
    workspaceKeys: ReadonlySet<string>,
): AllocationModelRecipient[] {
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

export function allocationOverlapsRange(
    start: number,
    end: number,
    range: {start: number | null; end: number | null},
): boolean {
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

function allocationNormalizeSlice(
    allocation: Allocation,
    category: ProductCategoryV2,
    now: number,
): AllocationModelSlice {
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

function allocationSliceMatchesState(
    slice: AllocationModelSlice,
    state: AllocationStatus,
    product: AllocationModelProduct,
    now: number,
): boolean {
    if (state === "expired") return slice.retired;
    if (slice.retired) return false;
    if (state === "upcoming") return slice.start > now;
    if (slice.start > now || slice.end <= now) return false;
    if (state === "expiring") return slice.end <= now + allocationMonthMs;
    if (state === "exhausted") return product.maxUsable !== undefined && product.maxUsable <= 0;
    return true;
}

function allocationProductStates(product: AllocationModelProduct, now: number): Set<AllocationStatus> {
    const states: AllocationStatus[] = ["active", "expiring", "upcoming", "exhausted", "expired"];
    return new Set(states.filter(state => product.grants.some(grant =>
        grant.slices.some(slice => allocationSliceMatchesState(slice, state, product, now)))));
}

function allocationSummarizeGrant(
    grant: Pick<AllocationModelGrant, "key" | "grantId" | "sliced">,
    slices: AllocationModelSlice[],
): AllocationModelGrant {
    const pooled = slices.some(slice => slice.attribution === "pooled");
    const notApplicable = slices.some(slice => slice.attribution === "not-applicable");
    let attribution: AllocationAttribution = "recorded";
    if (pooled) attribution = "pooled";
    else if (notApplicable) attribution = "not-applicable";
    else if (slices.every(slice => slice.attribution === "future")) attribution = "future";
    return {
        ...grant,
        allocationId: slices[0].allocationId,
        start: Math.min(...slices.map(slice => slice.start)),
        end: Math.max(...slices.map(slice => slice.end)),
        quota: slices.reduce((sum, slice) => sum + slice.quota, 0),
        rawQuota: slices.reduce((sum, slice) => sum + slice.rawQuota, 0),
        used: pooled || notApplicable ? null : slices.reduce((sum, slice) => sum + (slice.used ?? 0), 0),
        recordedUsed: slices.reduce((sum, slice) => sum + (slice.attribution === "recorded" ? slice.used ?? 0 : 0), 0),
        attribution,
        slices,
    };
}

function allocationFilterGrants(
    grants: AllocationModelGrant[],
    matches: (slice: AllocationModelSlice) => boolean,
): AllocationModelGrant[] {
    return grants.flatMap(grant => {
        const slices = grant.slices.filter(matches);
        if (slices.length === 0) return [];
        return [slices.length === grant.slices.length ? grant : allocationSummarizeGrant(grant, slices)];
    });
}

export function allocationFilterProductsByState(
    products: AllocationModelProduct[],
    state: AllocationStatus | "all",
    now: number,
): AllocationModelProduct[] {
    if (state === "all") return products;
    return products.flatMap(product => {
        if (!product.states.has(state)) return [];
        const grants = allocationFilterGrants(
            product.grants,
            slice => allocationSliceMatchesState(slice, state, product, now),
        );
        if (grants.length === 0) return [];
        return [{
            ...product,
            grants,
            start: Math.min(...grants.map(grant => grant.start)),
            end: Math.max(...grants.map(grant => grant.end)),
        }];
    });
}

export function allocationFilterProductsByRange(
    products: AllocationModelProduct[],
    dateRange: AllocationDateRange | undefined,
    now: number,
): AllocationModelProduct[] {
    const range = allocationDateRangeBounds(dateRange);
    if (range.start === null && range.end === null) return products;
    return products.flatMap(product => {
        const grants = allocationFilterGrants(product.grants, slice =>
            allocationOverlapsRange(slice.start, slice.end - 1, range)
        );
        if (grants.length === 0) return [];
        const filtered: AllocationModelProduct = {
            ...product,
            grants,
            start: Math.min(...grants.map(grant => grant.start)),
            end: Math.max(...grants.map(grant => grant.end)),
        };
        filtered.states = allocationProductStates(filtered, now);
        return [filtered];
    });
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
        incoming.activeUsed = wallet.activeUsage ?? incoming.activeUsed;
        incoming.activeBalance = incoming.activeQuota - incoming.activeUsed;
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
            const ownerKey = wallet.owner.type === "project" ? wallet.owner.projectId : wallet.owner.username;
            const groupId = child.group.id ?? child.group.allocations
                .map(allocation => allocation.id)
                .sort((a, b) => a - b)
                .join(",");
            const groupKey = `${key}/${ownerKey}/${groupId}`;
            if (seenGroups.has(groupKey) || child.group.allocations.length === 0) continue;
            seenGroups.add(groupKey);
            let product = products.get(key);
            if (!product) {
                product = {
                    key,
                    workspaceKey,
                    owner,
                    category,
                    unit: explainUnit(category).name,
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
            if (child.maxUsable !== undefined) {
                product.maxUsable = child.maxUsable * explainUnit(category).balanceFactor;
            }
            const groupAmounts = allocationGroupAmounts(child.group, category, now);
            allocationAddAmounts(product.raw, groupAmounts);
            const activeCount = child.group.allocations.filter(allocation =>
                !allocation.retired && allocation.startDate <= now && allocation.endDate > now
            ).length;
            const estimate = category.accountingModel === "CONSUMPTION" && activeCount > 0 ?
                groupAmounts.activeUsed * explainUnit(category).balanceFactor / activeCount : undefined;
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
                for (const slice of slices) {
                    if (slice.attribution === "pooled" && slice.end > now) slice.estimatedUsed = estimate;
                }
                const first = allocations[0];
                const grant = allocationSummarizeGrant({
                    key: grantKey,
                    grantId: first.grantedIn ?? undefined,
                    sliced: first.allocationPackage === true,
                }, slices);
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

export function allocationWorkspaceInfo(
    owner: AllocationModelProduct["owner"],
    projectInfo: Record<string, ProjectInfo | null>,
) {
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

export function allocationSelectProducts(
    model: AllocationModel,
    selection: AllocationProductSelection,
): AllocationModelProduct[] {
    const range = allocationDateRangeBounds(selection.dateRange);
    const needle = selection.query?.trim().toLowerCase() ?? "";
    return model.products.filter(product => {
        if (!selection.personalWorkspaces && product.owner.reference.type === "user") return false;
        const categoryKey = `${product.category.provider}/${product.category.name}`;
        if (selection.categories?.length && !selection.categories.includes(categoryKey)) return false;
        if (selection.providers?.length && !selection.providers.includes(product.category.provider)) return false;
        if (selection.state && selection.state !== "all" && !product.states.has(selection.state)) return false;
        const overlaps = product.grants.some(grant => grant.slices.some(slice =>
            allocationOverlapsRange(slice.start, slice.end - 1, range)
        ));
        if (!overlaps) return false;
        const workspace = allocationWorkspaceInfo(product.owner, selection.projectInfo ?? {});
        const text = [
            workspace.title,
            workspace.pi,
            product.category.name,
            getShortProviderTitle(product.category.provider),
        ].join(" ").toLowerCase();
        return needle === "" || text.includes(needle);
    }).sort((a, b) => {
        const aWorkspace = allocationWorkspaceInfo(a.owner, selection.projectInfo ?? {});
        const bWorkspace = allocationWorkspaceInfo(b.owner, selection.projectInfo ?? {});
        return aWorkspace.title.localeCompare(bWorkspace.title) || allocationCompareProducts(a.category, b.category);
    });
}

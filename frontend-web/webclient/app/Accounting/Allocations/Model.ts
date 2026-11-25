import {explainUnit, ProductTypesByPriority, productCategoryDisplayName} from "@/Accounting";
import type {
    Allocation,
    AllocationDisplayTreeRecipient,
    AllocationGroup,
    ProductCategoryV2,
    WalletV2,
} from "@/Accounting";
import type {ProjectInfo} from "@/Project/InfoCache";
import {projectInfoPi, projectInfoTitle} from "@/Project/InfoCache";
import {getProviderTitle, getShortProviderTitle} from "@/Providers/ProviderTitle";
import {formatNumber} from "@/Utilities/NumberFormatting";

export function categoryKey(category: ProductCategoryV2): string {
    return `${category.provider}/${category.name}`;
}

export function formatAmount(value: number): string {
    return formatNumber(value, {precision: 2, removeTrailingZeros: true});
}

export function compareProducts(a: ProductCategoryV2, b: ProductCategoryV2): number {
    const provider = getProviderTitle(a.provider).localeCompare(getProviderTitle(b.provider));
    if (provider !== 0) return provider;
    const providerId = a.provider.localeCompare(b.provider);
    if (providerId !== 0) return providerId;

    const productTypes = [...ProductTypesByPriority, "PRIVATE_NETWORK", "PRIVATE_NETWORK_IP"];
    const type = productTypes.indexOf(a.productType) - productTypes.indexOf(b.productType);
    if (type !== 0) return type;
    const unit = explainUnit(a).name.localeCompare(explainUnit(b).name);
    if (unit !== 0) return unit;
    return productCategoryDisplayName(a).localeCompare(productCategoryDisplayName(b));
}

export interface DateRange {
    start: Date;
    end: Date;
}

export type Status = "active" | "expiring" | "upcoming" | "exhausted" | "expired";
export type FilterState = Status | "all";
export type Attribution = "recorded" | "pooled" | "future" | "not-applicable";

export interface EditRequest {
    category: ProductCategoryV2;
    allocationId: number;
    start: number;
    end: number;
    quota: number;
    workspaceTitle: string;
}

export interface Source {
    key: string;
    title: string;
    pi: string;
    type: "project" | "user" | "root";
}

export interface Amounts {
    granted: number;
    used: number;
    activeQuota: number;
    activeUsed: number;
    activeBalance: number;
    retiredUsed: number;
    expiredUnused: number;
    future: number;
}

export interface Slice {
    key: string;
    allocationId: number;
    start: number;
    end: number;
    quota: number;
    rawQuota: number;
    used: number | null;
    estimatedUsed?: number;
    attribution: Attribution;
    retired: boolean;
}

export interface Grant {
    key: string;
    allocationId: number;
    grantId?: number;
    start: number;
    end: number;
    quota: number;
    rawQuota: number;
    used: number | null;
    recordedUsed: number;
    attribution: Attribution;
    slices: Slice[];
    sliced: boolean;
}

export interface ProductAllocation {
    key: string;
    workspaceKey: string;
    owner: AllocationDisplayTreeRecipient["owner"];
    source?: Source;
    category: ProductCategoryV2;
    unit: string;
    maxUsable?: number;
    groupIds: number[];
    start: number;
    end: number;
    grants: Grant[];
    raw: Amounts;
    amounts: Amounts;
    states: Set<Status>;
}

export interface Model {
    now: number;
    products: ProductAllocation[];
    received: {category: ProductCategoryV2; amounts: Amounts}[];
    receivedProducts: ProductAllocation[];
}

export interface Recipient {
    owner: ProductAllocation["owner"];
    products: ProductAllocation[];
}

export function recipientsFor(
    model: Model,
    workspaceKeys: ReadonlySet<string>,
): Recipient[] {
    const recipients = new Map<string, Recipient>();
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

export interface Selection {
    dateRange?: DateRange;
    personalWorkspaces: boolean;
    categories?: string[];
    providers?: string[];
    query?: string;
    state?: Status | "all";
    projectInfo?: Record<string, ProjectInfo | null>;
}

const monthMs = 30 * 24 * 60 * 60 * 1000;

export function calendarYearRange(year: number): DateRange {
    return {start: new Date(year, 0, 1), end: new Date(year, 11, 31)};
}

export function currentCalendarYear(now = Date.now()): DateRange {
    return calendarYearRange(new Date(now).getFullYear());
}

export function rangeBounds(range?: DateRange): {start: number | null; end: number | null} {
    if (!range) return {start: null, end: null};
    const first = range.start.getTime() <= range.end.getTime() ? range.start : range.end;
    const last = first === range.start ? range.end : range.start;
    return {
        start: new Date(first.getFullYear(), first.getMonth(), 1).getTime(),
        end: new Date(last.getFullYear(), last.getMonth() + 1, 1).getTime() - 1,
    };
}

export function overlapsRange(
    start: number,
    end: number,
    range: {start: number | null; end: number | null},
): boolean {
    if (range.start !== null && end < range.start) return false;
    if (range.end !== null && start > range.end) return false;
    return true;
}

function emptyAmounts(): Amounts {
    return {
        granted: 0,
        used: 0,
        activeQuota: 0,
        activeUsed: 0,
        activeBalance: 0,
        retiredUsed: 0,
        expiredUnused: 0,
        future: 0,
    };
}

function amountsForGroup(group: AllocationGroup, category: ProductCategoryV2, now: number): Amounts {
    const amounts = emptyAmounts();
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
        } else if (allocation.endDate > now) {
            amounts.activeQuota += quota;
        }
    }
    amounts.activeQuota = group.activeQuota ?? amounts.activeQuota;
    amounts.activeUsed = group.activeUsage ?? amounts.used - amounts.retiredUsed;
    amounts.activeBalance = amounts.activeQuota - amounts.activeUsed;
    return amounts;
}

function addAmounts(target: Amounts, source: Amounts): void {
    for (const key of Object.keys(target) as (keyof Amounts)[]) target[key] += source[key];
}

export function sumAmounts(sources: ReadonlyArray<{amounts: Amounts}>): Amounts {
    const result = emptyAmounts();
    for (const source of sources) addAmounts(result, source.amounts);
    return result;
}

function displayAmounts(raw: Amounts, category: ProductCategoryV2): Amounts {
    const result = {...raw};
    const factor = explainUnit(category).balanceFactor;
    for (const key of Object.keys(result) as (keyof Amounts)[]) result[key] *= factor;
    return result;
}

function normalizeSlice(
    allocation: Allocation,
    category: ProductCategoryV2,
    now: number,
): Slice {
    const consumption = category.accountingModel === "CONSUMPTION";
    const factor = explainUnit(category).balanceFactor;
    const rawQuota = allocation.retired ? allocation.retiredQuota ?? allocation.quota : allocation.quota;
    let attribution: Attribution = "pooled";
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

function sliceMatchesState(
    slice: Slice,
    state: Status,
    product: ProductAllocation,
    now: number,
): boolean {
    if (state === "expired") return slice.retired;
    if (slice.retired) return false;
    if (state === "upcoming") return slice.start > now;
    if (slice.start > now || slice.end <= now) return false;
    if (state === "expiring") return slice.end <= now + monthMs;
    if (state === "exhausted") return product.maxUsable !== undefined && product.maxUsable <= 0;
    return true;
}

function productStates(product: ProductAllocation, now: number): Set<Status> {
    const states: Status[] = ["active", "expiring", "upcoming", "exhausted", "expired"];
    return new Set(states.filter(state => product.grants.some(grant =>
        grant.slices.some(slice => sliceMatchesState(slice, state, product, now)))));
}

function summarizeGrant(
    grant: Pick<Grant, "key" | "grantId" | "sliced">,
    slices: Slice[],
): Grant {
    const pooled = slices.some(slice => slice.attribution === "pooled");
    const notApplicable = slices.some(slice => slice.attribution === "not-applicable");
    let attribution: Attribution = "recorded";
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

function filterGrants(
    grants: Grant[],
    matches: (slice: Slice) => boolean,
): Grant[] {
    return grants.flatMap(grant => {
        const slices = grant.slices.filter(matches);
        if (slices.length === 0) return [];
        return [slices.length === grant.slices.length ? grant : summarizeGrant(grant, slices)];
    });
}

export function filterByState(
    products: ProductAllocation[],
    state: Status | "all",
    now: number,
): ProductAllocation[] {
    if (state === "all") return products;
    return products.flatMap(product => {
        if (!product.states.has(state)) return [];
        const grants = filterGrants(
            product.grants,
            slice => sliceMatchesState(slice, state, product, now),
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

export function filterByRange(
    products: ProductAllocation[],
    dateRange: DateRange | undefined,
    now: number,
): ProductAllocation[] {
    const range = rangeBounds(dateRange);
    if (range.start === null && range.end === null) return products;
    return products.flatMap(product => {
        const grants = filterGrants(product.grants, slice =>
            overlapsRange(slice.start, slice.end - 1, range)
        );
        if (grants.length === 0) return [];
        const filtered: ProductAllocation = {
            ...product,
            grants,
            start: Math.min(...grants.map(grant => grant.start)),
            end: Math.max(...grants.map(grant => grant.end)),
        };
        filtered.states = productStates(filtered, now);
        return [filtered];
    });
}

function receivedSource(
    parent: {projectId?: string; projectTitle: string; pi: string} | null | undefined,
): Source {
    if (!parent) {
        return {key: "root", title: "Root allocation", pi: "", type: "root"};
    }
    if (parent.projectId) {
        return {
            key: `project:${parent.projectId}`,
            title: parent.projectTitle,
            pi: parent.pi,
            type: "project",
        };
    }
    return {
        key: `user:${parent.projectTitle}`,
        title: parent.projectTitle,
        pi: parent.pi,
        type: "user",
    };
}

function sourceOwner(source: Source): AllocationDisplayTreeRecipient["owner"] {
    if (source.type === "project") {
        return {
            reference: {type: "project", projectId: source.key.replace("project:", "")},
            title: source.title,
            primaryUsername: source.pi,
        };
    }
    return {
        reference: {type: "user", username: source.key.replace("user:", "") || source.title},
        title: source.title,
        primaryUsername: source.pi,
    };
}

export function normalize(wallets: WalletV2[], now: number): Model {
    const products = new Map<string, ProductAllocation>();
    const received: Model["received"] = [];
    const receivedProducts = new Map<string, ProductAllocation>();
    const seenGroups = new Set<string>();
    const seenReceivedGroups = new Set<string>();
    for (const wallet of wallets) {
        if (wallet.paysFor.freeToUse) continue;
        const category = wallet.paysFor;
        const incoming = emptyAmounts();
        for (const parent of wallet.allocationGroups) {
            const groupAmounts = amountsForGroup(parent.group, category, now);
            addAmounts(incoming, groupAmounts);

            const source = receivedSource(parent.parent);
            const receivedKey = `received:${source.key}/${category.provider}/${category.name}`;
            const receivedGroupId = parent.group.id ?? parent.group.allocations
                .map(allocation => allocation.id)
                .sort((a, b) => a - b)
                .join(",");
            const receivedGroupKey = `${receivedKey}/${receivedGroupId}`;
            if (!seenReceivedGroups.has(receivedGroupKey) && parent.group.allocations.length > 0) {
                seenReceivedGroups.add(receivedGroupKey);
                let receivedProduct = receivedProducts.get(receivedKey);
                if (!receivedProduct) {
                    receivedProduct = {
                        key: receivedKey,
                        workspaceKey: source.key,
                        owner: sourceOwner(source),
                        source,
                        category,
                        unit: explainUnit(category).name,
                        groupIds: [],
                        start: Number.POSITIVE_INFINITY,
                        end: Number.NEGATIVE_INFINITY,
                        grants: [],
                        raw: emptyAmounts(),
                        amounts: emptyAmounts(),
                        states: new Set(),
                    };
                    receivedProducts.set(receivedKey, receivedProduct);
                }
                if (parent.group.id !== undefined) receivedProduct.groupIds.push(parent.group.id);
                addAmounts(receivedProduct.raw, groupAmounts);
                const activeCount = parent.group.allocations.filter(allocation =>
                    !allocation.retired && allocation.startDate <= now && allocation.endDate > now
                ).length;
                const estimate = category.accountingModel === "CONSUMPTION" && activeCount > 0 ?
                    groupAmounts.activeUsed * explainUnit(category).balanceFactor / activeCount : undefined;
                const packages = new Map<string, Allocation[]>();
                for (const allocation of parent.group.allocations) {
                    const grantKey = allocation.allocationPackage && allocation.grantedIn != null ?
                        `package:${receivedGroupKey}/${allocation.grantedIn}` : `allocation:${allocation.id}`;
                    const entries = packages.get(grantKey) ?? [];
                    entries.push(allocation);
                    packages.set(grantKey, entries);
                }
                for (const [grantKey, allocations] of packages) {
                    const slices = allocations.map(allocation => normalizeSlice(allocation, category, now))
                        .sort((a, b) => a.start - b.start || a.allocationId - b.allocationId);
                    for (const slice of slices) {
                        if (slice.attribution === "pooled" && slice.end > now) slice.estimatedUsed = estimate;
                    }
                    const first = allocations[0];
                    const grant = summarizeGrant({
                        key: `${receivedGroupKey}/${grantKey}`,
                        grantId: first.grantedIn ?? undefined,
                        sliced: first.allocationPackage === true,
                    }, slices);
                    receivedProduct.grants.push(grant);
                    receivedProduct.start = Math.min(receivedProduct.start, grant.start);
                    receivedProduct.end = Math.max(receivedProduct.end, grant.end);
                }
            }
        }
        incoming.activeUsed = wallet.activeUsage ?? incoming.activeUsed;
        incoming.activeBalance = incoming.activeQuota - incoming.activeUsed;
        received.push({category, amounts: displayAmounts(incoming, category)});
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
                    raw: emptyAmounts(),
                    amounts: emptyAmounts(),
                    states: new Set(),
                };
                products.set(key, product);
            }
            if (child.group.id !== undefined) product.groupIds.push(child.group.id);
            if (child.maxUsable !== undefined) {
                product.maxUsable = child.maxUsable * explainUnit(category).balanceFactor;
            }
            const groupAmounts = amountsForGroup(child.group, category, now);
            addAmounts(product.raw, groupAmounts);
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
                const slices = allocations.map(allocation => normalizeSlice(allocation, category, now))
                    .sort((a, b) => a.start - b.start || a.allocationId - b.allocationId);
                for (const slice of slices) {
                    if (slice.attribution === "pooled" && slice.end > now) slice.estimatedUsed = estimate;
                }
                const first = allocations[0];
                const grant = summarizeGrant({
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
        product.amounts = displayAmounts(product.raw, product.category);
        product.states = productStates(product, now);
    }

    for (const product of receivedProducts.values()) {
        product.grants.sort((a, b) => a.start - b.start || a.allocationId - b.allocationId);
        product.amounts = displayAmounts(product.raw, product.category);
        product.maxUsable = product.amounts.activeBalance;
        product.states = productStates(product, now);
    }

    return {
        now,
        products: [...products.values()],
        received,
        receivedProducts: [...receivedProducts.values()],
    };
}

export function workspaceInfo(
    owner: ProductAllocation["owner"],
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

export function productWorkspaceInfo(
    product: ProductAllocation,
    projectInfo: Record<string, ProjectInfo | null>,
) {
    if (product.source) {
        if (product.source.type === "project") {
            return {
                ...workspaceInfo(product.owner, projectInfo),
                id: product.source.key,
            };
        }
        return {
            title: product.source.title,
            pi: product.source.pi,
            type: product.source.type,
            id: product.source.key,
        };
    }
    return workspaceInfo(product.owner, projectInfo);
}

export function selectProducts(
    model: Model,
    selection: Selection,
    products: ProductAllocation[] = model.products,
): ProductAllocation[] {
    const range = rangeBounds(selection.dateRange);
    const needle = selection.query?.trim().toLowerCase() ?? "";
    return products.filter(product => {
        if (!selection.personalWorkspaces && product.owner.reference.type === "user") return false;
        const categoryKey = `${product.category.provider}/${product.category.name}`;
        if (selection.categories?.length && !selection.categories.includes(categoryKey)) return false;
        if (selection.providers?.length && !selection.providers.includes(product.category.provider)) return false;
        if (selection.state && selection.state !== "all" && !product.states.has(selection.state)) return false;
        const overlaps = product.grants.some(grant => grant.slices.some(slice =>
            overlapsRange(slice.start, slice.end - 1, range)
        ));
        if (!overlaps) return false;
        const workspace = productWorkspaceInfo(product, selection.projectInfo ?? {});
        const text = [
            workspace.title,
            workspace.pi,
            product.category.name,
            productCategoryDisplayName(product.category),
            getShortProviderTitle(product.category.provider),
        ].join(" ").toLowerCase();
        return needle === "" || text.includes(needle);
    }).sort((a, b) => {
        const aWorkspace = productWorkspaceInfo(a, selection.projectInfo ?? {});
        const bWorkspace = productWorkspaceInfo(b, selection.projectInfo ?? {});
        return aWorkspace.title.localeCompare(bWorkspace.title) || compareProducts(a.category, b.category);
    });
}

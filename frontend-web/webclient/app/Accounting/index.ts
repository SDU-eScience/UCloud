import {IconName} from "@/ui-components/Icon";
import {apiBrowse, apiRetrieve, apiUpdate} from "@/Authentication/DataHook";
import {BulkRequest, PageV2, PaginationRequestV2} from "@/UCloud";
import {getProviderTitle} from "@/Providers/ProviderTitle";
import {ThemeColor} from "@/ui-components/theme";
import {timestampUnixMs} from "@/UtilityFunctions";
import {projectCache} from "@/Project/ProjectSwitcher";
import {groupBy} from "@/Utilities/CollectionUtilities";

export const UCLOUD_PROVIDER = "ucloud";
export const UNABLE_TO_USE_FULL_ALLOC_MESSAGE =
    `You will not be able to use the full amount of your allocated quota due to over-allocation from your grant giver.
Contact your grant giver for more information.
Click to read more.`;

/* @deprecated */
export type ProductArea = ProductType;

export interface ProductCategoryId {
    name: string;
    provider: string;
    title?: string;
}

export function productAreaTitle(area: ProductArea): string {
    switch (area) {
        case "COMPUTE":
            return "Compute";
        case "STORAGE":
            return "Storage";
        case "INGRESS":
            return "Public link";
        case "LICENSE":
            return "Application license";
        case "NETWORK_IP":
            return "Public IP";
    }
}

export interface UpdateAllocationRequestItem {
    id: string;
    balance: number;
    startDate: number;
    endDate?: number | null;
    reason: string;
    dry?: boolean;
}

export function updateAllocation(request: BulkRequest<UpdateAllocationRequestItem>): APICallParameters {
    return apiUpdate(request, "/api/accounting", "allocation");
}

export type WalletOwner = { type: "user"; username: string } | { type: "project"; projectId: string; };

export function productCategoryEquals(a: ProductCategoryId, b: ProductCategoryId): boolean {
    return a.provider === b.provider && a.name === b.name;
}

export type ChargeType = "ABSOLUTE" | "DIFFERENTIAL_QUOTA";
export type ProductPriceUnit =
    "PER_UNIT" |
    "CREDITS_PER_MINUTE" | "CREDITS_PER_HOUR" | "CREDITS_PER_DAY" |
    "UNITS_PER_MINUTE" | "UNITS_PER_HOUR" | "UNITS_PER_DAY";

export type ProductType = "STORAGE" | "COMPUTE" | "INGRESS" | "LICENSE" | "NETWORK_IP";
export type Type = "storage" | "compute" | "ingress" | "license" | "network_ip";

export interface ProductMetadata {
    category: ProductCategoryId;
    freeToUse: boolean;
    productType: ProductType;
    unitOfPrice: ProductPriceUnit;
    chargeType: ChargeType;
    hiddenInGrantApplications: boolean;
}

export interface ProductBase extends ProductMetadata {
    type: Type;
    pricePerUnit: number;
    name: string;
    description: string;
    priority: number;
    version?: number;
    balance?: number | null;
    maxBalance?: number | null;
}

export interface ProductStorage extends ProductBase {
    type: "storage";
    productType: "STORAGE"
}

export interface ProductCompute extends ProductBase {
    type: "compute";
    productType: "COMPUTE";
    cpu?: number | null;
    memoryInGigs?: number | null;
    gpu?: number | null;

    cpuModel?: string | null;
    memoryModel?: string | null;
    gpuModel?: string | null;
}

export interface ProductIngress extends ProductBase {
    type: "ingress";
    productType: "INGRESS"
}

export interface ProductLicense extends ProductBase {
    type: "license";
    productType: "LICENSE";
    tags?: string[];
}

export interface ProductNetworkIP extends ProductBase {
    type: "network_ip";
    productType: "NETWORK_IP";
}

export type Product = ProductStorage | ProductCompute | ProductIngress | ProductNetworkIP | ProductLicense;

export function productTypeToIcon(type: ProductType): IconName {
    switch (type) {
        case "INGRESS":
            return "heroLink"
        case "COMPUTE":
            return "heroCpuChip";
        case "STORAGE":
            return "heroCircleStack";
        case "NETWORK_IP":
            return "heroGlobeEuropeAfrica";
        case "LICENSE":
            return "heroDocumentCheck";
    }
}

export function addThousandSeparators(numberOrString: string | number): string {
    const numberAsString = typeof numberOrString === "string" ? numberOrString : numberOrString.toString(10);
    const dotIndex = numberAsString.indexOf(".");
    const substring = dotIndex === -1 ? numberAsString : numberAsString.substring(0, dotIndex);
    const isNegative = substring.startsWith("-");

    let result = "";
    let i = 0;
    // Note(Jonas): Skip '-' if present.
    const len = isNegative ? substring.length - 1 : substring.length;
    for (const char of substring.slice(isNegative ? 1 : 0)) {
        result += char;
        i += 1;
        if ((i - len) % 3 === 0 && i !== len) {
            result += " ";
        }
    }

    if (dotIndex !== -1) {
        result += ",";
        result += numberAsString.substring(dotIndex + 1);
    }

    if (isNegative) {
        result = "-" + result;
    }

    return result;
}

// Version 2 API
// =====================================================================================================================
const baseContextV2 = "/api/accounting/v2";
const visualizationContextV2 = "/api/accounting/v2/visualization";
const productsContextV2 = "/api/products/v2";

export function browseProductsV2(
    request: PaginationRequestV2 & {
        filterName?: string,
        filterProvider?: string,
        filterProductType?: ProductType,
        filterCategory?: string,

        includeBalance?: boolean,
        includeMaxBalance?: boolean,
    }
): APICallParameters<unknown, PageV2<ProductV2>> {
    return apiBrowse(request, productsContextV2);
}

export interface AccountingUnit {
    name: string;
    namePlural: string;
    floatingPoint: boolean;
    displayFrequencySuffix: boolean;
}

export type AccountingFrequency = "ONCE" | "PERIODIC_MINUTE" | "PERIODIC_HOUR" | "PERIODIC_DAY";

export function frequencyToMillis(frequency: AccountingFrequency): number {
    switch (frequency) {
        case "ONCE":
            return 1;
        case "PERIODIC_MINUTE":
            return 1000 * 60;
        case "PERIODIC_HOUR":
            return 1000 * 60 * 60;
        case "PERIODIC_DAY":
            return 1000 * 60 * 60 * 24;
    }
}

export function frequencyToSuffix(frequency: AccountingFrequency, plural: boolean): string | null {
    const pluralize = (text: string) => plural ? text + "s" : text;
    switch (frequency) {
        case "ONCE":
            return null;
        case "PERIODIC_MINUTE":
            return pluralize("minute");
        case "PERIODIC_HOUR":
            return pluralize("hour");
        case "PERIODIC_DAY":
            return pluralize("day");
    }
}

export interface ProductCategoryV2 {
    name: string;
    provider: string;
    productType: ProductType;
    accountingUnit: AccountingUnit;
    accountingFrequency: AccountingFrequency;
    freeToUse: boolean;
}

export function categoryComparator(a: ProductCategoryV2, b: ProductCategoryV2): number {
    function typeToOrdinal(type: ProductType): number {
        switch (type) {
            case "COMPUTE":
                return 0;
            case "STORAGE":
                return 1;
            case "NETWORK_IP":
                return 2;
            case "INGRESS":
                return 3;
            case "LICENSE":
                return 4;
        }
    }

    const aProvider = getProviderTitle(a.provider);
    const bProvider = getProviderTitle(b.provider);
    const providerCmp = aProvider.localeCompare(bProvider);
    if (providerCmp !== 0) return providerCmp;

    const aType = typeToOrdinal(a.productType);
    const bType = typeToOrdinal(b.productType);
    if (aType < bType) return -1;
    if (aType > bType) return 1;

    const aName = a.name;
    const bName = b.name;
    return aName.localeCompare(bName);
}

export type ProductV2 =
    ProductV2Storage
    | ProductV2Compute
    | ProductV2Ingress
    | ProductV2License
    | ProductV2NetworkIP
    ;

interface ProductV2Base {
    category: ProductCategoryV2;
    name: string;
    description: string;
    productType: ProductType;
    price: number;
    hiddenInGrantApplications: boolean;
}

export interface ProductV2Storage extends ProductV2Base {
    type: "storage";
}

export interface ProductV2Compute extends ProductV2Base {
    type: "compute";
    cpu?: number | null;
    memoryInGigs?: number | null;
    gpu?: number | null;
    cpuModel?: string | null;
    memoryModel?: string | null;
    gpuModel?: string | null;
}

export interface ProductV2Ingress extends ProductV2Base {
    type: "ingress";
}

export interface ProductV2License extends ProductV2Base {
    type: "license";
}

export interface ProductV2NetworkIP extends ProductV2Base {
    type: "network_ip";
}

const hardcodedProductCategoryDescriptions: Record<string, Record<string, string>> = {
    "k8": {
        "cpu": `A compute node used for demonstration purposes. The node probably doesn't contain a lot of resources.`,
        "storage": `Storage for the compute nodes. If you are applying for k8 based compute, then you must also apply for storage.`,
        "public-ip": "A generic IP address used for demonstration purposes.",
        "license": "A generic license used for demonstration purposes."
    },

    "slurm": {
        "cpu": `A compute node used for demonstration purposes. The node probably doesn't contain a lot of resources.`,
        "storage": `Storage for the compute nodes. If you are applying for slurm based compute, then you must also apply for storage.`,
    },

    "ucloud": {
        "cephfs": `The storage system for DeiC Interactive HPC at SDU. If you are applying for compute from the same location then you must also apply for storage.`,
        "u1-storage": `The storage system for DeiC Interactive HPC at SDU. If you are applying for compute from the same location then you must also apply for storage.`,
        "public-ip": `A publicly accessible IP address. You should apply for these only if you are running an application which explicitly requires this.`,
        "u1-fat": `2x Intel(R) Xeon(R) Gold 6130 CPU@2.10 GHz, 32 virtual cores/CPU, and 768 GB of memory.`,
        "u1-standard": `The u1-standard machines are equipped with 2x Intel(R) Xeon(R) Gold 6130 CPU@2.10 GHz, 32 virtual cores/CPU, and 384 GB of memory.`,
        "u2-gpu": `96 vCPU, 2TB of memory and 8x NVIDIA Tesla A100 GPUs Accelerators 40GB (PCIe).`,
        "u1-gpu": `80 vCPU, 182 GB of memory and 4x NVIDIA Tesla V100 SXM2 Volta GPUs Accelerators 32GB (NVLink).`,
    },

    "hippo": {
        "hippo-ess": `Hippo storage uses an IBM Elastic Storage System (ESS). If you applying for Hippo based compute then you must also apply for storage.`,
        "hippo-fat": `Hippo machines are high memory machines. Each machine has 2x AMD EPYC 64-Core and between 1TB and 4TB of memory.`
    },

    "aau": {
        "uc-t4": `Virtual machine with NVIDIA T4 GPUs.`,
        "uc-a10": `Virtual machine with NVIDIA A10 GPUs.`,
        "uc-general": `Virtual machine with no GPUs.`,
        "uc-a40": `Virtual machine with NVIDIA A40 GPUs.`,
        "uc-a100": `Virtual machine with NVIDIA A100 GPUs.`,
    },

    "sophia": {
        "sophia-storage": ``,
        "sophia-slim": ``,
    },

    "lumi-sdu": {
        "lumi-cpu": ``,
        "lumi-gpu": ``,
        "lumi-parallel": ``,
    },
}

function removeSuffix(text: string, suffix: string): string {
    if (text.endsWith(suffix)) return text.substring(0, text.length - suffix.length);
    return text;
}

export function guesstimateProductCategoryDescription(
    category: string,
    provider: string
): string {
    const normalizedCategory = removeSuffix(category, "-h");
    return hardcodedProductCategoryDescriptions[provider]?.[normalizedCategory] ?? "";
}

type BalanceAndCategory = { balance: number; category: ProductCategoryV2; }
type CombinedBalance = { productType: ProductType, normalizedBalance: number, unit: string };

export function combineBalances(
    balances: BalanceAndCategory[]
): CombinedBalance[] {
    const result: ReturnType<typeof combineBalances> = [];

    // This function combines many balances from (potentially) different categories and combines them into a unified
    // view. This is useful when we want to give a unified view of how many resources you have available.
    for (const it of balances) {
        const unit = explainUnit(it.category);
        const normalizedBalance = unit.balanceFactor * it.balance;

        const existing = result.find(it => it.unit === unit.name && it.productType === unit.productType);
        if (existing) {
            existing.normalizedBalance += normalizedBalance;
        } else {
            result.push({unit: unit.name, normalizedBalance, productType: it.category.productType});
        }
    }

    return result;
}

export interface FrontendAccountingUnit {
    name: string;
    priceFactor: number;
    invBalanceFactor: number;
    balanceFactor: number;
    productType: ProductType;
    frequencyFactor: number;
    desiredFrequency: AccountingFrequency;
}

export function explainUnit(category: ProductCategoryV2): FrontendAccountingUnit {
    return explainUnitEx(category.accountingUnit, category.accountingFrequency, category.productType);
}

export function explainUnitEx(
    unit: AccountingUnit,
    frequency: AccountingFrequency,
    productType: ProductType | null,
): FrontendAccountingUnit {
    let priceFactor = 1;
    let frequencyFactor = 1;
    let balanceFactor = 1;
    let unitName = unit.namePlural;
    let suffix = "";

    let estimatedProductType: ProductType;
    if (productType !== null) {
        estimatedProductType = productType;
    } else {
        if (StandardStorageUnits.indexOf(unit.name) !== -1) {
            estimatedProductType = "STORAGE";
        } else if (StandardStorageUnitsSi.indexOf(unit.name) !== -1) {
            estimatedProductType = "STORAGE";
        } else if (unit.name == "Core") {
            estimatedProductType = "COMPUTE";
        } else if (unit.name == "GPU") {
            estimatedProductType = "COMPUTE";
        } else if (unit.name == "IP") {
            estimatedProductType = "NETWORK_IP";
        } else if (unit.name == "Link") {
            estimatedProductType = "INGRESS";
        } else if (unit.name == "License") {
            estimatedProductType = "LICENSE";
        } else {
            estimatedProductType = "COMPUTE";
        }
    }

    let desiredFrequency: AccountingFrequency = frequency;
    if (frequency !== "ONCE") {
        switch (estimatedProductType) {
            case "COMPUTE":
                desiredFrequency = "PERIODIC_HOUR";
                break;

            default:
                desiredFrequency = "PERIODIC_DAY";
                break
        }

        frequencyFactor = frequencyToMillis(frequency) / frequencyToMillis(desiredFrequency);
        priceFactor = frequencyFactor;
        balanceFactor = priceFactor;

        if (unit.displayFrequencySuffix) {
            suffix = "-" + frequencyToSuffix(desiredFrequency, true);
            unitName = unit.name;
        } else {
            balanceFactor = 1;
        }
    }

    if (unit.floatingPoint) {
        priceFactor *= 1 / 1000000;
        balanceFactor *= 1 / 1000000;
    }

    return {
        name: unitName + suffix,
        priceFactor,
        invBalanceFactor: 1 / balanceFactor,
        balanceFactor,
        productType: estimatedProductType,
        frequencyFactor,
        desiredFrequency: desiredFrequency,
    };
}

export function priceToString(product: ProductV2, numberOfUnits: number, durationInMinutes?: number, opts?: {
    showSuffix: boolean
}): string {
    const unit = explainUnit(product.category);
    const pricePerUnitPerFrequency = product.price * (1 / unit.frequencyFactor);
    const durationInMinutesOrDefault = durationInMinutes ?? frequencyToMillis(unit.desiredFrequency) / frequencyToMillis("PERIODIC_MINUTE");
    let normalizedDuration = durationInMinutesOrDefault * unit.frequencyFactor;
    if (unit.desiredFrequency === "ONCE") {
        normalizedDuration = 1;
    }

    const totalPrice = normalizedDuration * pricePerUnitPerFrequency * numberOfUnits * unit.balanceFactor;

    if (totalPrice === 0 || product.category.freeToUse) return "Free";
    let withoutSuffix = balanceToStringFromUnit(product.category.productType, unit.name, totalPrice);
    if (unit.desiredFrequency !== "ONCE" && opts?.showSuffix !== false) {
        return withoutSuffix + "/" + frequencyToSuffix(unit.desiredFrequency, false);
    } else {
        if (totalPrice === 1 && ProbablyCurrencies.indexOf(unit.name) === -1 && unit.desiredFrequency === "ONCE") {
            if (product.productType === "STORAGE") {
                return "Quota based (" + unit.name + ")";
            } else {
                return "Quota based";
            }
        }
        return withoutSuffix;
    }
}

const StandardStorageUnitsSi = ["KB", "MB", "GB", "TB", "PB", "EB"];
const StandardStorageUnits = ["KiB", "MiB", "GiB", "TiB", "PiB", "EiB"];
const DefaultUnits = ["K", "M", "B", "T"];
const ProbablyCurrencies = ["DKK", "kr", "EUR", "€", "USD", "$"];

interface UsageAndQuotaRaw {
    usage: number;
    quota: number;
    unit: string;
    maxUsable: number;
    retiredAmount: number;
    retiredAmountStillCounts: boolean;
    type: ProductType;
    ownedByPersonalProviderProject: boolean;
}

export interface UsageAndQuotaDisplay {
    usageAndQuotaPercent: string;
    usageAndQuota: string;
    onlyUsage: string;
    onlyQuota: string;
    percentageUsed: number; // 0-100
    maxUsablePercentage: number; // 0-100
    maxUsableBalance: string;
    displayOverallocationWarning: boolean;
    currentBalance: string;
}

export class UsageAndQuota {
    raw: UsageAndQuotaRaw;
    type: ProductType;
    display: UsageAndQuotaDisplay;

    constructor(input: UsageAndQuotaRaw) {
        this.raw = input;
        this.type = input.type;
        this.updateDisplay();
    }

    // Must be called if raw is modified directly
    updateDisplay() {
        const uqRaw = this.raw;

        if (uqRaw.ownedByPersonalProviderProject) {
            this.display = {
                usageAndQuota: "-",
                onlyUsage: "-",
                onlyQuota: "-",
                percentageUsed: 0,
                maxUsablePercentage: 100,
                displayOverallocationWarning: false,
                usageAndQuotaPercent: "-",
                maxUsableBalance: "-",
                currentBalance: "-"
            };
            return;
        }

        let usage: number;
        if (uqRaw.retiredAmountStillCounts) {
            usage = uqRaw.usage;
        } else {
            usage = uqRaw.usage - uqRaw.retiredAmount;
        }
        usage = Math.max(0, usage);

        const maxUsablePercentage = uqRaw.quota === 0 ? 100 : ((uqRaw.maxUsable + usage) / uqRaw.quota) * 100;
        const percentageUsed = uqRaw.quota === 0 ? 0 : (usage / uqRaw.quota) * 100;
        const displayOverallocationWarning = showWarning(uqRaw.quota, uqRaw.maxUsable, usage);
        const maxUsableBalance = balanceToStringFromUnit(uqRaw.type, uqRaw.unit, uqRaw.maxUsable, {precision: 2});
        const currentBalance = balanceToStringFromUnit(uqRaw.type, uqRaw.unit, uqRaw.quota - usage, {precision: 2});

        const usageAndQuota = formatUsageAndQuota(usage, uqRaw.quota, uqRaw.type === "STORAGE", uqRaw.unit, {precision: 2});

        let usageAndQuotaPercent = usageAndQuota;
        if (uqRaw.quota !== 0) {
            usageAndQuotaPercent += " (";
            usageAndQuotaPercent += Math.round((usage / uqRaw.quota) * 100);
            usageAndQuotaPercent += "%)";
        }

        const onlyUsage = balanceToStringFromUnit(uqRaw.type, uqRaw.unit, usage, {precision: 2});
        const onlyQuota = balanceToStringFromUnit(uqRaw.type, uqRaw.unit, uqRaw.quota, {precision: 2});

        this.display = {
            usageAndQuota,
            onlyUsage,
            onlyQuota,
            percentageUsed,
            maxUsablePercentage,
            displayOverallocationWarning,
            usageAndQuotaPercent,
            maxUsableBalance,
            currentBalance
        };
    }
}

function showWarning(quota: number, maxUsable: number, usage: number): boolean {
    // Have we used everything? In that case, we don't want to show an overallocation warning.
    if (maxUsable + usage >= quota) return false;

    // If we don't have a quota, then don't show a warning.
    if (quota === 0) return false;

    // Here we try to remove our usages from the quota to determine what our effective quota is.
    const expectedEffectiveQuota = quota;
    const actualEffectiveQuota = maxUsable + usage;

    if (actualEffectiveQuota === expectedEffectiveQuota) return false;

    const showBecauseOfMismatch = (actualEffectiveQuota / expectedEffectiveQuota) < 0.95;
    if (showBecauseOfMismatch) return true;

    // If we are almost out of resources and we don't have matching effective quotas then warn once we close to the limit
    return (usage / actualEffectiveQuota) >= 0.9;
}

export interface AllocationNote {
    rowShouldBeGreyedOut: boolean;
    icon: IconName;
    iconColor: ThemeColor;
    text: string;
}

export interface AllocationDisplayTreeRecipientOwner {
    title: string;
    primaryUsername: string;
    reference: WalletOwner;
}

export const ProductTypesByPriority: ProductType[] = [
    "COMPUTE",
    "STORAGE",
    "NETWORK_IP",
    "INGRESS",
    "LICENSE",
];

export interface AllocationDisplayWallet {
    category: ProductCategoryV2;
    usageAndQuota: UsageAndQuota;
    totalAllocated: number;

    allocations: {
        id: number;
        grantedIn?: number;
        note?: AllocationNote;

        start: number;
        end: number;

        raw: {
            quota: number;
            retiredAmount: number;
            shouldShowRetiredAmount: boolean
        };
        display: {
            quota: string;
        }
    }[];
}

export interface AllocationDisplayTree {
    yourAllocations: {
        [P in ProductType]?: {
            usageAndQuota: UsageAndQuota[];
            wallets: AllocationDisplayWallet[];
        }
    };

    subAllocations: {
        recipients: {
            owner: AllocationDisplayTreeRecipientOwner;

            usageAndQuota: UsageAndQuota[];

            groups: {
                category: ProductCategoryV2;
                usageAndQuota: UsageAndQuota;
                totalGranted: number;

                allocations: {
                    allocationId: number;
                    quota: number;
                    note?: AllocationNote;
                    isEditing: boolean;
                    grantedIn?: number;

                    start: number;
                    end: number;
                }[];
            }[];
        }[];
    };
}

// Fri Jan 01 2100 01:00:00 GMT+0100 (Central European Standard Time)
export const NO_EXPIRATION_FALLBACK = 4102444800353;

export interface Period {
    start: number;
    end: number;
}

export function allocationToPeriod(alloc: Allocation): Period {
    return {start: alloc.startDate, end: alloc.endDate ?? NO_EXPIRATION_FALLBACK};
}

export function normalizePeriodForComparison(period: Period): Period {
    return {start: Math.floor(period.start / 1000) * 1000, end: Math.floor(period.end / 1000) * 1000};
}

export function allocationIsActive(
    alloc: Allocation,
    now: number,
): boolean {
    return periodsOverlap(allocationToPeriod(alloc), {start: now, end: now});
}

function allocationNote(
    alloc: Allocation
): AllocationNote | undefined {
    const now = timestampUnixMs();

    // NOTE(Dan): We color code and potentially grey out rows when the end-user should be aware of something
    // on the allocation.
    //
    // - If a row should be greyed out, then it means that the row is not currently active (and is not counted
    //   in summaries).
    // - If the accompanying row has a red calendar, then the note is about something which has happened.
    // - If the accompanying row has a blue calendar, then the note is about something which will happen.
    const icon: IconName = "heroCalendarDays";
    const colorInThePast: ThemeColor = "errorMain";
    const colorForTheFuture: ThemeColor = "primaryMain";

    const allocPeriod = normalizePeriodForComparison(allocationToPeriod(alloc));
    if (now > allocPeriod.end) {
        return {
            rowShouldBeGreyedOut: true,
            icon,
            iconColor: colorInThePast,
            text: `Already expired (${utcDate(allocPeriod.end)})`,
        };
    }

    if (allocPeriod.start > now) {
        return {
            rowShouldBeGreyedOut: true,
            icon,
            iconColor: colorForTheFuture,
            text: `Starts in the future (${utcDate(allocPeriod.start)})`,
        };
    }

    return undefined;
}

export function buildAllocationDisplayTree(allWallets: WalletV2[]): AllocationDisplayTree {
    // NOTE(Dan): This function assumes that allWallets are owned by the same owner.

    // NOTE(Dan): Detect Core2 server by looking for Core2 only fields.
    const isCore2Response = allWallets.some(
        w => w.allocationGroups.some(
            ag => ag.group.allocations.some(
                a => a.retiredQuota !== undefined
            )
        )
    );


    const relevantWallets = allWallets.filter(it => !it.paysFor.freeToUse);
    const tree: AllocationDisplayTree = {
        yourAllocations: {},
        subAllocations: {
            recipients: [],
        }
    };

    let ownedByPersonalProviderProject = false;
    if (allWallets.length > 0) {
        const owner = allWallets[0].owner;
        if (owner.type === "project") {
            const projectId = owner.projectId;
            let items = projectCache.retrieveFromCacheOnly("")?.items;
            const project = (items ?? []).find(it => it.id === projectId);
            if (project) {
                ownedByPersonalProviderProject = project.status.personalProviderProjectFor != null;
            }
        }
    }

    const yourAllocations = tree.yourAllocations;
    {
        const walletsByType = groupBy(relevantWallets, it => it.paysFor.productType);
        for (const [type, wallets] of Object.entries(walletsByType)) {
            yourAllocations[type as ProductType] = {
                usageAndQuota: [],
                wallets: []
            };
            const entry = yourAllocations[type as ProductType]!;

            const quotaBalances = wallets.flatMap(wallet =>
                ({balance: wallet.quota, category: wallet.paysFor})
            );
            const usageBalances = wallets.flatMap(wallet =>
                ({balance: wallet.totalUsage, category: wallet.paysFor})
            );

            const maxUsableBalances = wallets.map(wallet =>
                ({balance: wallet.maxUsable, category: wallet.paysFor})
            );

            const retired = wallets.map(wallet => {
                let totalRetired = 0
                wallet.allocationGroups.forEach(group =>
                    group?.group?.allocations.forEach(alloc =>
                        totalRetired += alloc.retiredUsage ?? 0
                    )
                )
                return ({balance: totalRetired, category: wallet.paysFor})
            })

            const combinedQuotas = combineBalances(quotaBalances);
            const combinedUsage = combineBalances(usageBalances);
            const combineMaxUsable = combineBalances(maxUsableBalances);
            const combineRetire = combineBalances(retired)

            const combineShouldUseRetired = wallets.map(wallet =>
                wallet.paysFor.accountingFrequency === "ONCE"
            );

            for (let i = 0; i < combinedQuotas.length; i++) {
                const usage = combinedUsage[i];
                const quota = combinedQuotas[i];
                const maxUsable = combineMaxUsable[i];
                const retired = combineRetire[i];
                const shouldUseRetired = combineShouldUseRetired[i];

                entry.usageAndQuota.push(new UsageAndQuota({
                    usage: usage.normalizedBalance,
                    quota: quota.normalizedBalance,
                    unit: usage.unit,
                    maxUsable: maxUsable.normalizedBalance,
                    retiredAmount: retired.normalizedBalance,
                    retiredAmountStillCounts: shouldUseRetired,
                    type: type as ProductType,
                    ownedByPersonalProviderProject,
                }));
            }

            for (const wallet of wallets) {
                const usage = combineBalances([{balance: wallet.totalUsage, category: wallet.paysFor}]);
                const quota = combineBalances([{balance: wallet.quota, category: wallet.paysFor}]);
                const maxUsable = combineBalances([{
                    balance: wallet.maxUsable,
                    category: wallet.paysFor
                }]);
                let totalRetired = 0;
                wallet.allocationGroups.forEach(({group}) =>
                    group.allocations.forEach(alloc => (
                        totalRetired += alloc.retiredUsage ?? 0
                    ))
                )

                const retiredAmount = combineBalances([{balance: totalRetired, category: wallet.paysFor}])
                const shouldUseRetired = wallet.paysFor.accountingFrequency === "ONCE";
                entry.wallets.push({
                    category: wallet.paysFor,

                    usageAndQuota: new UsageAndQuota({
                        usage: usage?.[0]?.normalizedBalance ?? 0,
                        quota: quota?.[0]?.normalizedBalance ?? 0,
                        unit: usage?.[0]?.unit ?? "",
                        maxUsable: maxUsable?.[0]?.normalizedBalance ?? 0,
                        retiredAmount: retiredAmount?.[0]?.normalizedBalance ?? 0,
                        retiredAmountStillCounts: shouldUseRetired,
                        type: wallet.paysFor.productType,
                        ownedByPersonalProviderProject,
                    }),

                    totalAllocated: wallet.totalAllocated,

                    allocations: wallet.allocationGroups.flatMap(({group}) => {
                        const shouldShowRetiredAmount = wallet.paysFor.accountingFrequency !== "ONCE";

                        return group.allocations.map(alloc => {
                            const note = allocationNote(alloc);

                            let quotaString = "";
                            if (shouldShowRetiredAmount && note !== undefined) {
                                quotaString += balanceToString(
                                    wallet.paysFor,
                                    alloc.retiredUsage ?? 0,
                                    {precision: 2}
                                );
                                quotaString += " / ";
                                quotaString += balanceToString(wallet.paysFor, alloc.quota, {precision: 2});
                            } else {
                                quotaString += balanceToString(wallet.paysFor, alloc.quota, {precision: 2});
                            }

                            return ({
                                id: alloc.id,
                                grantedIn: alloc.grantedIn ?? undefined,
                                note,
                                start: alloc.startDate,
                                end: alloc.endDate ?? NO_EXPIRATION_FALLBACK,
                                raw: {
                                    quota: alloc.quota,
                                    retiredAmount: alloc.retiredUsage ?? 0,
                                    shouldShowRetiredAmount,
                                },
                                display: {
                                    quota: quotaString,
                                },
                            });
                        });
                    }),
                });
            }
        }
    }

    // Start building the sub-allocations UI
    const subAllocations = tree.subAllocations;

    const filteredSubAllocations: {
        wallet: WalletV2,
        childGroup: AllocationGroupWithChild
    }[] = [];

    for (const wallet of relevantWallets) {
        if (wallet.paysFor.freeToUse) continue;
        const children = wallet.children ?? [];
        for (const childGroup of children) {
            filteredSubAllocations.push({wallet, childGroup});
        }
    }

    {
        for (const {childGroup, wallet} of filteredSubAllocations) {
            let allocOwner: WalletOwner;
            if (childGroup.child.projectId) {
                allocOwner = {type: "project", projectId: childGroup.child.projectId};
            } else {
                allocOwner = {type: "user", username: childGroup.child.projectTitle};
            }

            let recipient = subAllocations.recipients
                .find(it => walletOwnerEquals(it.owner.reference, allocOwner));
            if (!recipient) {
                recipient = {
                    owner: {
                        reference: allocOwner,
                        primaryUsername: childGroup.child.pi,
                        title: childGroup.child.projectTitle,
                    },
                    groups: [],
                    usageAndQuota: [],
                };

                subAllocations.recipients.push(recipient);
            }

            const shouldUseRetired = wallet.paysFor.accountingFrequency === "ONCE";

            let combinedQuota = 0;
            childGroup.group.allocations.forEach(alloc => {
                if (allocationIsActive(alloc, new Date().getTime())) {
                    combinedQuota += alloc.quota;
                }
            });

            const maxUsable = combineBalances([{
                balance: wallet.maxUsable,
                category: wallet.paysFor
            }]);
            const combinedRetired = childGroup.group.allocations.reduce((acc, val) => acc + (val.retiredUsage ?? 0), 0);
            // Need to have total usage in case retired should be included in final result
            let combinedUsage = childGroup.group.usage;
            if (!shouldUseRetired) {
                combinedUsage += combinedRetired;
            }

            if (isCore2Response) {
                combinedQuota = childGroup.group.quota!;
                combinedUsage = childGroup.group.usage;
            }

            const localUsage = combineBalances([{balance: childGroup.group.usage, category: wallet.paysFor}]);
            const usage = combineBalances([{balance: combinedUsage, category: wallet.paysFor}]);
            const quota = combineBalances([{balance: combinedQuota, category: wallet.paysFor}]);
            const retiredAmount = combineBalances([{balance: combinedRetired, category: wallet.paysFor}]);
            let totalAllocated = 0;
            const newGroup: AllocationDisplayTree["subAllocations"]["recipients"][0]["groups"][0] = {
                category: wallet.paysFor,
                usageAndQuota: new UsageAndQuota({
                    usage: usage?.[0]?.normalizedBalance ?? 0,
                    quota: quota?.[0]?.normalizedBalance ?? 0,
                    maxUsable: maxUsable[0]?.normalizedBalance ?? 0,
                    unit: usage?.[0]?.unit ?? "",
                    retiredAmount: retiredAmount?.[0]?.normalizedBalance ?? 0,
                    retiredAmountStillCounts: shouldUseRetired,
                    type: wallet.paysFor.productType,
                    ownedByPersonalProviderProject,
                }),
                allocations: [],
                totalGranted: 0
            };

            const uq = newGroup.usageAndQuota;
            uq.raw.maxUsable = uq.raw.quota - (localUsage[0]?.normalizedBalance ?? 0);

            for (const alloc of childGroup.group.allocations.reverse()) {
                if (allocationIsActive(alloc, new Date().getTime())) {
                    totalAllocated += alloc.quota;
                }
                newGroup.allocations.push({
                    allocationId: alloc.id,
                    quota: alloc.quota,
                    note: allocationNote(alloc),
                    isEditing: false,
                    start: alloc.startDate,
                    end: alloc.endDate,
                    grantedIn: alloc.grantedIn ?? undefined,
                });
            }
            newGroup.totalGranted = totalAllocated;
            recipient.groups.push(newGroup);
        }

        for (const recipient of subAllocations.recipients) {
            const uqBuilder: UsageAndQuota[] = [];
            for (const group of recipient.groups) {
                const existing = uqBuilder.find(it =>
                    it.type === group.category.productType && it.raw.unit === group.usageAndQuota.raw.unit);

                if (existing) {
                    existing.raw.usage += group.usageAndQuota.raw.usage;
                    existing.raw.quota += group.usageAndQuota.raw.quota;

                    existing.updateDisplay();
                } else {
                    uqBuilder.push(new UsageAndQuota({
                        type: group.category.productType,
                        usage: group.usageAndQuota.raw.usage,
                        quota: group.usageAndQuota.raw.quota,
                        unit: group.usageAndQuota.raw.unit,
                        maxUsable: group.usageAndQuota.raw.maxUsable,
                        retiredAmount: group.usageAndQuota.raw.retiredAmount,
                        retiredAmountStillCounts: group.usageAndQuota.raw.retiredAmountStillCounts,
                        ownedByPersonalProviderProject,
                    }));
                }
            }

            recipient.groups.sort((a, b) => {
                const providerCmp = a.category.provider.localeCompare(b.category.provider);
                if (providerCmp !== 0) return providerCmp;
                const categoryCmp = a.category.name.localeCompare(b.category.name);
                if (categoryCmp !== 0) return categoryCmp;
                return 0;
            });

            for (const b of uqBuilder) {
                // NOTE(Dan): We do not know how much is usable locally since this depends on other allocations which
                // might not be coming from us. The backend doesn't tell us this since it would leak information we do
                // not have access to.
                //
                // As a result, we set the maxUsable to be equivalent to the remaining balance. That is, we tell the UI
                // that we can use the entire quota (even if we cannot).
                b.raw.maxUsable = b.raw.quota - b.raw.usage;

                b.updateDisplay();
            }

            recipient.usageAndQuota = uqBuilder;
        }
    }

    subAllocations.recipients.sort((a, b) => {
        return a.owner.title.localeCompare(b.owner.title);
    });

    if (isCore2Response) {
        // TODO(Dan): Clean up this code later when we are getting ready to make the switch. I am currently trying to
        //   minimize the number of places these changes are visible.

        const updateUsageAndQuota = (uq: UsageAndQuota) => {
            uq.raw.retiredAmount = 0;
            uq.raw.retiredAmountStillCounts = false;
            uq.updateDisplay();
        };

        for (const subtree of Object.values(tree.yourAllocations)) {
            for (const uq of subtree.usageAndQuota) {
                updateUsageAndQuota(uq);
            }

            for (const w of subtree.wallets) {
                updateUsageAndQuota(w.usageAndQuota);
            }
        }

        for (const subtree of tree.subAllocations.recipients) {
            for (const uq of subtree.usageAndQuota) {
                updateUsageAndQuota(uq);
            }

            for (const g of subtree.groups) {
                updateUsageAndQuota(g.usageAndQuota);
            }
        }
    }

    return tree;
}

export function explainWallet(wallet: WalletV2): AllocationDisplayWallet | null {
    const tree = Object.values(buildAllocationDisplayTree([wallet]).yourAllocations);
    if (tree.length === 0) return null;
    const wallets = tree[0].wallets;
    if (wallets.length === 0) return null;
    return wallets[0];
}

export function balanceToString(
    category: ProductCategoryV2,
    balance: number,
    opts?: { precision?: number, removeUnitIfPossible?: boolean }
): string {
    const unit = explainUnit(category);
    const normalizedBalance = balance * unit.balanceFactor;
    return balanceToStringFromUnit(unit.productType, unit.name, normalizedBalance, opts)
}

export function truncateValues(
    normalizedBalances: number[],
    isStorage: boolean,
    unit: string,
    opts?: { removeUnitIfPossible?: boolean, referenceBalance?: number }
): { truncated: number[]; attachedSuffix: string | null, unitToDisplay: string, canRemoveUnit: boolean } {
    let canRemoveUnit = opts?.removeUnitIfPossible ?? false;
    let balanceToDisplay = opts?.referenceBalance ?? Math.max(...normalizedBalances);

    let truncated = [...normalizedBalances];
    let unitToDisplay = unit;
    let attachedSuffix: string | null = null;

    const storageUnitSiIdx = StandardStorageUnitsSi.indexOf(unit);
    const storageUnitIdx = StandardStorageUnits.indexOf(unit);
    if (isStorage && (storageUnitSiIdx !== -1 || storageUnitIdx !== -1)) {
        canRemoveUnit = false;
        const base = storageUnitIdx !== -1 ? 1024 : 1000;
        const array = storageUnitIdx !== -1 ? StandardStorageUnits : StandardStorageUnitsSi;
        let idx = storageUnitIdx !== -1 ? storageUnitIdx : storageUnitSiIdx;

        while (balanceToDisplay > base && idx < array.length - 1) {
            balanceToDisplay /= base;
            for (let i = 0; i < truncated.length; i++) {
                truncated[i] /= base;
            }
            idx++;
        }

        unitToDisplay = array[idx];
    } else {
        let threshold = 1000;
        if (ProbablyCurrencies.indexOf(unitToDisplay) !== -1) threshold = 1000000;

        if (balanceToDisplay >= threshold) {
            let idx = -1;
            while (balanceToDisplay >= 1000 && idx < DefaultUnits.length - 1) {
                balanceToDisplay /= 1000;
                for (let i = 0; i < truncated.length; i++) {
                    truncated[i] /= 1000;
                }
                idx++;
            }

            attachedSuffix = DefaultUnits[idx];
        }
    }

    return {truncated, attachedSuffix, unitToDisplay, canRemoveUnit};
}

export function formatUsage(usage: number, productType: ProductType | null, unit: string) {
    const isStorage = productType === "STORAGE" || StandardStorageUnitsSi.indexOf(unit) !== -1 ||
        StandardStorageUnits.indexOf(unit) !== -1;

    const {
        truncated,
        attachedSuffix,
        unitToDisplay,
        canRemoveUnit
    } = truncateValues([usage], isStorage, unit, {});

    const [truncatedUsage] = truncated;

    let result = fmt(truncatedUsage);
    result += `${attachedSuffix} `;
    result += `${unitToDisplay}`;

    return result;
}

export function formatUsageAndQuota(usage: number, quota: number, isStorage: boolean, unit: string, opts?: {
    precision?: number,
    removeUnitIfPossible?: boolean
}): string {
    const {
        truncated,
        attachedSuffix,
        unitToDisplay,
        canRemoveUnit
    } = truncateValues([usage, quota], isStorage, unit, opts);
    const [truncatedUsage, truncatedQuota] = truncated;

    let usageAndQuota = fmt(truncatedUsage, opts?.precision);
    usageAndQuota += "/";
    usageAndQuota += fmt(truncatedQuota, opts?.precision);
    usageAndQuota += "  ";

    if (attachedSuffix) {
        usageAndQuota += `${attachedSuffix} `;
    }

    if (!canRemoveUnit) {
        usageAndQuota += `${unitToDisplay}`;
    }

    return usageAndQuota;
}

function fmt(val: number, precision: number = 2): string {
    return addThousandSeparators(removeSuffix(val.toFixed(precision), ".00"))
}

export function balanceToStringFromUnit(
    productType: ProductType | null,
    unit: string,
    normalizedBalance: number,
    opts?: { precision?: number, removeUnitIfPossible?: boolean, referenceBalance?: number }
): string {
    const isStorage = productType === "STORAGE" || StandardStorageUnitsSi.indexOf(unit) !== -1 ||
        StandardStorageUnits.indexOf(unit) !== -1;

    const {
        attachedSuffix,
        truncated,
        unitToDisplay,
        canRemoveUnit
    } = truncateValues([normalizedBalance], isStorage, unit, opts);

    const [balanceToDisplay] = truncated;

    let builder = "";
    builder += fmt(balanceToDisplay, opts?.precision);
    if (attachedSuffix) builder += attachedSuffix;
    if (!canRemoveUnit) {
        builder += " ";
        builder += unitToDisplay;
    }
    return builder;
}

export interface WalletV2 {
    owner: WalletOwner;
    paysFor: ProductCategoryV2;
    allocationGroups: AllocationGroupWithParent[];
    children?: AllocationGroupWithChild[] | null;

    totalUsage: number;
    localUsage: number;
    maxUsable: number;
    quota: number;
    totalAllocated: number;
}

export interface AllocationGroupWithParent {
    parent?: ParentOrChildWallet | null;
    group: AllocationGroup;
}

export interface AllocationGroupWithChild {
    child: ParentOrChildWallet;
    group: AllocationGroup;
}

export interface ParentOrChildWallet {
    projectId?: string;
    projectTitle: string;
    pi: string;
}

export interface AllocationGroup {
    usage: number;
    allocations: Allocation[];

    // Core2 only properties:
    quota?: number;
}

export interface Allocation {
    id: number;
    startDate: number;
    endDate: number;
    quota: number;
    grantedIn?: number | null;
    retiredUsage?: number | null;

    // Core2 only properties:
    activated?: boolean;
    retired?: boolean;
    retiredQuota?: number;
}

export function browseWalletsV2(
    request: PaginationRequestV2 & {
        filterType?: ProductType;
        includeChildren?: boolean;
    }
): APICallParameters<unknown, PageV2<WalletV2>> {
    return apiBrowse(request, baseContextV2, "wallets");
}

export interface RootAllocateRequestItem {
    category: ProductCategoryId;
    quota: number;
    start: number;
    end: number;
}

export function rootAllocate(request: BulkRequest<RootAllocateRequestItem>): APICallParameters {
    return apiUpdate(request, baseContextV2, "rootAllocate");
}

export function updateAllocationV2(request: BulkRequest<{
    allocationId: number,
    newQuota?: number | null,
    newStart?: number | null,
    newEnd?: number | null,
    reason: string
}>): APICallParameters {
    return apiUpdate(request, baseContextV2, "updateAllocation");
}

export interface UsageOverTimeDatePointAPI {
    usage: number;
    quota: number;
    timestamp: number;
}

export interface UsageOverTimeAPI {
    data: UsageOverTimeDatePointAPI[];
}

export interface BreakdownByProjectPointAPI {
    title: string;
    projectId: string;
    usage: number;
}

export interface BreakdownByProjectAPI {
    data: BreakdownByProjectPointAPI[];
}

export interface ChartsAPI {
    categories: ProductCategoryV2[];
    allocGroups: AllocationGroupWithProductCategoryIndex[];
    charts: ChartsForCategoryAPI[];
    usagePerUser: UsagePerUserAPI
}

export interface UsagePerUserAPI {
    data: UsagePerUserPointAPI[]
}

export interface UsagePerUserPointAPI {
    username: string;
    category: ProductCategoryV2;
    usage: number;
}

export interface ChartsForCategoryAPI {
    categoryIndex: number;
    overTime: UsageOverTimeAPI;
    breakdownByProject: BreakdownByProjectAPI;
}

export interface AllocationGroupWithProductCategoryIndex {
    group: AllocationGroup;
    productCategoryIndex: number;
}

export function retrieveChartsV2(request: {
    start: number,
    end: number,
}): APICallParameters {
    return apiRetrieve(request, visualizationContextV2, "charts");
}

export function walletOwnerEquals(a: WalletOwner, b: WalletOwner): boolean {
    switch (a.type) {
        case "project": {
            if (b.type !== "project") return false
            return a.projectId === b.projectId;
        }

        case "user": {
            if (b.type !== "user") return false;
            return a.username === b.username;
        }
    }
}

export function utcDate(ts: number): string {
    const d = new Date(ts);
    if (ts >= Number.MAX_SAFE_INTEGER) return "31/12/9999";

    return `${d.getUTCDate().toString().padStart(2, '0')}/${(d.getUTCMonth() + 1).toString().padStart(2, '0')}/${d.getUTCFullYear()}`;
}

export function periodsOverlap(a: { start: number, end: number }, b: { start: number, end: number }): boolean {
    return a.start <= b.end && b.start <= a.end;
}

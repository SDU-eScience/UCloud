import {IconName} from "@/ui-components/Icon";
import {apiBrowse, apiRetrieve, apiUpdate} from "@/Authentication/DataHook";
import {BulkRequest, PageV2, PaginationRequestV2} from "@/UCloud";
import {getProviderTitle} from "@/Providers/ProviderTitle";

export const UCLOUD_PROVIDER = "ucloud";
export const UNABLE_TO_USE_FULL_ALLOC_MESSAGE =
    `You will not be able to use the full amount of your allocated quota due to over-allocation from your grant giver.
    Contact your grant giver for more information.`;

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

export type WalletOwner = {type: "user"; username: string} | {type: "project"; projectId: string;};

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

    let result = "";
    let i = 0;
    const len = substring.length;
    for (const char of substring) {
        result += char;
        i += 1;
        if ((i - len) % 3 === 0 && i !== len) {
            result += ".";
        }
    }

    if (dotIndex !== -1) {
        result += ",";
        result += numberAsString.substring(dotIndex + 1);
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
            case "COMPUTE": return 0;
            case "STORAGE": return 1;
            case "NETWORK_IP": return 2;
            case "INGRESS": return 3;
            case "LICENSE": return 4;
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

export function guestimateProductCategoryDescription(
    category: string,
    provider: string
): string {
    const normalizedCategory = removeSuffix(category, "-h");
    return hardcodedProductCategoryDescriptions[provider]?.[normalizedCategory] ?? "";
}

export function combineBalances(
    balances: {
        balance: number,
        category: ProductCategoryV2
    }[]
): { productType: ProductType, normalizedBalance: number, unit: string }[] {
    const result: ReturnType<typeof combineBalances> = [];

    // This function combines many balances from (potentially) different categories and combines them into a unified
    // view. This is useful when we want to give a unified view of how many resources you have available.
    for (const it of balances) {
        const unit = explainUnit(it.category);
        const normalizedBalance = unit.priceFactor * it.balance;

        const existing = result.find(it => it.unit === unit.name && it.productType === unit.productType);
        if (existing) {
            existing.normalizedBalance += normalizedBalance;
        } else {
            result.push({ unit: unit.name, normalizedBalance, productType: it.category.productType });
        }
    }

    return result;
}

export interface FrontendAccountingUnit {
    name: string;
    priceFactor: number;
    invPriceFactor: number;
    productType: ProductType;
    frequencyFactor: number;
    desiredFrequency: AccountingFrequency;
}

export function explainUnit(category: ProductCategoryV2): FrontendAccountingUnit {
    const unit = category.accountingUnit;
    let priceFactor = 1;
    let frequencyFactor = 1;
    let unitName = unit.namePlural;
    let suffix = "";
    let desiredFrequency: AccountingFrequency = "ONCE";

    if (category.accountingFrequency !== "ONCE") {
        switch (category.productType) {
            case "COMPUTE":
                desiredFrequency = "PERIODIC_HOUR";
                break;

            default:
                desiredFrequency = "PERIODIC_DAY";
                break
        }

        const actualFrequency = category.accountingFrequency;
        frequencyFactor = frequencyToMillis(actualFrequency) / frequencyToMillis(desiredFrequency);
        priceFactor = frequencyFactor;

        if (unit.displayFrequencySuffix) {
            suffix = "-" + frequencyToSuffix(desiredFrequency, true);
            unitName = unit.name;
        }
    }

    if (unit.floatingPoint) priceFactor *= 1 / 1000000;

    return { name: unitName + suffix, priceFactor, invPriceFactor: 1 / priceFactor, productType: category.productType, frequencyFactor, desiredFrequency};
}

export function priceToString(product: ProductV2, numberOfUnits: number, durationInMinutes?: number, opts?: { showSuffix: boolean }): string {
    const unit = explainUnit(product.category);
    const pricePerUnitPerFrequency = product.price * (1 / unit.frequencyFactor);
    const durationInMinutesOrDefault = durationInMinutes ?? frequencyToMillis(unit.desiredFrequency) / frequencyToMillis("PERIODIC_MINUTE");
    let normalizedDuration = durationInMinutesOrDefault * (frequencyToMillis("PERIODIC_MINUTE") / frequencyToMillis(unit.desiredFrequency));
    if (unit.desiredFrequency === "ONCE") {
        normalizedDuration = 1;
    }

    const totalPrice = normalizedDuration * pricePerUnitPerFrequency * numberOfUnits * unit.priceFactor;

    if (totalPrice === 0 || product.category.freeToUse) return "Free";
    let withoutSuffix = balanceToStringFromUnit(product.category.productType, unit.name, totalPrice);
    if (unit.desiredFrequency !== "ONCE" && opts?.showSuffix !== false) {
        return withoutSuffix + "/" + frequencyToSuffix(unit.desiredFrequency, false);
    } else {
        if (totalPrice === 1 && probablyCurrencies.indexOf(unit.name) === -1 && unit.desiredFrequency === "ONCE") {
            return "Quota based (" + unit.name + ")";
        }
        return withoutSuffix;
    }
}

const standardStorageUnitsSi = ["KB", "MB", "GB", "TB", "PB", "EB"];
const standardStorageUnits = ["KiB", "MiB", "GiB", "TiB", "PiB", "EiB"];
const defaultUnits = ["K", "M", "B", "T"];
const probablyCurrencies = ["DKK", "kr", "EUR", "€", "USD", "$"];

export function balanceToString(
    category: ProductCategoryV2,
    balance: number,
    opts?: { precision?: number, removeUnitIfPossible?: boolean }
): string {
    const unit = explainUnit(category);
    const normalizedBalance = balance * unit.priceFactor;
    return balanceToStringFromUnit(unit.productType, unit.name, normalizedBalance, opts)
}

export function balanceToStringFromUnit(
    productType: ProductType,
    unit: string,
    normalizedBalance: number,
    opts?: { precision?: number, removeUnitIfPossible?: boolean }
): string {
    let canRemoveUnit = opts?.removeUnitIfPossible ?? false;
    let balanceToDisplay = normalizedBalance;
    let unitToDisplay = unit;
    let attachedSuffix: string | null = null;

    const storageUnitSiIdx = standardStorageUnitsSi.indexOf(unit);
    const storageUnitIdx = standardStorageUnits.indexOf(unit);
    if (productType === "STORAGE" && (storageUnitSiIdx !== -1 || storageUnitIdx !== -1)) {
        canRemoveUnit = false;
        const base = storageUnitIdx !== -1 ? 1024 : 1000;
        const array = storageUnitIdx !== -1 ? standardStorageUnits : standardStorageUnitsSi;
        let idx = storageUnitIdx !== -1 ? storageUnitIdx : storageUnitSiIdx;

        while (balanceToDisplay > base && idx < array.length - 1) {
            balanceToDisplay /= base;
            idx++;
        }

        unitToDisplay = array[idx];
    } else {
        let threshold = 1000;
        if (probablyCurrencies.indexOf(unitToDisplay) !== -1) threshold = 1000000;

        if (normalizedBalance >= threshold) {
            let idx = -1;
            while (balanceToDisplay >= 1000 && idx < defaultUnits.length - 1) {
                balanceToDisplay /= 1000;
                idx++;
            }

            attachedSuffix = defaultUnits[idx];
        }
    }

    let builder = "";
    builder += addThousandSeparators(removeSuffix(balanceToDisplay.toFixed(opts?.precision ?? 2), ".00"));
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
}

export interface AllocationGroup {
    usage: number;
    allocations: Allocation[];
}

export interface Allocation {
    id: number;
    startDate: number;
    endDate: number;
    quota: number;
    grantedIn?: number | null;
    retiredUsage?: number | null;
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

import {buildQueryString} from "@/Utilities/URIUtilities";
import {IconName} from "@/ui-components/Icon";
import {apiBrowse, apiRetrieve, apiSearch, apiUpdate} from "@/Authentication/DataHook";
import { BulkRequest, PageV2, PaginationRequestV2 } from "@/UCloud";
import {getProviderTitle} from "@/Providers/ProviderTitle";
import * as AccountingB from "./AccountingBinary";

export const UCLOUD_PROVIDER = "ucloud";

export type AccountType = "USER" | "PROJECT";
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

export interface VisualizationFlags {
    filterStartDate?: number | null;
    filterEndDate?: number | null;
    filterType?: ProductType | null;
    filterProvider?: string | null;
    filterProductCategory?: string | null;
    filterAllocation?: string | null;
}

export function retrieveUsage(request: VisualizationFlags): APICallParameters {
    return apiRetrieve(request, "/api/accounting/visualization", "usage");
}

export function retrieveBreakdown(request: VisualizationFlags): APICallParameters {
    return apiRetrieve(request, "/api/accounting/visualization", "breakdown");
}

export function browseWallets(request: PaginationRequestV2): APICallParameters {
    return apiBrowse(request, "/api/accounting/wallets");
}

export interface TransferRecipient {
    id: string;
    isProject: boolean;
    title: string;
    principalInvestigator: string;
    numberOfMembers: number;
}

export interface RetrieveRecipientResponse {
    id: string;
    isProject: boolean;
    title: string;
    principalInvestigator: string;
    numberOfMembers: number;
}

interface RetrieveRecipientRequest {
    query: string;
}

export function retrieveRecipient(request: RetrieveRecipientRequest): APICallParameters<RetrieveRecipientRequest, RetrieveRecipientResponse> {
    return apiRetrieve(request, "/api/accounting/wallets", "recipient");
}

export interface DepositToWalletRequestItem {
    recipient: WalletOwner;
    sourceAllocation: string;
    amount: number;
    description: string;
    startDate: number;
    endDate?: number;
    dry?: boolean;
}

export interface TransferToWalletRequestItem {
    categoryId: ProductCategoryId;
    target: WalletOwner;
    source: WalletOwner;
    amount: number;
    startDate: number;
    endDate: number;
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

export function deposit(request: BulkRequest<DepositToWalletRequestItem>): APICallParameters {
    return apiUpdate(request, "/api/accounting", "deposit");
}

export function transfer(request: BulkRequest<TransferToWalletRequestItem>): APICallParameters {
    return apiUpdate(request, "/api/accounting", "transfer");
}

interface RootDepositRequestItem {
    categoryId: ProductCategoryId;
    recipient: WalletOwner;
    amount: number;
    description: string;
    startDate?: number | null;
    endDate?: number | null;
    transactionId?: string;
}

export function rootDeposit(request: BulkRequest<RootDepositRequestItem>): APICallParameters {
    return apiUpdate(request, "/api/accounting", "rootDeposit");
}

export type WalletOwner = {type: "user"; username: string} | {type: "project"; projectId: string;};

export interface WalletAllocation {
    id: string;
    allocationPath: string;
    balance: number;
    initialBalance: number;
    localBalance: number;
    startDate: number;
    endDate?: number | null;
    grantedIn?: number
    maxUsableBalance?: number
    canAllocate: boolean;
}

export interface Wallet {
    owner: WalletOwner;
    paysFor: ProductCategoryId;
    allocations: WalletAllocation[];
    chargePolicy: "EXPIRE_FIRST";
    productType: ProductType;
    chargeType: ChargeType;
    unit: ProductPriceUnit;
}

export function productCategoryEquals(a: ProductCategoryId, b: ProductCategoryId): boolean {
    return a.provider === b.provider && a.name === b.name;
}

export interface ListProductsRequest extends PaginationRequest {
    provider: string;
}

export interface ListProductsByAreaRequest extends PaginationRequest {
    provider: string;
    area: string;
    showHidden: boolean
}

export type ListProductsResponse = Product[];

export function listProducts(request: ListProductsRequest): APICallParameters<ListProductsRequest> {
    return {
        method: "GET",
        path: buildQueryString("/products/list", request),
        parameters: request,
        reloadId: Math.random()
    };
}

export function listByProductArea(request: ListProductsByAreaRequest): APICallParameters<ListProductsByAreaRequest> {
    return {
        method: "GET",
        path: buildQueryString("/products/listByArea", request),
        parameters: request,
        reloadId: Math.random()
    };
}

export interface RetrieveFromProviderRequest {
    provider: string;
}

export type RetrieveFromProviderResponse = Product[];

export function retrieveFromProvider(
    request: RetrieveFromProviderRequest
): APICallParameters<RetrieveFromProviderRequest> {
    return {
        method: "GET",
        path: buildQueryString("/products/retrieve", request),
        parameters: request,
        reloadId: Math.random()
    };
}

export interface UsageChart {
    type: ProductType;
    periodUsage: number;
    chargeType: ChargeType;
    unit: ProductPriceUnit;
    chart: {
        lines: {
            name: string;
            points: {
                timestamp: number;
                value: number;
            }[]
        }[]
    }
}


export type ChargeType = "ABSOLUTE" | "DIFFERENTIAL_QUOTA";
export type ProductPriceUnit =
    "PER_UNIT" |
    "CREDITS_PER_MINUTE" | "CREDITS_PER_HOUR" | "CREDITS_PER_DAY" |
    "UNITS_PER_MINUTE" | "UNITS_PER_HOUR" | "UNITS_PER_DAY";

export type ProductType = "STORAGE" | "COMPUTE" | "INGRESS" | "LICENSE" | "NETWORK_IP";
export type Type = "storage" | "compute" | "ingress" | "license" | "network_ip";
export const productTypes: ProductType[] = ["STORAGE", "COMPUTE", "INGRESS", "NETWORK_IP", "LICENSE"];

export function productTypeToJsonType(type: ProductType): Type {
    switch (type) {
        case "COMPUTE": return "compute";
        case "INGRESS": return "ingress";
        case "LICENSE": return "license";
        case "NETWORK_IP": return "network_ip";
        case "STORAGE": return "storage";
        default: return (type as any).toString().tolowerCase();
    }
}

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

export function productTypeToTitle(type: ProductType): string {
    switch (type) {
        case "INGRESS":
            return "Public Link"
        case "COMPUTE":
            return "Compute";
        case "STORAGE":
            return "Storage";
        case "NETWORK_IP":
            return "Public IP";
        case "LICENSE":
            return "Software License";
    }
}

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

export function explainAllocation(type: ProductType, unit: ProductPriceUnit): string {
    switch (unit) {
        case "PER_UNIT": {
            switch (type) {
                case "INGRESS":
                    return "Public link(s)";
                case "NETWORK_IP":
                    return "Public IP(s)";
                case "LICENSE":
                    return "License(s)";
                case "STORAGE":
                    return "GB";
                case "COMPUTE":
                    return "Job(s)";
            }
        }

        // eslint-disable-next-line no-fallthrough
        case "CREDITS_PER_MINUTE":
        case "CREDITS_PER_HOUR":
        case "CREDITS_PER_DAY": {
            return "DKK";
        }

        case "UNITS_PER_MINUTE":
        case "UNITS_PER_HOUR":
        case "UNITS_PER_DAY": {
            switch (type) {
                case "INGRESS":
                    return "Days of public link";
                case "NETWORK_IP":
                    return "Days of public IP";
                case "LICENSE":
                    return "Days of license";
                case "STORAGE":
                    // TODO Someone should come up with a better name for this. I don't see _why_ you would want to
                    //  bill like this, but I also don't know what to call it.
                    return "Days of GB";
                case "COMPUTE":
                    return "Core hours";
            }
        }
    }
}

export function explainUsage(type: ProductType, chargeType: ChargeType, unit: ProductPriceUnit): string {
    // NOTE(Dan): I think this is generally the case, but I am leaving a separate function just in case we need to
    // change it.
    return explainAllocation(type, unit);
}

function explainPrice(type: ProductType, unit: ProductPriceUnit): {readableUnit: string, durationInMinutes: number} {
    switch (unit) {
        case "PER_UNIT": {
            switch (type) {
                case "INGRESS":
                    return {readableUnit: "Public link(s)", durationInMinutes: 1};
                case "NETWORK_IP":
                    return {readableUnit: "Public IP(s)", durationInMinutes: 1};
                case "LICENSE":
                    return {readableUnit: "License(s)", durationInMinutes: 1};
                case "STORAGE":
                    return {readableUnit: "GB", durationInMinutes: 1};
                case "COMPUTE":
                    return {readableUnit: "Job(s)", durationInMinutes: 1};
            }
        }

        // eslint-disable-next-line no-fallthrough
        case "CREDITS_PER_MINUTE":
        case "CREDITS_PER_HOUR":
        case "CREDITS_PER_DAY": {
            switch (type) {
                case "INGRESS":
                    return {readableUnit: "DKK/day", durationInMinutes: DAY};
                case "NETWORK_IP":
                    return {readableUnit: "DKK/day", durationInMinutes: DAY};
                case "LICENSE":
                    return {readableUnit: "DKK/day", durationInMinutes: DAY};
                case "STORAGE":
                    return {readableUnit: "DKK/GB-day", durationInMinutes: DAY};
                case "COMPUTE":
                    return {readableUnit: "DKK/hour", durationInMinutes: HOUR};
            }
        }

        // eslint-disable-next-line no-fallthrough
        case "UNITS_PER_MINUTE":
        case "UNITS_PER_HOUR":
        case "UNITS_PER_DAY": {
            switch (type) {
                case "INGRESS":
                    return {readableUnit: "Link days", durationInMinutes: DAY};
                case "NETWORK_IP":
                    return {readableUnit: "IP days", durationInMinutes: DAY};
                case "LICENSE":
                    return {readableUnit: "License days", durationInMinutes: DAY};
                case "STORAGE":
                    return {readableUnit: "GB days", durationInMinutes: DAY};
                case "COMPUTE":
                    return {readableUnit: "Core hour(s)/hour", durationInMinutes: HOUR};
            }
        }
    }
}

const MINUTE = 1;
const HOUR = MINUTE * 60;
const DAY = HOUR * 24;

export function normalizeBalanceForBackend(
    balance: number,
    type: ProductType,
    chargeType: ChargeType,
    unit: ProductPriceUnit
): number {
    switch (unit) {
        case "PER_UNIT": {
            return balance;
        }

        // eslint-disable-next-line no-fallthrough
        case "CREDITS_PER_MINUTE":
        case "CREDITS_PER_HOUR":
        case "CREDITS_PER_DAY": {
            return balance * 1000000;
        }

        case "UNITS_PER_MINUTE":
        case "UNITS_PER_HOUR":
        case "UNITS_PER_DAY": {
            const inputIs = unit === "UNITS_PER_MINUTE" ? MINUTE : unit === "UNITS_PER_HOUR" ? HOUR : DAY;

            switch (type) {
                case "INGRESS": {
                    const factor = DAY / inputIs;
                    return Math.ceil(balance * factor);
                }
                case "NETWORK_IP": {
                    const factor = DAY / inputIs;
                    return Math.ceil(balance * factor);
                }
                case "LICENSE": {
                    const factor = DAY / inputIs;
                    return Math.ceil(balance * factor);
                }
                case "STORAGE": {
                    const factor = DAY / inputIs;
                    return Math.ceil(balance * factor);
                }
                case "COMPUTE": {
                    const factor = HOUR / inputIs;
                    return Math.ceil(balance * factor);
                }
            }
        }
    }
}

export function normalizeBalanceForFrontendOpts(
    balance: number,
    type: ProductType,
    unit: ProductPriceUnit,
    opts: {
        precisionOverride?: number,
        forceInteger?: boolean
    }
): string {
    return normalizeBalanceForFrontend(balance, type, unit,
        opts.precisionOverride, opts.forceInteger);
}

export function normalizeBalanceForFrontend(
    balance: number,
    type: ProductType,
    unit: ProductPriceUnit,
    precisionOverride?: number,
    forceInteger?: boolean
): string {
    switch (unit) {
        case "PER_UNIT": {
            return balance.toString();
        }

        // eslint-disable-next-line no-fallthrough
        case "CREDITS_PER_MINUTE":
        case "CREDITS_PER_HOUR":
        case "CREDITS_PER_DAY": {
            return currencyFormatter(balance, precisionOverride ?? 2, forceInteger ?? false);
        }

        // eslint-disable-next-line no-fallthrough
        case "UNITS_PER_MINUTE":
        case "UNITS_PER_HOUR":
        case "UNITS_PER_DAY": {
            const inputIs = unit === "UNITS_PER_MINUTE" ? MINUTE : unit === "UNITS_PER_HOUR" ? HOUR : DAY;

            switch (type) {
                case "INGRESS": {
                    const factor = DAY / inputIs;
                    return Math.floor(balance / factor).toString();
                }
                case "NETWORK_IP": {
                    const factor = DAY / inputIs;
                    return Math.floor(balance / factor).toString();
                }
                case "LICENSE": {
                    const factor = DAY / inputIs;
                    return Math.floor(balance / factor).toString();
                }
                case "STORAGE": {
                    const factor = DAY / inputIs;
                    return Math.floor(balance / factor).toString();
                }
                case "COMPUTE": {
                    const factor = HOUR / inputIs;
                    return Math.floor(balance / factor).toString();
                }
            }
        }
    }
}

function currencyFormatter(credits: number, precision = 2, forceInteger: boolean): string {
    if (precision < 0 || precision > 6) throw Error("Precision must be in 0..6");

    if (forceInteger) {
        return ((credits / 1000000) | 0).toString();
    }

    // Edge-case handling
    if (credits < 0) {
        return "-" + currencyFormatter(-credits, precision, forceInteger);
    } else if (credits === 0) {
        return "0";
    } else if (credits < Math.pow(10, 6 - precision)) {
        if (precision === 0) return "< 1";
        let builder = "< 0,";
        for (let i = 0; i < precision - 1; i++) builder += "0";
        builder += "1";
        return builder;
    }

    // Group into before and after decimal separator
    const stringified = credits.toString().padStart(6, "0");

    let before = stringified.substring(0, stringified.length - 6);
    let after = stringified.substring(stringified.length - 6);
    if (before === "") before = "0";
    if (after === "") after = "0";
    after = after.padStart(precision, "0");
    after = after.substring(0, precision);

    // Truncate trailing zeroes (but keep at least two)
    if (precision > 2) {
        let firstZeroAt = -1;
        for (let i = 2; i < after.length; i++) {
            if (after[i] === "0") {
                if (firstZeroAt === -1) firstZeroAt = i;
            } else {
                firstZeroAt = -1;
            }
        }

        if (firstZeroAt !== -1) { // We have trailing zeroes
            after = after.substring(0, firstZeroAt);
        }
    }

    // Thousand separator
    const beforeFormatted = addThousandSeparators(before);

    if (after === "") return `${beforeFormatted}`;
    else return `${beforeFormatted},${after}`;
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

// TODO(Dan): This interface is completely insane. Re-do this at some point.
export function priceExplainer(product: Product): string {
    if (product.chargeType === "DIFFERENTIAL_QUOTA") {
        return "Quota based";
    }

    const {readableUnit, durationInMinutes} = explainPrice(product.productType, product.unitOfPrice);

    const amount = normalizeBalanceForFrontend(
        costOfDuration(durationInMinutes, 1, product),
        product.productType,
        product.unitOfPrice,
    );
    return `${amount} ${readableUnit}`
}

export function costOfDuration(minutes: number, numberOfProducts: number, product: Product): number {
    let unitsToBuy: number;
    const cpuFactor = product.productType === "COMPUTE" ? product["cpu"] as number : 1;
    switch (product.unitOfPrice) {
        case "PER_UNIT":
            unitsToBuy = 1;
            break;
        case "CREDITS_PER_MINUTE":
        case "UNITS_PER_MINUTE":
            unitsToBuy = minutes;
            break;
        case "CREDITS_PER_HOUR":
        case "UNITS_PER_HOUR":
            unitsToBuy = Math.ceil(minutes / 60);
            break;
        case "CREDITS_PER_DAY":
        case "UNITS_PER_DAY":
            unitsToBuy = Math.ceil(minutes / (60 * 24));
            break;
    }

    return unitsToBuy * product.pricePerUnit * numberOfProducts;
}

export function usageExplainer(
    usage: number,
    productType: ProductType,
    chargeType: ChargeType,
    unitOfPrice: ProductPriceUnit
): string {
    const amount = normalizeBalanceForFrontend(usage, productType, unitOfPrice);
    const suffix = explainUsage(productType, chargeType, unitOfPrice);
    return `${amount} ${suffix}`;
}

// Version 2 API
// =====================================================================================================================
const baseContextV2 = "/api/accounting/v2";
const visualizationContextV2 = "/api/accounting/v2/visualization";

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
    const nameCmp = aName.localeCompare(bName);
    return nameCmp;
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

interface ProductV2Storage extends ProductV2Base {
    type: "storage";
}

interface ProductV2Compute extends ProductV2Base {
    type: "compute";
    cpu?: number | null;
    memoryInGigs?: number | null;
    gpu?: number | null;
    cpuModel?: string | null;
    memoryModel?: string | null;
    gpuModel?: string | null;
}

interface ProductV2Ingress extends ProductV2Base {
    type: "ingress";
}

interface ProductV2License extends ProductV2Base {
    type: "license";
}

interface ProductV2NetworkIP extends ProductV2Base {
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
}

export function explainUnit(category: ProductCategoryV2): FrontendAccountingUnit {
    const unit = category.accountingUnit;
    let priceFactor = 1;
    let unitName = unit.namePlural;
    let suffix = "";

    if (unit.displayFrequencySuffix && category.accountingFrequency !== "ONCE") {
        let desiredFrequency: AccountingFrequency;
        switch (category.productType) {
            case "COMPUTE":
                desiredFrequency = "PERIODIC_HOUR";
                break;

            default:
                desiredFrequency = "PERIODIC_DAY";
                break
        }

        const actualFrequency = category.accountingFrequency;
        priceFactor = frequencyToMillis(actualFrequency) / frequencyToMillis(desiredFrequency);
        suffix = "-" + frequencyToSuffix(desiredFrequency, true);
        unitName = unit.name;
    }

    if (unit.floatingPoint) priceFactor *= 1 / 1000000;

    return { name: unitName + suffix, priceFactor, invPriceFactor: 1 / priceFactor, productType: category.productType };
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
    allocations: WalletAllocationV2[];
}

export interface WalletAllocationV2 {
    id: string;
    allocationPath: string[];

    localUsage: number;
    quota: number;
    treeUsage?: number | null;

    startDate: number;
    endDate: number;

    grantedIn?: number | null;
    deicAllocationId?: string | null;

    canAllocate: boolean;
    allowSubAllocationsToAllocate: boolean;
}

export interface SubAllocationV2 {
    id: string;
    path: string;

    startDate: number;
    endDate?: number;

    productCategory: ProductCategoryV2;

    workspaceId: string;
    workspaceTitle: string;
    workspaceIsProject: boolean;
    projectPI?: string | null;

    usage: number;
    quota: number;

    grantedIn?: number | null;
}

export function browseWalletsV2(
    request: PaginationRequestV2 & { filterType?: ProductType }
): APICallParameters<unknown, PageV2<WalletV2>> {
    return apiBrowse(request, baseContextV2, "wallets");
}

export function browseSubAllocations(
    request: PaginationRequestV2 & { filterType?: ProductType }
): APICallParameters<unknown, PageV2<SubAllocationV2>> {
    return apiBrowse(request, baseContextV2, "subAllocations");
}

export function searchSubAllocations(
    request: PaginationRequestV2 & { query: string, filterType?: ProductType }
): APICallParameters<unknown, PageV2<SubAllocationV2>> {
    return apiSearch(request, baseContextV2, "subAllocations");
}

export function updateAllocationV2(request: BulkRequest<{
    allocationId: string,
    newQuota?: number | null,
    newStart?: number | null,
    newEnd?: number | null,
    reason: string
}>): APICallParameters {
    return apiUpdate(request, baseContextV2, "updateAllocation");
}

export function retrieveChartsV2(request: {
    start: number,
    end: number,
}): APICallParametersBinary<AccountingB.Charts> {
    return {
        ...apiRetrieve(request, visualizationContextV2, "charts"),
        responseConstructor: AccountingB.Charts
    };
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

export function translateBinaryProductType(t: AccountingB.ProductTypeB): ProductType {
    switch (t) {
        case AccountingB.ProductTypeB.STORAGE:
            return "STORAGE";
        case AccountingB.ProductTypeB.COMPUTE:
            return "COMPUTE";
        case AccountingB.ProductTypeB.LICENSE:
            return "LICENSE";
        case AccountingB.ProductTypeB.INGRESS:
            return "INGRESS";
        case AccountingB.ProductTypeB.NETWORK_IP:
            return "NETWORK_IP";
    }
}

export function translateBinaryFrequency(t: AccountingB.AccountingFrequencyB): AccountingFrequency {
    switch (t) {
        case AccountingB.AccountingFrequencyB.ONCE:
            return "ONCE";
        case AccountingB.AccountingFrequencyB.PERIODIC_MINUTE:
            return "PERIODIC_MINUTE";
        case AccountingB.AccountingFrequencyB.PERIODIC_HOUR:
            return "PERIODIC_HOUR";
        case AccountingB.AccountingFrequencyB.PERIODIC_DAY:
            return "PERIODIC_DAY";
    }
}

export function translateBinaryUnit(t: AccountingB.AccountingUnitB): AccountingUnit {
    return {
        name: t.name,
        namePlural: t.namePlural,
        displayFrequencySuffix: t.displayFrequencySuffix,
        floatingPoint: t.floatingPoint
    };
}

export function translateBinaryProductCategory(pc: AccountingB.ProductCategoryB): ProductCategoryV2 {
    return {
        name: pc.name,
        provider: pc.provider,
        productType: translateBinaryProductType(pc.productType),
        accountingFrequency: translateBinaryFrequency(pc.accountingFrequency),
        accountingUnit: translateBinaryUnit(pc.accountingUnit),
        freeToUse: false
    };
}

export function subAllocationOwner(alloc: SubAllocationV2): WalletOwner {
    if (alloc.workspaceIsProject) {
        return { type: "project", projectId: alloc.workspaceId };
    } else {
        return { type: "user", username: alloc.workspaceId };
    }
}

export function utcDate(ts: number): string {
    const d = new Date(ts);
    if (ts >= Number.MAX_SAFE_INTEGER) return "31/12/9999";

    return `${d.getUTCDate().toString().padStart(2, '0')}/${(d.getUTCMonth() + 1).toString().padStart(2, '0')}/${d.getUTCFullYear()}`;
}

export function utcDateAndTime(ts: number): string {
    const d = new Date(ts);
    if (ts >= Number.MAX_SAFE_INTEGER) return "31/12/9999";

    let message = "";
    message += d.getUTCDate().toString().padStart(2, '0');
    message += "/";
    message += (d.getUTCMonth() + 1).toString().padStart(2, '0');
    message += "/";
    message += d.getUTCFullYear();
    message += " ";
    message += d.getUTCHours().toString().padStart(2, '0');
    message += ":";
    message += d.getUTCMinutes().toString().padStart(2, '0');
    return message;
}

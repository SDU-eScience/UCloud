import {buildQueryString} from "@/Utilities/URIUtilities";
import {IconName} from "@/ui-components/Icon";
import {apiBrowse, apiRetrieve, apiUpdate} from "@/Authentication/DataHook";
import {BulkRequest, PaginationRequestV2} from "@/UCloud";

export const UCLOUD_PROVIDER = "ucloud";

export type AccountType = "USER" | "PROJECT";
/* @deprecated */
export type ProductArea = ProductType;

export interface ProductCategoryId {
    name: string;
    provider: string;
    title?: string;
}

export function productToArea(product: Product): ProductArea {
    switch (product.type) {
        case "compute": return "COMPUTE";
        case "ingress": return "INGRESS";
        case "license": return "LICENSE";
        case "storage": return "STORAGE";
        case "network_ip": return "NETWORK_IP";
    }
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

export function retrieveRecipient(request: { query: string }): APICallParameters {
    return apiRetrieve(request, "/api/accounting/wallets", "recipient");
}

export interface DepositToWalletRequestItem {
    recipient: WalletOwner;
    sourceAllocation: string;
    amount: number;
    description: string;
    startDate: number;
    endDate: number;
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

export type WalletOwner = { type: "user"; username: string } | { type: "project"; projectId: string; };

export interface WalletAllocation {
    id: string;
    allocationPath: string;
    balance: number;
    initialBalance: number;
    localBalance: number;
    startDate: number;
    endDate?: number | null;
    grantedIn?: number
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
    version: number;
    balance?: number | null;
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
            return "globeEuropeSolid"
        case "COMPUTE":
            return "cpu";
        case "STORAGE":
            return "hdd";
        case "NETWORK_IP":
            return "networkWiredSolid";
        case "LICENSE":
            return "apps";
    }
}

export function explainAllocation(type: ProductType, chargeType: ChargeType, unit: ProductPriceUnit): string {
    switch (unit) {
        case "PER_UNIT": {
            switch (type) {
                case "INGRESS":
                    return "Public links";
                case "NETWORK_IP":
                    return "Public IPs";
                case "LICENSE":
                    return "Licenses";
                case "STORAGE":
                    return "GB";
                case "COMPUTE":
                    return "Jobs";
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
    return explainAllocation(type, chargeType, unit);
}

export function explainPrice(type: ProductType, chargeType: ChargeType, unit: ProductPriceUnit): string {
    switch (unit) {
        case "PER_UNIT": {
            switch (type) {
                case "INGRESS":
                    return "Public links";
                case "NETWORK_IP":
                    return "Public IPs";
                case "LICENSE":
                    return "Licenses";
                case "STORAGE":
                    return "GB";
                case "COMPUTE":
                    return "Jobs";
            }
        }

        // eslint-disable-next-line no-fallthrough
        case "CREDITS_PER_MINUTE":
        case "CREDITS_PER_HOUR":
        case "CREDITS_PER_DAY": {
            switch (type) {
                case "INGRESS":
                    return "DKK/day";
                case "NETWORK_IP":
                    return "DKK/day";
                case "LICENSE":
                    return "DKK/day";
                case "STORAGE":
                    return "DKK/day of one GB";
                case "COMPUTE":
                    return "DKK/hour";
            }
        }

        // eslint-disable-next-line no-fallthrough
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

export function normalizeBalanceForFrontend(
    balance: number,
    type: ProductType,
    chargeType: ChargeType,
    unit: ProductPriceUnit,
    isPrice: boolean,
    precisionOverride?: number
): string {
    switch (unit) {
        case "PER_UNIT": {
            return balance.toString();
        }

        // eslint-disable-next-line no-fallthrough
        case "CREDITS_PER_MINUTE":
        case "CREDITS_PER_HOUR":
        case "CREDITS_PER_DAY": {
            if (!isPrice) {
                return currencyFormatter(balance, precisionOverride ?? 2);
            }

            const inputIs = unit === "CREDITS_PER_MINUTE" ? MINUTE : unit === "CREDITS_PER_HOUR" ? HOUR : DAY;

            switch (type) {
                case "INGRESS": {
                    const factor = DAY / inputIs;
                    return currencyFormatter(balance * factor, precisionOverride ?? 2);
                }
                case "NETWORK_IP": {
                    const factor = DAY / inputIs;
                    return currencyFormatter(balance * factor, precisionOverride ?? 2);
                }
                case "LICENSE": {
                    const factor = DAY / inputIs;
                    return currencyFormatter(balance * factor, precisionOverride ?? 2);
                }
                case "STORAGE": {
                    const factor = DAY / inputIs;
                    return currencyFormatter(balance * factor, precisionOverride ?? 2);
                }
                case "COMPUTE": {
                    const factor = HOUR / inputIs;
                    return currencyFormatter(balance * factor, precisionOverride ?? 4);
                }
            }
        }

        // eslint-disable-next-line no-fallthrough
        case "UNITS_PER_MINUTE":
        case "UNITS_PER_HOUR":
        case "UNITS_PER_DAY": {
            const inputIs = unit === "UNITS_PER_MINUTE" ? MINUTE : unit === "UNITS_PER_HOUR" ? HOUR : DAY;

            switch (type) {
                case "INGRESS": {
                    const factor = DAY / inputIs;
                    return Math.floor(balance * factor).toString();
                }
                case "NETWORK_IP": {
                    const factor = DAY / inputIs;
                    return Math.floor(balance * factor).toString();
                }
                case "LICENSE": {
                    const factor = DAY / inputIs;
                    return Math.floor(balance * factor).toString();
                }
                case "STORAGE": {
                    const factor = DAY / inputIs;
                    return Math.floor(balance * factor).toString();
                }
                case "COMPUTE": {
                    const factor = HOUR / inputIs;
                    return Math.floor(balance * factor).toString();
                }
            }
        }
    }
}

function currencyFormatter(credits: number, precision = 2): string {
    if (precision < 0 || precision > 6) throw Error("Precision must be in 0..6");

    // Edge-case handling
    if (credits < 0) {
        return "-" + currencyFormatter(-credits);
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

    let before = stringified.substr(0, stringified.length - 6);
    let after = stringified.substr(stringified.length - 6);
    if (before === "") before = "0";
    if (after === "") after = "0";
    after = after.padStart(precision, "0");
    after = after.substr(0, precision);

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
            after = after.substr(0, firstZeroAt);
        }
    }

    // Thousand separator
    const beforeFormatted = addThousandSeparators(before);

    if (after === "") return `${beforeFormatted}`;
    else return `${beforeFormatted},${after}`;
}

function addThousandSeparators(numberOrString: string | number): string {
    const numberAsString = typeof numberOrString === "string" ? numberOrString : numberOrString.toString(10);
    let result = "";
    const chunksInTotal = Math.ceil(numberAsString.length / 3);
    let offset = 0;
    for (let i = 0; i < chunksInTotal; i++) {
        if (i === 0) {
            let firstChunkSize = numberAsString.length % 3;
            if (firstChunkSize === 0) firstChunkSize = 3;
            result += numberAsString.substr(0, firstChunkSize);
            offset += firstChunkSize;
        } else {
            result += '.';
            result += numberAsString.substr(offset, 3);
            offset += 3;
        }
    }
    return result;
}

export function priceExplainer(product: Product): string {
    const amount = normalizeBalanceForFrontend(product.pricePerUnit, product.productType, product.chargeType,
        product.unitOfPrice, true);
    const suffix = explainPrice(product.productType, product.chargeType, product.unitOfPrice);
    return `${amount} ${suffix}`
}

export function costOfDuration(minutes: number, numberOfProducts: number, product: Product): number {
    let unitsToBuy: number;
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
    const amount = normalizeBalanceForFrontend(usage, productType, chargeType, unitOfPrice, false);
    const suffix = explainUsage(productType, chargeType, unitOfPrice);
    return `${amount} ${suffix}`;
}

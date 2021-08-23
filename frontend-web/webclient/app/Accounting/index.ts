import {buildQueryString} from "Utilities/URIUtilities";
import {Client} from "Authentication/HttpClientInstance";
import {PropType} from "UtilityFunctions";
import * as UCloud from "UCloud";
import {IconName} from "ui-components/Icon";

export const productCacheKey = {cacheKey: "accounting.products", cacheTtlMs: 1000 * 60 * 30};

export type AccountType = "USER" | "PROJECT";
export type PaymentModel = NonNullable<PropType<UCloud.accounting.ProductNS.License, "paymentModel">>;
export type ProductArea = NonNullable<PropType<UCloud.accounting.ListProductsByAreaRequest, "area">>;
export const productAreas: ProductArea[] = ["STORAGE", "COMPUTE", "INGRESS", "LICENSE"];

export interface ProductCategoryId {
    name: string;
    provider: string;
    title?: string;
}

export function productToArea(product: UCloud.accounting.Product): ProductArea {
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

export interface WalletBalance {
    wallet: Wallet;
    balance: number;
    allocated: number;
    used: number;
    area: ProductArea;
}

export interface Wallet {
    id: string;
    type: AccountType;
    paysFor: ProductCategoryId;
}

export function walletEquals(a: Wallet, b: Wallet): boolean {
    return a.id === b.id && a.type === b.type && productCategoryEquals(a.paysFor, b.paysFor);
}

export function productCategoryEquals(a: ProductCategoryId, b: ProductCategoryId): boolean {
    return a.provider === b.provider && a.name === b.name;
}

export interface RetrieveBalanceRequest {
    id?: string;
    type?: AccountType;
    includeChildren?: boolean;
}

export interface RetrieveBalanceResponse {
    wallets: WalletBalance[];
}

export function retrieveBalance(request: RetrieveBalanceRequest): APICallParameters<RetrieveBalanceRequest> {
    return {
        method: "GET",
        path: buildQueryString("/accounting/wallets/balance", request),
        parameters: request,
        reloadId: Math.random()
    };
}

export interface GrantCreditsRequest {
    wallet: Wallet;
    credits: number;
}

export type GrantCreditsResponse = {};

export function grantCredits(request: GrantCreditsRequest): APICallParameters<GrantCreditsRequest> {
    return {
        method: "POST",
        path: "/accounting/wallets/add-credits",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}

export interface SetCreditsRequest {
    wallet: Wallet;
    lastKnownBalance: number;
    newBalance: number;
}

export type SetCreditsResponse = {};

export function setCredits(request: SetCreditsRequest): APICallParameters<SetCreditsRequest> {
    return {
        method: "POST",
        path: "/accounting/wallets/set-balance",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}

export interface RetrieveCreditsRequest {
    id: string;
    type: AccountType;
}

export interface RetrieveCreditsResponse {
    wallets: WalletBalance[];
}

export function retrieveCredits(request: RetrieveCreditsRequest): APICallParameters<RetrieveCreditsRequest> {
    return {
        method: "GET",
        path: buildQueryString("/accounting/wallets/balance", request),
        parameters: request,
        reloadId: Math.random()
    };
}

// Machines
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

export type ChargeType = "ABSOLUTE" | "DIFFERENTIAL_QUOTA";
export type ProductPriceUnit =
    "PER_UNIT" |
    "CREDITS_PER_MINUTE" | "CREDITS_PER_HOUR" | "CREDITS_PER_DAY" |
    "UNITS_PER_MINUTE" | "UNITS_PER_HOUR" | "UNITS_PER_DAY";

export type ProductType = "STORAGE" | "COMPUTE" | "INGRESS" | "LICENSE" | "NETWORK_IP";
export const productTypes: ProductType[] = ["STORAGE", "COMPUTE", "INGRESS", "NETWORK_IP", "LICENSE"];

export interface ProductBase {
    type: string;
    category: ProductCategoryId;
    pricePerUnit: number;
    name: string;
    description: string;
    priority: number;
    version: number;
    freeToUse: boolean;
    productType: ProductType;
    unitOfPrice: ProductPriceUnit;
    chargeType: ChargeType;
    hiddenInGrantApplications: boolean;
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
                    const factor = inputIs / DAY;
                    return Math.ceil(balance * factor);
                }
                case "NETWORK_IP": {
                    const factor = inputIs / DAY;
                    return Math.ceil(balance * factor);
                }
                case "LICENSE": {
                    const factor = inputIs / DAY;
                    return Math.ceil(balance * factor);
                }
                case "STORAGE": {
                    const factor = inputIs / DAY;
                    return Math.ceil(balance * factor);
                }
                case "COMPUTE": {
                    const factor = inputIs / HOUR;
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
    isPrice: boolean
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
                return currencyFormatter(balance, 2);
            }

            const inputIs = unit === "CREDITS_PER_MINUTE" ? MINUTE : unit === "CREDITS_PER_HOUR" ? HOUR : DAY;

            switch (type) {
                case "INGRESS": {
                    const factor = DAY / inputIs;
                    return currencyFormatter(balance * factor, 2);
                }
                case "NETWORK_IP": {
                    const factor = DAY / inputIs;
                    return currencyFormatter(balance * factor, 2);
                }
                case "LICENSE": {
                    const factor = DAY / inputIs;
                    return currencyFormatter(balance * factor, 2);
                }
                case "STORAGE": {
                    const factor = DAY / inputIs;
                    return currencyFormatter(balance * factor, 2);
                }
                case "COMPUTE": {
                    const factor = HOUR / inputIs;
                    return currencyFormatter(balance * factor, 4);
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
        return "0 DKK";
    } else if (credits < Math.pow(10, 6 - precision)) {
        if (precision === 0) return "< 1 DKK";
        let builder = "< 0,";
        for (let i = 0; i < precision - 1; i++) builder += "0";
        builder += "1 DKK";
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

    if (after === "") return `${beforeFormatted} DKK`;
    else return `${beforeFormatted},${after} DKK`;
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

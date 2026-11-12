import {explainUnit, ProductTypesByPriority} from "@/Accounting";
import type {ProductCategoryV2} from "@/Accounting";
import {getProviderTitle} from "@/Providers/ProviderTitle";

export function allocationDisplayUnit(category: ProductCategoryV2): string {
    const unit = explainUnit(category).name;
    if (unit === "Core-hours" || unit === "Core-h") return "CPU-hours";
    return unit.replace(/GPU-h\b/g, "GPU-hours").replace(/CPU-h\b/g, "CPU-hours");
}

export function allocationCompareProducts(a: ProductCategoryV2, b: ProductCategoryV2): number {
    const provider = getProviderTitle(a.provider).localeCompare(getProviderTitle(b.provider));
    if (provider !== 0) return provider;
    const providerId = a.provider.localeCompare(b.provider);
    if (providerId !== 0) return providerId;

    const productTypes = [...ProductTypesByPriority, "PRIVATE_NETWORK", "PRIVATE_NETWORK_IP"];
    const type = productTypes.indexOf(a.productType) - productTypes.indexOf(b.productType);
    if (type !== 0) return type;
    const unit = allocationDisplayUnit(a).localeCompare(allocationDisplayUnit(b));
    if (unit !== 0) return unit;
    return a.name.localeCompare(b.name);
}

import {explainUnit, ProductTypesByPriority} from "@/Accounting";
import type {ProductCategoryV2} from "@/Accounting";
import {getProviderTitle} from "@/Providers/ProviderTitle";

export function allocationCompareProducts(a: ProductCategoryV2, b: ProductCategoryV2): number {
    const provider = getProviderTitle(a.provider).localeCompare(getProviderTitle(b.provider));
    if (provider !== 0) return provider;
    const providerId = a.provider.localeCompare(b.provider);
    if (providerId !== 0) return providerId;

    const productTypes = [...ProductTypesByPriority, "PRIVATE_NETWORK", "PRIVATE_NETWORK_IP"];
    const type = productTypes.indexOf(a.productType) - productTypes.indexOf(b.productType);
    if (type !== 0) return type;
    const unit = explainUnit(a).name.localeCompare(explainUnit(b).name);
    if (unit !== 0) return unit;
    return a.name.localeCompare(b.name);
}

import {ProductV2PrivateNetwork} from "@/Accounting";
import PrivateNetworkApi, {PrivateNetworkSupport} from "@/UCloud/PrivateNetworkApi";
import {retrieveSupportV2, SupportByProviderV2} from "@/UCloud/ResourceApi";
import {AsyncCache} from "@/Utilities/AsyncCache";

const privateNetworkSupportCache = new AsyncCache<
    SupportByProviderV2<ProductV2PrivateNetwork, PrivateNetworkSupport>
>({
    globalTtl: 60_000,
});

export function retrievePrivateNetworkSupport(
    projectId: string,
): Promise<SupportByProviderV2<ProductV2PrivateNetwork, PrivateNetworkSupport>> {
    return privateNetworkSupportCache.retrieve(
        projectId,
        () => retrieveSupportV2<ProductV2PrivateNetwork, PrivateNetworkSupport>(PrivateNetworkApi),
    );
}

export function retrievePrivateNetworkSupportFromCache(
    projectId: string,
): SupportByProviderV2<ProductV2PrivateNetwork, PrivateNetworkSupport> | undefined {
    return privateNetworkSupportCache.retrieveFromCacheOnly(projectId);
}

export function privateNetworkEthernetProductId(
    support: SupportByProviderV2<ProductV2PrivateNetwork, PrivateNetworkSupport>,
    providerId?: string,
): string | undefined {
    const entries = providerId
        ? support.productsByProvider[providerId] ?? []
        : Object.values(support.productsByProvider).flat();
    const productIds = new Set(entries
        .filter(it => (it.support.networkType || "ethernet") === "ethernet")
        .map(it => it.product.name));
    if (productIds.size !== 1) return undefined;
    const [productId] = productIds;
    return productId;
}

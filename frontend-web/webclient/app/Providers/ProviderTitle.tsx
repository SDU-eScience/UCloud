import * as React from "react";
import {capitalized} from "@/UtilityFunctions";
import {providerBrandingStore} from "@/ProviderBrandings/AutomaticProviderBranding";

export const ProviderTitle: React.FunctionComponent<{providerId: string}> = ({providerId}) => {
    return <>{getProviderTitle(providerId)}</>;
};

export function getProviderTitle(providerId: string): string {
    const title = providerBrandingStore.getProviderProperty(providerId, "title");
    return title ?? capitalized(providerId.replace("_", " ").replace("-", " "));
}
export function getShortProviderTitle(providerId: string): string {
    const shortTitle = providerBrandingStore.getProviderProperty(providerId, "shortTitle");
    return shortTitle ?? capitalized(providerId.replace("_", " ").replace("-", " "));
}

import * as React from "react";
import {capitalized} from "@/UtilityFunctions";
import {providerBrandingStore, useProviderProperty} from "@/ProviderBrandings/AutomaticProviderBranding";

export const ProviderTitle: React.FunctionComponent<{providerId: string}> = ({providerId}) => {
    const title = useProviderProperty(providerId, "title");
    return <>{providerTitleFromBranding(providerId, title)}</>;
};

export function getProviderTitle(providerId: string): string {
    const title = providerBrandingStore.getProviderProperty(providerId, "title");
    return providerTitleFromBranding(providerId, title);
}

export function getShortProviderTitle(providerId: string): string {
    const shortTitle = providerBrandingStore.getProviderProperty(providerId, "shortTitle");
    return providerTitleFromBranding(providerId, shortTitle);
}

function providerTitleFromBranding(providerId: string, title: string | undefined): string {
    return title ?? capitalized(providerId.replace("_", " ").replace("-", " "));
}

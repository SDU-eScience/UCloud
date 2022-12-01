import * as React from "react";
import ProviderInfo from "@/Assets/provider_info.json";
import {capitalized} from "@/UtilityFunctions";

interface ProviderInfo {
    id: string;
    title: string;
    logo: string | null;
}

export const ProviderTitle: React.FunctionComponent<{providerId: string}> = ({providerId}) => {
    return <>{getProviderTitle(providerId)}</>;
};

export function getProviderTitle(providerId: string): string {
    const providers: ProviderInfo[] = ProviderInfo.providers;
    const myInfo = providers.find(p => p.id === providerId);
    return myInfo?.title ?? capitalized(providerId.replace("_", " ").replace("-", " "));
}
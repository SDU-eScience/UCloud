import * as React from "react";
import ProviderInfo from "@/Assets/Providers/info.json";
import {capitalized} from "@/UtilityFunctions";

interface ProviderInfo {
    id: string;
    title: string;
    logo: string | null;
}

export const ProviderTitle: React.FunctionComponent<{providerId: string}> = ({providerId}) => {
    const providers: ProviderInfo[] = ProviderInfo.providers;
    const myInfo = providers.find(p => p.id === providerId);

    if (myInfo) return <>{myInfo.title}</>;
    return <>{capitalized(providerId.replace("_", " ").replace("-", " "))}</>;
};

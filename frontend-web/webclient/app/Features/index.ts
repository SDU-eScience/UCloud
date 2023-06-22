import {inDevEnvironment, onDevSite} from "@/UtilityFunctions";

export enum Feature {
    SSH,
    PROVIDER_CONNECTION,
    INLINE_TERMINAL,
    NEW_IDPS,
}

export function hasFeature(feature: Feature): boolean {
    if (feature == Feature.SSH) return true;
    if (feature == Feature.PROVIDER_CONNECTION) return true;

    if (localStorage.getItem("no-features") != null) return false;

    switch (feature) {
        case Feature.NEW_IDPS:
            return localStorage.getItem("new-idps") != null || inDevEnvironment() || onDevSite();

        case Feature.INLINE_TERMINAL:
            return localStorage.getItem("inline-terminal") != null && inDevEnvironment();

        default:
            if (inDevEnvironment() || onDevSite()) return true;
            break;
    }

    return false;
}

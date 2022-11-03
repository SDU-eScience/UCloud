import {inDevEnvironment, onDevSite} from "@/UtilityFunctions";

export enum Feature {
    SSH,
    PROVIDER_CONNECTION,
    INLINE_TERMINAL
}

export function hasFeature(feature: Feature): boolean {
    if (localStorage.getItem("no-features") != null) return false;

    switch (feature) {
        case Feature.INLINE_TERMINAL:
            return localStorage.getItem("inline-terminal") != null && inDevEnvironment();

        default:
            if (inDevEnvironment() || onDevSite()) return true;
            break;
    }

    
    switch (feature) {

    }
    return false;
}

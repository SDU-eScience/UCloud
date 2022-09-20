import {inDevEnvironment, onDevSite} from "@/UtilityFunctions";

export enum Feature {
    SSH,
    PROVIDER_CONNECTION
}

export function hasFeature(feature: Feature): boolean {
    if (localStorage.getItem("no-features") != null) return false;
    if (inDevEnvironment() || onDevSite()) return true;
    switch (feature) {

    }
    return false;
}

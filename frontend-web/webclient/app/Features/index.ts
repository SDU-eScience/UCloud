import {inDevEnvironment, onDevSite} from "@/UtilityFunctions";

export enum Feature {
    SSH,
    PROVIDER_CONNECTION,
    INLINE_TERMINAL,
    NEW_IDPS,
    COMPONENT_STORED_CUT_COPY,
    TRANSFER_TO,

    // NOTE(Dan, 27/06/23): Waiting for clarification if we are allowed to ask for this optional info under our
    // current policies.
    ADDITIONAL_USER_INFO,

    COPY_APP_MOCKUP,
}

export function hasFeature(feature: Feature): boolean {
    if (feature == Feature.SSH) return true;
    if (feature == Feature.PROVIDER_CONNECTION) return true;

    if (localStorage.getItem("no-features") != null) return false;

    switch (feature) {
        case Feature.NEW_IDPS:
            return localStorage.getItem("new-idps") != null || inDevEnvironment() || onDevSite();

        case Feature.ADDITIONAL_USER_INFO:
            return localStorage.getItem("additional-user-info") != null || inDevEnvironment() || onDevSite();

        case Feature.INLINE_TERMINAL:
            return localStorage.getItem("inline-terminal") != null && inDevEnvironment();

        case Feature.COPY_APP_MOCKUP:
            return localStorage.getItem("copy-app") != null
                || (inDevEnvironment() && window.location.hostname === "ucloud.localhost.direct");

        case Feature.COMPONENT_STORED_CUT_COPY:
            return localStorage.getItem("component-stored-cut-copy") != null || inDevEnvironment();

        case Feature.TRANSFER_TO:
            return localStorage.getItem("transfer-to") != null || inDevEnvironment();

        default:
            if (inDevEnvironment() || onDevSite()) return true;
            break;
    }

    return false;
}

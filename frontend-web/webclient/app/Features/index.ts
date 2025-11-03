import {inDevEnvironment, onDevSite} from "@/UtilityFunctions";

export enum Feature {
    PROVIDER_CONNECTION,
    NEW_IDPS,
    COMPONENT_STORED_CUT_COPY,
    TRANSFER_TO,

    // NOTE(Dan, 27/06/23): Waiting for clarification if we are allowed to ask for this optional info under our
    // current policies.
    ADDITIONAL_USER_INFO,


    PROVIDER_CONDITION,


    ALTERNATIVE_USAGE_SELECTOR,
    NEW_SYNCTHING_UI,

    HIDE_PROJECTS,

    CORE2,

    // MISSING BACKEND SUPPORT
    JOB_RENAME,
    REORDER_APP_GROUP,
}

enum Environment {
    LOCAL_DEV_STACK,
    LOCAL_DEV,
    PUBLIC_DEV,
    SANDBOX_DEV,
    PROD
}

const noEnvironments: Environment[] = [];

const allLocalEnvironments: Environment[] =
    [Environment.LOCAL_DEV, Environment.LOCAL_DEV_STACK];

const allDevEnvironments: Environment[] =
    [...allLocalEnvironments, Environment.SANDBOX_DEV, Environment.PUBLIC_DEV];

const allEnvironments: Environment[] =
    [...allDevEnvironments, Environment.PROD];

function publicFeature(feature: Feature): FeatureConfig {
    return {
        feature,
        showWithoutFlag: allEnvironments,
    };
}

interface FeatureConfig {
    feature: Feature;
    showWithoutFlag?: Environment[];
    showWithFlag?: Environment[];
}

const featureMap: Record<string, FeatureConfig> = {
    "provider-connection": publicFeature(Feature.PROVIDER_CONNECTION),

    "new-idps": {
        feature: Feature.NEW_IDPS,
        showWithoutFlag: allDevEnvironments,
        showWithFlag: allEnvironments,
    },

    "additional-user-info": {
        feature: Feature.ADDITIONAL_USER_INFO,
        showWithoutFlag: allDevEnvironments,
        showWithFlag: allEnvironments,
    },

    "component-stored-cut-copy": {
        feature: Feature.COMPONENT_STORED_CUT_COPY,
        showWithoutFlag: allEnvironments,
    },

    "transfer-to": {
        feature: Feature.TRANSFER_TO,
        showWithoutFlag: allEnvironments,
    },

    "provider-condition": {
        feature: Feature.PROVIDER_CONDITION,
        showWithoutFlag: allDevEnvironments,
        showWithFlag: allEnvironments,
    },

    "job-rename": {
        feature: Feature.JOB_RENAME,
        showWithFlag: allDevEnvironments,
        showWithoutFlag: allLocalEnvironments
    },

    "alternative-usage-selector": {
        feature: Feature.ALTERNATIVE_USAGE_SELECTOR,
        showWithoutFlag: allEnvironments,
        showWithFlag: allEnvironments,
    },

    "new-syncthing-ui": {
        feature: Feature.NEW_SYNCTHING_UI,
        showWithoutFlag: allEnvironments,
        showWithFlag: allEnvironments,
    },

    "reorder-app-group": {
        feature: Feature.REORDER_APP_GROUP,
        showWithoutFlag: allDevEnvironments,
        showWithFlag: allDevEnvironments,
    },

    "hide-projects": {
        feature: Feature.HIDE_PROJECTS,
        showWithFlag: allDevEnvironments,
        showWithoutFlag: noEnvironments
    },

    "core2": {
        feature: Feature.CORE2,
        showWithFlag: allLocalEnvironments,
        showWithoutFlag: noEnvironments,
    }
};

function getCurrentEnvironment(): Environment {
    if (window.location.hostname === "sandbox.dev.cloud.sdu.dk") return Environment.SANDBOX_DEV;

    if (inDevEnvironment()) {
        if (window.location.hostname === "ucloud.localhost.direct") return Environment.LOCAL_DEV_STACK;
        else return Environment.LOCAL_DEV;
    } else if (onDevSite()) {
        return Environment.PUBLIC_DEV;
    } else {
        return Environment.PROD;
    }
}

export function hasFeature(feature: Feature): boolean {
    if (feature == Feature.PROVIDER_CONNECTION) return true;

    const env = getCurrentEnvironment();
    for (const [key, config] of Object.entries(featureMap)) {
        if (config.feature !== feature) continue;

        const withFlag = config.showWithFlag ?? [];
        const withoutFlag = config.showWithoutFlag ?? [];
        const flagValue = localStorage.getItem(key);
        if (flagValue === "false") return false;
        if (withoutFlag.indexOf(env) !== -1) return true;
        if (withFlag.indexOf(env) !== -1 && flagValue != null) return true;
    }
    return false;
}

import {inDevEnvironment, onDevSite} from "@/UtilityFunctions";

export enum Feature {
    PROVIDER_CONNECTION,
    INLINE_TERMINAL,
    NEW_IDPS,
    COMPONENT_STORED_CUT_COPY,
    TRANSFER_TO,

    // NOTE(Dan, 27/06/23): Waiting for clarification if we are allowed to ask for this optional info under our
    // current policies.
    ADDITIONAL_USER_INFO,

    COPY_APP_MOCKUP,

    APP_CATALOG_FILTER,

    NEW_TASKS
}

enum Environment {
    LOCAL_DEV_STACK,
    LOCAL_DEV,
    PUBLIC_DEV,
    PROD
}

const allEnvironments: Environment[] =
    [Environment.LOCAL_DEV, Environment.LOCAL_DEV_STACK, Environment.PUBLIC_DEV, Environment.PROD];

const allDevEnvironments: Environment[] =
    [Environment.LOCAL_DEV, Environment.LOCAL_DEV_STACK, Environment.PUBLIC_DEV];

const allLocalEnvironments: Environment[] =
    [Environment.LOCAL_DEV, Environment.LOCAL_DEV_STACK];

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

    "inline-terminal": {
        feature: Feature.INLINE_TERMINAL,
        showWithFlag: allLocalEnvironments,
    },

    "copy-app": {
        feature: Feature.COPY_APP_MOCKUP,
        showWithFlag: allDevEnvironments,
    },

    "component-stored-cut-copy": {
        feature: Feature.COMPONENT_STORED_CUT_COPY,
        showWithoutFlag: allDevEnvironments,
        showWithFlag: allEnvironments,
    },

    "app-catalog-filter": {
        feature: Feature.APP_CATALOG_FILTER,
        showWithoutFlag: allDevEnvironments,
        showWithFlag: allEnvironments
    },

    "transfer-to": {
        feature: Feature.TRANSFER_TO,
        showWithoutFlag: allDevEnvironments,
    },

    "new-tasks": {
        feature: Feature.NEW_TASKS,
        showWithoutFlag: allLocalEnvironments,
        showWithFlag: allEnvironments,
    }
};

function getCurrentEnvironment(): Environment {
    if (inDevEnvironment()) {
        if (window.location.hostname === "ucloud.localhost.direct") return Environment.LOCAL_DEV_STACK
        else return Environment.LOCAL_DEV
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
        if (withoutFlag.indexOf(env) !== -1) return true;
        if (withFlag.indexOf(env) !== -1 && localStorage.getItem(key) !== null) return true;
    }
    return false;
}

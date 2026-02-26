import {inDevEnvironment, onDevSite} from "@/UtilityFunctions";

export enum Feature {
    COMPONENT_STORED_CUT_COPY,

    REORDER_APP_GROUP,

    ALLOCATIONS_PAGE_IMPROVEMENTS,
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

const localAndDevEnvironment: Environment[] =
    [...allLocalEnvironments, Environment.PUBLIC_DEV];

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
    "component-stored-cut-copy": {
        feature: Feature.COMPONENT_STORED_CUT_COPY,
        showWithoutFlag: allEnvironments,
    },

    "reorder-app-group": {
        feature: Feature.REORDER_APP_GROUP,
        showWithoutFlag: allDevEnvironments,
        showWithFlag: allDevEnvironments,
    },

    "allocations-improvements": {
        feature: Feature.ALLOCATIONS_PAGE_IMPROVEMENTS,
        showWithFlag: allLocalEnvironments,
        showWithoutFlag: noEnvironments,
    },
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

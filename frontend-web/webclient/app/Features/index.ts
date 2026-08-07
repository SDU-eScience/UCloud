import {inDevEnvironment, onDevSite} from "@/UtilityFunctions";

export enum Feature {
    REORDER_APP_GROUP,

    STACKS,

    INFERENCE,

    INFERENCE_WORKSPACE,

    FILE_BROWSER_STATUS_BAR,

    CONTAINER_REPOSITORIES,
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
    "reorder-app-group": {
        feature: Feature.REORDER_APP_GROUP,
        showWithoutFlag: allDevEnvironments,
        showWithFlag: allDevEnvironments,
    },

    "stacks": {
        feature: Feature.STACKS,
        showWithoutFlag: allDevEnvironments,
        showWithFlag: allEnvironments,
    },

    "inference": {
        feature: Feature.INFERENCE,
        showWithoutFlag: [Environment.PUBLIC_DEV, Environment.LOCAL_DEV_STACK],
        showWithFlag: allEnvironments,
    },

    "inference-workspace": {
        feature: Feature.INFERENCE_WORKSPACE,
        showWithoutFlag: allLocalEnvironments,
        showWithFlag: allEnvironments,
    },

    "file-browser-status-bar": {
        feature: Feature.FILE_BROWSER_STATUS_BAR,
        showWithoutFlag: allDevEnvironments,
        showWithFlag: allEnvironments,
    },

    "container-repositories": {
        feature: Feature.CONTAINER_REPOSITORIES,
        showWithoutFlag: allDevEnvironments,
        showWithFlag: allEnvironments,
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

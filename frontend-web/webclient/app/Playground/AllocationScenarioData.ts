import type {WalletV2} from "@/Accounting";
import type {ProjectInfo} from "@/Project/InfoCache";
import {allocationNormalize} from "@/Accounting/Allocations/AllocationModel";
import type {AllocationAmounts} from "@/Accounting/Allocations/AllocationModel";

export interface AllocationScenarioCheckpoint {
    label: string;
    now: number;
    wallets: WalletV2[];
    walletsByProject: Record<string, WalletV2[]>;
    expected: Pick<AllocationAmounts, "granted" | "used" | "activeQuota" | "activeUsed" | "activeBalance" | "retiredUsed" | "expiredUnused" | "future">;
    checksByProject?: Record<string, {expected: AllocationScenarioCheckpoint["expected"]}>;
    childProjectId: string;
    category: {name: string; provider: string};
}

export interface AllocationScenarioData {
    formatVersion: 1;
    scenario: string;
    description: string;
    projectId: string;
    projectInfo: Record<string, ProjectInfo>;
    projects: Record<string, string>;
    checkpoints: AllocationScenarioCheckpoint[];
    failure?: string;
}

export function allocationScenarioRead(text: string): AllocationScenarioData {
    const value = JSON.parse(text) as AllocationScenarioData;
    const valid = value?.formatVersion === 1 && typeof value.projectId === "string"
        && Array.isArray(value.checkpoints) && value.checkpoints.length > 0;
    if (!valid) throw new Error("Expected a version 1 accounting scenario file with at least one checkpoint.");
    for (const checkpoint of value.checkpoints) {
        const validCheckpoint = Number.isFinite(checkpoint.now) && Array.isArray(checkpoint.wallets)
            && checkpoint.expected && checkpoint.category && typeof checkpoint.childProjectId === "string";
        if (!validCheckpoint) throw new Error("Invalid accounting scenario checkpoint.");
        allocationNormalize(checkpoint.wallets, checkpoint.now);
    }
    return value;
}

export function allocationScenarioFailures(checkpoint: AllocationScenarioCheckpoint): string[] {
    const model = allocationNormalize(checkpoint.wallets, checkpoint.now);
    const productsByWorkspace = new Map(model.products.filter(product =>
        product.category.name === checkpoint.category.name && product.category.provider === checkpoint.category.provider
    ).map(product => [product.workspaceKey, product] as const));
    const checks = checkpoint.checksByProject ?? {[checkpoint.childProjectId]: {expected: checkpoint.expected}};
    return Object.entries(checks).flatMap(([projectId, {expected}]) => {
        const product = productsByWorkspace.get(`project:${projectId}`);
        const prefix = checkpoint.checksByProject ? `${projectId}: ` : "";
        if (!product) return [`${prefix}Expected funding relationship is missing.`];
        const fields = Object.keys(expected) as (keyof AllocationScenarioCheckpoint["expected"])[];
        return fields.flatMap(field => product.raw[field] === expected[field] ? [] :
            [`${prefix}${field}: expected ${expected[field]}, got ${product.raw[field]}`]);
    });
}

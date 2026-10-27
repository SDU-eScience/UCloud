export type PrivateNetworkType = "ethernet" | "infiniband";
export type NetworkReclaimState = "active" | "pending" | "reclaiming";

export interface PrivateNetworkTypeSpecification {
    type?: PrivateNetworkType;
}

export interface PrivateNetworkTypeResource {
    specification?: PrivateNetworkTypeSpecification;
    status?: {
        resolvedSupport?: {
            support?: {networkType?: PrivateNetworkType};
        };
    };
}

export function privateNetworkTypeFromSpecification(
    specification: PrivateNetworkTypeSpecification | undefined,
): PrivateNetworkType {
    if (specification?.type) return specification.type;
    return "ethernet";
}

export function privateNetworkTypeFromNetwork(network: PrivateNetworkTypeResource | null | undefined): PrivateNetworkType {
    return network?.status?.resolvedSupport?.support?.networkType ||
        privateNetworkTypeFromSpecification(network?.specification);
}

export function privateNetworkTypeLabel(type: PrivateNetworkType): string {
    return type === "infiniband" ? "InfiniBand" : "Ethernet";
}

export function privateNetworkTypeLongLabel(type: PrivateNetworkType): string {
    return type === "infiniband" ? "InfiniBand network" : "Ethernet network";
}

export function networkReclaimStateLabel(state: NetworkReclaimState): string {
    switch (state) {
        case "pending":
            return "Pending deletion";
        case "reclaiming":
            return "Reclaiming";
        default:
            return "Active";
    }
}

import {
    ProductSupport,
    Resource,
    ResourceApi, ResourceIncludeFlags,
    ResourceSpecification,
    ResourceStatus,
    ResourceUpdate
} from "UCloud/ResourceApi";
import {accounting} from "UCloud/index";
import ProductNS = accounting.ProductNS;

export interface IngressSpecification extends ResourceSpecification {
    domain: string;
}

export type IngressState = "READY" | "PREPARING" | "UNAVAILABLE";

export interface IngressStatus extends ResourceStatus {
    boundTo?: string;
    state: IngressState;
}

export interface IngressSupport extends ProductSupport {
    domainPrefix: string;
    domainSuffix: string;
}

export interface IngressUpdate extends ResourceUpdate {
    state?: IngressState;
    didBind: boolean;
    newBinding?: string;
}

export interface Ingress extends Resource<IngressUpdate, IngressStatus, IngressSpecification> {}

class IngressApi extends ResourceApi<Ingress, ProductNS.Ingress, IngressSpecification, IngressUpdate,
    ResourceIncludeFlags, IngressStatus, IngressSupport> {
    title = "Public Link";

    constructor() {
        super("ingresses");
    }
}

export default new IngressApi();

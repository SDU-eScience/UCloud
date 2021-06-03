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

interface IngressSpecification extends ResourceSpecification {
    domain: string;
}

type IngressState = "READY" | "PREPARING" | "UNAVAILABLE";

interface IngressStatus extends ResourceStatus {
    boundTo?: string;
    state: IngressState;
}

interface IngressSupport extends ProductSupport {
    domainPrefix: string;
    domainSuffix: string;
}

interface IngressUpdate extends ResourceUpdate {
    state?: IngressState;
    didBind: boolean;
    newBinding?: string;
}

interface Ingress extends Resource<IngressUpdate, IngressStatus, IngressSpecification> {}

class IngressApi extends ResourceApi<Ingress, ProductNS.Ingress, IngressSpecification, IngressUpdate,
    ResourceIncludeFlags, IngressStatus, IngressSupport> {
    constructor() {
        super("ingresses");
    }
}

export default new IngressApi();

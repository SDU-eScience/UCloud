import * as React from "react";
import {Icon} from "@/ui-components";
import {ItemRenderer} from "@/ui-components/Browse";
import {ProductStorage} from "@/Accounting";
import {Operation} from "@/ui-components/Operation";
import {Client} from "@/Authentication/HttpClientInstance";
import {apiBrowse, apiUpdate} from "@/Authentication/DataHook";
import {BulkRequest, BulkResponse, PageV2, PaginationRequestV2} from "@/UCloud";
import {
    CREATE_TAG,
    ProductSupport,
    Resource,
    ResourceApi,
    ResourceBrowseCallbacks,
    ResourceIncludeFlags,
    ResourceSpecification,
    ResourceStatus,
    ResourceUpdate,
} from "@/UCloud/ResourceApi";

export interface ContainerRepositorySpecification extends ResourceSpecification {
    name: string;
}

export type ContainerRepositoryStatus = ResourceStatus;

export type ContainerRepositoryUpdate = ResourceUpdate;

export type ContainerRepositoryFlags = ResourceIncludeFlags;

export interface ContainerRepositorySupport extends ProductSupport {
    collection?: {
        usersCanCreate?: boolean;
    };
    containerRepositories?: {
        enabled?: boolean;
        server?: string;
    };
}

export type ContainerRepository = Resource<
    ContainerRepositoryUpdate,
    ContainerRepositoryStatus,
    ContainerRepositorySpecification
>;

export interface ContainerRepositoryImageLayer {
    digest: string;
    mediaType: string;
    sizeInBytes: number;
    platforms: string[];
}

export interface ContainerRepositoryImage {
    kind: "IMAGE" | "TAG";
    name: string;
    repository: string;
    tag: string;
    tagCount: number;
    digest: string;
    mediaType: string;
    sizeInBytes: number;
    layers: ContainerRepositoryImageLayer[];
}

export interface DeleteContainerRepositoryImageRequest {
    repositoryId: string;
    repository: string;
    tag: string;
}

class ContainerRepositoriesApi extends ResourceApi<
    ContainerRepository,
    ProductStorage,
    ContainerRepositorySpecification,
    ContainerRepositoryUpdate,
    ContainerRepositoryFlags,
    ContainerRepositoryStatus,
    ContainerRepositorySupport
> {
    routingNamespace = "container-repositories";
    title = "Container repository";
    productType = "STORAGE" as const;

    renderer: ItemRenderer<ContainerRepository> = {
        MainTitle({resource}) {
            const name = resource?.specification?.name ?? "";
            return <span title={name}>{name}</span>;
        },
        Icon({size}) {
            return <Icon name="heroArchiveBox" size={size} />;
        },
    };

    constructor() {
        super("containerRepositories");
        this.sortEntries.unshift({
            icon: "heroArchiveBox",
            title: "Name",
            column: "name",
            helpText: "Name of the container repository",
        });
    }

    get titlePlural(): string {
        return "Container repositories";
    }

    browseImages(request: PaginationRequestV2 & {repositoryId: string; repository?: string; tag?: string}): APICallParameters<
        PaginationRequestV2 & {repositoryId: string; repository?: string; tag?: string},
        PageV2<ContainerRepositoryImage>
    > {
        return apiBrowse(request, this.baseContext, "images");
    }

    deleteImage(request: BulkRequest<DeleteContainerRepositoryImageRequest>): APICallParameters<
        BulkRequest<DeleteContainerRepositoryImageRequest>,
        BulkResponse<Record<string, never>>
    > {
        return apiUpdate(request, this.baseContext, "deleteImage");
    }

    retrieveOperations(): Operation<
        ContainerRepository,
        ResourceBrowseCallbacks<ContainerRepository, ProductStorage>
    >[] {
        const operations = super.retrieveOperations();
        const create = operations.find(operation => operation.tag === CREATE_TAG);
        if (create) {
            const enabled = create.enabled;
            create.enabled = (selected, callbacks, all) => {
                const isEnabled = enabled(selected, callbacks, all);
                if (isEnabled !== true) return isEnabled;

                const supportByProvider = callbacks.supportByProvider.productsByProvider;
                const providers = Object.values(supportByProvider);
                if (providers.length === 0) {
                    return "You have no resources to create container repositories for.";
                }

                const anySupported = providers.some(products => products.some(entry => {
                    const support = entry.support as ContainerRepositorySupport;
                    return support.containerRepositories?.enabled === true && support.collection?.usersCanCreate === true;
                }));
                if (!anySupported) return false;

                if (!Client.hasActiveProject) {
                    return "Container repositories can only be created in a project.";
                }

                if (!callbacks.isWorkspaceAdmin) {
                    return "Only project administrators can create a new container repository!";
                }
                return true;
            };
        }
        return operations;
    }
}

const api = new ContainerRepositoriesApi();
export {api};
export default api;

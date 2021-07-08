import {
    ProductSupport,
    Resource,
    ResourceApi,
    ResourceIncludeFlags,
    ResourceSpecification,
    ResourceStatus,
    ResourceUpdate
} from "UCloud/ResourceApi";
import {SidebarPages} from "ui-components/Sidebar";
import {Icon} from "ui-components";
import {EnumFilter} from "Resource/Filter";
import * as React from "react";
import {accounting} from "UCloud/index";
import ProductNS = accounting.ProductNS;

export interface FileCollection extends Resource<FileCollectionUpdate, FileCollectionStatus, FileCollectionSpecification> {

}

export interface FileCollectionUpdate extends ResourceUpdate {}
export interface FileCollectionStatus extends ResourceStatus {}
export interface FileCollectionSpecification extends ResourceSpecification {
    title: string;
}
export interface FileCollectionFlags extends ResourceIncludeFlags {}
export interface FileCollectionSupport extends ProductSupport {
    stats: {
        sizeInBytes?: boolean;
        sizeIncludingChildrenInBytes?: boolean;

        modifiedAt?: boolean;
        createdAt?: boolean;
        accessedAt?: boolean;

        unixPermissions?: boolean;
        unixOwner?: boolean;
        unixGroup?: boolean;
    },

    collection: {
        aclSupported?: boolean;
        aclModifiable?: boolean;

        usersCanCreate?: boolean;
        usersCanDelete?: boolean;
        usersCanRename?: boolean;

        searchSupported?: boolean;
    },

    files: {
        aclSupported?: boolean;
        aclModifiable?: boolean;

        trashSupported?: boolean;
        isReadOnly?: boolean;
    }
}

class IngressApi extends ResourceApi<FileCollection, ProductNS.Storage, FileCollectionSpecification,
    FileCollectionUpdate, FileCollectionFlags, FileCollectionStatus, FileCollectionSupport> {
    routingNamespace = "drives";
    title = "Drive";
    page = SidebarPages.Files;

    InlineTitleRenderer = ({resource}) => <>{(resource as FileCollection).specification.title}</>;
    IconRenderer = ({resource, size}) => <Icon name={"ftFileSystem"} size={size}/>
    TitleRenderer = this.InlineTitleRenderer;

    constructor() {
        super("files.collections");

        this.sortEntries.push({
            icon: "id",
            title: "Name",
            column: "title",
            helpText: "Name of the drive, for example: Research data"
        });
    }
}

export default new IngressApi();

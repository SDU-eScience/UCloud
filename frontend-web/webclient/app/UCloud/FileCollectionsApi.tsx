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
import * as React from "react";
import * as H from "history";
import {buildQueryString} from "Utilities/URIUtilities";
import {ItemRenderer} from "ui-components/Browse";
import {ProductStorage} from "Accounting";

export type FileCollection = Resource<FileCollectionUpdate, FileCollectionStatus, FileCollectionSpecification>;

export type FileCollectionUpdate = ResourceUpdate;
export type FileCollectionStatus = ResourceStatus;
export interface FileCollectionSpecification extends ResourceSpecification {
    title: string;
}
export type FileCollectionFlags = ResourceIncludeFlags;
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

class FileCollectionsApi extends ResourceApi<FileCollection, ProductStorage, FileCollectionSpecification,
    FileCollectionUpdate, FileCollectionFlags, FileCollectionStatus, FileCollectionSupport> {
    routingNamespace = "drives";
    title = "Drive";
    page = SidebarPages.Files;
    productType = "STORAGE" as const;

    renderer: ItemRenderer<FileCollection> = {
        MainTitle({resource}) {return <>{resource?.specification?.title ?? ""}</>},
        Icon({resource, size}) {return <Icon name={"ftFileSystem"} size={size} />}
    };

    constructor() {
        super("files.collections");

        this.sortEntries.push({
            icon: "id",
            title: "Name",
            column: "title",
            helpText: "Name of the drive, for example: Research data"
        });
    }

    navigateToChildren(history: H.History, resource: FileCollection) {
        history.push(buildQueryString("/files", {path: `/${resource.id}`}))
    }
}

export default new FileCollectionsApi();

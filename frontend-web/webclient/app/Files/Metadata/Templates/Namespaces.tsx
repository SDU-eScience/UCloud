import * as React from "react";
import {default as Api, FileMetadataTemplate, FileMetadataTemplateNamespace} from "@/UCloud/MetadataNamespaceApi";
import {ResourceBrowse} from "@/Resource/Browse";
import {ResourceRouter} from "@/Resource/Router";
import Create from "@/Files/Metadata/Templates/Create";
import {BrowseType} from "@/Resource/BrowseType";

export const MetadataNamespacesBrowse: React.FunctionComponent<{
    onSelect?: (selection: FileMetadataTemplateNamespace) => void;
    isSearch?: boolean;
    browseType: BrowseType;
    onTemplateSelect?: (selection: FileMetadataTemplate) => void;
}> = ({onTemplateSelect, ...props}) => {
    return <ResourceBrowse api={Api} {...props} propsForInlineResources={{onTemplateSelect}} />;
};

const MetadataNamespacesRouter: React.FunctionComponent = () => {
    return <ResourceRouter api={Api} Browser={MetadataNamespacesBrowse} Create={Create} />;
};

export default MetadataNamespacesRouter;

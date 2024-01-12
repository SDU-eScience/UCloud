import {
    Resource,
    ResourceApi, ResourceBrowseCallbacks,
    ResourceIncludeFlags,
    ResourceSpecification,
    ResourceStatus,
    ResourceUpdate
} from "@/UCloud/ResourceApi";
import {BulkRequest, FindByStringId, PaginationRequestV2} from "@/UCloud/index";
import {Grid, Icon} from "@/ui-components";
import * as React from "react";
import {buildQueryString} from "@/Utilities/URIUtilities";
import {Operation, ShortcutKey} from "@/ui-components/Operation";
import {ItemRenderer, StandardCallbacks, StandardList} from "@/ui-components/Browse";
import {ListRowStat} from "@/ui-components/List";
import {ResourceProperties} from "@/Resource/Properties";
import TitledCard from "@/ui-components/HighlightedCard";
import {SvgFt} from "@/ui-components/FtIcon";
import {dateToString} from "@/Utilities/DateUtilities";
import {useCallback, useMemo, useState} from "react";
import {Section} from "@/ui-components/Section";
import * as Heading from "@/ui-components/Heading";
import {prettierString} from "@/UtilityFunctions";
import {Product} from "@/Accounting";
import {apiBrowse, apiCreate, apiRetrieve} from "@/Authentication/DataHook";
import {getCssPropertyValue} from "@/Utilities/StylingUtilities";

export type FileMetadataTemplateNamespaceType = "COLLABORATORS" | "PER_USER";

export type FileMetadataTemplateNamespace = Resource<FileMetadataTemplateNamespaceUpdate,
    FileMetadataTemplateNamespaceStatus, FileMetadataTemplateNamespaceSpecification>;

export type FileMetadataTemplateNamespaceUpdate = ResourceUpdate;

export interface FileMetadataTemplateNamespaceStatus extends ResourceStatus {
    latestTitle?: string;
}

export interface FileMetadataTemplateNamespaceSpecification extends ResourceSpecification {
    name: string;
    namespaceType: FileMetadataTemplateNamespaceType;
}

export interface FileMetadataTemplate {
    namespaceId: string;
    namespaceName?: string;
    title: string;
    version: string;
    schema: Record<string, any>;
    inheritable: boolean;
    requireApproval: boolean;
    description: string;
    changeLog: string;
    uiSchema?: Record<string, any>;
    namespaceType: FileMetadataTemplateNamespaceType;
    createdAt?: number;
}

export interface FileMetadataTemplateAndVersion {
    id: string;
    version: string;
}

export interface FileMetadataTemplateFlags extends ResourceIncludeFlags {
    filterName?: string;
}

interface TemplateCallbacks {
    namespace: FileMetadataTemplateNamespace;
    setPreviewing: (previewing: FileMetadataTemplate | null) => void;
    previewing: FileMetadataTemplate | null;
}

class MetadataNamespaceApi extends ResourceApi<FileMetadataTemplateNamespace, Product,
    FileMetadataTemplateNamespaceSpecification, FileMetadataTemplateNamespaceUpdate, FileMetadataTemplateFlags,
    FileMetadataTemplateNamespaceStatus> {
    routingNamespace = "metadata";
    title = "Metadata Template";
    productType = undefined;
    isCoreResource = true;

    renderer: ItemRenderer<FileMetadataTemplateNamespace, StandardCallbacks<FileMetadataTemplate> & TemplateCallbacks> = {
        Icon({resource, size}) {return <Icon name={"docs"} size={size} />},
        MainTitle({resource}) {
            return <>{resource?.status?.latestTitle ?? resource?.specification?.name ?? ""}</>
        },
        Stats({resource}) {
            return !resource ? null : <>
                <ListRowStat icon={"id"}>{resource.specification.name}</ListRowStat>
            </>
        }
    };

    templateRenderer: ItemRenderer<FileMetadataTemplate, TemplateCallbacks> = {
        Icon({resource, size}) {
            return <SvgFt width={size} height={size} type={"text"} ext={""}
                          color={getCssPropertyValue("FtIconColor")} color2={getCssPropertyValue("FtIconColor2")}
                          hasExt={false} />
        },
        MainTitle({resource}) {return !resource ? null : <>{resource.title}</>},
        Stats({resource}) {
            return !resource ? null : <>
                <ListRowStat icon={"calendar"}>{dateToString(resource.createdAt ?? 0)}</ListRowStat>
                <ListRowStat icon={"hashtag"}>{resource.version}</ListRowStat>
            </>
        }
    };

    private templateOps: Operation<FileMetadataTemplate, StandardCallbacks<FileMetadataTemplate> & TemplateCallbacks>[] = [
        {
            icon: "backward",
            text: "Back to versions",
            primary: true,
            enabled: (selected, cb) => cb.previewing != null,
            onClick: (selected, cb) => {
                cb.setPreviewing(null);
            },
            shortcut: ShortcutKey.Backspace
        },
        {
            icon: "upload",
            text: "Create new version",
            primary: true,
            enabled: (selected, cb) => selected.length === 0 && cb.previewing == null,
            onClick: (selected, cb) => {
                cb.navigate(buildQueryString("/" + this.routingNamespace + "/create", {namespace: cb.namespace.id}));
            },
            shortcut: ShortcutKey.U
        },
        {
            icon: "properties",
            text: "Properties",
            enabled: (selected, cb) => selected.length === 1 && cb.previewing == null,
            onClick: (selected, cb) => {
                cb.setPreviewing(selected[0]);
            },
            shortcut: ShortcutKey.P
        }
    ];

    private TemplateBrowse: React.FunctionComponent<{
        resource: FileMetadataTemplateNamespace;
        reload: () => void;
        onSelect?: (template: FileMetadataTemplate) => void;
    }> = (props) => {
        const [previewing, setPreviewing] = useState<FileMetadataTemplate | null>(null);
        const extraCallbacks: TemplateCallbacks = useMemo((() => ({
            previewing, setPreviewing,
            namespace: props.resource
        })), [previewing, setPreviewing, props.resource]);
        const generateCall = useCallback((next?: string): APICallParameters => {
            return this.browseTemplates({id: props.resource.id, next, itemsPerPage: 50})
        }, []);
        return <TitledCard>
            <StandardList
                generateCall={generateCall}
                renderer={this.templateRenderer}
                operations={this.templateOps}
                title={"Version"}
                embedded={"inline"}
                onSelect={props.onSelect}
                extraCallbacks={extraCallbacks}
                navigate={it => setPreviewing(it)}
                hide={!!previewing}
            />
            {previewing ? <>
                <Grid gridGap={"32px"} width={"800px"} margin={"10px auto"}>
                    <Section>
                        <Heading.h3>Information</Heading.h3>
                        <ul>
                            <li><b>ID: </b>{props.resource.specification.name}</li>
                            <li><b>Title: </b>{previewing.title}</li>
                            <li><b>Description: </b>{previewing.description}</li>
                        </ul>
                    </Section>
                    <Section>
                        <Heading.h3>Versioning</Heading.h3>
                        <ul>
                            <li><b>Version: </b>{previewing.version}</li>
                            <li><b>Changes since last version: </b>{previewing.changeLog}</li>
                        </ul>
                    </Section>
                    <Section>
                        <Heading.h3>Behavior</Heading.h3>
                        <ul>
                            <li><b>Namespace type: </b>{prettierString(previewing.namespaceType)}</li>
                            <li><b>Changes require approval: </b>{previewing.requireApproval ? "Yes" : "No"}
                            </li>
                            <li>
                                <b>Metadata should be inherited from ancestor directories: </b>
                                {previewing.inheritable ? "Yes" : "No"}
                            </li>
                        </ul>
                    </Section>
                </Grid>
            </> : null}
        </TitledCard>
    };

    Properties = (props) => {
        const contentChildren = useMemo(() => ({resource, reload}) => {
            return <this.TemplateBrowse
                resource={resource as FileMetadataTemplateNamespace} reload={reload}
                onSelect={props["onTemplateSelect"]} />
        },
            []
        );
        return <ResourceProperties
            {...props} api={this}
            showMessages={false}
            showPermissions={true}
            ContentChildren={contentChildren}
        />
    };

    constructor() {
        super("files.metadataTemplates");
    }

    retrieveOperations(): Operation<FileMetadataTemplateNamespace, ResourceBrowseCallbacks<FileMetadataTemplateNamespace>>[] {
        return super.retrieveOperations();
    }

    createTemplate(request: BulkRequest<FileMetadataTemplate>): APICallParameters<BulkRequest<FileMetadataTemplate>> {
        return apiCreate(request, this.baseContext, "templates");
    }

    retrieveLatest(request: FindByStringId): APICallParameters<FindByStringId> {
        return apiRetrieve(request, this.baseContext, "latest")
    }

    /* Unused  */
    retrieveTemplate(request: FileMetadataTemplateAndVersion): APICallParameters<FileMetadataTemplateAndVersion> {
        return apiRetrieve(request, this.baseContext, "templates");
    }

    browseTemplates(request: FindByStringId & PaginationRequestV2): APICallParameters<FindByStringId & PaginationRequestV2> {
        return apiBrowse(request, this.baseContext, "templates");
    }
}

export default new MetadataNamespaceApi()

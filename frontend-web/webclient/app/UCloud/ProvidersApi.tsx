import * as React from "react";
import {useCallback, useEffect, useMemo} from "react";
import {
    CREATE_TAG,
    PERMISSIONS_TAG,
    ProductSupport,
    PROPERTIES_TAG,
    Resource,
    ResourceApi,
    ResourceBrowseCallbacks,
    ResourceIncludeFlags,
    ResourceSpecification,
    ResourceStatus,
    ResourceUpdate,
} from "@/UCloud/ResourceApi";
import {Box, Icon, TextArea} from "@/ui-components";
import {ItemRenderer} from "@/ui-components/Browse";
import * as Types from "@/Accounting";
import {
    Product,
} from "@/Accounting";
import {ListRowStat} from "@/ui-components/List";
import {ResourceProperties} from "@/Resource/Properties";
import TitledCard from "@/ui-components/HighlightedCard";
import {doNothing} from "@/UtilityFunctions";
import {apiUpdate, InvokeCommand, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import * as UCloud from "@/UCloud/index";
import {BulkRequest, PageV2} from "@/UCloud/index";
import {useToggleSet} from "@/Utilities/ToggleSet";
import {Operation, Operations, ShortcutKey} from "@/ui-components/Operation";
import {Client} from "@/Authentication/HttpClientInstance";
import {ResourcePermissionEditor} from "@/Resource/PermissionEditor";
import {dialogStore} from "@/Dialog/DialogStore";
import {emptyPageV2} from "@/Utilities/PageUtilities";

export interface ProviderSpecification extends ResourceSpecification {
    id: string;
    domain: string;
    https: boolean;
    port?: number | null;
}

export interface ProviderStatus extends ResourceStatus {
}

export interface ProviderSupport extends ProductSupport {
}

export interface ProviderUpdate extends ResourceUpdate {
}

export interface ProviderFlags extends ResourceIncludeFlags {
}

export interface Provider extends Resource<ProviderUpdate, ProviderStatus, ProviderSpecification> {
    refreshToken: string;
    publicKey: string;
}

interface ProductCallbacks {
    invokeCommand: InvokeCommand;
}

interface ProviderCallbacks {
    editProvider: (product: Provider) => void;
}

class ProviderApi extends ResourceApi<Provider, Product, ProviderSpecification, ProviderUpdate,
    ProviderFlags, ProviderStatus, ProviderSupport> {
    routingNamespace = "providers";
    title = "Provider";
    productType = undefined;
    isCoreResource = true;

    renderer: ItemRenderer<Provider> = {
        Icon({resource, size}) {
            return <Icon name={"cubeSolid"} size={size} />
        },
        MainTitle({resource}) {
            return <>{resource?.specification?.id ?? ""}</>
        },
        Stats({resource}) {
            if (resource == null) return null;
            return <>
                <ListRowStat icon={"globeEuropeSolid"}>
                    {resource.specification.https ? "https://" : "http://"}
                    {resource.specification.domain}
                    {resource.specification.port == null ? null : `:${resource.specification.port}`}
                </ListRowStat>
            </>
        }
    };

    public retrieveOperations(): Operation<Provider, ResourceBrowseCallbacks<Provider> & ProviderCallbacks>[] {
        return [
            {
                text: "Create " + this.title.toLowerCase(),
                icon: "upload",
                color: "primaryMain",
                primary: true,
                enabled: (selected, cb) => {
                    return !(Client.userIsAdmin && (selected.length !== 0 || cb.startCreation == null || cb.isCreating));
                },
                onClick: (selected, cb) => cb.startCreation!(),
                tag: CREATE_TAG,
                shortcut: ShortcutKey.N
            },
            {
                text: "Edit",
                icon: "edit",
                enabled: (selected, cb) =>
                    selected.length === 1 && (selected[0].permissions.myself.some(it => it === "EDIT") || Client.userIsAdmin),
                onClick: (selected, cb) => cb.navigate("/providers/edit/" + selected[0].id),
                shortcut: ShortcutKey.E
            },
            {
                text: "Permissions",
                icon: "share",
                enabled: (selected, cb) => {
                    return selected.length === 1 &&
                        selected[0].owner.project != null &&
                        cb.viewProperties != null &&
                        selected[0].permissions.myself.some(it => it === "ADMIN");
                },
                onClick: (selected, cb) => {
                    if (!cb.embedded) {
                        dialogStore.addDialog(
                            <ResourcePermissionEditor reload={cb.reload} entity={selected[0]} api={cb.api} />,
                            doNothing,
                            true
                        );
                    } else {
                        cb.viewProperties!(selected[0]);
                    }
                },
                tag: PERMISSIONS_TAG,
                shortcut: ShortcutKey.W 
            },
            {
                text: "Properties",
                icon: "properties",
                enabled: (selected, cb) => selected.length === 1 && cb.viewProperties != null,
                onClick: (selected, cb) => {
                    cb.viewProperties!(selected[0]);
                },
                tag: PROPERTIES_TAG,
                shortcut: ShortcutKey.P
            }
        ];
    }

    Properties = (props) => {
        return <ResourceProperties
            {...props} api={this}
            showMessages={false} showPermissions={true} showProperties={false}
            noPermissionsWarning={"Only administrators of this project can manage this provider. Note: the provider can still be used by normal users."}
            InfoChildren={props => {
                if (props.resource == null) return null;
                const provider = props.resource as Provider;
                return <>
                    <TitledCard title={"Metadata"} icon={"mapMarkedAltSolid"}>
                        <Box mb={"8px"}>
                            <b>Host: </b>
                            {provider.specification.https ? "https://" : "http://"}
                            {provider.specification.domain}
                            {provider.specification.port == null ? null : `:${provider.specification.port}`}
                        </Box>
                        <Box mb={"8px"}>
                            <label htmlFor={"refresh"}><b>Refresh Token:</b></label>
                            <TextArea id={"refresh"} width="100%" value={provider.refreshToken} rows={1}
                                onChange={doNothing} />
                        </Box>
                        <Box mb={"8px"}>
                            <label htmlFor={"cert"}><b>Certificate: </b></label>
                            <TextArea id={"cert"} width="100%" value={provider.publicKey} rows={3}
                                onChange={doNothing} />
                        </Box>
                    </TitledCard>
                </>
            }}
            ContentChildren={props => {
                const provider = props.resource as Provider;
                const [products, fetchProducts] = useCloudAPI<PageV2<Types.Product>>({noop: true}, emptyPageV2);
                const toggleSet = useToggleSet(products.data.items);

                const loadMore = useCallback(() => {
                    fetchProducts(UCloud.accounting.products.browse({
                        filterProvider: provider?.specification?.id,
                        next: products.data.next
                    }));
                }, [products.data.next, provider?.specification?.id]);

                useEffect(() => {
                    fetchProducts(UCloud.accounting.products.browse({filterProvider: provider?.specification?.id}));
                }, [provider?.specification?.id]);

                const [commandLoading, invokeCommand] = useCloudCommand();

                const callbacks: ProductCallbacks = useMemo(() => ({
                    invokeCommand
                }), []);

                if (provider == null) return null;
                return <>
                    <TitledCard>
                        <Operations
                            topbarIcon={"cubeSolid"}
                            location={"TOPBAR"}
                            operations={[]}
                            selected={toggleSet.checked.items}
                            extra={callbacks}
                            entityNameSingular={"Product"}
                        />
                    </TitledCard>
                </>;
            }}
        />;
    };

    update(
        request: BulkRequest<ProviderSpecification>
    ): APICallParameters<BulkRequest<ProviderSpecification>> {
        return apiUpdate(request, "/api/providers", "update");
    }

    constructor() {
        super("providers");
    }
}

export default new ProviderApi();

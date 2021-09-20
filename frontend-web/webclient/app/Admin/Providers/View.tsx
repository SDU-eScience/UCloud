import {useRouteMatch} from "react-router";
import * as React from "react";
import {InvokeCommand, useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import * as UCloud from "UCloud";
import LoadingSpinner from "LoadingIcon/LoadingIcon";
import MainContainer from "MainContainer/MainContainer";
import {ResourcePage} from "ui-components/ResourcePage";
import {useCallback, useEffect, useMemo, useState} from "react";
import {Box, Button, Label, List, Select, TextArea} from "ui-components";
import {doNothing, inDevEnvironment, onDevSite, PropType} from "UtilityFunctions";
import {Operation, Operations} from "ui-components/Operation";
import {useRefreshFunction} from "Navigation/Redux/HeaderActions";
import {useLoading, useTitle} from "Navigation/Redux/StatusActions";
import * as Heading from "ui-components/Heading";
import {addStandardDialog} from "UtilityComponents";
import {auth, BulkResponse, PageV2, provider} from "UCloud";
import {bulkRequestOf, emptyPageV2} from "DefaultObjects";
import {ListV2} from "Pagination";
import {NoResultsCardBody} from "Dashboard/Dashboard";
import Provider = provider.Provider;
import AccessToken = auth.AccessToken;
import {ListRow, ListRowStat, ListStatContainer} from "ui-components/List";
import ResourceDoc = provider.ResourceDoc;
import {priceExplainer} from "Accounting";
import ResourceForm from "Products/CreateProduct";
import * as Types from "Accounting";

const entityName = "Provider";

function View(): JSX.Element | null {
    const match = useRouteMatch<{id: string}>();
    const {id} = match.params;
    const [provider, fetchProvider] = useCloudAPI<Provider | null>({noop: true}, null);
    const [products, fetchProducts] = useCloudAPI<PageV2<Types.Product>>({noop: true}, emptyPageV2);
    const [productGeneration, setProductGeneration] = useState(0);
    const [isCreatingProduct, setIsCreatingProduct] = useState(false);

    const reload = useCallback(() => {
        fetchProvider(UCloud.provider.providers.retrieve({id}));
        fetchProducts(UCloud.accounting.products.browse({filterProvider: id}));
        setProductGeneration(gen => gen + 1);
    }, [id]);

    useEffect(() => {
        reload();
    }, [reload]);

    const [commandLoading, invokeCommand] = useCloudCommand();
    const startProductCreation = useCallback(() => {
        setIsCreatingProduct(true);
    }, [setIsCreatingProduct]);
    const stopProductCreation = useCallback(() => {
        setIsCreatingProduct(false);
    }, [setIsCreatingProduct]);

    const loadMore = useCallback(() => {
        fetchProducts(UCloud.accounting.products.browse({filterProvider: id, next: products.data.next}));
    }, [products.data.next, id]);

    const callbacks: OpCallbacks = useMemo(() => {
        return {invokeCommand, reload, startProductCreation, stopProductCreation, isCreatingProduct, provider: id};
    }, [invokeCommand, reload, setIsCreatingProduct, isCreatingProduct, id]);

    const onProductAdded = useCallback(() => {
        reload();
        stopProductCreation();
    }, [reload, stopProductCreation]);

    useRefreshFunction(reload);
    useLoading(provider.loading || commandLoading);
    useTitle(`${id} (Provider)`)

    if (provider.loading && provider.data == null) return <MainContainer main={<LoadingSpinner />} />;
    if (provider.data == null) return null;

    return (
        <MainContainer
            sidebar={
                <>
                    <Operations
                        location={"SIDEBAR"}
                        operations={operations}
                        selected={[provider.data]}
                        extra={callbacks}
                        entityNameSingular={entityName}
                        showSelectedCount={false}
                    />
                </>
            }
            main={
                <ResourcePage
                    entityName={entityName}
                    aclOptions={[{icon: "edit", name: "EDIT", title: "Edit"}]}
                    entity={provider.data as ResourceDoc}
                    reload={reload}
                    showMissingPermissionHelp={false}
                    stats={[
                        {
                            title: "Domain",
                            render: t => {
                                const f = t as any;
                                let stringBuilder = "";
                                if (f.specification.https) {
                                    stringBuilder += "https://"
                                } else {
                                    stringBuilder += "http://"
                                }

                                stringBuilder += f.specification.domain;

                                if (f.specification.port) {
                                    stringBuilder += ":";
                                    stringBuilder += f.specification.port;
                                }

                                return stringBuilder;
                            }
                        },
                        {
                            title: "Refresh Token",
                            // eslint-disable-next-line react/display-name
                            render: t => <TextArea width="100%" value={(t as any).refreshToken} rows={3} onChange={doNothing} />,
                            inline: false,
                        },
                        {
                            title: "Certificate",
                            // eslint-disable-next-line react/display-name
                            render: t => <TextArea width="100%" value={(t as any).publicKey} rows={10} onChange={doNothing} />,
                            inline: false,
                        },
                    ]}
                    updateAclEndpoint={UCloud.provider.providers.updateAcl}

                    beforeUpdates={
                        <>
                            <Heading.h4>Products</Heading.h4>
                            <List>
                                <ListV2
                                    infiniteScrollGeneration={productGeneration}
                                    page={products.data}
                                    pageRenderer={p => isCreatingProduct ? null : p.map(item =>
                                        <ListRow
                                            key={item.name}
                                            left={item.name}
                                            right={<></>}
                                            leftSub={
                                                <ListStatContainer>
                                                    <ListRowStat icon={"id"}>{item.category.name}</ListRowStat>
                                                    <ListRowStat icon={"grant"}>
                                                        Unit price: {priceExplainer(item)}
                                                    </ListRowStat>
                                                </ListStatContainer>
                                            }
                                        />
                                    )}
                                    loading={products.loading}
                                    customEmptyPage={
                                        isCreatingProduct ? <></> :
                                            <NoResultsCardBody title={"No products"}>
                                                <Button onClick={startProductCreation}>New product</Button>
                                            </NoResultsCardBody>
                                    }
                                    onLoadMore={loadMore}
                                />
                            </List>
                            {!isCreatingProduct ? null :
                                <ProductCreationForm provider={provider.data} onComplete={onProductAdded} />
                            }
                        </>
                    }
                />
            }
        />
    );
}

const productTypes: {value: PropType<Types.Product, "type">, title: string}[] = [
    {value: "compute", title: "Compute"},
    {value: "network_ip", title: "Public IP"},
    {value: "ingress", title: "Public link"},
    {value: "license", title: "Software license"},
    {value: "storage", title: "Storage"},
];

function unitNameFromType(type: PropType<Types.Product, "type">): string {
    let unitName = "";
    switch (type) {
        case "compute":
            unitName = "minute";
            break;
        case "storage":
            unitName = "GB/day";
            break;
        case "network_ip":
        case "license":
        case "ingress":
            unitName = "activation";
            break;
    }
    return unitName;
}

const ProductCreationForm: React.FunctionComponent<{provider: Provider, onComplete: () => void}> = props => {
    const [type, setType] = useState<PropType<Types.Product, "type">>("compute");
    const onTypeChange = useCallback(e => setType(e.target.value as PropType<Types.Product, "type">), [setType]);
    const [licenseTagCount, setTagCount] = useState(1);
    const [, invokeCommand] = useCloudCommand();

    const unitName = unitNameFromType(type);

    return <Box>
        <Label>
            Type
            <Select value={type} onChange={onTypeChange}>
                {productTypes.map(it => <option key={it.value} value={it.value}>{it.title}</option>)}
            </Select>
        </Label>

        <ResourceForm
            title="Product"
            createRequest={async (data): Promise<APICallParameters<any>> => {
                const tokens = await invokeCommand<BulkResponse<AccessToken>>(UCloud.auth.providers.refresh(
                    bulkRequestOf({refreshToken: props.provider.refreshToken}))
                );

                const accessToken = tokens?.responses[0]?.accessToken;
                let product: Types.Product;

                const shared: Omit<Types.ProductBase, "productType"> = {
                    type,
                    category: {name: data.fields.name, provider: props.provider.id},
                    pricePerUnit: data.fields.pricePerUnit * 10_000,
                    name: data.fields.name,
                    description: data.fields.description,
                    priority: data.fields.priority,
                    version: data.fields.version,
                    freeToUse: data.fields.freeToUse,
                    unitOfPrice: data.fields.unitOfPrice,
                    chargeType: data.fields.chargeType,
                    hiddenInGrantApplications: data.fields.hiddenInGrantApplications,
                };

                const tags: string[] = [];
                for (let i = 0; i < licenseTagCount; i++) {
                    const entry = data.fields[`tag-${i}`];
                    if (entry) tags.push(entry);
                }

                switch (type) {
                    case "storage":
                        product = {
                            ...shared,
                            productType: "STORAGE"
                        } as Types.ProductStorage;
                        break;
                    case "compute":
                        product = {
                            ...shared,
                            productType: "COMPUTE",
                            cpu: data.fields.cpu,
                            memoryInGigs: data.fields.memory,
                            gpu: data.fields.gpu
                        } as Types.ProductCompute;
                        break;
                    case "ingress":
                        product = {
                            ...shared,
                            productType: "INGRESS",
                        } as Types.ProductIngress;
                        break;
                    case "network_ip":
                        product = {
                            ...shared,
                            productType: "NETWORK_IP"
                        } as Types.ProductNetworkIP;
                        break;
                    case "license":
                        product = {
                            ...shared,
                            productType: "LICENSE",
                            tags
                        } as Types.ProductLicense;
                        break;
                }

                return {...UCloud.accounting.products.createProduct(bulkRequestOf(product as any)), accessTokenOverride: accessToken};
            }}
        >
            <ResourceForm.Text required id="name" placeholder="Name..." label="Name (e.g. u1-standard-1)" styling={{}} />
            <ResourceForm.Number required id="pricePerUnit" placeholder="Price..." rightLabel="DKK" label={`Price per ${unitName}`} step="0.01" min={0} styling={{}} />
            <ResourceForm.TextArea required id="description" placeholder="Description..." label="Description" rows={10} styling={{}} />
            <ResourceForm.Number required id="priority" placeholder="Priority..." label="Priority" styling={{}} />
            <ResourceForm.Number required id="version" placeholder="Version..." label="Version" min={0} styling={{}} />
            <ResourceForm.Checkbox id="freeToUse" defaultChecked={false} label="Free to use" styling={{}} />
            <ResourceForm.Select id="unitOfPrice" label="Unit of Price" required options={[
                {value: "PER_UNIT", text: "Per Unit"},
                {value: "CREDITS_PER_MINUTE", text: "Credits Per Minute"},
                {value: "CREDITS_PER_HOUR", text: "Credits Per Hour"},
                {value: "CREDITS_PER_DAY", text: "Credits Per Day"},
                {value: "UNITS_PER_MINUTE", text: "Units Per Minute"},
                {value: "UNITS_PER_HOUR", text: "Units Per Hour"},
                {value: "UNITS_PER_DAY", text: "Units Per Day"}
            ]} styling={{}} />
            <ResourceForm.Select id="chargeType" label="Chargetype" required options={[
                {value: "ABSOLUTE", text: "Absolute"},
                {value: "DIFFERENTIAL_QUOTA", text: "Differential Quota"}
            ]} styling={{}} />
            <ResourceForm.Checkbox id="hiddenInGrantApplications" label="Hidden in Grant Applications" defaultChecked={false} styling={{}} />

            {type !== "compute" ? null : (
                <>
                    <ResourceForm.Number id="cpu" placeholder="vCPU..." label="vCPU" required styling={{}} />
                    <ResourceForm.Number id="memory" placeholder="Memory..." label="Memory in GB" required styling={{}} />
                    <ResourceForm.Number id="gpus" placeholder="GPUs..." label="Number of GPUs" required styling={{}} />
                </>
            )}
            {type !== "license" ? null : (
                <>
                    {[...Array(licenseTagCount).keys()].map(id =>
                        <ResourceForm.Text key={id} id={`tag-${id}`} label={`Tag ${id + 1}`} styling={{}} />
                    )}
                    <div>
                        <Button fullWidth type="button" onClick={() => setTagCount(t => t + 1)} mt="6px">Add tag</Button>
                    </div>
                </>
            )}
        </ResourceForm>
    </Box>;
};

interface OpCallbacks {
    provider: string;
    invokeCommand: InvokeCommand;
    reload: () => void;
    startProductCreation: () => void;
    stopProductCreation: () => void;
    isCreatingProduct: boolean;
}

const operations: Operation<UCloud.provider.Provider, OpCallbacks>[] = [
    {
        enabled: (_, cb) => !cb.isCreatingProduct,
        onClick: (_, cb) => {
            cb.startProductCreation();
        },
        text: "Create product",
        operationType: () => Button,
    },
    {
        enabled: selected => selected.length > 0,
        onClick: (selected, cb) =>
            addStandardDialog({
                title: "WARNING!",
                message: <>
                    <p>Are you sure you want to renew the provider token?</p>
                    <p>
                        This will invalidate every current security token. Your provider <i>must</i> be reconfigured
                        to use the new tokens.
                    </p>
                </>,
                confirmText: "Confirm",
                cancelButtonColor: "blue",
                confirmButtonColor: "red",
                cancelText: "Cancel",
                onConfirm: async () => {
                    await cb.invokeCommand(UCloud.provider.providers.renewToken(
                        {type: "bulk", items: selected.map(it => ({id: it.id}))}
                    ));

                    cb.reload();
                }
            }),
        text: "Renew token",
        color: "red",
        icon: "trash",
        operationType: () => Button,
    },
    {
        enabled: selected => selected.length === 1 && (inDevEnvironment() || onDevSite()),
        onClick: async (selected, cb) => {
            await cb.invokeCommand(
                UCloud.accounting.wallets.grantProviderCredits({provider: cb.provider})
            );
            cb.reload();
        },
        text: "Grant credits",
        operationType: () => Button
    }
];

export default View;

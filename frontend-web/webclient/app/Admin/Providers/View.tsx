import {useRouteMatch} from "react-router";
import * as React from "react";
import {callAPI, InvokeCommand, useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import * as UCloud from "UCloud";
import LoadingSpinner from "LoadingIcon/LoadingIcon";
import MainContainer from "MainContainer/MainContainer";
import {ResourcePage} from "ui-components/ResourcePage";
import {useCallback, useEffect, useMemo, useState} from "react";
import {Button, Flex, Grid, Input, Label, List, Select, TextArea} from "ui-components";
import {doNothing, inDevEnvironment, onDevSite, PropType} from "UtilityFunctions";
import {Operation, Operations} from "ui-components/Operation";
import {useRefreshFunction} from "Navigation/Redux/HeaderActions";
import {useLoading, useTitle} from "Navigation/Redux/StatusActions";
import * as Heading from "ui-components/Heading";
import {addStandardDialog} from "UtilityComponents";
import {accounting, auth, BulkResponse, PageV2, provider} from "UCloud";
import {bulkRequestOf, emptyPageV2} from "DefaultObjects";
import {ListV2} from "Pagination";
import {NoResultsCardBody} from "Dashboard/Dashboard";
import Product = accounting.Product;
import Warning from "ui-components/Warning";
import Provider = provider.Provider;
import AccessToken = auth.AccessToken;
import {snackbarStore} from "Snackbar/SnackbarStore";
import ProductNS = accounting.ProductNS;
import {ListRow, ListRowStat, ListStatContainer} from "ui-components/List";
import {creditFormatter} from "Project/ProjectUsage";
import {useProjectId} from "Project";
import {Client} from "Authentication/HttpClientInstance";

const entityName = "Provider";

function View(): JSX.Element | null {
    const match = useRouteMatch<{ id: string }>();
    const {id} = match.params;
    const [provider, fetchProvider] = useCloudAPI<UCloud.provider.Provider | null>({noop: true}, null);
    const [products, fetchProducts] = useCloudAPI<PageV2<UCloud.accounting.Product>>({noop: true}, emptyPageV2);
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

    if (provider.loading && provider.data == null) return <MainContainer main={<LoadingSpinner/>}/>;
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
                    entity={provider.data}
                    reload={reload}
                    showMissingPermissionHelp={false}
                    stats={[
                        {
                            title: "Domain",
                            render: t => {
                                let stringBuilder = "";
                                if (t.specification.https) {
                                    stringBuilder += "https://"
                                } else {
                                    stringBuilder += "http://"
                                }

                                stringBuilder += t.specification.domain;

                                if (t.specification.port) {
                                    stringBuilder += ":";
                                    stringBuilder += t.specification.port;
                                }

                                return stringBuilder;
                            }
                        },
                        {
                            title: "Refresh Token",
                            // eslint-disable-next-line react/display-name
                            render: t => <TextArea width="100%" value={t.refreshToken} rows={3} onChange={doNothing}/>,
                            inline: false,
                        },
                        {
                            title: "Certificate",
                            // eslint-disable-next-line react/display-name
                            render: t => <TextArea width="100%" value={t.publicKey} rows={10} onChange={doNothing}/>,
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
                                                key={item.id}
                                                left={item.id}
                                                right={<></>}
                                                leftSub={
                                                    <ListStatContainer>
                                                        <ListRowStat icon={"id"}>{item.category.id}</ListRowStat>
                                                        <ListRowStat icon={"grant"}>
                                                            Unit price: {creditFormatter(item.pricePerUnit)}
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
                                <ProductCreationForm provider={provider.data} onComplete={onProductAdded}/>
                            }
                        </>
                    }
                />
            }
        />
    );
}

const productTypes: { value: PropType<Product, "type">, title: string }[] = [
    {value: "compute", title: "Compute"},
    {value: "network_ip", title: "Public IP"},
    {value: "ingress", title: "Public link"},
    {value: "license", title: "Software license"},
    {value: "storage", title: "Storage"},
];

function unitNameFromType(type: PropType<Product, "type">): string {
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

const ProductCreationForm: React.FunctionComponent<{ provider: Provider, onComplete: () => void }> = props => {
    const [type, setType] = useState<PropType<Product, "type">>("compute");
    const onTypeChange = useCallback(e => setType(e.target.value as PropType<Product, "type">), [setType]);

    const [pricePerUnit, setPricePerUnit] = useState<string>("0");
    const onPriceChange = useCallback(e => setPricePerUnit(e.target.value), [setPricePerUnit]);
    const [pricePerUnitDecimal, setPricePerUnitDecimal] = useState<string>("0");
    const onPriceDecimalChange = useCallback(e => setPricePerUnitDecimal(e.target.value), [setPricePerUnitDecimal]);

    const [id, setId] = useState<string>("")
    const onIdChange = useCallback(e => setId(e.target.value), [setId]);

    const [category, setCategory] = useState<string>("")
    const onCategoryChange = useCallback(e => setCategory(e.target.value), [setCategory]);

    const [description, setDescription] = useState<string>("")
    const onDescriptionChange = useCallback(e => setDescription(e.target.value), [setDescription]);

    const [cpu, setCpu] = useState<string>("")
    const onCpuChange = useCallback(e => setCpu(e.target.value), [setCpu]);

    const [memoryInGigs, setMemoryInGigs] = useState<string>("")
    const onMemoryInGigsChange = useCallback(e => setMemoryInGigs(e.target.value), [setMemoryInGigs]);

    const [gpus, setGpus] = useState<string>("")
    const onGpusChange = useCallback(e => setGpus(e.target.value), [setGpus]);

    const projectId = useProjectId();

    const [commandLoading, invokeCommand] = useCloudCommand();

    const addProduct = useCallback(async () => {
        const wholePricePart = parseInt(pricePerUnit, 10);
        const decimalPricePart = parseInt(pricePerUnitDecimal.padEnd(6, '0'), 10);

        if (isNaN(wholePricePart) || isNaN(decimalPricePart)) {
            snackbarStore.addFailure("Invalid price per unit", false);
            return;
        }

        if (decimalPricePart >= 1000000) {
            snackbarStore.addFailure(
                "The decimal part of the price is too specific. " +
                "Try rounding your number to a less precise one.",
                false
            );
            return;
        }

        const actualPricePerUnit = wholePricePart * 1_000_000 + decimalPricePart;

        const actualCpu = parseInt(cpu, 10);
        if (isNaN(actualCpu) || actualCpu < 1) {
            snackbarStore.addFailure("Invalid number of vCPU", false);
            return;
        }

        const actualMemoryInGigs = parseInt(memoryInGigs, 10);
        if (isNaN(actualMemoryInGigs) || actualMemoryInGigs < 1) {
            snackbarStore.addFailure("Invalid amount of memory", false);
            return;
        }

        const actualGpu = parseInt(gpus, 10);
        if (isNaN(actualGpu) || actualGpu < 0) {
            snackbarStore.addFailure("Invalid number of GPUs", false);
            return;
        }

        const tokens = await invokeCommand<BulkResponse<AccessToken>>(UCloud.auth.providers.refresh(
            bulkRequestOf({refreshToken: props.provider.refreshToken}))
        );
        const accessToken = tokens?.responses[0]?.accessToken;

        let product: Product | null = null;
        switch (type) {
            case "storage":
                /*
                product = {
                    type: "storage",
                    id: id,
                    description: description,
                    pricePerUnit: actualPricePerUnit,
                    category: {id: category, provider: props.provider.id},
                    hiddenInGrantApplications: false,
                    priority: 1,
                } as ProductNS.Storage;
                 */
                break;
            case "compute":
                product = {
                    type: "compute",
                    id,
                    description,
                    pricePerUnit: actualPricePerUnit,
                    category: {id: category, provider: props.provider.id},
                    hiddenInGrantApplications: false,
                    priority: 1,
                    cpu: actualCpu,
                    memoryInGigs: actualMemoryInGigs,
                    gpu: actualGpu
                } as ProductNS.Compute;
                break;
        }

        if (product === null) {
            snackbarStore.addFailure("Provider support has not yet been implemented for this type of product", true);
            return;
        } else {
            await invokeCommand(
                {...UCloud.accounting.products.createProduct(product), accessTokenOverride: accessToken}
            );

            if (inDevEnvironment() || onDevSite()) {
                await invokeCommand(
                    UCloud.accounting.wallets.setBalance({
                        wallet: {
                            paysFor: {provider: product.category.provider, id: product.category.id},
                            type: projectId === undefined ? "USER" : "PROJECT",
                            id: projectId === undefined ? Client.username! : projectId
                        },
                        lastKnownBalance: 0,
                        newBalance: 1000000 * 10000
                    })
                );
            }
            props.onComplete();
        }
    }, [type, pricePerUnit, pricePerUnitDecimal, id, category, description, invokeCommand, cpu, memoryInGigs, gpus]);

    const unitName = unitNameFromType(type);

    return <Grid gridTemplateColumns={"1 fr"} gridGap={"16px"}>
        {type === "compute" ? null : <Warning>Provider support not yet implemented for this type of product</Warning>}

        <Label>
            Type
            <Select value={type} onChange={onTypeChange}>
                {productTypes.map(it => <option key={it.value} value={it.value}>{it.title}</option>)}
            </Select>
        </Label>

        <Label>
            Category (e.g. u1-standard)
            <Input value={category} onChange={onCategoryChange}/>
        </Label>

        <Label>
            ID (e.g. u1-standard-1)
            <Input value={id} onChange={onIdChange}/>
        </Label>

        <Label>
            Price per {unitName}
            <Flex alignItems={"end"}>
                <Input type={"number"} value={pricePerUnit} onChange={onPriceChange} mr={"8px"}/>
                ,
                <Input type={"number"} value={pricePerUnitDecimal} onChange={onPriceDecimalChange} mx={"8px"}/>
                DKK
            </Flex>
        </Label>

        <Label>
            Description <br/>
            <TextArea width={"100%"} rows={10} value={description} onChange={onDescriptionChange}/>
        </Label>

        {type !== "compute" ? null :
            <>
                <Label>
                    vCPU
                    <Input type={"number"} value={cpu} onChange={onCpuChange}/>
                </Label>

                <Label>
                    Memory in GB
                    <Input type={"number"} value={memoryInGigs} onChange={onMemoryInGigsChange}/>
                </Label>

                <Label>
                    Number of GPUs
                    <Input type={"number"} value={gpus} onChange={onGpusChange}/>
                </Label>
            </>
        }

        <Button fullWidth onClick={addProduct}>Add new product</Button>
    </Grid>;
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

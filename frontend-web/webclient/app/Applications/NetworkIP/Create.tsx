import networkApi = UCloud.compute.networkips;
import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import {useProjectId} from "Project";
import {callAPI, useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import * as UCloud from "UCloud";
import {accounting, compute, PageV2} from "UCloud";
import ProductNS = accounting.ProductNS;
import {Box, Button, Input, Label, Select} from "ui-components";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Spacer} from "ui-components/Spacer";
import {emptyPageV2} from "DefaultObjects";
import NetworkIPsCreateResponse = compute.NetworkIPsCreateResponse;
import NetworkIP = compute.NetworkIP;

const Create: React.FunctionComponent<{computeProvider?: string; onCreateFinished?: (ip: NetworkIP) => void}> = props => {
    const [selectedProvider, setSelectedProvider] = useState(props.computeProvider);
    const canChangeProvider = props.computeProvider === undefined;
    const [selectedProduct, setSelectedProduct] = useState<ProductNS.Ingress | null>(null);
    const [, invokeCommand] = useCloudCommand();

    const projectId = useProjectId();

    const [allProducts, fetchProducts] = useCloudAPI<PageV2<UCloud.accounting.Product>>(
        {noop: true},
        emptyPageV2
    );

    const viableProviders = Array.from(new Set<string>(allProducts.data.items.map(it => it.category.provider)));

    const reload = useCallback(() => {
        fetchProducts(
            UCloud.accounting.products.browse({
                filterProvider: selectedProvider,
                filterUsable: true,
                filterArea: "NETWORK_IP",
                itemsPerPage: 250,
            })
        );
    }, [selectedProvider, selectedProduct]);

    useEffect(() => {
        reload();
    }, [reload, projectId, selectedProvider, selectedProduct]);

    return <div>
        <Spacer mb="6px"
                left={
                    <Box width="calc(50% - 10px)">
                        <Label>
                            {canChangeProvider ? <>1. Select Provider</> : <>Provider</>}
                            {canChangeProvider ? <Select placeholder="Provider..." onChange={e => setSelectedProvider(e.target.value ? e.target.value : undefined)}>
                                <option />
                                {viableProviders.map(provider =>
                                    <option key={provider}>
                                        {provider}
                                    </option>
                                )}
                            </Select> : <Input value={props.computeProvider} readOnly />}
                        </Label>
                    </Box>
                }
                right={<Box width="calc(50% - 10px)">
                    <Label>
                        {!canChangeProvider ? "1" : "2"}. Select Product
                        <Select placeholder="Product..." onChange={e => {
                            setSelectedProduct(allProducts.data.items.find(it => it.id === e.target.value) as ProductNS.Ingress ?? null);
                        }}>
                            <option onClick={() => setSelectedProduct(null)} />
                            {allProducts.data.items.map(product =>
                                <option key={product.id} value={product.id}>
                                    {product.id}
                                </option>
                            )}
                        </Select>
                    </Label>
                </Box>}
        />
        <Button onClick={register} fullWidth>Allocate IP address</Button>
    </div>

    async function register() {
        if (!selectedProduct) {
            snackbarStore.addFailure("Please select a product.", false);
            return;
        }

        if (!selectedProvider && !canChangeProvider) {
            snackbarStore.addFailure("Please select a provider.", false);
            return;
        }

        const resp = await invokeCommand<NetworkIPsCreateResponse>(networkApi.create({
            product: {
                category: selectedProduct.category.id,
                id: selectedProduct.id,
                provider: props.computeProvider ?? selectedProvider!
            }
        }));


        if (resp?.ids?.length) {
            snackbarStore.addSuccess(`Allocated a public IP.`, false);
            const ip = await callAPI(networkApi.retrieve({id: resp.ids[0], includeAcl: true, includeProduct: true,
                includeUpdates: true}));
            props.onCreateFinished?.(ip);
        }
    }
};

export default Create;

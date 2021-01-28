import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import {useProjectId} from "Project";
import {useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import * as UCloud from "UCloud";
import {accounting, PageV2} from "UCloud";
import ProductNS = accounting.ProductNS;
import {Box, Button, Flex, Input, Label, Select, Text} from "ui-components";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Spacer} from "ui-components/Spacer";
import {emptyPageV2} from "DefaultObjects";

const Create: React.FunctionComponent<{computeProvider?: string; onCreateFinished?: () => void}> = props => {
    const [selectedProvider, setSelectedProvider] = useState(props.computeProvider);
    const canChangeProvider = props.computeProvider === undefined;
    const [selectedProduct, setSelectedProduct] = useState<ProductNS.Ingress | null>(null);
    const [, invokeCommand] = useCloudCommand();
    const domainRef = React.useRef<HTMLInputElement>(null);

    const projectId = useProjectId();

    const [allProducts, fetchProducts] = useCloudAPI<PageV2<UCloud.accounting.Product>>(
        {noop: true},
        emptyPageV2
    );

    const [ingressSettings, fetchIngressSettings] = useCloudAPI<UCloud.compute.IngressSettings>(
        {noop: true},
        {domainPrefix: "", domainSuffix: ""}
    );

    const viableProviders = Array.from(new Set<string>(allProducts.data.items.map(it => it.category.provider)));

    const reload = useCallback(() => {
        fetchProducts(
            UCloud.accounting.products.browse({
                filterProvider: selectedProvider,
                filterUsable: true,
                filterArea: "INGRESS",
                itemsPerPage: 250,
            })
        );

        if (selectedProduct) {
            fetchIngressSettings(
                UCloud.compute.ingresses.retrieveSettings({
                    id: selectedProduct.id,
                    provider: selectedProduct.category.provider,
                    category: selectedProduct.category.id
                })
            );
        }
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
                        console.log(allProducts.data.items.find(it => it.id === e.target.value) as ProductNS.Ingress ?? null);
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
        {!selectedProduct ? null : <Label my="6px">
            {!canChangeProvider ? "2" : "3"}. Select domain
            <Flex>
                <Text mt="7px">
                    {ingressSettings.data.domainPrefix}</Text><Input placeholder="Enter domain..." ref={domainRef} type="text" />
                <Text mt="7px">
                    {ingressSettings.data.domainSuffix}
                </Text>
            </Flex>
        </Label>}
        <Button onClick={register} fullWidth>Register public link</Button>
    </div>

    async function register() {
        if (!domainRef.current?.value) {
            snackbarStore.addFailure("Domain can't be empty.", false);
            return;
        }

        if (!selectedProduct) {
            snackbarStore.addFailure("Please select a product.", false);
            return;
        }

        if (!selectedProvider && !canChangeProvider) {
            snackbarStore.addFailure("Please select a provider.", false);
            return;
        }

        const {ids} = await invokeCommand(UCloud.compute.ingresses.create({
            domain: ingressSettings.data.domainPrefix + domainRef.current.value + ingressSettings.data.domainSuffix,
            product: {
                category: selectedProduct.category.id,
                id: selectedProduct.id,
                provider: props.computeProvider ?? selectedProvider!
            }
        }));


        if (ids?.length) {
            snackbarStore.addSuccess(`Created ${ids.length} public link(s).`, false);
            props.onCreateFinished?.();
        }
    }
};

export default Create;

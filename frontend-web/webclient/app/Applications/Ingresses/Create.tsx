import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import {useProjectId} from "Project";
import {useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import * as UCloud from "UCloud";
import {accounting} from "UCloud";
import ProductNS = accounting.ProductNS;
import {Box, Button, Flex, Input, Label, Select, Text} from "ui-components";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Spacer} from "ui-components/Spacer";

const Create: React.FunctionComponent<{computeProvider?: string; onCreateFinished?: () => void}> = props => {
    const [selectedProvider, setSelectedProvider] = useState(props.computeProvider);
    const canChangeProvider = props.computeProvider === undefined;
    const [selectedProduct, setSelectedProduct] = useState<ProductNS.Ingress | null>(null);
    const [, invokeCommand] = useCloudCommand();
    const domainRef = React.useRef<HTMLInputElement>(null);

    const projectId = useProjectId();

    const [allProductsFromProvider, fetchProductsFromProvider] = useCloudAPI<UCloud.accounting.Product[]>(
        {noop: true},
        []
    );

    const [wallets, fetchWallets] = useCloudAPI<UCloud.accounting.RetrieveBalanceResponse>(
        {noop: true},
        {wallets: []}
    );

    const [ingressSettings, fetchIngressSettings] = useCloudAPI<UCloud.compute.IngressSettings>(
        {noop: true},
        {domainPrefix: "", domainSuffix: ""}
    );

    // Select provider dropdown will select from viableProviders
    // When a provider is selected their products are loaded
    // When a product is selected their settings are fetched
    const viableProviders: string[] = [];
    for (const wallet of wallets.data.wallets) {
        if (wallet.area === "INGRESS") {
            viableProviders.push(wallet.wallet.paysFor.provider);
        }
    }

    const reload = useCallback(() => {
        if (selectedProvider) {
            fetchProductsFromProvider(
                UCloud.accounting.products.retrieveAllFromProvider({provider: selectedProvider})
            );
        }

        if (selectedProduct) {
            fetchIngressSettings(
                UCloud.compute.ingresses.retrieveSettings({
                    id: selectedProduct.id,
                    provider: selectedProduct.category.provider,
                    category: selectedProduct.category.id
                })
            );
        }

        fetchWallets(UCloud.accounting.wallets.retrieveBalance({}));
    }, [selectedProvider, selectedProduct]);


    useEffect(() => {
        reload();
    }, [reload, projectId, selectedProvider, selectedProduct]);

    return <div>
        <Spacer mb="6px"
            left={!canChangeProvider ? null : <Box width="calc(50% - 10px)">
                <Label>
                    1. Select Provider
                    <Select placeholder="Provider...">
                        <option onClick={() => setSelectedProvider(undefined)}></option>
                        {viableProviders.map(provider =>
                            <option key={provider} onClick={() => setSelectedProvider(provider)}>
                                {provider}
                            </option>
                        )}
                    </Select>
                </Label>
            </Box>}
            right={<Box width="calc(50% - 10px)">
                <Label>
                    {!canChangeProvider ? "1" : "2"}. Select Product
                    <Select placeholder="Product...">
                        <option onClick={() => setSelectedProduct(null)}></option>
                        {allProductsFromProvider.data.filter(it => it.type === "ingress").map(product =>
                            <option key={product.id} onClick={() => setSelectedProduct(product as ProductNS.Ingress)}>
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
        <Button onClick={register} fullWidth>Register ingress</Button>
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
            snackbarStore.addSuccess(`Created ${ids.length} ingresse(s).`, false);
            props.onCreateFinished?.();
        }
    }
};

export default Create;

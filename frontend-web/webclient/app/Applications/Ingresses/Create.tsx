import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import {useProjectId} from "Project";
import {useCloudAPI} from "Authentication/DataHook";
import {emptyPageV2} from "DefaultObjects";
import * as UCloud from "UCloud";
import {PageRenderer} from "Pagination/PaginationV2";
import * as Pagination from "Pagination";
import {useLoading, useTitle} from "Navigation/Redux/StatusActions";
import {useRefreshFunction} from "Navigation/Redux/HeaderActions";
import MainContainer from "MainContainer/MainContainer";
import {accounting, compute} from "UCloud";
import Ingress = compute.Ingress;
import ProductNS = accounting.ProductNS;

const Create: React.FunctionComponent<{ computeProvider?: string }> = props => {
    const [selectedProvider, setSelectedProvider] = useState(props.computeProvider);
    const canChangeProvider = props.computeProvider === undefined;
    const [selectedProduct, setSelectedProduct] = useState<ProductNS.Ingress | null>(null);

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
                UCloud.compute.ingresses.retrieveSettings({product: selectedProduct})
            )
        }

        fetchWallets(UCloud.accounting.wallets.retrieveBalance({}));
    }, [selectedProvider, selectedProduct]);


    useEffect(() => {
        reload();
    }, [reload, projectId, selectedProvider, selectedProduct]);

    return null;
};

export default Create;

import * as React from "react";
import {
    listByProductArea,
    Product,
    productCategoryEquals,
    ProductCategoryId,
    UCLOUD_PROVIDER
} from "Accounting/index";
import {Text, Tooltip} from "ui-components";
import {addThousandSeparators, creditFormatter} from "Project/ProjectUsage";
import styled from "styled-components";
import {TextAlignProps} from "styled-system";
import {useGlobalCloudAPI} from "Authentication/DataHook";
import {emptyPage} from "DefaultObjects";

// Hack: We need products to only load once
// TODO: Find a proper way to load this kind of shared data
let didLoadProducts = false;

export const Balance: React.FunctionComponent<{
    amount: number;
    productCategory: ProductCategoryId;
    precision?: number;
} & TextAlignProps> = props => {
    return (
        <Tooltip
            position={"top"}
            trigger={
                <BalanceTextWrapper textAlign={props.textAlign}>
                    {creditFormatter(props.amount, props.precision)}
                </BalanceTextWrapper>
            }
        >
            <BalanceExplainer {...props}/>
        </Tooltip>
    );
};

export const BalanceExplainer: React.FunctionComponent<{
    amount: number;
    productCategory: ProductCategoryId;
    precision?: number;
}> = props => {
    const [computeProducts, fetchCompute, computeParams] = useGlobalCloudAPI<Page<Product>>(
        "computeProducts",
        listByProductArea({itemsPerPage: 100, page: 0, area: "COMPUTE", provider: UCLOUD_PROVIDER}),
        emptyPage
    );
    const [storageProducts, fetchStorage, storageParams] = useGlobalCloudAPI<Page<Product>>(
        "storageProducts",
        listByProductArea({itemsPerPage: 100, page: 0, area: "STORAGE", provider: UCLOUD_PROVIDER}),
        emptyPage
    );

    if (!didLoadProducts) {
        didLoadProducts = true;
        fetchCompute(computeParams);
        fetchStorage(storageParams);
    }

    let pricePerUnit: number | null = null;
    let productName: string | null = null;
    let productArea: "compute" | "storage" | null = null;
    {
        for (const prod of computeProducts.data.items) {
            if (productCategoryEquals(prod.category, props.productCategory)) {
                if (pricePerUnit === null || prod.pricePerUnit > pricePerUnit) {
                    pricePerUnit = prod.pricePerUnit;
                    productName = prod.id;
                    productArea = prod.type;
                }
            }
        }
        for (const prod of storageProducts.data.items) {
            if (productCategoryEquals(prod.category, props.productCategory)) {
                if (pricePerUnit === null || prod.pricePerUnit > pricePerUnit) {
                    pricePerUnit = prod.pricePerUnit;
                    productName = prod.id;
                    productArea = prod.type;
                }
            }
        }
    }

    if (productName === null || pricePerUnit === null || productArea === null) {
        return <>{creditFormatter(props.amount)}</>;
    } else {
        let units = Math.floor(props.amount / pricePerUnit);
        const unitText = productArea === "compute" ? "hours on" : "months of 50 GB on";
        if (productArea === "storage") {
            units = Math.floor(units / (30 * 50));
        }

        return <>{addThousandSeparators(units)} {unitText} {productName}</>;
    }
};

// Provides a way to style the text
// By default it will take up all the space of the parent
export const BalanceTextWrapper = styled(Text)`
    width: 100%;
`;

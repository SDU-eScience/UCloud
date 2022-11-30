import { priceExplainer, Product, ProductCompute } from "@/Accounting";
import { ProviderLogo } from "@/Providers/ProviderLogo";
import { Box, Icon, Input, theme } from "@/ui-components";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import Table, { TableCell, TableRow } from "@/ui-components/Table";
import { doNothing } from "@/UtilityFunctions";
import * as React from "react";
import styled from "styled-components";

export const Selector: React.FunctionComponent<{
    products: Product[];
    selected: Product | null;
    onSelect: (product: Product) => void;
}> = ({selected, ...props}) => {
    const [filteredProducts, setFilteredProducts] = React.useState<Product[]>([]);
    const type = props.products[0].productType;

    let productName = "product";
    switch (type) {
        case "COMPUTE":
            productName = "machine type";
            break;
    }

    const headers: string[] = React.useMemo(() => {
        const result: string[] = [];
        if (type === "COMPUTE") {
            result.push("vCPU", "Memory (GB)", "GPU");
        }
        return result;
    }, [type]);

    const searchRef = React.useRef<HTMLInputElement>(null);
    const onSearchType = React.useCallback(() => {
        const input = searchRef.current;
        if (!input) return;

        const query = input.value.toLowerCase();
        setFilteredProducts(props.products.filter(p => {
            return p.name.toLowerCase().indexOf(query) !== -1 ||
                p.category.name.toLowerCase().indexOf(query) !== -1 ||
                p.category.provider.toLowerCase().indexOf(query) !== -1;
        }));
    }, [props.products]);

    React.useEffect(() => {
        if (searchRef.current) searchRef.current.value = ""
        setFilteredProducts(props.products);
    }, [props.products]);

    return <ClickableDropdown
        fullWidth
        colorOnHover={false}
        keepOpenOnClick={true}
        paddingControlledByContent
        trigger={
            <SelectorBox>
                <div className="selected">
                    {selected ? null : <b>No {productName} selected</b>}
                    {!selected ? null : <>
                        <b>{selected.name}</b><br />
                        <table>
                            <thead>
                                <tr>
                                    {headers.map(it => <th key={it}>{it}</th>)}
                                    <th>Price</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr>
                                    <ProductStats product={selected} />
                                    <td>{priceExplainer(selected)}</td>
                                </tr>
                            </tbody>
                        </table>
                    </>}
                </div>

                <Icon name="chevronDown" />
            </SelectorBox>
        }
    >
        <div style={{ height: "400px", overflowY: "auto", cursor: "default", padding: "16px" }}>
            <Input placeholder={`Search ${productName}s...`} autoComplete={"off"} ref={searchRef} onInput={onSearchType} />

            <TableWrapper>
                <Table my={"16px"}>
                    <thead>
                        <TableRow>
                            <th style={{width: "32px"}} />
                            <th>Name</th>
                            {headers.map(it => <th key={it}>{it}</th>)}
                            <th>Price</th>
                        </TableRow>
                    </thead>
                    <tbody>
                        {filteredProducts.map((p, i) => (
                            <TableRow key={i}>
                                <TableCell><ProviderLogo providerId={p.category.provider} size={24} /></TableCell>
                                <TableCell>{p.name}</TableCell>
                                <ProductStats product={p} />
                                <TableCell>{priceExplainer(p)}</TableCell>
                            </TableRow>
                        ))}
                    </tbody>
                </Table>
            </TableWrapper>
        </div>
    </ClickableDropdown>;
};

const TableWrapper = styled.div`
    table {
        user-select: none;
    }

    & > table > tbody > ${TableRow}:hover {
        cursor: pointer;
        background-color: var(--lightGray, #f00);
        color: var(--black, #f00);
    }
`;

const ProductStats: React.FunctionComponent<{ product: Product }> = ({product}) => {
    switch (product.productType) {
        case "COMPUTE":
            return <>
                <TableCell>{product.cpu}</TableCell>
                <TableCell>{product.memoryInGigs}</TableCell>
                <TableCell>{product.gpu}</TableCell>
            </>
        default:
            return <></>
    }
}

const SelectorBox = styled(Box)`
  cursor: pointer;
  border-radius: 5px;
  border: ${theme.borderWidth} solid var(--midGray, #f00);
  width: 100%;

  & p {
    margin: 0;
  }

  & ${Icon} {
    position: absolute;
    bottom: 15px;
    right: 15px;
    height: 8px;
  }

  .selected {
    cursor: pointer;
    padding: 16px;

    ul {
        list-style: none;
        margin: 0;
        padding: 0;
    }

    li {
        display: inline-block;
        margin-right: 16px;
    }

    table {
        margin-top: 16px;
        width: 100%;

        thead th {
            text-align: left;
            font-weight: bold;
        }
    }
  }
`;

export const Foo: React.FunctionComponent = () => {
    return <Selector
        products={[cpuProduct]}
        selected={cpuProduct}
        onSelect={doNothing}
    />
};

const cpuProduct: ProductCompute = {
    name: "hm1-1",
    category: {
        name: "hm1",
        provider: "hippo"
    },
    chargeType: "ABSOLUTE",
    pricePerUnit: 1,
    productType: "COMPUTE",
    unitOfPrice: "UNITS_PER_MINUTE",
    type: "compute",
    description: "foobar",
    hiddenInGrantApplications: false,
    freeToUse: false,
    balance: 1000,
    cpu: 1,
    gpu: 0,
    memoryInGigs: 32,
    version: 1,
    priority: 1
};

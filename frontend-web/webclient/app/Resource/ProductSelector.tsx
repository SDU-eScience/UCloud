import * as React from "react";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import Icon from "../ui-components/Icon";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "@/ui-components/Table";
import {NoResultsCardBody} from "@/Dashboard/Dashboard";
import {Button, Link, theme} from "@/ui-components";
import styled from "styled-components";
import Box from "../ui-components/Box";
import {useEffect, useState} from "react";
import {priceExplainer, Product, ProductPriceUnit} from "@/Accounting";

export const ProductSelector: React.FunctionComponent<{
    initialSelection?: Product;
    products: Product[];
    onProductSelected: (product: Product) => void;
    slim?: boolean;
}> = ({initialSelection, products, onProductSelected, slim}) => {
    const [selected, setSelected] = useState<Product | null>(initialSelection ?? null);
    useEffect(() => {
        if (initialSelection) setSelected(initialSelection);
    }, [initialSelection]);
    useEffect(() => {
        if (selected) onProductSelected(selected);
    }, [selected, onProductSelected]);
    const productType = products.length === 0 ? "storage" : products[0].type;
    if (products.length > 0 && !products.every(it => productType === it.type)) {
        throw "Bad input passed to ProductSelector";
    }

    const unitOfPrice: ProductPriceUnit = products.length === 0 ? "PER_UNIT" : products[0].unitOfPrice;
    const hasPrice = unitOfPrice === "CREDITS_PER_DAY" || unitOfPrice === "CREDITS_PER_HOUR" ||
        unitOfPrice === "CREDITS_PER_MINUTE";

    // TODO(Dan) This is not ideal. It should use fixed positioning (to not overflow the row) but still be anchored to
    //  the 'parent'. If you remove `useMousePositioning` then the dropdown contents will immediately be hidden because
    //  it overflows the row.
    return (
        <ClickableDropdown
            fullWidth
            colorOnHover={false}
            useMousePositioning
            trigger={(
                <ProductDropdown height={slim === true ? "36px" : undefined}>
                    <ProductBox product={selected} />

                    <Icon name="chevronDown" />
                </ProductDropdown>
            )}
        >
            <Wrapper>
                {products.length === 0 ? null :
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHeaderCell pl="6px">Name</TableHeaderCell>
                                {!hasPrice ? null : <TableHeaderCell>Price</TableHeaderCell>}
                            </TableRow>
                        </TableHeader>
                        <tbody>
                            {products.map(machine => {
                                if (machine === null) return null;
                                return <TableRow key={machine.name} onClick={() => setSelected(machine)}>
                                    <TableCell>{machine.name}</TableCell>
                                    {!hasPrice ? null : <TableCell>{priceExplainer(machine)}</TableCell>}
                                </TableRow>;
                            })}
                        </tbody>
                    </Table>
                }

                {products.length !== 0 ? null : (<>
                    <NoResultsCardBody title={"No products available for use"}>
                        <SmallText>
                            You do not currently have credits for any product which you are able to use here.
                        </SmallText>

                        <Link to={"/project/grants-landing"}>
                            <Button fullWidth mb={"4px"}>Apply for resources</Button>
                        </Link>
                    </NoResultsCardBody>
                </>)}
            </Wrapper>
        </ClickableDropdown>
    )
};

const SmallText = styled.span`
    white-space: initial;
    margin-left: 16px;
    margin-right: 33px;
    font-size: 16px;
`;

const Wrapper = styled.div`
  width: 500px;
  & > table {
    margin-left: -9px;
  }

  & > table > tbody > ${TableRow}:hover {
    cursor: pointer;
    background-color: var(--lightGray, #f00);
    color: var(--black, #f00);
  }
`;

const ProductBoxWrapper = styled.div`
  cursor: pointer;
  padding: 8px;
  padding-top: 1px;

  ul {
    list-style: none;
    margin: 0;
    padding: 0;
  }

  li {
    display: inline-block;
    margin-right: 16px;
  }
`;

const ProductBox: React.FunctionComponent<{product: Product | null}> = ({product}) => (
    <ProductBoxWrapper>
        {product ? null : (
            <b>No product selected</b>
        )}

        {!product ? null : (
            <>
                <b>{product.name}</b><br />

                <ul>
                    <li>Price: {priceExplainer(product)}</li>
                </ul>
            </>
        )}
    </ProductBoxWrapper>
);


const ProductDropdown = styled(Box)`
  cursor: pointer;
  border-radius: 5px;
  border: ${theme.borderWidth} solid var(--midGray, #f00);
  width: 100%;
  min-width: 500px;

  & p {
    margin: 0;
  }

  & ${Icon} {
    position: absolute;
    bottom: 15px;
    right: 15px;
    height: 8px;
  }
`;

import * as React from "react";
import ClickableDropdown from "ui-components/ClickableDropdown";
import Icon from "../ui-components/Icon";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "ui-components/Table";
import {priceExplainer} from "Project/ProjectUsage";
import {NoResultsCardBody} from "Dashboard/Dashboard";
import {Button, Link, theme} from "ui-components";
import styled from "styled-components";
import Box from "../ui-components/Box";
import {accounting} from "UCloud";
import Product = accounting.Product;
import {useEffect, useState} from "react";

export const ProductSelector: React.FunctionComponent<{
    initialSelection?: Product;
    products: Product[];
    onProductSelected: (product: Product) => void;
}> = ({initialSelection, products, onProductSelected}) => {
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

    return (
        <ClickableDropdown
            fullWidth
            colorOnHover={false}
            trigger={(
                <ProductDropdown>
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
                                <TableHeaderCell>Price</TableHeaderCell>
                            </TableRow>
                        </TableHeader>
                        <tbody>
                            {products.map(machine => {
                                if (machine === null) return null;
                                return <TableRow key={machine.id} onClick={() => setSelected(machine)}>
                                    <TableCell pl="6px">{machine.id}</TableCell>
                                    <TableCell>{priceExplainer(machine)}</TableCell>
                                </TableRow>;
                            })}
                        </tbody>
                    </Table>
                }

                
                    <NoResultsCardBody title={"No products available for use"}>
                        <SmallText>
                            You do not currently have credits for any product which you are able to use here.
                        </SmallText>

                        <Link to={"/project/grants-landing"}>
                            <Button fullWidth mb={"4px"}>Apply for resources</Button>
                        </Link>
                    </NoResultsCardBody>
                
            </Wrapper>
        </ClickableDropdown>
    )
};

const SmallText = styled.span`
    white-space: initial;
    margin-left: 16px;
    margin-right: 33px;
    font-size: 16px;
`

const Wrapper = styled.div`
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
                <b>{product.id}</b><br />

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

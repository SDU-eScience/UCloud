import { priceExplainer, Product, ProductCompute, ProductType } from "@/Accounting";
import { Client } from "@/Authentication/HttpClientInstance";
import { NoResultsCardBody } from "@/Dashboard/Dashboard";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import { connectionState } from "@/Providers/ConnectionState";
import { ProviderLogo } from "@/Providers/ProviderLogo";
import { getProviderTitle, ProviderTitle } from "@/Providers/ProviderTitle";
import { Box, Button, Icon, Input, Link, theme } from "@/ui-components";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import Table, { TableCell, TableRow } from "@/ui-components/Table";
import { useUState } from "@/Utilities/UState";
import { grantsLink, stopPropagation } from "@/UtilityFunctions";
import * as React from "react";
import styled from "styled-components";
import { boxShadow, BoxShadowProps } from "styled-system";

const NEED_CONNECT = "need-connection";

export const ProductSelector: React.FunctionComponent<{
    products: Product[];
    selected: Product | null;
    type?: ProductType;
    slim?: boolean;
    loading?: boolean;
    onSelect: (product: Product) => void;
}> = ({ selected, ...props }) => {
    useUState(connectionState);
    const [filteredProducts, setFilteredProducts] = React.useState<Product[]>([]);
    const type = props.products.length > 0 ? props.products[0].productType : props.type;
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

    const lastSearchQuery = React.useRef<string>("");
    const searchRef = React.useRef<HTMLInputElement>(null);
    const onSearchType = React.useCallback(() => {
        const input = searchRef.current;
        if (!input) return;

        const query = input.value.toLowerCase();
        lastSearchQuery.current = query;
        setFilteredProducts(props.products.filter(p => {
            return p.name.toLowerCase().indexOf(query) !== -1 ||
                p.category.name.toLowerCase().indexOf(query) !== -1 ||
                p.category.provider.toLowerCase().indexOf(query) !== -1;
        }));
    }, [props.products, props.onSelect]);

    const categorizedProducts: (Product | string)[] = React.useMemo(() => {
        const result: (Product | string)[] = [];

        const sortedProducts = filteredProducts.sort((a, b) => {
            const pCompare = a.category.provider.localeCompare(b.category.provider);
            if (pCompare !== 0) return pCompare;

            const cCompare = a.category.name.localeCompare(b.category.name);
            if (cCompare !== 0) return cCompare;

            const aNumberSuffix = a.name.match(/^.*-(\d+)$/);
            const bNumberSuffix = b.name.match(/^.*-(\d+)$/);
            if (aNumberSuffix && bNumberSuffix) {
                return parseInt(aNumberSuffix[1]) - parseInt(bNumberSuffix[1]);
            } else {
                return a.name.localeCompare(b.name);
            }
        });

        let lastCategory = "";
        for (const product of sortedProducts) {
            let categoryName = product.category.name;
            const numberSuffix = product.name.match(/(^.*)-(\d+)$/);
            if (numberSuffix != null) {
                categoryName = numberSuffix[1];
            }

            categoryName = getProviderTitle(product.category.provider) + ": " + categoryName;

            if (lastCategory !== categoryName) {
                result.push(categoryName);
                lastCategory = categoryName;

                if (connectionState.canConnectToProvider(product.category.provider)) {
                    result.push(NEED_CONNECT);
                }
            }

            result.push(product);
        }

        return result;
    }, [filteredProducts, connectionState.lastRefresh]);


    const boxRef = React.useRef<HTMLDivElement>(null);
    const boxRect = boxRef?.current?.getBoundingClientRect() ?? { x: 0, y: 0, width: 0, height: 0, top: 0, right: 0, bottom: 0, left: 0 };
    let dialogX = boxRect.x;
    let dialogY = boxRect.y + boxRect.height;
    let dialogHeight = 500;
    const minimumWidth = 500 + headers.length * 90;
    let dialogWidth = Math.min(Math.max(minimumWidth, boxRect.width), window.innerWidth - boxRect.x - 16);
    {
        const dialogOutOfBounds = (): boolean => dialogX <= 0 || dialogY <= 0 || 
            dialogY + dialogHeight >= window.innerHeight || dialogHeight < 200;

        // Attempt to move the dialog box up a bit
        if (dialogOutOfBounds()) dialogY = boxRect.y + 30;

        // Try making it smaller
        if (dialogOutOfBounds()) dialogHeight = window.innerHeight - dialogY - 50;

        // What if we try putting it directly above?
        if (dialogOutOfBounds()) {
            dialogY = boxRect.y - 500;
            dialogHeight = 500;
        }

        // What about a smaller version?
        if (dialogOutOfBounds()) {
            dialogY = boxRect.y - 300;
            dialogHeight = 300;
        }

        // Display a modal, we cannot find any space for it.
        if (dialogOutOfBounds()) {
            dialogX = 50;
            dialogY = 50;
            dialogWidth = window.innerWidth - 50 * 2;
            dialogHeight = window.innerHeight - 50 * 2;
        }
    }


    const [isOpen, setIsOpen] = React.useState(false);
    const onClose = React.useCallback(() => {
        setIsOpen(false);
    }, []);

    const onOpen = React.useCallback(() => {
        setIsOpen(true);
    }, []);

    const onToggle = React.useCallback((e: React.SyntheticEvent) => {
        e.stopPropagation();

        if (isOpen) onClose();
        else onOpen();
    }, [isOpen]);

    React.useLayoutEffect(() => {
        setTimeout(() => {
            if (isOpen && searchRef.current) {
                searchRef.current.value = lastSearchQuery.current;
                searchRef.current.focus();
            }
        }, 30);
    }, [isOpen]);

    React.useEffect(() => {
        if (searchRef.current) searchRef.current.value = ""
        lastSearchQuery.current = "";
        setFilteredProducts(props.products);
    }, [props.products]);

    React.useLayoutEffect(() => {
        const wrapper = boxRef.current!;
        const scrollingParentFn = (elem: HTMLElement): HTMLElement => {
            let parent = elem.parentElement;
            while (parent) {
                const {overflow} = window.getComputedStyle(parent);
                if (overflow.split(" ").every(it => it === "auto" || it === "scroll")) {
                    return parent;
                } else {
                    parent = parent.parentElement;
                }
            }
            return document.documentElement;
        };
        const scrollingParent = scrollingParentFn(wrapper);

        const noScroll = (e) => {
            onClose();
        };

        document.body.addEventListener("click", onClose);
        if (isOpen) scrollingParent.addEventListener("scroll", noScroll);

        return () => {
            document.body.removeEventListener("click", onClose);
            if (isOpen) scrollingParent.removeEventListener("scroll", noScroll);
        };
    }, [isOpen]);

    const showHeadings = filteredProducts.length >= 5 || categorizedProducts.some(it => it === NEED_CONNECT);

    return <>
        <SelectorBox className={props.slim === true ? "slim" : undefined} onClick={onToggle} ref={boxRef}>
            <div className="selected">
                <b>{selected ? selected.name : <>No {productName} selected</>}</b><br />
                <table>
                    <thead>
                        <tr>
                            {headers.map(it =>
                                <th key={it} style={{ width: `${(1 / (headers.length + 1)) * 100}%` }}>
                                    {it}
                                </th>
                            )}
                            <th>Price</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            {selected ? <>
                                <ProductStats product={selected} />
                                <td>{priceExplainer(selected)}</td>
                            </> : <>
                                {headers.map((it, i) => <td key={i}>-</td>)}
                                <td>-</td>
                            </>}
                        </tr>
                    </tbody>
                </table>
                <ProviderLogo className={"provider-logo"} providerId={selected?.category?.provider ?? "?"} size={32} />
            </div>

            <Icon name="chevronDown" />
        </SelectorBox>

        {!isOpen ? null : <>
            <SelectorDialog style={{ left: dialogX, top: dialogY, width: dialogWidth, height: dialogHeight }} onClick={stopPropagation}>
                {props.loading && props.products.length === 0 ? <>
                    <HexSpin />
                </> : props.products.length === 0 ?
                    <>
                        <NoResultsCardBody title={`No machines available for use`}>
                            You do not currently have credits for any {productName} which you are able to use for this purpose. 
                            {type !== "COMPUTE" ? null : <>
                                If you are trying to run a virtual machine, please make sure you have applied for the correct credits
                                in your grant application.
                            </>}

                            <Link to={grantsLink(Client)}>
                                <Button fullWidth mb={"4px"}>Apply for resources</Button>
                            </Link>
                        </NoResultsCardBody>
                    </> :
                    <>
                        <div className="input-wrapper">
                            <Input
                                placeholder={`Search ${productName}s...`}
                                autoComplete={"off"}
                                ref={searchRef}
                                onInput={onSearchType}
                            />
                        </div>

                        <Table>
                            <thead>
                                <TableRow>
                                    <th style={{ width: "32px" }} />
                                    <th>Name</th>
                                    {headers.map(it => <th key={it}>{it}</th>)}
                                    <th>Price</th>
                                </TableRow>
                            </thead>
                            <tbody>
                                {categorizedProducts.map((p, i) => {
                                    if (typeof p === "string") {
                                        if (!showHeadings) return null;

                                        return <tr key={i} className="table-info">
                                            {p === NEED_CONNECT ?
                                                <td colSpan={3 + headers.length}>
                                                    <div>
                                                        <Link to="/providers/connect">
                                                            <Icon name="warning" color="orange" mr="8px" />
                                                            Connection required! You must connect with the provider before you can consume resources from it.
                                                        </Link>
                                                    </div>
                                                </td> :
                                                <td colSpan={3 + headers.length}>
                                                    <div>
                                                        <div className="spacer" />
                                                        {p}
                                                        <div className="spacer" />
                                                    </div>
                                                </td>
                                            }
                                        </tr>
                                    } else {
                                        const isDisabled = connectionState.canConnectToProvider(p.category.provider);
                                        const onClick = () => {
                                            if (isDisabled) return;
                                            props.onSelect(p);
                                            onClose();
                                        }

                                        return <TableRow key={i} onClick={onClick} className={isDisabled ? "disabled" : undefined}>
                                            <TableCell><ProviderLogo providerId={p.category.provider} size={24} /></TableCell>
                                            <TableCell><ProductName product={p} /></TableCell>
                                            <ProductStats product={p} />
                                            <TableCell>{priceExplainer(p)}</TableCell>
                                        </TableRow>
                                    }
                                })}
                            </tbody>
                        </Table>
                    </>
                }
            </SelectorDialog>
        </>}
    </>;
};

const ProductName: React.FunctionComponent<{ product: Product }> = ({ product }) => {
    return <>{product.name}</>;
}

const SelectorDialog = styled.div<BoxShadowProps>`
    position: fixed;
    cursor: default;
    height: 500px;
    overflow-y: auto;
    border-radius: 5px;
    ${boxShadow}
    border: 1px solid var(--borderGray);
    background: var(--white);
    padding: 16px;
    padding-top: 0;
    z-index: 1000;

    .input-wrapper {
        padding-top: 16px;
        padding-bottom: 16px;
        position: sticky;
        top: 0;
        background: var(--white);
    }

    thead > tr {
        position: sticky;
        top: 74px;
        background: var(--white);
    }

    th, td {
        text-align: left;
        overflow: hidden;
    }

    table {
        user-select: none;
    }

    table > tbody > ${TableRow}:hover {
        cursor: pointer;
        background-color: var(--lightGray, #f00);
    }

    td[colspan] div.spacer {
        content: " ";
        display: block;
        width: 45px;
        height: 1px;
        background: var(--black);
    }

    td[colspan] > div {
        display: flex;
        text-align: center;
        font-weight: bold;
        padding: 16px;
        justify-content: center;
        align-items: center;
        gap: 8px;
    }

    .table-info + .table-info > td > div {
        margin-top: -16px;
    }

    ${TableRow}.disabled {
        background-color: var(--lightGray);
        color: var(--borderGray);
        cursor: not-allowed !important;
    }
`;
SelectorDialog.defaultProps = {
    boxShadow: "md"
};

const ProductStats: React.FunctionComponent<{ product: Product }> = ({ product }) => {
    switch (product.productType) {
        case "COMPUTE":
            return <>
                <TableCell>{product.cpu} <HardwareModel model={product.cpuModel} /></TableCell>
                <TableCell>{product.memoryInGigs} <HardwareModel model={product.memoryModel} /></TableCell>
                <TableCell>
                    {product.gpu === 0 || product.gpu == null ?
                        <span style={{ color: "#a9b0b9" }}>None</span> :
                        <>{product.gpu} <HardwareModel model={product.gpuModel} /></>
                    }
                </TableCell>
            </>
        default:
            return <></>
    }
}

const HardwareModel: React.FunctionComponent<{ model?: string | null }> = props => {
    if (!props.model) return null;
    return <span style={{ color: "#a9b0b9" }}>{" "}({props.model})</span>
}

const SelectorBox = styled(Box)`
    position: relative;
    cursor: pointer;
    border-radius: 5px;
    border: ${theme.borderWidth} solid var(--midGray, #f00);
    width: 100%;
    user-select: none;
    min-width: 500px;
    font-size: initial;

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

    .provider-logo {
        position: absolute;
        top: 16px;
        right: 16px;
    }

    &.slim table, &.slim .provider-logo {
        display: none;
    }

    &.slim .selected {
        padding: 5px;
    }

    &.slim ${Icon} {
        position: absolute;
        top: calc(50% - 4px);
        right: 5px;
    }
`;

export const ProductSelectorPlayground: React.FunctionComponent = () => {
    const [selected, setSelected] = React.useState<Product | null>(null);
    return <>
        <Box height={50} width={400}>
            <ProductSelector
                products={products}
                selected={selected}
                onSelect={setSelected}
                slim
            />
        </Box>
        <ProductSelector
            products={products}
            selected={selected}
            onSelect={setSelected}
        />
    </>
};

const products: ProductCompute[] = (() => {
    let res: ProductCompute[] = [];
    res = res.concat(generateProducts({
        baseName: "hm1",
        maxCpuCount: 128,
        memPerCore: 32,
        providerName: "hippo",
        cpuModel: "AMD EPYC 7742",

    }));

    res = res.concat(generateProducts({
        baseName: "hm2",
        maxCpuCount: 128,
        memPerCore: 8,
        providerName: "hippo",
        cpuModel: "AMD EPYC 7713",
    }));

    res = res.concat(generateProducts({
        baseName: "u1-standard",
        maxCpuCount: 64,
        memPerCore: 6,
        providerName: "ucloud",
        cpuModel: "Intel Xeon Gold 6130",
    }));

    res = res.concat(generateProducts({
        baseName: "u1-fat",
        maxCpuCount: 64,
        memPerCore: 12,
        providerName: "ucloud",
        cpuModel: "Intel Xeon Gold 6130",
    }));

    res = res.concat(generateProducts({
        baseName: "u1-gpu",
        maxCpuCount: 64,
        memPerCore: 3,
        maxGpuCount: 4,
        providerName: "ucloud",
        cpuModel: "Intel Xeon Gold 6230",
        gpuModel: "NVIDIA V100",
    }));

    res = res.concat(generateProducts({
        baseName: "u2-gpu",
        maxCpuCount: 96,
        memPerCore: 21,
        maxGpuCount: 8,
        providerName: "ucloud",
        cpuModel: "AMD EPYC 7F72",
        gpuModel: "NVIDIA A100"
    }));

    res = res.concat(generateProducts({
        baseName: "st-slim",
        maxCpuCount: 32,
        memPerCore: 4,
        providerName: "sophia",
        cpuModel: "AMD EPYC 7351"
    }));

    res = res.concat(generateProducts({
        baseName: "st-fat",
        maxCpuCount: 32,
        memPerCore: 8,
        providerName: "sophia",
        cpuModel: "AMD EPYC 7351"
    }));

    res = res.concat(generateProducts({
        baseName: "uc-a10",
        maxCpuCount: 40,
        maxGpuCount: 4,
        memPerCore: 4,
        providerName: "aau",
        gpuModel: "NVIDIA A10"
    }));

    res = res.concat(generateProducts({
        baseName: "uc-general",
        maxCpuCount: 16,
        memPerCore: 4,
        providerName: "aau",
    }));

    res = res.concat(generateProducts({
        baseName: "uc-t4",
        maxCpuCount: 16,
        memPerCore: 4,
        maxGpuCount: 4,
        providerName: "aau",
        gpuModel: "NVIDIA T4"
    }));

    return res
        .map(value => ({ value, sort: Math.random() }))
        .sort((a, b) => a.sort - b.sort)
        .map(it => it.value);
})();

function generateProducts(
    opts: {
        baseName: string,
        maxCpuCount: number,
        maxGpuCount?: number,
        memPerCore: number,
        providerName: string,
        cpuModel?: string,
        memoryModel?: string,
        gpuModel?: string,
        payByCoreHours?: boolean,
        pricePerUnit?: number
    }
): ProductCompute[] {
    const result: ProductCompute[] = [];
    let iteration = 1;
    let coreCount = 1;
    if (opts.maxGpuCount != null) {
        coreCount = opts.maxCpuCount / opts.maxGpuCount;
    }
    while (coreCount <= opts.maxCpuCount) {
        const name = opts.maxGpuCount == null ? `${opts.baseName}-${coreCount}` : `${opts.baseName}-${iteration}`;
        result.push({
            name,
            category: {
                name: opts.baseName,
                provider: opts.providerName
            },
            chargeType: "ABSOLUTE",
            pricePerUnit: opts.pricePerUnit ?? 1,
            productType: "COMPUTE",
            unitOfPrice: (opts.payByCoreHours ?? true) ? "UNITS_PER_MINUTE" : "CREDITS_PER_MINUTE",
            type: "compute",
            description: "foobar",
            hiddenInGrantApplications: false,
            freeToUse: false,
            balance: 1000,
            cpu: coreCount,
            gpu: opts.maxGpuCount == null ? 0 : iteration,
            memoryInGigs: coreCount * opts.memPerCore,
            version: 1,
            priority: 1,
            cpuModel: opts.cpuModel,
            memoryModel: opts.memoryModel,
            gpuModel: opts.gpuModel,
        });

        if (opts.maxGpuCount == null) coreCount *= 2;
        else coreCount += (opts.maxCpuCount / opts.maxGpuCount);

        iteration++;
    }
    return result;
}

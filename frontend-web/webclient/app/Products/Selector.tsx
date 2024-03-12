import * as React from "react";
import ReactDOM from "react-dom";

import {ProductV2, productCategoryEquals, ProductV2Compute, ProductType, explainUnit, ProductCategoryV2, priceToString} from "@/Accounting";
import {Client} from "@/Authentication/HttpClientInstance";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {connectionState} from "@/Providers/ConnectionState";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {getProviderTitle} from "@/Providers/ProviderTitle";
import {Box, Button, Flex, Icon, Input, Link, Tooltip} from "@/ui-components";
import Table, {TableCell, TableRow} from "@/ui-components/Table";
import {useUState} from "@/Utilities/UState";
import {clamp, grantsLink, stopPropagation} from "@/UtilityFunctions";
import {ResolvedSupport} from "@/UCloud/ResourceApi";
import {explainMaintenance, maintenanceIconColor, shouldAllowMaintenanceAccess} from "@/Products/Maintenance";
import {classConcat, injectStyle} from "@/Unstyled";
import {NoResultsCardBody} from "@/UtilityComponents";

const NEED_CONNECT = "need-connection";

const dropdownPortal = "product-selector-portal";

export const ProductSelector: React.FunctionComponent<{
    products: ProductV2[];
    support?: ResolvedSupport[];
    selected: ProductV2 | null;
    type?: ProductType;
    slim?: boolean;
    loading?: boolean;
    omitBorder?: boolean;
    onSelect: (product: ProductV2) => void;
}> = ({selected, ...props}) => {
    let portal = document.getElementById(dropdownPortal);
    if (!portal) {
        const elem = document.createElement("div");
        elem.id = dropdownPortal;
        document.body.appendChild(elem);
        portal = elem;
    }

    useUState(connectionState);
    const [filteredProducts, setFilteredProducts] = React.useState<ProductV2[]>([]);
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

    const categorizedProducts: (ProductV2 | string)[] = React.useMemo(() => {
        const result: (ProductV2 | string)[] = [];

        const sortedProducts = filteredProducts.sort((a, b) => {
            const pCompare = a.category.provider.localeCompare(b.category.provider);
            if (pCompare !== 0) return pCompare;

            const cCompare = a.category.name.localeCompare(b.category.name);
            if (cCompare !== 0) return cCompare;

            const aNumberMatches = a.name.match(/(^.*)-(\d+)$/);
            const bNumberMatches = b.name.match(/(^.*)-(\d+)$/);
            if (aNumberMatches && bNumberMatches) {
                const aPrefix = aNumberMatches[1];
                const bPrefix = bNumberMatches[1];
                const pCompare = aPrefix.localeCompare(bPrefix);
                if (pCompare !== 0) return pCompare;
                return parseInt(aNumberMatches[2]) - parseInt(bNumberMatches[2]);
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
    const boxRect = boxRef?.current?.getBoundingClientRect() ?? {x: 0, y: 0, width: 0, height: 0, top: 0, right: 0, bottom: 0, left: 0};
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


    const arrowKeyIndex = React.useRef(-1);
    const itemWrapperRef = React.useRef<HTMLTableSectionElement>(null);

    const [isOpen, setIsOpen] = React.useState(false);
    const onClose = React.useCallback(() => {
        setIsOpen(false);
    }, []);

    const onOpen = React.useCallback(() => {
        arrowKeyIndex.current = -1;
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

        const noScroll = () => {
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
        <div className={classConcat(SelectorBoxClass, props.slim === true ? "slim" : undefined)} data-omit-border={props.omitBorder} onClick={onToggle} ref={boxRef}>
            <div className="selected">
                {selected ? selected.name : <>No {productName} selected</>}<br />
                {selected ? <>
                    <table>
                        <thead>
                            <tr>
                                {headers.map(it =>
                                    <th key={it} style={{width: `${(1 / (headers.length + 1)) * 100}%`}}>
                                        {it}
                                    </th>
                                )}
                                <th>Price</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr>
                                <ProductStats product={selected} />
                                <td>{priceToString(selected, 1)}</td>
                            </tr>
                        </tbody>
                    </table>
                    <ProviderLogo className={"provider-logo"} providerId={selected?.category?.provider ?? "?"} size={32} />
                </> : null}
            </div>

            <Icon name="chevronDownLight" />
        </div>

        {!isOpen ? null :
            ReactDOM.createPortal(
                <div className={SelectorDialog} style={{left: dialogX, top: dialogY, width: dialogWidth, height: dialogHeight}} onClick={stopPropagation}>
                    {props.loading && props.products.length === 0 ? <>
                        <Flex mt={(dialogHeight - 64 - 20) / 2 /* subract margin + height of HexSpin */}>
                            <HexSpin size={64} />
                        </Flex>
                    </> : props.products.length === 0 ?
                        <>
                            <NoResultsCardBody title={`No ${productName} available for use`}>
                                You do not currently have credits for any {productName} which you are able to use for this purpose.{" "}
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
                                    inputRef={searchRef}
                                    onInput={onSearchType}
                                    onKeyDown={e => {
                                        e.stopPropagation();
                                        if (!itemWrapperRef.current) return;
                                        if (["ArrowUp", "ArrowDown"].includes(e.key)) {
                                            const isDown = e.key === "ArrowDown";
                                            const isUp = e.key === "ArrowUp";

                                            const listEntries = itemWrapperRef.current.querySelectorAll(`[data-active]`);
                                            // If listEntries.length has changed, the active index may no longer be valid, but we may also not
                                            // have used the keys yet, so -1 can be the active index.
                                            arrowKeyIndex.current = clamp(arrowKeyIndex.current, -1, listEntries.length - 1);
                                            if (listEntries.length === 0) return;

                                            const oldIndex = arrowKeyIndex.current;
                                            let behavior: "instant" | "smooth" = "instant";
                                            if (isDown) {
                                                arrowKeyIndex.current += 1;
                                                if (arrowKeyIndex.current >= listEntries.length) {
                                                    arrowKeyIndex.current = 0;
                                                    behavior = "smooth";
                                                }
                                            } else if (isUp) {
                                                arrowKeyIndex.current -= 1;
                                                if (arrowKeyIndex.current < 0) {
                                                    arrowKeyIndex.current = listEntries.length - 1;
                                                    behavior = "smooth";
                                                }
                                            }

                                            if (oldIndex !== -1) listEntries.item(oldIndex)["style"].backgroundColor = "";
                                            listEntries.item(arrowKeyIndex.current)["style"].backgroundColor = "var(--lightBlue)";
                                            listEntries.item(arrowKeyIndex.current).scrollIntoView({behavior, block: "nearest"});
                                            e.stopPropagation();
                                        } else if (e.key === "Enter") {
                                            const items = itemWrapperRef.current.querySelectorAll("[data-active]");
                                            if (items.length === 0) return;
                                            if (arrowKeyIndex.current === -1) return;
                                            const p = items.item(arrowKeyIndex.current).getAttribute("data-active") as string;
                                            try {
                                                props.onSelect(categorizedProducts[parseInt(p, 10)] as ProductV2);
                                                onClose();
                                            } catch (e) {
                                                console.warn("An error ocurred parsing index for array", e);
                                            }
                                        } else if (e.key === "Escape") {
                                            e.stopPropagation();
                                            if (e.target["value"] !== "") {
                                                e.target["value"] = "";
                                            } else {
                                                onClose();
                                            }
                                        }
                                    }}
                                />
                            </div>

                            <Table>
                                <thead>
                                    <TableRow>
                                        <th style={{width: "32px"}} />
                                        <th>Name</th>
                                        {headers.map(it => <th key={it}>{it}</th>)}
                                        <th>Price</th>
                                    </TableRow>
                                </thead>
                                <tbody ref={itemWrapperRef}>
                                    {categorizedProducts.map((p, i) => {
                                        if (typeof p === "string") {
                                            if (!showHeadings) return null;
                                            return <tr key={i} className="table-info">
                                                {p === NEED_CONNECT ?
                                                    <td colSpan={3 + headers.length}>
                                                        <div>
                                                            <Link to="/providers/connect">
                                                                <Icon name="warning" color="warningMain" mr="8px" />
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
                                            const maintenance = (props.support ?? []).find(s =>
                                                s.product.name === p.name &&
                                                productCategoryEquals(s.product.category, p.category)
                                            )?.support?.maintenance;

                                            const isDisabled =
                                                connectionState.canConnectToProvider(p.category.provider) ||
                                                (maintenance?.availability === "NO_SERVICE" && !shouldAllowMaintenanceAccess());

                                            const onClick = () => {
                                                if (isDisabled) return;
                                                props.onSelect(p);
                                                onClose();
                                            }

                                            return <TableRow key={i} data-active={isDisabled ? undefined : i.toString()} onClick={onClick} className={isDisabled ? "disabled" : undefined}>
                                                <TableCell>
                                                    {maintenance ?
                                                        <Tooltip
                                                            trigger={
                                                                <Icon
                                                                    name="warning"
                                                                    color={maintenanceIconColor(maintenance)}
                                                                    size={24}
                                                                />
                                                            }
                                                        >
                                                            {explainMaintenance(maintenance)}
                                                        </Tooltip> :
                                                        <ProviderLogo providerId={p.category.provider} size={24} />
                                                    }
                                                </TableCell>
                                                <TableCell><ProductName product={p} /></TableCell>
                                                <ProductStats product={p} />
                                                <TableCell>{priceToString(p, 1)}</TableCell>
                                            </TableRow>
                                        }
                                    })}
                                </tbody>
                            </Table>
                        </>
                    }
                </div>,
                portal
            )
        }
    </>;
};

const ProductName: React.FunctionComponent<{product: ProductV2}> = ({product}) => {
    return <>{product.name}</>;
}

const SelectorDialog = injectStyle("selector-dialog", k => `
    ${k} {
        position: fixed;
        cursor: default;
        height: 500px;
        overflow-y: auto;
        border-radius: 5px;
        border: 1px solid var(--borderColor);
        background: var(--backgroundDefault);
        color: var(--textPrimary);
        padding: 16px;
        padding-top: 0;
        z-index: 1000;
    }

    ${k} .input-wrapper {
        padding-top: 16px;
        padding-bottom: 16px;
        position: sticky;
        top: 0;
        background: var(--backgroundDefault);
    }

    ${k} thead > tr {
        background: var(--backgroundDefault);
    }

    ${k} th, ${k} td {
        text-align: left;
        overflow: hidden;
        padding-left: 5px;
    }

    ${k} table {
        user-select: none;
        -webkit-user-select: none;
    }

    ${k} table > tbody > tr:hover {
        cursor: pointer;
        background-color: var(--rowHover);
    }

    ${k} td[colspan] div.spacer {
        content: " ";
        display: block;
        width: 45px;
        height: 1px;
        background: var(--textPrimary);
    }

    ${k} td[colspan] > div {
        display: flex;
        text-align: center;
        font-weight: bold;
        padding: 16px;
        justify-content: center;
        align-items: center;
        gap: 8px;
    }

    ${k} .table-info + .table-info > td > div {
        margin-top: -16px;
    }

    ${k} tr.disabled {
        background-color: var(--textDisabled);
        color: var(--borderColor);
        cursor: not-allowed !important;
    }
`);

const ProductStats: React.FunctionComponent<{product: ProductV2}> = ({product}) => {
    const computeProduct = product as ProductV2Compute;
    switch (computeProduct.type) {
        case "compute":
            return <>
                <TableCell>{computeProduct.cpu} <HardwareModel model={computeProduct.cpuModel} /></TableCell>
                <TableCell>{computeProduct.memoryInGigs} <HardwareModel model={computeProduct.memoryModel} /></TableCell>
                <TableCell>
                    {computeProduct.gpu === 0 || computeProduct.gpu == null ?
                        <span style={{color: "#a9b0b9"}}>None</span> :
                        <>{computeProduct.gpu} <HardwareModel model={computeProduct.gpuModel} /></>
                    }
                </TableCell>
            </>
        default:
            return <></>
    }
}

const HardwareModel: React.FunctionComponent<{model?: string | null}> = props => {
    if (!props.model) return null;
    return <span style={{color: "#a9b0b9"}}>{" "}({props.model})</span>
}

const SelectorBoxClass = injectStyle("selector-box", k => `
    ${k} {
        position: relative;
        cursor: pointer;
        border-radius: 5px;
        border: 1px solid var(--borderColor, #f00);
        width: 100%;
        user-select: none;
        -webkit-user-select: none;
        min-width: 500px;
        background: var(--backgroundDefault);
        box-shadow: inset 0 .0625em .125em rgba(10,10,10,.05);
    }

    ${k}:hover {
        border-color: var(--borderColorHover);
    }

    ${k} &[data-omit-border="true"] {
        border: unset;
    }

    ${k} & p {
        margin: 0;
    }

    ${k} svg {
        position: absolute;
        bottom: 13px;
        right: 15px;
        height: 16px;
    }

    ${k} .selected {
        cursor: pointer;
        padding: 7px 12px;
        line-height: 28px;
    }

    ${k} .selected ul {
        list-style: none;
        margin: 0;
        padding: 0;
    }

    ${k} .selected li {
        display: inline-block;
        margin-right: 16px;
    }

    ${k} .selected table {
        margin-top: 16px;
        width: 100%;
    }

    ${k} .selected thead th {
        text-align: left;
        font-weight: bold;
    }

    ${k} .provider-logo {
        position: absolute;
        top: 16px;
        right: 16px;
    }

    ${k}.slim table, ${k}.slim .provider-logo {
        display: none;
    }

    ${k}.slim .selected {
        padding: 5px;
    }

    ${k}.slim svg {
        position: absolute;
        top: 30%;
        right: 5px;
    }
`);

export const ProductSelectorPlayground: React.FunctionComponent = () => {
    const [selected, setSelected] = React.useState<ProductV2 | null>(null);
    return <>
        <Box height={50} width={400}>
            <ProductSelector
                products={products}
                loading={false}
                selected={selected}
                onSelect={setSelected}
                slim
            />
        </Box>
        <ProductSelector
            products={products}
            loading={false}
            selected={selected}
            onSelect={setSelected}
        />
    </>
};

const products: ProductV2Compute[] = (() => {
    let res: ProductV2Compute[] = [];
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
        .map(value => ({value, sort: Math.random()}))
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
): ProductV2Compute[] {
    const result: ProductV2Compute[] = [];
    let iteration = 1;
    let coreCount = 1;
    if (opts.maxGpuCount != null) {
        coreCount = opts.maxCpuCount / opts.maxGpuCount;
    }
    while (coreCount <= opts.maxCpuCount) {
        const name = opts.maxGpuCount == null ? `${opts.baseName}-${coreCount}` : `${opts.baseName}-${iteration}`;
        const category: ProductCategoryV2 = {
            name: opts.baseName,
            provider: opts.providerName,
            productType: "COMPUTE",
            accountingUnit: {
                name: "",
                namePlural: "",
                floatingPoint: false,
                displayFrequencySuffix: false
            },
            accountingFrequency: "ONCE",
            freeToUse: false
        };

        result.push({
            name,
            category: category,
            productType: "COMPUTE",
            type: "compute",
            description: "foobar",
            hiddenInGrantApplications: false,
            cpu: coreCount,
            gpu: opts.maxGpuCount == null ? 0 : iteration,
            memoryInGigs: coreCount * opts.memPerCore,
            price: explainUnit(category).priceFactor,
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

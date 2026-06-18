import * as React from "react";
import ReactDOM from "react-dom";

import {ProductV2, productCategoryEquals, ProductV2Compute, ProductType, explainUnit, ProductCategoryV2, priceToString, Product} from "@/Accounting";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {connectionState} from "@/Providers/ConnectionState";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {ProviderTitle} from "@/Providers/ProviderTitle";
import {Box, Button, Flex, Icon, Input, Link, Text, Tooltip} from "@/ui-components";
import Table, {TableCell, TableRow} from "@/ui-components/Table";
import {useUState} from "@/Utilities/UState";
import {clamp, grantsLink, stopPropagation} from "@/UtilityFunctions";
import {ProductSupport, ResolvedSupport} from "@/UCloud/ResourceApi";
import {explainMaintenance, maintenanceIconColor, shouldAllowMaintenanceAccess} from "@/Products/Maintenance";
import {classConcat, injectStyle, injectStyleSimple} from "@/Unstyled";
import {MandatoryField, NoResultsBody} from "@/UtilityComponents";
import {ComputeSupport, JobQueueStatus} from "@/UCloud/JobsApi";
import {ThemeColor} from "@/ui-components/theme";
import {TooltipV2} from "@/ui-components/Tooltip";
import {useSelector} from "react-redux";
import {ServiceProviderItem, ServiceProviderSelector} from "@/Applications/ApiTokens/Add";
import {InputClass} from "@/ui-components/Input";

interface ComputeCategory {
    provider: string;
    category: string;
    kind: "CPU" | "GPU";
    products: ProductV2Compute[];
}

function isComputeCategory(val: ProductV2 | ComputeCategory): val is ComputeCategory {
    return "kind" in val;
}

function legacyGroupName(product: ProductV2): string {
    const numberSuffix = product.name.match(/(^.*)-(\d+)$/);
    if (numberSuffix != null) {
        return numberSuffix[1];
    }

    return product.name;
}

function productGroupName(product: ProductV2): string {
    if (product.type !== "compute") {
        return legacyGroupName(product);
    }

    const computeProduct = product as ProductV2Compute;
    const fraction = computeProduct.fraction;
    if (!fraction) {
        return legacyGroupName(product);
    }

    if (fraction.numerator === 1 && fraction.denominator === 1) {
        return product.category.name;
    }

    if ((computeProduct.gpu ?? 0) > 0) {
        const gpuModel = (computeProduct.gpuModel ?? "").toLowerCase();
        if (gpuModel.includes("nvidia") || gpuModel.includes("mig")) {
            return `${product.category.name}-mig.${fraction.numerator}g`;
        } else {
            return `${product.category.name}-frac`;
        }
    }

    const milliCpu = Math.floor((fraction.numerator * 1000) / fraction.denominator);
    return `${product.category.name}-mcpu.${milliCpu}`;
}

export const ProductSelector: React.FunctionComponent<{
    products: ProductV2[];
    support?: ResolvedSupport[];
    selected: ProductV2 | null;
    type?: ProductType;
    slim?: boolean;
    loading?: boolean;
    onSelect: (product: ProductV2) => void;
}> = ({selected, ...props}) => {
    const portalRef = React.useRef<HTMLDivElement | null>(null);
    if (!portalRef.current) {
        portalRef.current = document.createElement("div");
    }

    React.useEffect(() => {
        const portal = portalRef.current;
        if (!portal) return;

        if (portal.parentNode !== document.body) {
            document.body.appendChild(portal);
        }

        return () => {
            if (portal.parentNode === document.body) {
                document.body.removeChild(portal);
            }
        };
    }, []);

    useUState(connectionState);
    const [filteredProducts, setFilteredProducts] = React.useState<ProductV2[]>([]);
    const type = props.products.length > 0 ? props.products[0].productType : props.type;
    const isCompute = type === "COMPUTE";
    const isDetailed = isCompute
    let productName = "product";
    switch (type) {
        case "COMPUTE":
            productName = "machine type";
            break;
    }

    const headers: {name: string, width?: string}[] = React.useMemo(() => {
        const result: {name: string, width?: string}[] = [];
        if (type === "COMPUTE") {
            result.push({name: "Type", width: "80px"}, {name: "Product category", width: "180px"}, {name: "Description"}, {name: "Status", width: "120px"});
        } else {
            result.push({name: "Name"}, {name: "Price"}, {name: "Provider"});
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

    const categorizedProducts: (ProductV2 | ComputeCategory)[] = React.useMemo(() => {
        const result: (ProductV2 | ComputeCategory)[] = [];

        const sortedProducts = [...filteredProducts].sort((a, b) => {
            const pCompare = a.category.provider.localeCompare(b.category.provider);
            if (pCompare !== 0) return pCompare;

            const aGroup = a.category.name
            const bGroup = b.category.name;

            const cCompare = aGroup.localeCompare(bGroup);
            if (cCompare !== 0) return cCompare;

            if (a.type === "compute" && b.type === "compute") {
                let aVal = (a.cpu ?? 1) + (a.gpu ?? 0 * ((a.fraction?.numerator ?? 1) / (a.fraction?.denominator ?? 1)));
                let bVal = (b.cpu ?? 1) + (b.gpu ?? 0 * ((b.fraction?.numerator ?? 1) / (b.fraction?.denominator ?? 1)));

                return aVal - bVal;
            }

            return a.name.localeCompare(b.name);
        });

        let lastCategory = "";
        for (const [index, product] of Object.entries(sortedProducts)) {
            if (product.category.provider === "aau") continue;

            let categoryName = product.category.name

            if (lastCategory !== categoryName) {
                if (type === "COMPUTE") {
                    const defaultProduct = sortedProducts[parseInt(index)] as ProductV2Compute;
                    result.push({
                        kind: kindFromProduct(defaultProduct),
                        category: product.category.name,
                        provider: product.category.provider,
                        products: []
                    });
                }

                lastCategory = categoryName;
            }

            if (type === "COMPUTE") {
                const last = result.findLast(it => isComputeCategory(it));
                last?.products.push(product as ProductV2Compute);
            } else {
                result.push(product);
            }
        }

        return result;
    }, [filteredProducts, connectionState.lastRefresh]);


    const {boxRef, dialogX, dialogY, dialogHeight, dialogWidth} = useDialogSize(headers.length, isCompute);

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
        const wrapper = boxRef.current;
        if (!wrapper) return;
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

    const showHeadings = isDetailed;
    let extraColumns = 3;
    if (type === "COMPUTE") extraColumns++;

    // Only relevant for Machine-selector, e.g. product type === "COMPUTE";
    const [serviceProvider, setServiceProvider] = React.useState("");
    const [selectedComputeCategory, setComputeCategory] = React.useState<ComputeCategory | undefined>();
    const [queueStatuses, setQueueStatuses] = React.useState<Record<string, JobQueueStatus>>({});
    React.useEffect(() => {
        if (type === "COMPUTE") {
            setQueueStatuses(naiveCategoryStatus(categorizedProducts, props.support ?? []));
        }
    }, [type, categorizedProducts, props.support]);

    React.useEffect(() => {
        const [first] = categorizedProducts;
        if (!first) return;
        if (!isComputeCategory(first)) return;
        setServiceProvider(serviceProvider => {
            if (serviceProvider) return serviceProvider;
            return first.provider;
        });
    }, [categorizedProducts]);

    // On parameter import, find correct category
    React.useEffect(() => {
        if (type === "COMPUTE" && selected) {
            const sel = selected;
            setComputeCategory(cat => {
                for (const categorized of categorizedProducts) {
                    if (isComputeCategory(categorized)) {
                        for (const p of categorized.products) {
                            if (p.name === sel.name && p.category.name === sel.category.name &&
                                p.category.provider === sel.category.provider) {
                                return categorized;
                            }
                        }
                    }
                }
                return cat;
            });
        }
    }, [type, selected, categorizedProducts]);

    const serviceProviders = React.useMemo(() => {
        if (categorizedProducts.length === 0) return [];
        const [first] = categorizedProducts;
        if (!isComputeCategory(first)) return [];
        return [...new Set((categorizedProducts as ComputeCategory[]).map(it => it.provider))].map(it => ({key: it}));
    }, [categorizedProducts]);

    let queueStatus: JobQueueStatus | null = null
    if (type === "COMPUTE") {
        const support = (props.support ?? []).find(s =>
            s.product.name === selected?.name &&
            productCategoryEquals(s.product.category, selected?.category)
        )?.support;
        queueStatus = (support as ComputeSupport)?.queueStatus ?? null;
    }

    return <>
        <Flex gap="8px">
            {isCompute ? <Box width="50%">
                <ServiceProviderSelector
                    serviceProvider={serviceProvider}
                    serviceProviders={serviceProviders}
                    onSelect={el => {
                        setServiceProvider(el.key);
                    }}
                    renderRow={props => {
                        if (!props.element?.key) return null;
                        if (!connectionState.canConnectToProvider(props.element.key)) {
                            const height = props.dataProps == null ? "31.5px" : "38px";
                            return <Flex pl="8px" key={props.element.key} height={height} {...props.dataProps} onClick={props.onSelect} alignItems={"center"}
                                gap={"8px"}>
                                <ProviderLogo className={"provider-logo"} providerId={props.element.key} size={24} />
                                <ProviderTitle providerId={props.element.key} />
                            </Flex>;
                        }
                        return <ServiceProviderItem {...props} />
                    }} />
            </Box> : null}
            <Box width={type === "COMPUTE" ? "50%" : "100%"}>
                {type === "COMPUTE" ? <Box>Machine category <MandatoryField /></Box> : null}
                <div onClick={onToggle} className={InputClass} style={{display: "flex", height: "33.5px"}} ref={boxRef}>
                    {selected ?
                        <Flex alignItems={"center"}>
                            {isCompute ? <Icon size={24} ml="-4px" name="heroCpuChip" mr="4px" /> : <ProviderLogo providerId={selected?.category?.provider ?? "?"} size={24} />}
                            {selectedComputeCategory?.category}
                            {isCompute ? null : <>
                                <Box>-</Box>
                                <Box>{priceToString(selected, 1)}</Box>
                            </>}
                        </Flex> :
                        <Flex alignItems={"center"}>No {productName} selected</Flex>
                    }

                    <Icon ml="auto" name="heroChevronDown" />
                </div>
            </Box>
        </Flex>

        {isCompute && selected ? <>
            <div className={classConcat(SelectorBoxClass, props.slim === true ? "slim" : undefined)} onClick={onToggle} ref={boxRef} style={{marginTop: "4px"}}>
                <div className="selected">
                    <>
                        {props.slim !== true ?
                            <>
                                <Flex>{queueStatus ? <div style={{
                                    marginTop: "8px",
                                    marginRight: "12px",
                                    paddingTop: "4px",
                                }}><JobQueueStatusIndicator status={queueStatus} /></div> : null} {selected?.name}</Flex><br />
                                <ProductDescription serviceProvider={selected.category.provider} category={selected.category.name} />
                                {selected ? <>
                                    <table>
                                        <tbody>
                                            <tr>
                                                <ProductStats product={selected} />
                                                <td>{priceToString(selected, 1)}</td>
                                            </tr>
                                        </tbody>
                                    </table>
                                </> : null}
                            </> :
                            <Flex alignItems={"center"} gap={"8px"}>
                                <ProviderLogo className={"provider-logo"} providerId={selected?.category?.provider ?? "?"} size={24} />
                                {selected.name}
                                <Box>-</Box>
                                <Box>{priceToString(selected, 1)}</Box>
                            </Flex>
                        }
                    </>
                </div>
                <MachineTypeSelectionSlider
                    idx={selectedComputeCategory?.products.findIndex(prod => prod === selected) ?? -1}
                    onSelect={idx => {
                        if (!selectedComputeCategory) return;
                        props.onSelect(selectedComputeCategory.products[idx])
                    }} selectedCategory={selectedComputeCategory} />
            </div>
        </> : null}

        {!isOpen ? null :
            ReactDOM.createPortal(
                <div className={SelectorDialog} style={{left: dialogX, top: dialogY, width: dialogWidth, height: dialogHeight}} onClick={stopPropagation}>
                    {props.loading && props.products.length === 0 ? <>
                        <Flex mt={(dialogHeight - 64 - 20) / 2 /* subract margin + height of HexSpin */}>
                            <HexSpin size={64} />
                        </Flex>
                    </> : props.products.length === 0 ?
                        <>
                            <NoResultsBody title={`No ${productName} available for use`}>
                                You do not currently have credits for any {productName} which you are able to use for this purpose.{" "}
                                {type !== "COMPUTE" ? null : <>
                                    If you are trying to run a virtual machine, please make sure you have applied for the correct credits
                                    in your grant application.
                                </>}

                                <Link to={grantsLink()}>
                                    <Button fullWidth mt="20px" mb={"4px"}>Apply for resources</Button>
                                </Link>
                            </NoResultsBody>
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
                                        {headers.map(it => <th key={it.name} style={{width: it.width}}>{it.name}</th>)}
                                    </TableRow>
                                </thead>
                                <tbody ref={itemWrapperRef}>
                                    {categorizedProducts.map((p, i) => {
                                        if (isComputeCategory(p)) {
                                            if (!showHeadings) return null;
                                            let queueStatus = queueStatuses[p.category];

                                            return <tr key={i} onClick={() => {
                                                props.onSelect(p.products[0]);
                                                setComputeCategory(p);
                                                setIsOpen(false);
                                            }} className="table-info">
                                                <TableCell><Icon name="heroCpuChip" /></TableCell>
                                                <TableCell>{p.kind}</TableCell>
                                                <TableCell>{p.category}</TableCell>
                                                <TableCell><ProductDescription serviceProvider={serviceProvider} category={p.category} /></TableCell>
                                                <TableCell><Box ml="16px"><JobQueueStatusIndicator multiple status={queueStatus} /></Box></TableCell>
                                            </tr>
                                        } else {
                                            const support = (props.support ?? []).find(s =>
                                                s.product.name === p.name &&
                                                productCategoryEquals(s.product.category, p.category)
                                            )?.support;

                                            const maintenance = support?.maintenance;

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
                                                <TableCell><ProviderTitle providerId={p.category.provider} /></TableCell>
                                            </TableRow>
                                        }
                                    })}
                                </tbody>
                            </Table>
                        </>
                    }
                </div>,
                portalRef.current!
            )
        }
    </>;
};

const ProductName: React.FunctionComponent<{product: ProductV2}> = ({product}) => {
    return <>{product.name}</>;
}

function ProductDescription({serviceProvider, category}: {serviceProvider: string; category: string;}): React.ReactNode {
    const description = useProductDescription(serviceProvider, category);
    return <Text fontSize={14}>{description}</Text>;
}

function useProductDescription(serviceProvider: string, category: string): string {
    const providerBrandings = useSelector((r: ReduxObject) => r.providerBrandings.providers);
    return providerBrandings[serviceProvider]?.productDescription.find(it => it.category === category)?.shortDescription ?? "No description"
}

function naiveCategoryStatus(products: (ComputeCategory | ProductV2)[], productSupport: ResolvedSupport<Product, ProductSupport>[]): Record<string, JobQueueStatus> {
    const record: Record<string, JobQueueStatus> = {};
    for (const categorizedProduct of products) {
        if (isComputeCategory(categorizedProduct)) {
            const queueStatusList: JobQueueStatus[] = [];
            for (const product of categorizedProduct.products) {
                const support = productSupport.find(s =>
                    s.product.name === product?.name &&
                    productCategoryEquals(s.product.category, product?.category)
                )?.support;
                const status = (support as ComputeSupport)?.queueStatus;
                if (status) {
                    queueStatusList.push(status);
                }
            }

            record[categorizedProduct.category] = queueStateForCategory(queueStatusList);
        }
    }
    return record;
}

function queueStateForCategory(queueStatusList: JobQueueStatus[]): JobQueueStatus {
    if (queueStatusList.length === 0) {
        console.warn("No queue status")
    } else if (queueStatusList.some(it => it === JobQueueStatus.AVAILABLE)) {
        return JobQueueStatus.AVAILABLE;
    } else if (queueStatusList.some(it => it === JobQueueStatus.BUSY)) {
        return JobQueueStatus.BUSY;
    } else {
        return JobQueueStatus.FULL;
    }

    return JobQueueStatus.AVAILABLE;
}

export const SelectorDialog = injectStyle("selector-dialog", k => `
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
        font-weight: 500;
        padding: 16px;
        justify-content: center;
        align-items: center;
        gap: 8px;
    }

    ${k} tr.disabled, ${k} tr.disabled:hover {
        background-color: var(--rowDisabled);
        color: var(--borderColor);
        cursor: not-allowed !important;
    }
`);

const ProductStats: React.FunctionComponent<{product: ProductV2}> = ({product}) => {
    const computeProduct = product as ProductV2Compute;
    switch (computeProduct.type) {
        case "compute":
            return <>
                <TableCell>{computeProduct.cpu} CPU(s)<HardwareModel model={computeProduct.cpuModel} /></TableCell>
                <TableCell>{computeProduct.memoryInGigs} GB RAM<HardwareModel model={computeProduct.memoryModel} /></TableCell>
                <TableCell>GPU(s)
                    {computeProduct.gpu === 0 || computeProduct.gpu == null ?
                        <span style={{color: "#a9b0b9"}}>{" "} None</span> :
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

function MachineTypeSelectionSlider(props: {
    selectedCategory?: ComputeCategory;
    onSelect: (index: number) => void;
    idx: number;
}): React.ReactNode {

    const dividerIndex: number = React.useMemo(() => {
        if (!props.selectedCategory) return 0;
        if (!props.selectedCategory.products.length) return 0;

        const firstFractionalIndex =
            props.selectedCategory.products.findIndex(it => it.fraction?.denominator === 1 && it.fraction?.numerator === 1);
        if (firstFractionalIndex > 0) {
            return firstFractionalIndex;
        }

        return 0;
    }, [props.selectedCategory]);

    if (!props.selectedCategory) return null;

    const productCount = props.selectedCategory.products.length;

    return <Box mb="8px" mx="8px" px="8px" onClick={stopPropagation}>
        {dividerIndex > 0 ?
            <Flex>
                <Box ml="4px">MIG (partial GPUs)</Box>
                <Box style={{position: "absolute", width: "1px", left: `calc(100% * ${dividerIndex / productCount}`, height: "50px", border: "1px solid black"}}></Box>
                <Box style={{position: "absolute", left: `calc(100% * ${dividerIndex / productCount} + 20px)`}} ml="4px">Full GPUs</Box>
            </Flex>
            : null}
        <input value={props.idx} autoFocus onChange={e => props.onSelect(e.target.valueAsNumber)}
            className={FancySlider} min={0} max={props.selectedCategory.products.length - 1} type="range" list="markers" />
        <datalist id="markers" className={DataListStyle}>
            {props.selectedCategory.products.map((p, idx) => <option key={idx} value={idx} />)}
        </datalist>
        <CustomDataListThingy>
            {props.selectedCategory.products.map((p, idx) => <Box key={idx}>
                <Box>
                    <Box>{computeV2CountStringThing(p)}</Box>
                    <JobQueueStatusIndicator status={JobQueueStatus.AVAILABLE} />
                </Box>
            </Box>)}
        </CustomDataListThingy>
    </Box>
}

const ThingyStyle = injectStyle("thingy-style", cl => `
    ${cl} {
        width: 100%;
        justify-content: space-between;
        padding-left: 4px;
        padding-top: 8px;
    }

    ${cl} > div {
        width: 30px;

    }
`);

function CustomDataListThingy(props: React.PropsWithChildren): React.ReactNode {
    return <Flex className={ThingyStyle}>
        {props.children}
    </Flex>

}

function computeV2CountStringThing(c: ProductV2Compute): string {
    if (c.gpu) {
        if (c.fraction && c.fraction?.denominator != 1) {
            return `${c.fraction.numerator * c.price} / ${c.fraction.denominator}`;
        }
        return c.gpu.toString();
    }
    return c.cpu?.toString() ?? "";
};


const FancySlider = injectStyle("fancy-slider", cl => `
    ${cl} {
        width: calc(100% - 16px);
        padding-top: 8px;
        padding-bottom: 8px;
        cursor: pointer;
        accent-color: var(--primaryMain);
    }

    ${cl}::-moz-range-track {
        /* 
            color rhs of thumb
            background-color: var(--primaryMain);
         */
    }
`);

const DataListStyle = injectStyleSimple("datalist-style", `
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    writing-mode: vertical-lr;
    width: calc(100% * 0.99);
`);

export const SelectorBoxClass = injectStyle("selector-box", k => `
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

    ${k}[data-omit-border="true"] {
        border: unset;
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
        line-height: 18px;
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
        font-weight: 500;
    }

    ${k} .provider-logo {
        position: absolute;
        top: 16px;
        right: 16px;
    }
    
    ${k}.slim .provider-logo {
        position: unset;
        top: unset;
        right: unset;
    }

    ${k}.slim .selected {
        padding: 5px 12px;
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

const JobQueueStatusIndicator: React.FunctionComponent<{
    status: JobQueueStatus;
    multiple?: boolean;
}> = (props) => {
    let color: ThemeColor = "errorMain";
    let message = "";

    switch (props.status) {
        case JobQueueStatus.AVAILABLE:
            color = "successMain";
            message = props.multiple ? "At least one machine type is available for use." : "This machine type is available for use."
            break;
        case JobQueueStatus.BUSY:
            color = "warningMain";
            message = props.multiple ? "At least one machine type is available for use, but the cluster is busy." : "This machine type is available for use, but the cluster is busy."
            break;
        case JobQueueStatus.FULL:
            color = "errorMain"
            message = props.multiple ? "This machine type is not currently available and you will have to wait in a queue." : "This machine type is not currently available and you will have to wait in a queue."
            break;
    }

    const size = "12px";

    return <TooltipV2 tooltip={message}>
        <div style={{width: size, height: size, borderRadius: size, backgroundColor: `var(--${color})`}} />
    </TooltipV2>;
}

function useDialogSize(headerCount: number, rightAligned: boolean): {boxRef: React.RefObject<HTMLDivElement | null>; dialogX: number; dialogY: number; dialogHeight: number; dialogWidth: number;} {
    const boxRef = React.useRef<HTMLDivElement>(null);
    const boxRect = boxRef?.current?.getBoundingClientRect() ?? {x: 0, y: 0, width: 0, height: 0, top: 0, right: 0, bottom: 0, left: 0};
    let dialogX = boxRect.x;
    let dialogY = boxRect.y + boxRect.height;
    let dialogHeight = 500;
    const minimumWidth = (rightAligned ? 700 : 500) + headerCount * 90;
    if (rightAligned) {
        dialogX = boxRect.x + boxRect.width - minimumWidth;
    }
    let dialogWidth = Math.min(Math.max(minimumWidth, boxRect.width), window.innerWidth);
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

    return {boxRef, dialogX, dialogY, dialogHeight, dialogWidth};
}

function kindFromProduct(prod: ProductV2Compute): "CPU" | "GPU" {
    if (prod.gpu) return "GPU";
    return "CPU";
}


import {
    priceToString,
    ProductType,
    ProductV2,
    ProductV2Compute,
    UCLOUD_PROVIDER
} from "@/Accounting";
import {useCloudAPI} from "@/Authentication/DataHook";
import {MainContainer} from "@/ui-components/MainContainer";
import {ListV2} from "@/Pagination";
import {Card, Box, Flex, Icon, Text, ContainerForText} from "@/ui-components";
import * as React from "react";
import {capitalized} from "@/UtilityFunctions";
import * as Heading from "@/ui-components/Heading";
import {Table, TableCell, TableHeader, TableHeaderCell, TableRow} from "@/ui-components/Table";
import {Client} from "@/Authentication/HttpClientInstance";
import {default as ReactModal} from "react-modal";
import {defaultModalStyle} from "@/Utilities/ModalUtilities";
import {Spacer} from "@/ui-components/Spacer";
import * as UCloud from "@/UCloud";
import CONF from "../../site.config.json";
import {useTitle} from "@/Navigation/Redux";
import {NonAuthenticatedHeader} from "@/Navigation/Header";
import {injectStyle, injectStyleSimple} from "@/Unstyled";
import {CardClass} from "@/ui-components/Card";
import {emptyPage} from "@/Utilities/PageUtilities";

function Products(): JSX.Element {
    useTitle("SKUs");

    const main = (
        <ContainerForText>
            <Heading.h2>UCloud SKUs</Heading.h2>
            <Description />

            <Box my="16px" />
            <MachineView provider={UCLOUD_PROVIDER} key={UCLOUD_PROVIDER + "STORAGE"} productType={"STORAGE"} />
            <Box my="16px" />
            <MachineView provider={"aau"} key={"aau" + "STORAGE"} productType={"STORAGE"} />
            <Box my="16px" />
            <MachineView provider={UCLOUD_PROVIDER} key={UCLOUD_PROVIDER + "COMPUTE"} productType={"COMPUTE"} />
            <Box my="16px" />
            <MachineView provider={"aau"} key={"aau" + "COMPUTE"} productType={"COMPUTE"} />
            <Box my="16px" />
            <MachineView provider={UCLOUD_PROVIDER} key={UCLOUD_PROVIDER + "INGRESS"} productType={"INGRESS"} />
            <Box my="16px" />
            <MachineView provider={"aau"} key={"aau" + "INGRESS"} productType={"INGRESS"} />
        </ContainerForText>
    );

    if (!Client.isLoggedIn) return (<>
        <NonAuthenticatedHeader />
        <Box mb="72px" />
        <Box m={[0, 0, "15px"]}>
            {main}
        </Box>
    </>);

    return (<MainContainer main={main} />);
}

const DetailedView = injectStyle("detailed-view", k => `
    ${k} > th {
        text-align: left;
        border-top: 1px solid rgba(34, 36, 38, .1);
    }

    ${k} th, td {
        padding: 16px 0;
    }
`);

export const MachineView: React.FunctionComponent<{productType: ProductType, provider: string; color?: string}> = ({productType, provider, color = "var(--primary)"}) => {
    const [machines, refetch] = useCloudAPI<UCloud.PageV2<ProductV2>>(
        {...UCloud.accounting.products.browse({filterProductType: productType, filterProvider: provider, filterUsable: true, itemsPerPage: 10}), unauthenticated: !Client.isLoggedIn},
        emptyPage
    );

    const [activeMachine, setActiveMachine] = React.useState<ProductV2 | undefined>(undefined);
    const isCompute = "COMPUTE" === productType;

    const machineCount = machines.data.items.length;
    const [hasPrice, setHasPrice] = React.useState(false);
    React.useEffect(() => {
        setHasPrice(machines.data.items.some(it => it.price));
    }, [machines.data]);
    if (machineCount === 0) return null;

    return (<>
        <Card>
            <Heading.h3 mb={"16px"}>{capitalized(productType === "INGRESS" ? "public links" : productType)}</Heading.h3>

            <Flex alignItems="center">
                <ListV2
                    page={machines.data}
                    loading={machines.loading}
                    onLoadMore={() => refetch({...UCloud.accounting.products.browse({
                        filterProductType: productType, filterProvider: provider, filterUsable: true, next: machines.data.next
                    }), unauthenticated: !Client.isLoggedIn})}
                    pageRenderer={items => (
                        <div className={MachineTypesWrapper}>
                            <Table>
                                <TableHeader>
                                    <TableRow>
                                        <TableHeaderCell>Name</TableHeaderCell>
                                        {!isCompute ? null : <TableHeaderCell>vCPU</TableHeaderCell>}
                                        {!isCompute ? null : <TableHeaderCell>RAM (GB)</TableHeaderCell>}
                                        {!isCompute ? null : <TableHeaderCell>GPU</TableHeaderCell>}
                                        {!hasPrice ? null : <TableHeaderCell>Price</TableHeaderCell>}
                                        <TableHeaderCell>Description</TableHeaderCell>
                                    </TableRow>
                                </TableHeader>
                                <tbody>
                                    {items.map(machine => {
                                        if (provider === UCLOUD_PROVIDER && productType === "COMPUTE") {
                                            // Note(Jonas): Why in the world would this ever happen?
                                            if (machine === null) return null;
                                        }
                                        const computeProduct = productType === "COMPUTE" ? machine as ProductV2Compute : null;
                                        return <TableRow key={machine.name} onClick={() => setActiveMachine(machine)}>
                                            <TableCell>{machine.name}</TableCell>
                                            {!computeProduct ? null :
                                                <TableCell>{computeProduct.cpu ?? "Unspecified"}</TableCell>}
                                            {!computeProduct ? null :
                                                <TableCell>{computeProduct.memoryInGigs ?? "Unspecified"}</TableCell>}
                                            {!computeProduct ? null : <TableCell>{computeProduct.gpu ?? 0}</TableCell>}
                                            {!hasPrice ? null : <TableCell>{priceToString(machine, 1)}</TableCell>}
                                            <td className={TruncatedTableCell}>{machine.description}</td>
                                        </TableRow>;
                                    })}
                                </tbody>
                            </Table>
                        </div>
                    )}
                />
            </Flex>
        </Card>
        <ReactModal
            ariaHideApp={false}
            shouldCloseOnEsc
            shouldCloseOnOverlayClick
            onRequestClose={() => setActiveMachine(undefined)}
            isOpen={activeMachine !== undefined}
            style={defaultModalStyle}
            className={CardClass}
        >
            <Spacer
                left={null}
                right={<Icon name="close" cursor="pointer" onClick={() => setActiveMachine(undefined)} />}
            />
            <Box maxWidth="650px">
                {activeMachine === undefined ? null :
                    <table className={DetailedView}>
                        <tbody>
                            <TableRow>
                                <TableHeaderCell>Name</TableHeaderCell>
                                <TableCell>{activeMachine.name}</TableCell>
                            </TableRow>
                            {productType !== "COMPUTE" || !("cpu" in activeMachine) ? null :
                                <>
                                    <TableRow>
                                        <th>vCPU</th>
                                        <TableCell>{activeMachine.cpu}</TableCell>
                                    </TableRow>

                                    <TableRow>
                                        <th>RAM (GB)</th>
                                        <TableCell>{activeMachine.memoryInGigs ?? "Unspecified"}</TableCell>
                                    </TableRow>

                                    {!activeMachine.gpu ? null :
                                        <TableRow>
                                            <th>GPU</th>
                                            <TableCell>{activeMachine.gpu}</TableCell>
                                        </TableRow>
                                    }
                                </>
                            }
                            <TableRow>
                                <th>Price</th>
                                <TableCell>
                                    {priceToString(activeMachine, 1)}
                                </TableCell>
                            </TableRow>
                            <TableRow>
                                <th>Description</th>
                                <TableCell><Text>{activeMachine.description}</Text></TableCell>
                            </TableRow>
                        </tbody>
                    </table>
                }
            </Box>
        </ReactModal>
    </>);
}

function Description(): JSX.Element {
    return (<>
        Below is the available SKUs on the {CONF.PRODUCT_NAME} platform.
        They are divided into different product types, i.e. storage SKUs, compute SKUs, public link SKUs and license
        SKUs.
        The prices for compute will be visible when starting a job.
    </>);
}

const TruncatedTableCell = injectStyleSimple("truncated-table-cell", `
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
`);

const MachineTypesWrapper = injectStyle("machine-types-wrapper", k => `
    ${k} th {
        text-align: left;
    }

    ${k} tr {
        padding: 8px;
    }

    ${k} > tbody > tr:hover {
        cursor: pointer;
        background-color: var(--lightGray, #f00);
        color: var(--black, #f00);
    }
`);

export default Products;

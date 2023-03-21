import {
    listByProductArea, priceExplainer,
    Product,
    ProductArea,
    ProductCompute,
    UCLOUD_PROVIDER
} from "@/Accounting";
import {useCloudAPI} from "@/Authentication/DataHook";
import {emptyPage} from "@/DefaultObjects";
import {MainContainer} from "@/MainContainer/MainContainer";
import {List, ListV2} from "@/Pagination";
import {Card, Box, Flex, Icon, Text, ContainerForText} from "@/ui-components";
import * as React from "react";
import {capitalized} from "@/UtilityFunctions";
import * as Heading from "@/ui-components/Heading";
import {Table, TableCell, TableHeader, TableHeaderCell, TableRow} from "@/ui-components/Table";
import {Client} from "@/Authentication/HttpClientInstance";
import {NonAuthenticatedHeader} from "@/Navigation/Header";
import styled from "styled-components";
import {default as ReactModal} from "react-modal";
import {defaultModalStyle} from "@/Utilities/ModalUtilities";
import {Spacer} from "@/ui-components/Spacer";
import * as UCloud from "@/UCloud";
import CONF from "../../site.config.json";
import {useTitle} from "@/Navigation/Redux/StatusActions";

function Products(): JSX.Element {
    useTitle("SKUs");

    const main = (
        <ContainerForText>
            <Heading.h2>UCloud SKUs</Heading.h2>
            <Description />

            <Box my="16px" />
            <MachineView provider={UCLOUD_PROVIDER} key={UCLOUD_PROVIDER + "STORAGE"} area={"STORAGE"} />
            <Box my="16px" />
            <MachineView provider={"aau"} key={"aau" + "STORAGE"} area={"STORAGE"} />
            <Box my="16px" />
            <MachineView provider={UCLOUD_PROVIDER} key={UCLOUD_PROVIDER + "COMPUTE"} area={"COMPUTE"} />
            <Box my="16px" />
            <MachineView provider={"aau"} key={"aau" + "COMPUTE"} area={"COMPUTE"} />
            <Box my="16px" />
            <MachineView provider={UCLOUD_PROVIDER} key={UCLOUD_PROVIDER + "INGRESS"} area={"INGRESS"} />
            <Box my="16px" />
            <MachineView provider={"aau"} key={"aau" + "INGRESS"} area={"INGRESS"} />
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

const DetailedView = styled(Table)`
    th {
        text-align: left;
        border-top: 1px solid rgba(34, 36, 38, .1);
    }

    th, td {
        padding: 16px 0;
    }
`;

export const MachineView: React.FunctionComponent<{area: ProductArea, provider: string; color?: string}> = ({area, provider, color = "var(--blue, #f00)"}) => {
    const [machines, refetch] = useCloudAPI<UCloud.PageV2<Product>>(
        UCloud.accounting.products.browse({filterArea: area, filterProvider: provider, filterUsable: true, itemsPerPage: 10}),
        emptyPage
    );

    const [activeMachine, setActiveMachine] = React.useState<Product | undefined>(undefined);
    const isCompute = "COMPUTE" === area;

    const machineCount = machines.data.items.length;
    const [hasPrice, setHasPrice] = React.useState(false);
    React.useEffect(() => {
        setHasPrice(price => price || machines.data.items.some(it =>
            ["CREDITS_PER_DAY", "CREDITS_PER_HOUR", "CREDITS_PER_MINUTE"].includes(it.unitOfPrice)
        ));
    }, [machines.data]);
    if (machineCount === 0) return null;

    return (<>
        <Card
            my="8px"
            width={1}
            overflow="hidden"
            boxShadow="sm"
            borderWidth={0}
            borderRadius={6}
        >
            <Box style={{borderTop: `5px solid ${color}`}} />
            <Box px={3} py={3} height={"100%"}>
                <Heading.h3 mb={"16px"}>{capitalized(area === "INGRESS" ? "public links" : area)}</Heading.h3>

                <Flex alignItems="center">
                    <ListV2
                        page={machines.data}
                        loading={machines.loading}
                        onLoadMore={() => refetch(UCloud.accounting.products.browse({
                            filterArea: area, filterProvider: provider, filterUsable: true, next: machines.data.next
                        }))}
                        pageRenderer={items => (
                            <MachineTypesWrapper>
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
                                            if (provider === UCLOUD_PROVIDER && area === "COMPUTE") {
                                                // Note(Jonas): Why in the world would this ever happen?
                                                if (machine === null) return null;
                                            }
                                            const showPrice = ["CREDITS_PER_DAY", "CREDITS_PER_HOUR", "CREDITS_PER_MINUTE"].includes(machine.unitOfPrice);
                                            const computeProduct = area === "COMPUTE" ? machine as ProductCompute : null;
                                            return <TableRow key={machine.name} onClick={() => setActiveMachine(machine)}>
                                                <TableCell>{machine.name}</TableCell>
                                                {!computeProduct ? null :
                                                    <TableCell>{computeProduct.cpu ?? "Unspecified"}</TableCell>}
                                                {!computeProduct ? null :
                                                    <TableCell>{computeProduct.memoryInGigs ?? "Unspecified"}</TableCell>}
                                                {!computeProduct ? null : <TableCell>{computeProduct.gpu ?? 0}</TableCell>}
                                                {!hasPrice ? null : <TableCell>{showPrice ? priceExplainer(machine) : ""}</TableCell>}
                                                <TruncatedTableCell>{machine.description}</TruncatedTableCell>
                                            </TableRow>;
                                        })}
                                    </tbody>
                                </Table>
                            </MachineTypesWrapper>
                        )}
                    />
                </Flex>
            </Box>
        </Card>
        <ReactModal
            ariaHideApp={false}
            shouldCloseOnEsc
            shouldCloseOnOverlayClick
            onRequestClose={() => setActiveMachine(undefined)}
            isOpen={activeMachine !== undefined}
            style={defaultModalStyle}
        >
            <Spacer
                left={null}
                right={<Icon name="close" cursor="pointer" onClick={() => setActiveMachine(undefined)} />}
            />
            <Box maxWidth="650px">
                {activeMachine === undefined ? null :
                    <DetailedView>
                        <tbody>
                            <TableRow>
                                <TableHeaderCell>Name</TableHeaderCell>
                                <TableCell>{activeMachine.name}</TableCell>
                            </TableRow>
                            {area !== "COMPUTE" || !("cpu" in activeMachine) ? null :
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
                                    {priceExplainer(activeMachine)}
                                </TableCell>
                            </TableRow>
                            <TableRow>
                                <th>Description</th>
                                <TableCell><Text>{activeMachine.description}</Text></TableCell>
                            </TableRow>
                        </tbody>
                    </DetailedView>
                }
            </Box>
        </ReactModal>
    </>);
}

function Description(): JSX.Element {
    return (<>
        Below is the available SKUs on the {CONF.PRODUCT_NAME} platform.
        They are divided into different product areas, i.e. storage SKUs, compute SKUs, public link SKUs and license
        SKUs.
        The prices for compute will be visible when starting a job.
    </>);
}

const TruncatedTableCell = styled(TableCell)`
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

const MachineTypesWrapper = styled.div`
  th {
    text-align: left;
  }

  tr {
    padding: 8px;
  }

  tbody > tr:hover {
    cursor: pointer;
    background-color: var(--lightGray, #f00);
    color: var(--black, #f00);
  }
`;

export default Products;

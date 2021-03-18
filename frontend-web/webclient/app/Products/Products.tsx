import {listByProductArea, PaymentModel, Product, ProductArea, UCLOUD_PROVIDER} from "Accounting";
import {useCloudAPI} from "Authentication/DataHook";
import {emptyPage} from "DefaultObjects";
import {MainContainer} from "MainContainer/MainContainer";
import {List} from "Pagination";
import {Card, Box, Flex, Icon, Text, ContainerForText} from "ui-components";
import * as React from "react";
import {capitalized, prettierString} from "UtilityFunctions";
import * as Heading from "ui-components/Heading";
import {Table, TableCell, TableHeader, TableHeaderCell, TableRow} from "ui-components/Table";
import {creditFormatter} from "Project/ProjectUsage";
import {Client} from "Authentication/HttpClientInstance";
import {NonAuthenticatedHeader} from "Navigation/Header";
import styled from "styled-components";
import * as ReactModal from "react-modal";
import {defaultModalStyle} from "Utilities/ModalUtilities";
import {Spacer} from "ui-components/Spacer";
import CONF from "../../site.config.json";
import {accounting} from "UCloud";
import ProductNS = accounting.ProductNS;

function Products(): JSX.Element {
    const main = (
        <ContainerForText>
            <Heading.h2>UCloud SKUs</Heading.h2>
            <Description/>

            <Box my="16px"/>
            <MachineView provider={UCLOUD_PROVIDER} key={"STORAGE"} area={"STORAGE"}/>
            <Box my="16px"/>
            <MachineView provider={"aau"} key={"STORAGE"} area={"STORAGE"}/>
            <Box my="16px"/>
            <MachineView provider={UCLOUD_PROVIDER} key={"COMPUTE"} area={"COMPUTE"}/>
            <Box my="16px"/>
            <MachineView provider={"aau"} key={"COMPUTE"} area={"COMPUTE"}/>
            <Box my="16px"/>
            <MachineView provider={UCLOUD_PROVIDER} key={"INGRESS"} area={"INGRESS"}/>
            <Box my="16px"/>
            <MachineView provider={"aau"} key={"INGRESS"} area={"INGRESS"}/>
            <Box my="16px"/>
            <MachineView provider={UCLOUD_PROVIDER} key={"LICENSE"} area={"LICENSE"}/>
            <Box my="16px"/>
            <MachineView provider={"aau"} key={"LICENSE"} area={"LICENSE"}/>
        </ContainerForText>
    );

    if (!Client.isLoggedIn) return (<>
        <NonAuthenticatedHeader/>
        <Box mb="72px"/>
        <Box m={[0, 0, "15px"]}>
            {main}
        </Box>
    </>);

    return (<MainContainer main={main}/>);
}

const DetailedView = styled(Table)`
  th {
    text-align: left;
    border-top: 1px solid rgba(34, 36, 38, .1);
  }

  th, ${TableCell} {
    padding: 16px 0;
  }
`;

const MachineView: React.FunctionComponent<{ area: ProductArea, provider: string }> = ({area, provider}) => {
    const [machines, refetch] = useCloudAPI<Page<Product>>(
        listByProductArea({itemsPerPage: 100, page: 0, provider: provider, area, showHidden: false}),
        emptyPage
    );

    const [activeMachine, setActiveMachine] = React.useState<Product | undefined>(undefined);
    const isStorage = "STORAGE" === area;
    const isCompute = "COMPUTE" === area;
    const isIngressOrLicense = ["LICENSE", "INGRESS"].includes(area);

    const machineCount = machines.data.items.filter(machine => {
        const ingressOrLicenseProduct = ["INGRESS", "LICENSE"].includes(area) ? machine as ProductNS.Ingress : null
        return !(ingressOrLicenseProduct && ingressOrLicenseProduct.paymentModel === "FREE_BUT_REQUIRE_BALANCE");
    }).length;
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
            <Box style={{borderTop: `5px solid var(--blue, #f00)`}}/>
            <Box px={3} py={3} height={"100%"}>
                <Heading.h3 mb={"16px"}>{capitalized(area === "INGRESS" ? "public links" : area)}</Heading.h3>

                <Flex alignItems="center">
                    <List
                        page={machines.data}
                        loading={machines.loading}
                        onPageChanged={(newPage) => refetch(listByProductArea({
                            itemsPerPage: machines.data.itemsPerPage,
                            page: newPage,
                            provider: provider,
                            area,
                            showHidden: false
                        }))}
                        pageRenderer={() => (
                            <MachineTypesWrapper>
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHeaderCell>Name</TableHeaderCell>
                                            {!isCompute ? null : <TableHeaderCell>vCPU</TableHeaderCell>}
                                            {!isCompute ? null : <TableHeaderCell>RAM (GB)</TableHeaderCell>}
                                            {!isCompute ? null : <TableHeaderCell>GPU</TableHeaderCell>}
                                            <TableHeaderCell>Price</TableHeaderCell>
                                            {!isIngressOrLicense ? null :
                                                <TableHeaderCell>Payment Model</TableHeaderCell>}
                                            <TableHeaderCell>Description</TableHeaderCell>
                                        </TableRow>
                                    </TableHeader>
                                    <tbody>
                                    {machines.data.items.map(machine => {
                                        if (machine === null) return null;
                                        const computeProduct = area === "COMPUTE" ? machine as ProductNS.Compute : null;
                                        const ingressOrLicenseProduct = ["INGRESS", "LICENSE"].includes(area) ? machine as ProductNS.Ingress : null
                                        if (ingressOrLicenseProduct && ingressOrLicenseProduct.paymentModel === "FREE_BUT_REQUIRE_BALANCE") return null;
                                        return <TableRow key={machine.id} onClick={() => setActiveMachine(machine)}>
                                            <TableCell>{machine.id}</TableCell>
                                            {!computeProduct ? null :
                                                <TableCell>{computeProduct.cpu ?? "Unspecified"}</TableCell>}
                                            {!computeProduct ? null :
                                                <TableCell>{computeProduct.memoryInGigs ?? "Unspecified"}</TableCell>}
                                            {!computeProduct ? null : <TableCell>{computeProduct.gpu ?? 0}</TableCell>}
                                            {!isIngressOrLicense ? (
                                                <TableCell>
                                                    {creditFormatter(machine.pricePerUnit * (isStorage ? 30 : 60), 3)}{isStorage ? " per GB/month" : "/hour"}
                                                </TableCell>
                                            ) : (
                                                <TableCell>
                                                    {creditFormatter(machine.pricePerUnit, 3)}
                                                </TableCell>
                                            )}
                                            {!ingressOrLicenseProduct ? null :
                                                <TableCell>{prettierString(ingressOrLicenseProduct.paymentModel)}</TableCell>}
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
                right={<Icon name="close" cursor="pointer" onClick={() => setActiveMachine(undefined)}/>}
            />
            <Box maxWidth="650px">
                {activeMachine === undefined ? null :
                    <DetailedView>
                        <tbody>
                        <TableRow>
                            <TableHeaderCell>Name</TableHeaderCell>
                            <TableCell>{activeMachine.id}</TableCell>
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
                                {creditFormatter(activeMachine.pricePerUnit * (area === "COMPUTE" ? 60 : 30))}
                                {area === "COMPUTE" ? "/hour" : " per GB/month"}
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
  ${TableHeaderCell} {
    text-align: left;
  }

  ${TableRow} {
    padding: 8px;
  }

  tbody > ${TableRow}:hover {
    cursor: pointer;
    background-color: var(--lightGray, #f00);
    color: var(--black, #f00);
  }
`;

export default Products;

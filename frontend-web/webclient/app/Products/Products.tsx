import {listByProductArea, Product, ProductArea, UCLOUD_PROVIDER} from "Accounting";
import {useCloudAPI} from "Authentication/DataHook";
import {emptyPage} from "DefaultObjects";
import {MainContainer} from "MainContainer/MainContainer";
import {List} from "Pagination";
import {Card, Box, Flex} from "ui-components";
import * as React from "react";
import {capitalized} from "UtilityFunctions";
import * as Heading from "ui-components/Heading";
import {Table, TableCell, TableHeader, TableHeaderCell, TableRow} from "ui-components/Table";
import {creditFormatter} from "Project/ProjectUsage";
import {MachineTypesWrapper} from "Applications/MachineTypes";

function Products(): JSX.Element {
    return (
        <MainContainer
            main={
                <>
                    <MachineView area={ProductArea.STORAGE} />
                    <MachineView area={ProductArea.COMPUTE} />
                </>
            }
        />
    );
}

function MachineView({area}: {area: string}): JSX.Element {
    const [machines, refetch] = useCloudAPI<Page<Product>>(
        listByProductArea({itemsPerPage: 100, page: 0, provider: UCLOUD_PROVIDER, area}),
        emptyPage
    );

    const isStorage = ProductArea.STORAGE === area;

    return (
        <Card
            my="8px"
            width={1}
            overflow="hidden"
            boxShadow="sm"
            borderWidth={0}
            borderRadius={6}
        >
            <Box style={{borderTop: `5px solid var(--blue, #f00)`}} />
            <Flex ml={"0.5em"} alignItems="center">
                <Heading.h3>{capitalized(area)}</Heading.h3>
                <Box flexGrow={1} />
            </Flex>
            <Box px={3} py={1} height={"100%"}>
                <Flex alignItems="center">
                    <List
                        page={machines.data}
                        loading={machines.loading}
                        onPageChanged={(newPage) => refetch(listByProductArea({
                            itemsPerPage: machines.data.itemsPerPage, page: newPage, provider: UCLOUD_PROVIDER, area
                        }))}
                        pageRenderer={() => (
                            <MachineTypesWrapper>
                                <Table /* style={{alignItems: "center"}} */>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHeaderCell>Name</TableHeaderCell>
                                            {isStorage ? null : <TableHeaderCell>vCPU</TableHeaderCell>}
                                            {isStorage ? null : <TableHeaderCell>RAM (GB)</TableHeaderCell>}
                                            {isStorage ? null : <TableHeaderCell>GPU</TableHeaderCell>}
                                            <TableHeaderCell>Price</TableHeaderCell>
                                        </TableRow>
                                    </TableHeader>
                                    <tbody>
                                        {machines.data.items.map(machine => {
                                            if (machine === null) return null;
                                            return <TableRow key={machine.id}>
                                                <TableCell>{machine.id}</TableCell>
                                                {isStorage ? null : <TableCell>{machine.cpu ?? "Unspecified"}</TableCell>}
                                                {isStorage ? null : <TableCell>{machine.memoryInGigs ?? "Unspecified"}</TableCell>}
                                                {isStorage ? null : <TableCell>{machine.gpu ?? 0}</TableCell>}
                                                <TableCell>{creditFormatter(machine.pricePerUnit * 60)}/hour</TableCell>
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
    );
}

export default Products;

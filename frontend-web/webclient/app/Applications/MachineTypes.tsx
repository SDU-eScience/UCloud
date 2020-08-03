import {useCloudAPI} from "Authentication/DataHook";
import * as React from "react";
import {useEffect, useState} from "react";
import styled from "styled-components";
import {theme} from "ui-components";
import Box from "ui-components/Box";
import ClickableDropdown from "ui-components/ClickableDropdown";
import Icon from "ui-components/Icon";
import {listByProductArea, Product} from "Accounting";
import {emptyPage} from "DefaultObjects";
import {creditFormatter} from "Project/ProjectUsage";
import Table, {TableHeader, TableHeaderCell, TableCell, TableRow} from "ui-components/Table";

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

export const MachineTypes: React.FunctionComponent<{
    reservation: string;
    setReservation: (name: string, machine: Product) => void;
}> = props => {
    const [machines] = useCloudAPI<Page<Product>>(
        listByProductArea({itemsPerPage: 100, page: 0, provider: "ucloud", area: "COMPUTE"}),
        emptyPage
    );
    const [selected, setSelected] = useState<Product | null>(null);

    useEffect(() => {
        setSelected(machines.data.items.find(it => it.id === props.reservation) ?? null);
    }, [props.reservation]);

    return (
        <ClickableDropdown
            fullWidth
            trigger={(
                <MachineDropdown>
                    <MachineBox machine={selected} />

                    <Icon name="chevronDown" />
                </MachineDropdown>
            )}
        >
            <MachineTypesWrapper>
                <Table>
                    <TableHeader>
                        <TableRow>
                            <TableHeaderCell>Name</TableHeaderCell>
                            <TableHeaderCell>vCPU</TableHeaderCell>
                            <TableHeaderCell>RAM (GB)</TableHeaderCell>
                            <TableHeaderCell>GPU</TableHeaderCell>
                            <TableHeaderCell>Price</TableHeaderCell>
                        </TableRow>
                    </TableHeader>
                    <tbody>
                        {machines.data.items.map(machine => {
                            if (machine === null) return null;
                            return <TableRow key={machine.id} onClick={() => props.setReservation(machine.id, machine)}>
                                <TableCell>{machine.id}</TableCell>
                                <TableCell>{machine.cpu ?? "Unspecified"}</TableCell>
                                <TableCell>{machine.memoryInGigs ?? "Unspecified"}</TableCell>
                                <TableCell>{machine.gpu ?? 0}</TableCell>
                                <TableCell>{creditFormatter(machine.pricePerUnit * 60)}/hour</TableCell>
                            </TableRow>
                        })}
                    </tbody>
                </Table>
            </MachineTypesWrapper>
        </ClickableDropdown>
    );
};

const MachineBoxWrapper = styled.div`
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
`;

const MachineBox: React.FunctionComponent<{machine: Product | null}> = ({machine}) => (
    <MachineBoxWrapper>
        {machine ? null : (
            <b>No machine selected</b>
        )}

        {!machine ? null : (
            <>
                <b>{machine.id}</b><br />
                <ul>
                    <li>{machine.cpu ? <>vCPU: {machine.cpu}</> : <>vCPU: Unspecified</>}</li>
                    <li>{machine.memoryInGigs ? <>Memory: {machine.memoryInGigs}GB</> : <>Memory: Unspecified</>}</li>
                    {machine.gpu ? <li>GPU: {machine.gpu}</li> : null}
                    <li>Price: {creditFormatter(machine.pricePerUnit * 60, 4)}/hour</li>
                </ul>
            </>
        )}
    </MachineBoxWrapper>
);

const MachineDropdown = styled(Box)`
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
`;

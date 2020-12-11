import * as React from "react";
import * as UCloud from "UCloud";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "ui-components/Table";
import ClickableDropdown from "ui-components/ClickableDropdown";
import Icon from "ui-components/Icon";
import {creditFormatter} from "Project/ProjectUsage";
import Box from "ui-components/Box";
import {theme} from "ui-components";
import {useEffect, useState} from "react";
import {useCloudAPI} from "Authentication/DataHook";
import {emptyPage} from "DefaultObjects";
import {UCLOUD_PROVIDER} from "Accounting";
import {accounting} from "UCloud";
import ComputeProductReference = accounting.ProductReference;
import styled from "styled-components";

export const reservationMachine = "reservation-machine";

export const Machines: React.FunctionComponent<{
    onMachineChange?: (product: UCloud.accounting.ProductNS.Compute) => void;
}> = props => {
    const [machines] = useCloudAPI<UCloud.Page<UCloud.accounting.ProductNS.Compute>>(
        UCloud.accounting.products.listProductionsByType({
            itemsPerPage: 100,
            page: 0,
            provider: UCLOUD_PROVIDER,
            area: "COMPUTE"
        }),
        emptyPage,
    );

    const [selected, setSelectedOnlyByListener] = useState<UCloud.accounting.ProductNS.Compute | null>(null);

    useEffect(() => {
        let listener: (() => void) | null = null;
        const valueInput = document.getElementById(reservationMachine) as HTMLInputElement | null;
        if (valueInput) {
            listener = () => {
                const value = valueInput.value;
                if (value === "") {
                    setSelectedOnlyByListener(null);
                } else {
                    const ref = JSON.parse(value) as ComputeProductReference;
                    const newMachine = machines.data.items
                        .find(it =>
                            it.id === ref.id &&
                            it.category.id === ref.category &&
                            it.category.provider === ref.provider
                        );

                    if (newMachine) {
                        setSelectedOnlyByListener(newMachine);
                        if (props.onMachineChange) props.onMachineChange(newMachine);
                    }
                }
            };

            listener();
            valueInput.addEventListener("change", listener);
        }
        return () => {
            if (valueInput && listener) valueInput.removeEventListener("change", listener);
        };
    }, [machines.data.items.length, props.onMachineChange]);

    return (
        <ClickableDropdown
            fullWidth
            colorOnHover={false}
            trigger={(
                <MachineDropdown>
                    <input type={"hidden"} id={reservationMachine} />
                    <MachineBox machine={selected} />

                    <Icon name="chevronDown" />
                </MachineDropdown>
            )}
        >
            <Wrapper>
                <Table>
                    <TableHeader>
                        <TableRow>
                            <TableHeaderCell pl="6px">Name</TableHeaderCell>
                            <TableHeaderCell>vCPU</TableHeaderCell>
                            <TableHeaderCell>RAM (GB)</TableHeaderCell>
                            <TableHeaderCell>GPU</TableHeaderCell>
                            <TableHeaderCell>Price</TableHeaderCell>
                        </TableRow>
                    </TableHeader>
                    <tbody>
                        {machines.data.items.map(machine => {
                            if (machine === null) return null;
                            return <TableRow key={machine.id} onClick={() => setMachineReservation(machine)}>
                                <TableCell pl="6px">{machine.id}</TableCell>
                                <TableCell>{machine.cpu ?? "Unspecified"}</TableCell>
                                <TableCell>{machine.memoryInGigs ?? "Unspecified"}</TableCell>
                                <TableCell>{machine.gpu ?? 0}</TableCell>
                                <TableCell>{creditFormatter(machine.pricePerUnit * 60, 3)}/hour</TableCell>
                            </TableRow>;
                        })}
                    </tbody>
                </Table>
            </Wrapper>
        </ClickableDropdown>
    )
};

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

const MachineBox: React.FunctionComponent<{machine: UCloud.accounting.ProductNS.Compute | null}> = ({machine}) => (
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

export function setMachineReservation(compute: UCloud.accounting.ProductNS.Compute | null): void {
    const valueInput = document.getElementById(reservationMachine) as HTMLInputElement | null;
    if (valueInput === null) throw "Component is no longer mounted but setSelected was called";
    if (compute === null) {
        valueInput.value = "";
        valueInput.dispatchEvent(new Event("change"));
    } else {
        setMachineReservationFromRef({
            provider: compute.category.provider,
            category: compute.category.id,
            id: compute.id
        });
    }
}

export function setMachineReservationFromRef(ref: ComputeProductReference): void {
    const valueInput = document.getElementById(reservationMachine) as HTMLInputElement | null;
    if (valueInput === null) throw "Component is no longer mounted but setSelected was called";

    valueInput.value = JSON.stringify(ref);
    valueInput.dispatchEvent(new Event("change"));
}

export function validateMachineReservation(): ComputeProductReference | null {
    const valueInput = document.getElementById(reservationMachine) as HTMLInputElement | null;
    if (valueInput === null) throw "Component is no longer mounted but validateMachineReservation was called";

    if (valueInput.value === "") return null;
    return JSON.parse(valueInput.value) as ComputeProductReference
}

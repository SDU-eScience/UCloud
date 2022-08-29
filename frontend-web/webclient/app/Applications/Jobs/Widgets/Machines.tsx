import * as React from "react";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "@/ui-components/Table";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import Icon from "@/ui-components/Icon";
import Box from "@/ui-components/Box";
import {Button, Link, theme} from "@/ui-components";
import {useEffect, useState} from "react";
import {accounting} from "@/UCloud";
import ComputeProductReference = accounting.ProductReference;
import styled from "styled-components";
import {NoResultsCardBody} from "@/Dashboard/Dashboard";
import {priceExplainer, productCategoryEquals, ProductCompute} from "@/Accounting";
import * as UCloud from "@/UCloud";

export const reservationMachine = "reservation-machine";

export function findRelevantMachinesForApplication(
    application: UCloud.compute.Application,
    machineSupport: UCloud.compute.JobsRetrieveProductsResponse,
    wallets: UCloud.PageV2<ProductCompute>
): ProductCompute[] {
    return ([] as ProductCompute[]).concat.apply(
        [],
        Object.values(machineSupport.productsByProvider).map(products => {
            return products
                .filter(it => {
                    const tool = application.invocation.tool.tool!;
                    const backend = tool.description.backend;
                    switch (backend) {
                        case "DOCKER":
                            return it.support.docker.enabled;
                        case "SINGULARITY":
                            return false;
                        case "VIRTUAL_MACHINE":
                            return it.support.virtualMachine.enabled &&
                                (tool.description.supportedProviders ?? [])
                                    .some(p => p === it.product.category.provider);
                        case "NATIVE":
                            return it.support.native.enabled &&
                                (tool.description.supportedProviders ?? [])
                                    .some(p => p === it.product.category.provider);
                    }
                })
                .filter(product =>
                    wallets.items.some(wallet => productCategoryEquals(product.product.category, wallet.category))
                )
                .map(it => it.product);
        })
    );
}

export const Machines: React.FunctionComponent<{
    machines: ProductCompute[];
    onMachineChange?: (product: ProductCompute) => void;
}> = props => {
    const [selected, setSelectedOnlyByListener] = useState<ProductCompute | null>(null);

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
                    const newMachine = props.machines.find(it =>
                        it.name === ref.id &&
                        it.category.name === ref.category &&
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
    }, [props.machines, props.onMachineChange]);

    return (
        <ClickableDropdown
            fullWidth
            colorOnHover={false}
            trigger={(
                <MachineDropdown data-component={"machines"}>
                    <input type="hidden" id={reservationMachine} />
                    <MachineBox machine={selected} />

                    <Icon name="chevronDown" />
                </MachineDropdown>
            )}
        >
            <Wrapper>
                {props.machines.length === 0 ? null :
                    <Table data-component={"machine-table"}>
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
                            {props.machines.map(machine => {
                                if (machine === null) return null;
                                if (machine.name === "syncthing") return null;
                                return <TableRow key={machine.name} onClick={() => setMachineReservation(machine)}>
                                    <TableCell pl="6px">{machine.name}</TableCell>
                                    <TableCell>{machine.cpu ?? "Unspecified"}</TableCell>
                                    <TableCell>{machine.memoryInGigs ?? "Unspecified"}</TableCell>
                                    <TableCell>{machine.gpu ?? 0}</TableCell>
                                    <TableCell>{priceExplainer(machine)}</TableCell>
                                </TableRow>;
                            })}
                        </tbody>
                    </Table>
                }

                {props.machines.length !== 0 ? null : (<>
                    <NoResultsCardBody title={"No machines available for use"}>
                        You do not currently have credits for any machine which this application is able to use. If you
                        are trying to run a virtual machine, please make sure you have applied for the correct credits
                        in your grant application.

                        <Link to={"/project/grants-landing"}>
                            <Button fullWidth mb={"4px"}>Apply for resources</Button>
                        </Link>
                    </NoResultsCardBody>
                </>)}
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

const MachineBox: React.FunctionComponent<{machine: ProductCompute | null}> = ({machine}) => (
    <MachineBoxWrapper>
        {machine ? null : (
            <b>No machine selected</b>
        )}

        {!machine ? null : (
            <>
                <b>{machine.name}</b><br />
                <ul>
                    <li>{machine.cpu ? <>vCPU: {machine.cpu}</> : <>vCPU: Unspecified</>}</li>
                    <li>{machine.memoryInGigs ? <>Memory: {machine.memoryInGigs}GB</> : <>Memory: Unspecified</>}</li>
                    {machine.gpu ? <li>GPU: {machine.gpu}</li> : null}
                    <li>Price: {priceExplainer(machine)}</li>
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

export function setMachineReservation(compute: ProductCompute | null): void {
    const valueInput = document.getElementById(reservationMachine) as HTMLInputElement | null;
    if (valueInput === null) throw "Component is no longer mounted but setSelected was called";
    if (compute === null) {
        valueInput.value = "";
        valueInput.dispatchEvent(new Event("change"));
    } else {
        setMachineReservationFromRef({
            provider: compute.category.provider,
            category: compute.category.name,
            id: compute.name
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

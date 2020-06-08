import {useCloudAPI} from "Authentication/DataHook";
import * as React from "react";
import {useEffect, useState} from "react";
import styled from "styled-components";
import {theme} from "ui-components";
import Box from "ui-components/Box";
import ClickableDropdown from "ui-components/ClickableDropdown";
import Icon from "ui-components/Icon";
import {listMachines, MachineReservation} from "Accounting/Compute";
import {Page} from "Types";
import {emptyPage} from "DefaultObjects";

export const MachineTypes: React.FunctionComponent<{
    reservation: string;
    setReservation: (name: string) => void;
    runAsRoot: boolean;
}> = props => {
    const [machines] = useCloudAPI<Page<MachineReservation>>(
        listMachines({itemsPerPage: 100, page: 0, provider: "ucloud", productCategory: "COMPUTE"}),
        emptyPage
    );
    const [selected, setSelected] = useState<MachineReservation | null>(null);

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
            {machines.data.items.map(machine => (
                <Box key={machine.id} onClick={() => props.setReservation(machine.id)}>
                    <MachineBox machine={machine} />
                </Box>
            ))}
        </ClickableDropdown>
    );
};

const MachineBox: React.FunctionComponent<{machine: MachineReservation | null}> = ({machine}) => (
    <p style={{cursor: "pointer"}}>
        {machine ? null : (
            <b>No machine selected</b>
        )}

        {!machine ? null : (
            <>
                <b>{machine.id}</b><br />
                {!machine.cpu || !machine.memoryInGigs ?
                    "Uses all available CPU and memory. Recommended for most applications."
                    : null
                }
                {machine.cpu && machine.memoryInGigs ? (
                    <>
                        vCPU: {machine.cpu}<br />
                        Memory: {machine.memoryInGigs} GB
                    </>
                ) : null}
                {machine.gpu ? (
                    <>
                        <br />
                        GPU: {machine.gpu}
                    </>
                ) : null}
            </>
        )}

    </p>
);

const MachineDropdown = styled(Box)`
    cursor: pointer;
    border-radius: 5px;
    border: ${theme.borderWidth} solid var(--midGray, #f00);
    padding: 15px;
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

import {useCloudAPI} from "Authentication/DataHook";
import * as React from "react";
import {useEffect, useState} from "react";
import styled from "styled-components";
import {theme} from "ui-components";
import Box from "ui-components/Box";
import ClickableDropdown from "ui-components/ClickableDropdown";
import Icon from "ui-components/Icon";
import {listMachines, MachineReservation, MachineType} from "Accounting/Compute";

const defaultMachine = {
    name: "Default",
        pricePerHour: 0,
    type: MachineType.STANDARD
};

export const MachineTypes: React.FunctionComponent<{
    reservation: string;
    setReservation: (name: string) => void;
    runAsRoot: boolean;
}> = props => {
    const [machines] = useCloudAPI<MachineReservation[]>(listMachines({}), []);
    const [selected, setSelected] = useState<MachineReservation>(defaultMachine);

    useEffect(() => {
        setSelected(machines.data.find(it => it.name === props.reservation) ?? defaultMachine);
    }, [props.reservation]);

    const filteredMachines = machines.data.filter(m => !(m.name === "Unspecified" && props.runAsRoot));

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
            {filteredMachines.map(machine => (
                <Box key={machine.name} onClick={() => props.setReservation(machine.name)}>
                    <MachineBox machine={machine} />
                </Box>
            ))}
        </ClickableDropdown>
    );
};

const MachineBox: React.FunctionComponent<{machine: MachineReservation}> = ({machine}) => (
    <p style={{cursor: "pointer"}}>
        <b>{machine.name}</b><br />
        {!machine.cpu || !machine.memoryInGigs ?
            "Uses all available CPU and memory. Recommended for most applications."
            : null
        }
        {machine.cpu && machine.memoryInGigs ? (
            <>
                CPU: {machine.cpu}<br />
                Memory: {machine.memoryInGigs} GB memory
                </>
        ) : null}
        {machine.gpu ? (
            <>
                <br />
                GPU: {machine.gpu}
            </>
        ) : null}
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

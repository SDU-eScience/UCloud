import {MachineReservation, machineTypes} from "Applications/api";
import {useCloudAPI} from "Authentication/DataHook";
import * as React from "react";
import {useEffect, useState} from "react";
import styled from "styled-components";
import {theme} from "ui-components";
import Box from "ui-components/Box";
import ClickableDropdown from "ui-components/ClickableDropdown";
import Icon from "ui-components/Icon";
import {HiddenInputField} from "ui-components/Input";

export const MachineTypes: React.FunctionComponent<{
    inputRef: React.RefObject<HTMLInputElement>;
    runAsRoot: boolean;
}> = props => {
    const [machines] = useCloudAPI<MachineReservation[]>(machineTypes(), []);
    const [selected, setSelected] = useState<string>("");

    const selectedMachineFromList = machines.data.find(it => it.name === selected);
    const selectedMachine: MachineReservation = selectedMachineFromList ? selectedMachineFromList : {
        name: "Default",
        memoryInGigs: null,
        cpu: null,
        gpu: null
    };

    useEffect(() => {
        if (!props.inputRef) return;

        const current = props.inputRef.current;
        if (current === null) return;

        current.value = selected;
    }, [props.inputRef, selected]);

    const filteredMachines = machines.data.filter(m => !(m.name === "Unspecified" && props.runAsRoot));

    return (
        <ClickableDropdown
            fullWidth
            trigger={(
                <MachineDropdown>
                    <MachineBox machine={selectedMachine} />

                    <Icon name="chevronDown" />
                    <HiddenInputField ref={props.inputRef} />
                </MachineDropdown>
            )}
        >
            {filteredMachines.map(machine => (
                <Box key={machine.name} onClick={() => setSelected(machine.name)}>
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

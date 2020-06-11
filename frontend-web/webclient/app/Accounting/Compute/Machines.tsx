import * as React from "react";
import {useCallback, useEffect, useRef, useState} from "react";
import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {
    listMachines,
    MachineReservation
} from "Accounting/Compute/index";
import {MainContainer} from "MainContainer/MainContainer";
import {Box, Button, Icon, IconButton, Input, Label, List} from "ui-components";
import {ListRow, ListRowStat} from "ui-components/List";
import {dialogStore} from "Dialog/DialogStore";
import styled from "styled-components";
import Spinner from "LoadingIcon/LoadingIcon";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {useDispatch} from "react-redux";
import {setActivePage, updatePageTitle} from "Navigation/Redux/StatusActions";
import {SidebarPages} from "ui-components/Sidebar";
import {setLoading} from "Files/Redux/FileInfoActions";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";

const MachineActions = styled.div`
    display: flex;
    align-items: center;
    
    & > * {
        margin-right: 16px;
    }
`;

export const MachineAdmin: React.FunctionComponent = () => {
    const [machines, fetchMachines] = useCloudAPI<MachineReservation[]>(
        listMachines({itemsPerPage: 100, page: 0, productCategory: "COMPUTE", provider: "ucloud"}),
        []
    );

    const [loading, runCommand] = useAsyncCommand();
    const dispatch = useDispatch();

    const reload = useCallback(() => {
        fetchMachines(listMachines({itemsPerPage: 100, page: 0, productCategory: "COMPUTE", provider: "ucloud"}));
    }, []);

    useEffect(() => {
        dispatch(setActivePage(SidebarPages.Admin));
        dispatch(updatePageTitle("Machines"));

        return () => {
            dispatch(updatePageTitle(""));
            dispatch(setActivePage(SidebarPages.None));
        };
    }, []);

    useEffect(() => {
        dispatch(setRefreshFunction(reload));
        return () => {
            dispatch(setRefreshFunction());
        };
    }, [dispatch, reload]);

    useEffect(() => {
        dispatch(setLoading(machines.loading || loading));
    }, [machines.loading, loading]);

    return <MainContainer
        main={(
            <>
                <List>
                    {machines.loading ? <Spinner /> : null}
                    {!machines.error ? null : <>{machines.error.why}</>}
                    {machines.error ? null : machines.data.map(machine => (
                        <ListRow
                            key={machine.id}
                            left={(
                                <>
                                    {machine.id}
                                </>
                            )}
                            leftSub={(
                                <>
                                    <ListRowStat>vCPU: {machine.cpu ?? "unspecified"}</ListRowStat>
                                    <ListRowStat>RAM: {machine.memoryInGigs ?? "unspecified"}</ListRowStat>
                                    <ListRowStat>GPU: {machine.gpu ?? "unspecified"}</ListRowStat>
                                    <ListRowStat>Price: {machine.pricePerUnit}</ListRowStat>
                                </>
                            )}
                            right={(
                                <MachineActions>
                                    <IconButton name={"edit"} onClick={() => {
                                        dialogStore.addDialog(
                                            <MachineEditor isEdit={true} machine={machine} onSubmit={async machine => {
                                                //await runCommand(updateMachine(machine));
                                                reload();
                                                dialogStore.success();
                                            }} />,
                                            () => false,
                                            true
                                        );
                                    }} />
                                    <Button color={"red"}><Icon name={"trash"} /></Button>
                                </MachineActions>
                            )}
                        />
                    ))}
                </List>

                <Button type={"button"} onClick={() => {
                    dialogStore.addDialog(
                        <MachineEditor isEdit={false} onSubmit={async machine => {
                            //await runCommand(createMachine(machine));
                            reload();
                            dialogStore.success();
                        }} />,
                        () => false,
                        true
                    );
                }}>
                    Create new machine
                </Button>
            </>
        )}
    />;
};

const MachineEditorForm = styled.form`
    & > ${Label} {
        margin-bottom: 16px;
    }
`;

function parseIntForMachine(input: HTMLInputElement | null, fieldName: string): number | undefined {
    if (input === null) return undefined;
    const value = input.value;
    if (value.trim() === "") return undefined;
    if (!value.match(/\d+/)) {
        snackbarStore.addFailure("Invalid number given for " + fieldName, false);
        // eslint-disable-next-line
        throw "Invalid number";
    }

    return parseInt(value, 10);
}

const MachineEditor: React.FunctionComponent<{
    machine?: MachineReservation;
    onSubmit: (machine: MachineReservation) => void;
    isEdit: boolean;
}> = ({machine, onSubmit, isEdit}) => {
    const [name, setName] = useState<string>(machine?.id ?? "u1-standard-0");
    const [type, setType] = useState<string>(machine?.id ?? "standard");

    const cpuRef = useRef<HTMLInputElement>(null);
    const gpuRef = useRef<HTMLInputElement>(null);
    const memRef = useRef<HTMLInputElement>(null);
    const priceRef = useRef<HTMLInputElement>(null);

    return <Box minWidth={800} m={32}>
        <MachineEditorForm onSubmit={e => {
            e.preventDefault();

            try {
                const pricePerUnit = parseIntForMachine(priceRef.current, "Price");
                if (pricePerUnit === undefined) {
                    snackbarStore.addFailure("No price given", false);
                    return;
                }

                onSubmit({
                    cpu: parseIntForMachine(cpuRef.current, "vCPU"),
                    gpu: parseIntForMachine(gpuRef.current, "GPU"),
                    memoryInGigs: parseIntForMachine(memRef.current, "RAM"),
                    pricePerUnit,
                    id: name,
                    category: { provider: "ucloud", id: type },
                    priority: 1,
                    description: "",
                    availability: { type: "available" }
                });
            } catch (ignored) {
                // Ignored
            }
        }}>
            {isEdit ? <Label>Editing &apos;{name}&apos;</Label> :
                <Label>
                    Name
                    <Input placeholder={"Name"} value={name} onChange={e => setName(e.target.value)}
                           autocomplete={"off"}/>
                </Label>
            }

            <Label>
                Type <br/>
                <Input
                    value={type}
                    onChange={e => setType(e.target.value)}
                />
            </Label>

            <Label>
                vCPU
                <Input placeholder={"vCPU"} type={"number"} ref={cpuRef} min={1} autocomplete={"off"} />
            </Label>

            <Label>
                GPU
                <Input placeholder={"GPU"} type={"number"} ref={gpuRef} min={0}
                       autocomplete={"off"}/>
            </Label>

            <Label>
                RAM (GB)
                <Input placeholder={"RAM"} type={"number"} ref={memRef} min={1} autocomplete={"off"} />
            </Label>

            <Label>
                Price per hour
                <Input placeholder={"Price"} type={"number"} ref={priceRef} min={1} autocomplete={"off"} required />
            </Label>

            <Button fullWidth>{isEdit ? "Update" : "Create"}</Button>
        </MachineEditorForm>
    </Box>;
};

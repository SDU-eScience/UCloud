import * as React from "react";
import NetworkIPApi, {NetworkIP} from "@/UCloud/NetworkIPApi";
import {Box, Button, Icon, Input, Select, Text} from "@/ui-components";
import {MandatoryField, ShakingBox} from "@/UtilityComponents";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "@/ui-components/Table";
import {useCallback, useRef, useState} from "react";
import {useCloudCommand} from "@/Authentication/DataHook";
import {blankOrUndefined} from "@/UtilityFunctions";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {classConcat} from "@/Unstyled";
import TabbedCard, {TabbedCardTab} from "@/ui-components/TabbedCard";
import {compute} from "@/UCloud";

export const FirewallEditor: React.FunctionComponent<{
    inspecting: NetworkIP;
    reload: () => void;
}> = ({inspecting, reload}) => {
    const [commandLoading, invokeCommand] = useCloudCommand();
    const [didChange, setDidChange] = useState(false);

    const onAddRow = useCallback(async (first: string, last: string, protocol: "UDP" | "TCP") => {
        const {valid, firstPort, lastPort} = parseAndValidatePorts(first, last);
        if (!valid) return;

        await invokeCommand(NetworkIPApi.updateFirewall({
            type: "bulk",
            items: [{
                id: inspecting.id,
                firewall: {
                    openPorts: [
                        ...(inspecting.specification.firewall?.openPorts ?? []),
                        {start: firstPort, end: lastPort, protocol}
                    ]
                }
            }]
        }));
        reload();

        setDidChange(true);
    }, [inspecting, reload]);

    const onRemoveRow = useCallback(async (idx: number) => {
        const ports = [...(inspecting.specification.firewall?.openPorts ?? [])];
        ports.splice(idx, 1);
        await invokeCommand(NetworkIPApi.updateFirewall({
            type: "bulk",
            items: [{id: inspecting.id, firewall: {openPorts: ports}}]
        }));

        reload();
        setDidChange(true);
    }, [inspecting, reload]);

    return <FirewallTable didChange={didChange} onAddRow={onAddRow} onRemoveRow={onRemoveRow} openPorts={inspecting.specification.firewall?.openPorts ?? []} />
};

export function parseAndValidatePorts(first: string, last: string) {
    const firstPort = parseInt(first, 10);
    const lastPort = blankOrUndefined(last) ? firstPort : parseInt(last, 10);
    let valid = true;

    if (isNaN(firstPort) || firstPort < 1) {
        snackbarStore.addFailure("Port (First) is not a valid positive number", false);
        valid = false;
    }

    if (isNaN(lastPort) || lastPort < 1) {
        snackbarStore.addFailure("Port (Last) is not a valid positive number", false);
        valid = false;
    }

    if (firstPort > lastPort) {
        snackbarStore.addFailure("The first port is larger than the last port", false);
        valid = false;
    }

    return {firstPort, lastPort, valid}
}

interface FirewallTableProps {
    openPorts: compute.PortRangeAndProto[];
    isCreating?: boolean;
    didChange: boolean;
    onAddRow: (first: string, last: string, protocol: "TCP" | "UDP") => void;
    onRemoveRow: (idx: number) => void;
    showCard?: boolean;
}

export function FirewallTable({isCreating, didChange, onAddRow, onRemoveRow, openPorts, ...props}: FirewallTableProps) {
    const portFirstRef = useRef<HTMLInputElement>(null);
    const portLastRef = useRef<HTMLInputElement>(null);
    const protocolRef = useRef<HTMLSelectElement>(null);
    const showCard = props.showCard ?? true;

    const body = <>
        <form onSubmit={e => {
            e.preventDefault();
            const pf = portFirstRef.current;
            const pl = portLastRef.current;
            onAddRow(portFirstRef.current!.value, portLastRef.current!.value, protocolRef.current!.value as "TCP" | "UDP");
            if (pf) pf.value = "";
            if (pl) pl.value = "";
        }}>
            <Table>
                <TableHeader>
                    <TableRow>
                        <TableHeaderCell textAlign={"left"}>Port (First)<MandatoryField/></TableHeaderCell>
                        <TableHeaderCell textAlign={"left"}>Port (Last)</TableHeaderCell>
                        <TableHeaderCell textAlign={"left"}>Protocol</TableHeaderCell>
                        <TableHeaderCell width={"48px"} />
                    </TableRow>
                </TableHeader>
                <tbody>
                {openPorts.map((row, idx) => {
                    return <TableRow key={idx}>
                        <TableCell>{row.start}</TableCell>
                        <TableCell>{row.end}</TableCell>
                        <TableCell>{row.protocol}</TableCell>
                        <TableCell>
                            <Button
                                width="100%"
                                color="errorMain"
                                type={"button"}
                                onClick={() => onRemoveRow(idx)}
                            >
                                <Icon name={"heroTrash"} />
                            </Button>
                        </TableCell>
                    </TableRow>
                })}
                <TableRow>
                    <TableCell pr={"16px"}><Input type="number" min={0} max={65535} inputRef={portFirstRef} /></TableCell>
                    <TableCell pr={"16px"}><Input type="number" min={0} max={65535} inputRef={portLastRef} /></TableCell>
                    <TableCell pr={"16px"}>
                        <Select selectRef={protocolRef}>
                            <option>TCP</option>
                            <option>UDP</option>
                        </Select>
                    </TableCell>
                    <TableCell>
                        <Button type={"submit"} fullWidth><Icon name={"heroPlus"} /></Button>
                    </TableCell>
                </TableRow>
                </tbody>
            </Table>
        </form>
    </>;

    if (showCard) {
        return <TabbedCard>
            <TabbedCardTab name={"Firewall"} icon={"verified"}>
                {body}
            </TabbedCardTab>
        </TabbedCard>;
    } else {
        return body;
    }
}

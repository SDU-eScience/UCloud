import * as React from "react";
import NetworkIPApi, {NetworkIP} from "@/UCloud/NetworkIPApi";
import {Box, Button, Input, Select, Text} from "@/ui-components";
import {ShakingBox} from "@/UtilityComponents";
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
}

export function FirewallTable({isCreating, didChange, onAddRow, onRemoveRow, openPorts}: FirewallTableProps) {
    const portFirstRef = useRef<HTMLInputElement>(null);
    const portLastRef = useRef<HTMLInputElement>(null);
    const protocolRef = useRef<HTMLSelectElement>(null);

    return <TabbedCard>
        <TabbedCardTab name={"Firewall"} icon={"verified"}>
            {!didChange ?
                <>
                    <Box height={80}>
                        <b>Example:</b> to configure the firewall to accept SSH connections you would typically put in:
                        <pre><code>Port (First) = 22, Port (Last) = 22, Protocol = TCP</code></pre>
                    </Box>
                </> :
                <Box className={classConcat(ShakingBox, "shaking")} height={80}>
                    <b>Note:</b> Your application must be <i>restarted</i> for the firewall to take effect.
                </Box>
            }

            <form onSubmit={e => {
                e.preventDefault();
                onAddRow(portFirstRef.current!.value, portLastRef.current!.value, protocolRef.current!.value as "TCP" | "UDP");
            }}>
                <Table>
                    <TableHeader>
                        <TableRow>
                            <TableHeaderCell textAlign={"left"}>Port (First)</TableHeaderCell>
                            <TableHeaderCell textAlign={"left"}>Port (Last)</TableHeaderCell>
                            <TableHeaderCell textAlign={"left"}>Protocol</TableHeaderCell>
                        </TableRow>
                    </TableHeader>
                    <tbody>
                        {openPorts.map((row, idx) => {
                            return <TableRow key={idx}>
                                <TableCell>{row.start}</TableCell>
                                <TableCell>{row.end}</TableCell>
                                <TableCell>{row.protocol}</TableCell>
                                <TableCell>
                                    {isCreating ?
                                        <Button width="100%" color="errorMain" onClick={() => onRemoveRow(idx)}>
                                            Remove
                                        </Button> : <ConfirmationButton
                                            type={"button"}
                                            color={"errorMain"}
                                            fullWidth
                                            icon={"close"}
                                            actionText={"Remove"}
                                            onAction={() => onRemoveRow(idx)}
                                        />}
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
                            <TableCell><Button type={"submit"} fullWidth><Text fontSize={"18px"}>Add </Text></Button></TableCell>
                        </TableRow>
                    </tbody>
                </Table>
            </form>
        </TabbedCardTab>
    </TabbedCard>
}

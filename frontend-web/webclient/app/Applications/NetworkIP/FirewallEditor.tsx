import * as React from "react";
import NetworkIPApi, {NetworkIP} from "@/UCloud/NetworkIPApi";
import {Box, Button, Input, Select, Text} from "@/ui-components";
import {ShakingBox} from "@/UtilityComponents";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "@/ui-components/Table";
import {useCallback, useRef, useState} from "react";
import {useCloudCommand} from "@/Authentication/DataHook";
import {blankOrUndefined} from "@/UtilityFunctions";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import HighlightedCard from "@/ui-components/HighlightedCard";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {classConcat} from "@/Unstyled";

export const FirewallEditor: React.FunctionComponent<{
    inspecting: NetworkIP;
    reload: () => void;
}> = ({inspecting, reload}) => {
    const portFirstRef = useRef<HTMLInputElement>(null);
    const portLastRef = useRef<HTMLInputElement>(null);
    const protocolRef = useRef<HTMLSelectElement>(null);
    const [commandLoading, invokeCommand] = useCloudCommand();
    const [didChange, setDidChange] = useState(false);

    const onAddRow = useCallback(async (e: React.SyntheticEvent) => {
        e.preventDefault();

        const first = portFirstRef.current!.value;
        const last = portLastRef.current!.value;
        const protocol = protocolRef.current!.value as "TCP" | "UDP";

        const firstPort = parseInt(first, 10);
        const lastPort = blankOrUndefined(last) ? firstPort : parseInt(last, 10);

        if (isNaN(firstPort) || firstPort < 1) {
            snackbarStore.addFailure("Port (First) is not a valid positive number", false);
            return;
        }

        if (isNaN(lastPort) || lastPort < 1) {
            snackbarStore.addFailure("Port (Last) is not a valid positive number", false);
            return;
        }

        if (firstPort > lastPort) {
            snackbarStore.addFailure("The first port is larger than the last port", false);
            return;
        }

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

        if (portFirstRef.current) portFirstRef.current.value = "";
        if (portLastRef.current) portLastRef.current.value = "";
        if (protocolRef.current) protocolRef.current.value = "TCP";
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

    return <>
        <HighlightedCard color={"purple"} isLoading={false} title={"Firewall"} icon={"verified"}>
            {!didChange ?
                <>
                    <Box height={120}>
                        <b>Example:</b> to configure the firewall to accept SSH connections you would typically put in:
                        <pre><code>Port (First) = 22, Port (Last) = 22, Protocol = TCP</code></pre>
                    </Box>
                </> :
                <Box className={classConcat(ShakingBox, "shaking")} height={120}>
                    <b>Note:</b> Your application must be <i>restarted</i> for the firewall to take effect.
                </Box>
            }

            <form onSubmit={onAddRow}>
                <Table>
                    <TableHeader>
                        <TableRow>
                            <TableHeaderCell textAlign={"left"}>Port (First)</TableHeaderCell>
                            <TableHeaderCell textAlign={"left"}>Port (Last)</TableHeaderCell>
                            <TableHeaderCell textAlign={"left"}>Protocol</TableHeaderCell>
                        </TableRow>
                    </TableHeader>
                    <tbody>
                        {(inspecting.specification.firewall?.openPorts ?? []).map((row, idx) => {
                            return <TableRow key={idx}>
                                <TableCell>{row.start}</TableCell>
                                <TableCell>{row.end}</TableCell>
                                <TableCell>{row.protocol}</TableCell>
                                <TableCell>
                                    <ConfirmationButton
                                        color={"red"}
                                        fullWidth
                                        icon={"close"}
                                        actionText={"Remove"}
                                        onAction={() => onRemoveRow(idx)}
                                    />
                                </TableCell>
                            </TableRow>
                        })}
                        <TableRow>
                            <TableCell><Input inputRef={portFirstRef} /></TableCell>
                            <TableCell><Input inputRef={portLastRef} /></TableCell>
                            <TableCell>
                                <Select selectRef={protocolRef}>
                                    <option>TCP</option>
                                    <option>UDP</option>
                                </Select>
                            </TableCell>
                            <TableCell><Button type={"submit"} fullWidth onClick={onAddRow}><Text fontSize={"18px"}>Add </Text></Button></TableCell>
                        </TableRow>
                    </tbody>
                </Table>
            </form>
        </HighlightedCard>
    </>
};

import * as React from "react";
import {Box, Button, Icon, Input, Select} from "ui-components";
import * as Heading from "ui-components/Heading";
import {blankOrUndefined, prettierString, shortUUID} from "UtilityFunctions";
import {compute, provider} from "UCloud";
import NetworkIP = compute.NetworkIP;
import {useCloudCommand} from "Authentication/DataHook";
import {useCallback, useRef, useState} from "react";
import * as UCloud from "UCloud";
import {ShakingBox} from "UtilityComponents";
import {entityName} from ".";
import networkApi = UCloud.compute.networkips;
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "ui-components/Table";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {ResourcePage} from "ui-components/ResourcePage";
import ResourceDoc = provider.ResourceDoc;

const Inspect: React.FunctionComponent<{
    inspecting: NetworkIP,
    close: () => void,
    reload: () => void
}> = ({inspecting, close, reload}) => {
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

        if (isNaN(firstPort) || firstPort < 0) {
            snackbarStore.addFailure("Port (First) is not a valid positive number", false);
            return;
        }

        if (isNaN(lastPort) || lastPort < 0) {
            snackbarStore.addFailure("Port (Last) is not a valid positive number", false);
            return;
        }

        if (firstPort > lastPort) {
            snackbarStore.addFailure("The last port is larger than the first port", false);
            return;
        }

        await invokeCommand(networkApi.updateFirewall({
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
        await invokeCommand(networkApi.updateFirewall({
            type: "bulk",
            items: [{id: inspecting.id, firewall: {openPorts: ports}}]
        }));

        reload();
        setDidChange(true);
    }, [inspecting, reload]);

    return <ResourcePage
        entityName={entityName}
        entity={inspecting as ResourceDoc}
        aclOptions={[{icon: "search", name: "USE", title: "Use"}]}
        reload={reload}
        updateAclEndpoint={networkApi.updateAcl}
        stats={[
            {title: "IP Address", render: t => (t as any).status.ipAddress ?? "No address assigned"}
        ]}
        showBilling={true}
        showProduct={true}

        beforeStats={<Box><Icon name={"arrowDown"} rotation={90} size={"32px"} cursor={"pointer"} onClick={close}/></Box>}
        beforeUpdates={
            <>
                <Heading.h4 flexGrow={1} mb={8}>Firewall</Heading.h4>

                {!didChange ?
                    <>
                        <Box height={120}>
                            <b>Example:</b> to configure the firewall to accept SSH connections you would typically put in:
                            <pre><code>Port (First) = 22, Port (Last) = 22, Protocol = TCP</code></pre>
                        </Box>
                    </> :
                    <ShakingBox shaking height={120}>
                        <b>Note:</b> Your application must be <i>restarted</i> for the firewall to take effect.
                    </ShakingBox>
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
                                    <Button type={"button"} color={"red"} fullWidth
                                            onClick={() => onRemoveRow(idx)}>Remove</Button>
                                </TableCell>
                            </TableRow>
                        })}
                        <TableRow>
                            <TableCell><Input ref={portFirstRef}/></TableCell>
                            <TableCell><Input ref={portLastRef}/></TableCell>
                            <TableCell>
                                <Select selectRef={protocolRef}>
                                    <option>TCP</option>
                                    <option>UDP</option>
                                </Select>
                            </TableCell>
                            <TableCell><Button type={"submit"} fullWidth onClick={onAddRow}>Add</Button></TableCell>
                        </TableRow>
                        </tbody>
                    </Table>
                </form>
            </>
        }
    />;
};

export default Inspect;

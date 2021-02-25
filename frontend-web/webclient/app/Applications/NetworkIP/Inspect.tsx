import * as React from "react";
import {Box, Button, Flex, Icon, Input, RadioTile, RadioTilesContainer, Select, Text, Truncate} from "ui-components";
import * as Heading from "ui-components/Heading";
import {blankOrUndefined, prettierString, shortUUID} from "UtilityFunctions";
import {dateToString} from "Utilities/DateUtilities";
import {TextSpan} from "ui-components/Text";
import {compute, provider} from "UCloud";
import NetworkIP = compute.NetworkIP;
import {groupSummaryRequest, useProjectId} from "Project";
import {useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import {GroupWithSummary} from "Project/GroupList";
import {bulkRequestOf, emptyPage} from "DefaultObjects";
import {useCallback, useEffect, useRef, useState} from "react";
import * as UCloud from "UCloud";
import * as Pagination from "Pagination";
import {ShakingBox} from "UtilityComponents";
import {Link} from "react-router-dom";
import ResourceAclEntry = provider.ResourceAclEntry;
import {entityName, AclPermission} from ".";
import networkApi = UCloud.compute.networkips;
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "ui-components/Table";
import {snackbarStore} from "Snackbar/SnackbarStore";

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

        console.log(firstPort, lastPort, protocol);
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

    const product = inspecting.resolvedProduct!;
    return <>
        <Icon name={"arrowDown"} rotation={90} size={"32px"} cursor={"pointer"}
              onClick={close}/>
        <Box width={"500px"} margin={"0 auto"} marginTop={"-32px"}>
            <Flex>
                <Heading.h4 flexGrow={1}>ID</Heading.h4>
                {shortUUID(inspecting.id)}
            </Flex>

            <Flex>
                <Heading.h4 flexGrow={1}>Product</Heading.h4>
                {product.category.provider} / {product.id}
            </Flex>

            <Flex>
                <Heading.h4 flexGrow={1}>State</Heading.h4>
                {prettierString(inspecting.status.state)}
            </Flex>

            <Flex>
                <Heading.h4 flexGrow={1}>IP Address</Heading.h4>
                {inspecting.status.ipAddress ?? "No address"}
            </Flex>

            {inspecting.owner.project === undefined ? null : (
                <Box mt={"32px"}>
                    <Heading.h4 mb={8}>Permissions</Heading.h4>
                    <Permissions entity={inspecting} reload={reload}/>
                </Box>
            )}

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

            <Box mt={"32px"}>
                <Heading.h4>Updates</Heading.h4>
                <Table>
                    <tbody>
                    {inspecting.updates.map((update, idx) => {
                        return <TableRow key={idx}>
                            <TableCell>{dateToString(update.timestamp)}</TableCell>
                            <TableCell>
                                {!update.state ? null : prettierString(update.state)}
                            </TableCell>
                            <TableCell>
                                {update.status ? <TextSpan mr={"10px"}>{update.status}</TextSpan> : null}
                            </TableCell>
                        </TableRow>
                    })}
                    </tbody>
                </Table>
            </Box>
        </Box>
    </>;
};

const Permissions: React.FunctionComponent<{ entity: NetworkIP, reload: () => void }> = ({entity, reload}) => {
    const projectId = useProjectId();
    const [projectGroups, fetchProjectGroups, groupParams] =
        useCloudAPI<Page<GroupWithSummary>>({noop: true}, emptyPage);

    const [commandLoading, invokeCommand] = useCloudCommand();

    const [acl, setAcl] = useState<ResourceAclEntry<AclPermission>[]>(entity.acl ?? []);

    useEffect(() => {
        fetchProjectGroups(UCloud.project.group.listGroupsWithSummary({itemsPerPage: 50, page: 0}));
    }, [projectId]);

    useEffect(() => {
        setAcl(entity.acl ?? []);
    }, [entity]);

    const updateAcl = useCallback(async (group: string, permissions: AclPermission[]) => {
        if (!projectId) return;
        if (commandLoading) return;

        const newAcl = acl
            .filter(it => !(it.entity.projectId === projectId && it.entity.group === group));
        newAcl.push({entity: {projectId, group, type: "project_group"}, permissions});

        setAcl(newAcl);

        await invokeCommand(networkApi.updateAcl(bulkRequestOf({acl: newAcl, id: entity.id})))
        reload();
    }, [acl, projectId, commandLoading]);

    const anyGroupHasPermission = acl.some(it => it.permissions.indexOf("USE") !== -1);

    return <Pagination.List
        loading={projectGroups.loading}
        page={projectGroups.data}
        onPageChanged={(page) => fetchProjectGroups(groupSummaryRequest({
            ...groupParams.parameters,
            page
        }))}
        customEmptyPage={(
            <Flex width={"100%"} height={"100%"} alignItems={"center"} justifyContent={"center"}
                  flexDirection={"column"}>
                <ShakingBox shaking mb={"10px"}>
                    No groups exist for this project.{" "}
                    <TextSpan bold>As a result, this {entityName} can only be used by project admins!</TextSpan>
                </ShakingBox>

                <Link to={"/project/members"} target={"_blank"}><Button fullWidth>Create group</Button></Link>
            </Flex>
        )}
        pageRenderer={() => (
            <>
                {anyGroupHasPermission ? null :
                    <ShakingBox shaking mb={16}>
                        <Text bold>This IP address can only be used by project admins</Text>
                        <Text>
                            You must assign permissions to one or more group, if your collaborators need to configure
                            applications with this IP.
                        </Text>
                    </ShakingBox>
                }
                {projectGroups.data.items.map(summary => {
                    const g = summary.groupId;
                    const permissions = acl.find(it =>
                        it.entity.group === g &&
                        it.entity.projectId === projectId
                    )?.permissions ?? [];

                    return (
                        <Flex key={g} alignItems={"center"} mb={16}>
                            <Truncate width={"300px"} mr={16} title={summary.groupTitle}>
                                {summary.groupTitle}
                            </Truncate>

                            <RadioTilesContainer>
                                <RadioTile
                                    label={"None"}
                                    onChange={() => updateAcl(g, [])}
                                    icon={"close"}
                                    name={summary.groupId}
                                    checked={permissions.length === 0}
                                    height={40}
                                    fontSize={"0.5em"}
                                />
                                <RadioTile
                                    label={"Use"}
                                    onChange={() => updateAcl(g, ["USE"])}
                                    icon={"search"}
                                    name={summary.groupId}
                                    checked={permissions.indexOf("USE") !== -1 && permissions.length === 1}
                                    height={40}
                                    fontSize={"0.5em"}
                                />
                            </RadioTilesContainer>
                        </Flex>
                    );
                })}
            </>
        )}
    />;
};

export default Inspect;

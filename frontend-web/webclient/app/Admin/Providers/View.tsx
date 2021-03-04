import {useRouteMatch} from "react-router";
import * as React from "react";
import {
    Box,
    Label,
    Checkbox,
    TextArea,
    Button,
    Select,
    List,
    Text,
    RadioTilesContainer,
    RadioTile,
    Flex
} from "ui-components";
import Error from "ui-components/Error";
import * as Heading from "ui-components/Heading";
import {useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import * as UCloud from "UCloud";
import LoadingSpinner from "LoadingIcon/LoadingIcon";
import MainContainer from "MainContainer/MainContainer";
import {bulkRequestOf, emptyPage} from "DefaultObjects";
import HexSpin from "LoadingIcon/LoadingIcon";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {errorMessageOrDefault} from "UtilityFunctions";
import {ListRow} from "ui-components/List";
import {GroupWithSummary} from "Project/GroupList";

function View(): JSX.Element | null {
    const match = useRouteMatch<{ id: string }>();
    const {id} = match.params;
    const [provider, fetchProvider] = useCloudAPI<UCloud.provider.Provider | null>(
        UCloud.provider.providers.retrieve({id}),
        null
    );

    const [loading, invokeCommand] = useCloudCommand();
    // Different one, so it doesn't mess with the loading rendering.
    const [, invokeForGroups] = useCloudCommand();

    const [groupStorage, setGroupStorage] = React.useState<Record<string, GroupWithSummary[]>>({});

    const [projectId, setProjectId] = React.useState("");
    const [group, setGroup] = React.useState("");

    const [projects] = useCloudAPI<Page<UCloud.project.UserProjectSummary>>(
        UCloud.project.listProjects({itemsPerPage: 100, archived: true}),
        emptyPage
    );

    const [groups, fetchGroups] = useCloudAPI<Page<UCloud.project.GroupWithSummary>>(
        {noop: true},
        emptyPage
    )

    React.useEffect(() => {
        if (projectId) {
            fetchGroups({
                ...UCloud.project.group.listGroupsWithSummary({itemsPerPage: 100}),
                projectOverride: projectId
            });
        }
    }, [projectId]);

    React.useEffect(() => {
        for (const projectToOverride of projects.data.items) {
            const projectOverride = projectToOverride.projectId;
            invokeForGroups({...UCloud.project.group.listGroupsWithSummary({itemsPerPage: 100}), projectOverride})
                .then(it => setGroupStorage(gs => {
                    gs[projectOverride] = it.items;
                    return {...gs};
                }));
        }
    }, [projects.data.items]);

    const [showACLSelector, setShowACLSelector] = React.useState(false);

    if (provider.loading) return <MainContainer main={<LoadingSpinner/>}/>;
    if (provider.data == null) return null;

    const {https, manifest} = provider.data.specification;
    const {docker, virtualMachine} = manifest.features.compute;

    async function addACL() {
        try {
            if (provider.data == null) return;
            const otherAcls = provider.data.acl.filter(it => !(it.entity.projectId === projectId && it.entity.group === group));
            await invokeCommand(UCloud.provider.providers.updateAcl(bulkRequestOf({
                id,
                acl: [
                    ...otherAcls,
                    {permissions: ["EDIT"], entity: {projectId, type: "project_group", group}}
                ]
            })));
            setProjectId("");
            setGroup("");
            snackbarStore.addSuccess("ACL added", false);
            fetchProvider(UCloud.provider.providers.retrieve({id}))
        } catch (e) {
            snackbarStore.addFailure(errorMessageOrDefault(e, "Failed to update ACL"), false);
        }
    }

    async function removeACL(projectId: string, group: string) {
        try {
            if (provider.data == null) return;
            const otherAcls = provider.data.acl.filter(it => !(it.entity.projectId === projectId && it.entity.group === group));

            await invokeCommand(UCloud.provider.providers.updateAcl(bulkRequestOf({
                id,
                acl: otherAcls
            })));

            snackbarStore.addSuccess("ACL removed", false);
            fetchProvider(UCloud.provider.providers.retrieve({id}))
        } catch (e) {
            snackbarStore.addFailure(errorMessageOrDefault(e, "Failed to update ACL"), false);
        }
    }

    return (<MainContainer main={
        <Box width="650px" mx="auto">
            <Error error={provider.error?.why}/>
            <Heading.h3>{id}</Heading.h3>
            <Box>
                <b>Created by:</b> {provider.data.owner.createdBy}
            </Box>
            <Box>
                <b>Domain: </b> {provider.data.specification.domain}
            </Box>

            <Label>
                <Checkbox checked={https} onChange={e => e}/>
                Uses HTTPS
            </Label>

            <Heading.h4>Public key</Heading.h4>
            <TextArea maxWidth="1200px" width="100%" value={provider.data.publicKey}/>

            <Heading.h4>Refresh token</Heading.h4>
            <TextArea maxWidth="1200px" width="100%" value={provider.data.refreshToken}/>

            <Heading.h4>Docker Support</Heading.h4>
            <Label>
                <Checkbox checked={docker.enabled} onChange={e => e}/>
                Docker enabled: Flag to enable/disable this feature. <b>All other flags are ignored if this
                is <code>false</code>.</b>
            </Label>
            <Label>
                <Checkbox checked={docker.batch} onChange={e => e}/>
                Batch: Flag to enable/disable <code>BATCH</code> <code>Application</code>s
            </Label>
            <Label>
                <Checkbox checked={docker.logs} onChange={e => e}/>
                Log: lag to enable/disable the log API
            </Label>
            <Label>
                <Checkbox checked={docker.peers} onChange={e => e}/>
                Peers: Flag to enable/disable connection between peering <code>Job</code>s
            </Label>
            <Label>
                <Checkbox checked={docker.terminal} onChange={e => e}/>
                Terminal: Flag to enable/disable the interactive terminal API
            </Label>
            <Label>
                <Checkbox checked={docker.vnc} onChange={e => e}/>
                VNC: Flag to enable/disable the interactive interface of <code>VNC</code> <code>Application</code>s
            </Label>
            <Label>
                <Checkbox checked={docker.web} onChange={e => e}/>
                Web: Flag to enable/disable the interactive interface of <code>WEB</code> <code>Application</code>s
            </Label>

            <Heading.h4>Virtual Machine Support</Heading.h4>
            <Label>
                <Checkbox checked={virtualMachine.enabled} onChange={e => e}/>
                Virtual machine enabled: Flag to enable/disable this feature. <b>All other flags are ignored if this
                is <code>false</code>.</b>
            </Label>
            <Label>
                <Checkbox checked={virtualMachine.logs} onChange={e => e}/>
                Logs: Flag to enable/disable the log API
            </Label>
            <Label>
                <Checkbox checked={virtualMachine.terminal} onChange={e => e}/>
                Terminal: Flag to enable/disable the interactive terminal API
            </Label>
            <Label>
                <Checkbox checked={virtualMachine.vnc} onChange={e => e}/>
                VNC: Flag to enable/disable the VNC API
            </Label>

            <Heading.h3>ACLs</Heading.h3>

            {provider == null ? null : (
                <List>
                    {provider.data.acl.map(it => {
                        const group = groupStorage[it.entity.projectId]?.find(gs => gs.groupId === it.entity.group)?.groupTitle ?? it.entity.group;
                        return (
                            <ListRow
                                key={`${it.entity.projectId}-${group}`}
                                left={<Flex>
                                    <Text
                                        mr="8px">{projects.data.items.find(p => p.projectId === it.entity.projectId)?.title}</Text>
                                    <Text color="gray">{group}</Text>
                                </Flex>}
                                right={
                                    <RadioTilesContainer>
                                        <RadioTile
                                            label={"None"}
                                            name={it.entity.group}
                                            onChange={() => removeACL(it.entity.projectId, it.entity.group)}
                                            icon={"close"}
                                            checked={it.permissions.length === 0}
                                            height={40}
                                            fontSize={"0.5em"}
                                        />
                                        <RadioTile
                                            label={"Edit"}
                                            onChange={() => undefined}
                                            icon={"search"}
                                            name={it.entity.group}
                                            checked={it.permissions.indexOf("EDIT") !== -1 && it.permissions.length === 1}
                                            height={40}
                                            fontSize={"0.5em"}
                                        />
                                    </RadioTilesContainer>
                                }
                            />
                        )
                    })}
                </List>
            )}

            <Button fullWidth onClick={() => setShowACLSelector(t => !t)}>New ACL</Button>
            <Box mt="10px" hidden={!showACLSelector}>
                <Label>
                    <Heading.h3>Project</Heading.h3>
                    <Select>
                        <option/>
                        {projects.data.items.map(it => <option key={it.projectId}
                                                               onClick={() => (setProjectId(it.projectId), setGroup(""))}>{it.title}</option>)}
                    </Select>
                </Label>
                {!projectId ? null :
                    groups.loading ? <HexSpin/> : (<Label>
                        <Heading.h3>Group</Heading.h3>
                        {groups.data.itemsInTotal !== 0 ? <Select>
                            <option/>
                            {groups.data.items.map(it => <option key={it.groupId}
                                                                 onClick={() => setGroup(it.groupId)}>{it.groupTitle}</option>)}
                        </Select> : <Heading.h4>No groups found for project.</Heading.h4>}
                    </Label>)
                }
                <Button mt="15px" fullWidth disabled={loading || !group || !projectId} onClick={addACL}>Add ACL</Button>
            </Box>
        </Box>
    }/>);
}

export default View;
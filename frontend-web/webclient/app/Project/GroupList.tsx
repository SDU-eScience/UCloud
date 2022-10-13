import * as React from "react";
import * as Pagination from "@/Pagination";
import {Button, List, Icon, Flex} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {useHistory, useParams} from "react-router";
import GroupView from "./GroupView";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {errorMessageOrDefault} from "@/UtilityFunctions";
import {usePromiseKeeper} from "@/PromiseKeeper";
import {Client} from "@/Authentication/HttpClientInstance";
import {NamingField} from "@/UtilityComponents";
import {ListRow} from "@/ui-components/List";
import {BreadCrumbsBase} from "@/ui-components/Breadcrumbs";
import {useRef, useState} from "react";
import {useCloudCommand} from "@/Authentication/DataHook";
import {Spacer} from "@/ui-components/Spacer";
import {Operation, Operations} from "@/ui-components/Operation";
import ProjectAPI, {isAdminOrPI, ProjectGroup, useGroupIdAndMemberId, useProjectFromParams} from "@/Project/Api";
import {bulkRequestOf} from "@/DefaultObjects";

export interface GroupWithSummary {
    groupId: string;
    groupTitle: string;
    numberOfMembers: number;
    members: string[];
}

const baseContext = "/projects/groups";

// UNUSED
const GroupList: React.FunctionComponent = () => {
    const history = useHistory();
    const {project, reload} = useProjectFromParams();
    const [groupId, membersPage] = useGroupIdAndMemberId();

    const allowManagement = isAdminOrPI(project?.status.myRole);

    const [creatingGroup, setCreatingGroup] = useState(false);
    const [, setLoading] = useState(false);
    const createGroupRef = useRef<HTMLInputElement>(null);
    const promises = usePromiseKeeper();
    const [renamingGroup, setRenamingGroup] = useState<string | null>(null);
    const renameRef = useRef<HTMLInputElement>(null);
    const [, runCommand] = useCloudCommand();

    const operations: GroupOperation[] = [
        {
            enabled: groups => groups.length === 1 && allowManagement,
            onClick: ([group]) => setRenamingGroup(group.id),
            icon: "rename",
            text: "Rename"
        },
        {
            enabled: groups => groups.length > 0 && allowManagement,
            onClick: (groups) => ProjectAPI.deleteGroup(bulkRequestOf(...groups.map(it => ({id: it.id})))),
            icon: "trash",
            text: "Delete",
            color: "red",
            confirm: true
        }
    ];

    const groups = project?.status.groups ?? [];

    if (groupId) return <GroupView />;
    const content = (
        <>
            {groups.length !== 0 || creatingGroup ? null : (
                <Flex justifyContent={"center"} alignItems={"center"} minHeight={"300px"} flexDirection={"column"}>
                    <Heading.h4>You have no groups to manage.</Heading.h4>
                    <ul>
                        <li>Groups are used to manage permissions in your project</li>
                        <li>You must create a group to grant members access to the project&#039;s files</li>
                    </ul>
                </Flex>
            )}
            <List>
                {creatingGroup ?
                    <ListRow
                        left={
                            <NamingField
                                confirmText="Create"
                                onSubmit={createGroup}
                                onCancel={() => setCreatingGroup(false)}
                                inputRef={createGroupRef}
                            />
                        }
                        leftSub={
                            <div />
                        }
                        right={<div />}
                        isSelected={false}
                        select={() => undefined}
                    /> : null}
                {groups.map((g, index) => (<React.Fragment key={g.id + index}>
                    <ListRow
                        left={
                            renamingGroup !== g.id ? g.specification.title : (
                                <NamingField
                                    confirmText="Rename"
                                    defaultValue={g.specification.title}
                                    onCancel={() => setRenamingGroup(null)}
                                    onSubmit={renameGroup}
                                    inputRef={renameRef}
                                />
                            )
                        }
                        navigate={() => {
                            if (renamingGroup !== g.id) {
                                history.push(`/project/members/${encodeURIComponent(g.id)}/${membersPage ?? ""}`);
                            }
                        }}
                        leftSub={<div />}
                        right={
                            <>
                                {g.status.members?.length === 0 ? null :
                                    <Flex>
                                        <Icon mt="4px" mr="4px" size="18" name="user" /> {g.status.members?.length}
                                    </Flex>
                                }

                                <Operations
                                    location={"IN_ROW"}
                                    operations={operations}
                                    entityNameSingular="Group"
                                    entityNamePlural="Groups"
                                    extra={{}}
                                    selected={[g]}
                                    row={g}
                                />
                            </>
                        }
                        isSelected={false}
                    />
                </React.Fragment>))}
            </List>
        </>
    );
    return <>
        <Spacer
            mb="12px"
            left={
                <BreadCrumbsBase embedded={false}>
                    <span>Groups</span>
                </BreadCrumbsBase>
            }

            right={!allowManagement ? null : (
                <Button mt="2px" height="40px" width="120px" onClick={() => setCreatingGroup(true)}>New Group</Button>
            )}
        />

        {content}
    </>;

    async function createGroup(e: React.SyntheticEvent): Promise<void> {
        e.preventDefault();
        try {
            setLoading(true);
            const groupName = createGroupRef.current?.value ?? "";
            if (!groupName) {
                snackbarStore.addFailure("Groupname can't be empty", false);
                return;
            }
            await promises.makeCancelable(Client.put(baseContext, {group: groupName})).promise;
            snackbarStore.addSuccess(`Group created`, true);
            createGroupRef.current!.value = "";
            setCreatingGroup(false);
            reload();
        } catch (err) {
            snackbarStore.addFailure(errorMessageOrDefault(err, "Could not create group."), false);
        } finally {
            setLoading(false);
        }
    }

    async function renameGroup(): Promise<void> {
        const groupId = renamingGroup;
        if (!groupId) return;
        const newGroupName = renameRef.current?.value;
        if (!newGroupName) return;

        const success = await runCommand(ProjectAPI.renameGroup(bulkRequestOf({group: groupId, newTitle: newGroupName})));

        if (!success) {
            snackbarStore.addFailure("Failed to rename project group", true);
            return;
        }

        reload();
        setRenamingGroup(null);
        snackbarStore.addSuccess("Project group renamed", true);
    }
};

type GroupOperation = Operation<ProjectGroup>;
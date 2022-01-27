import * as React from "react";
import * as Pagination from "@/Pagination";
import {Button, List, Icon, Flex} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {useHistory} from "react-router";
import GroupView from "./GroupView";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {errorMessageOrDefault} from "@/UtilityFunctions";
import {usePromiseKeeper} from "@/PromiseKeeper";
import {Client} from "@/Authentication/HttpClientInstance";
import {NamingField} from "@/UtilityComponents";
import {ListRow} from "@/ui-components/List";
import {BreadCrumbsBase} from "@/ui-components/Breadcrumbs";
import {useProjectManagementStatus} from "@/Project/index";
import {deleteGroup, groupSummaryRequest, updateGroupName} from "@/Project";
import {useRef, useState} from "react";
import {useCloudCommand} from "@/Authentication/DataHook";
import {Spacer} from "@/ui-components/Spacer";
import {Operation, Operations} from "@/ui-components/Operation";

export interface GroupWithSummary {
    groupId: string;
    groupTitle: string;
    numberOfMembers: number;
    members: string[];
}

const baseContext = "/projects/groups";

const GroupList: React.FunctionComponent = () => {
    const history = useHistory();
    const {allowManagement, groupId, groupList, fetchGroupList, groupListParams,
        membersPage} = useProjectManagementStatus({isRootComponent: false});

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
            onClick: ([group]) => setRenamingGroup(group.groupId),
            icon: "rename",
            text: "Rename"
        },
        {
            enabled: groups => groups.length > 0 && allowManagement,
            onClick: (groups) => deleteSelectedGroups(groups),
            icon: "trash",
            text: "Delete",
            color: "red",
            confirm: true
        }
    ];


    if (groupId) return <GroupView />;
    const content = (
        <>
            {groupList.data.items.length !== 0 || creatingGroup ? null : (
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
                {groupList.data.items.map((g, index) => (<React.Fragment key={g.groupId + index}>
                    <ListRow
                        left={
                            renamingGroup !== g.groupId ? g.groupTitle : (
                                <NamingField
                                    confirmText="Rename"
                                    defaultValue={g.groupTitle}
                                    onCancel={() => setRenamingGroup(null)}
                                    onSubmit={renameGroup}
                                    inputRef={renameRef}
                                />
                            )
                        }
                        navigate={() => {
                            if (renamingGroup !== g.groupId) {
                                history.push(`/project/members/${encodeURIComponent(g.groupId)}/${membersPage ?? ""}`);
                            }
                        }}
                        leftSub={<div />}
                        right={
                            <>
                                {g.numberOfMembers === 0 ? null :
                                    <Flex>
                                        <Icon mt="4px" mr="4px" size="18" name="user" /> {g.numberOfMembers}
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

        <Pagination.List
            loading={groupList.loading}
            page={groupList.data}
            customEmptyPage={content}
            onPageChanged={(newPage, oldPage) => {
                fetchGroupList(groupSummaryRequest({page: newPage, itemsPerPage: oldPage.itemsPerPage}));
            }}
            pageRenderer={() => content}
        />
    </>;

    async function deleteSelectedGroups(groups: GroupWithSummary[]): Promise<void> {
        await runCommand(deleteGroup({groups: groups.map(it => it.groupId)}));
        fetchGroupList(groupListParams);
    }

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
            fetchGroupList({...groupListParams});
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

        const success = await runCommand(updateGroupName({groupId, newGroupName}));

        if (!success) {
            snackbarStore.addFailure("Failed to rename project group", true);
            return;
        }

        fetchGroupList(groupListParams);
        setRenamingGroup(null);
        snackbarStore.addSuccess("Project group renamed", true);
    }
};

type GroupOperation = Operation<GroupWithSummary>;

export default GroupList;

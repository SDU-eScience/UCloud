import * as React from "react";
import * as Pagination from "Pagination";
import {Button, Text, Input, List, Icon, Flex, Box} from "ui-components";
import * as Heading from "ui-components/Heading";
import {Operation} from "Types";
import {useHistory} from "react-router";
import GroupView from "./GroupView";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {errorMessageOrDefault, preventDefault} from "UtilityFunctions";
import {usePromiseKeeper} from "PromiseKeeper";
import {Client} from "Authentication/HttpClientInstance";
import {addStandardDialog, ConfirmCancelButtons} from "UtilityComponents";
import {KeyCode} from "DefaultObjects";
import {ListRow} from "ui-components/List";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {BreadCrumbsBase} from "ui-components/Breadcrumbs";
import {useProjectManagementStatus} from "Project/index";
import {deleteGroup, groupSummaryRequest, updateGroupName} from "Project";
import {MutableRefObject, useCallback, useRef, useState} from "react";
import {useAsyncCommand} from "Authentication/DataHook";
import {Spacer} from "ui-components/Spacer";

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
    const [, runCommand] = useAsyncCommand();

    const operations: GroupOperation[] = [
        {
            disabled: groups => groups.length !== 1,
            onClick: ([group]) => setRenamingGroup(group.groupId),
            icon: "rename",
            text: "Rename"
        },
        {
            disabled: groups => groups.length === 0 || !allowManagement,
            onClick: (groups) => promptDeleteGroups(groups),
            icon: "trash",
            text: "Delete",
            color: "red"
        }
    ];


    if (groupId) return <GroupView />;
    const [trashOp] = operations;
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
                                {operations.length === 0 ? null :
                                    operations.length > 1 ? <ClickableDropdown
                                        width="125px"
                                        left="-105px"
                                        trigger={(
                                            <Icon
                                                onClick={preventDefault}
                                                mr="10px"
                                                ml="12px"
                                                name="ellipsis"
                                                size="1em"
                                                rotation={90}
                                            />
                                        )}
                                    >
                                        <GroupOperations groupOperations={operations} selectedGroups={[g]} />
                                    </ClickableDropdown> :
                                        <Icon
                                            cursor="pointer"
                                            onClick={() => trashOp.onClick([g], Client)}
                                            size={20}
                                            mr="1em"
                                            ml="0.5em"
                                            color={trashOp.color}
                                            name={trashOp.icon}
                                        />
                                }
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
                <Button height="40px" width="120px" onClick={() => setCreatingGroup(true)}>New Group</Button>
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

    async function promptDeleteGroups(groups: GroupWithSummary[]): Promise<void> {
        if (groups.length === 0) {
            snackbarStore.addFailure("You haven't selected any groups.", false);
            return;
        }
        addStandardDialog({
            title: "Delete groups?",
            message: <>
                <Text mb="5px">Selected groups:</Text>
                {groups.map(g => <Text key={g.groupId} fontSize="12px">{g.groupTitle}</Text>)}
            </>,
            onConfirm: async () => {
                await runCommand(deleteGroup({groups: groups.map(it => it.groupId)}));
                fetchGroupList(groupListParams);
            },
            confirmText: "Delete"
        });
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

const NamingField: React.FunctionComponent<{
    onCancel: () => void;
    confirmText: string;
    inputRef: MutableRefObject<HTMLInputElement | null>;
    onSubmit: (e: React.SyntheticEvent) => void;
    defaultValue?: string;
}> = props => {
    const submit = useCallback((e) => {
        e.preventDefault();
        props.onSubmit(e);
    }, [props.onSubmit]);

    const keyDown = useCallback((e) => {
        if (e.keyCode === KeyCode.ESC) {
            props.onCancel();
        }
    }, [props.onCancel]);

    return (
        <form onSubmit={submit}>
            <Flex>
                <Input
                    pt="0px"
                    pb="0px"
                    pr="0px"
                    pl="0px"
                    noBorder
                    defaultValue={props.defaultValue ? props.defaultValue : ""}
                    fontSize={20}
                    maxLength={1024}
                    onKeyDown={keyDown}
                    borderRadius="0px"
                    type="text"
                    width="100%"
                    autoFocus
                    ref={props.inputRef}
                />
                <ConfirmCancelButtons
                    confirmText={props.confirmText}
                    cancelText="Cancel"
                    onConfirm={submit}
                    onCancel={props.onCancel}
                />
            </Flex>
        </form>
    );
};

type GroupOperation = Operation<GroupWithSummary>;

interface GroupOperationsProps {
    selectedGroups: GroupWithSummary[];
    groupOperations: GroupOperation[];
}

function GroupOperations(props: GroupOperationsProps): JSX.Element | null {
    if (props.groupOperations.length === 0) return null;

    function GroupOp(op: GroupOperation): JSX.Element | null {
        if (op.disabled(props.selectedGroups, Client)) return null;
        return (
            <Box
                onClick={() => op.onClick(props.selectedGroups, Client)}
                key={op.text}
                ml="-17px"
                mr="-17px"
                cursor="pointer"
                pl="15px">
                <span>
                    <Icon size={16} mr="1em" color={op.color} name={op.icon} />{op.text}
                </span>
            </Box>
        );
    }

    return <>{props.groupOperations.map(GroupOp)}</>;
}

export default GroupList;

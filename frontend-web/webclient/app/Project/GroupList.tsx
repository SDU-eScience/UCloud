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
import {addStandardDialog} from "UtilityComponents";
import {KeyCode} from "DefaultObjects";
import {ListRow} from "ui-components/List";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {BreadCrumbsBase} from "ui-components/Breadcrumbs";
import {useProjectManagementStatus} from "Project/View";
import {deleteGroup, groupSummaryRequest, updateGroupName} from "Project";
import {MutableRefObject, useCallback, useRef, useState} from "react";
import {useAsyncCommand} from "Authentication/DataHook";

export interface GroupWithSummary {
    group: string;
    numberOfMembers: number;
    members: string[];
}

const baseContext = "/projects/groups";

const GroupList: React.FunctionComponent = props => {
    const history = useHistory();
    const {allowManagement, group, groupList, fetchGroupList, groupListParams} = useProjectManagementStatus();

    const [creatingGroup, setCreatingGroup] = useState(false);
    const [, setLoading] = useState(false);
    const createGroupRef = useRef<HTMLInputElement>(null);
    const promises = usePromiseKeeper();
    const [renamingGroup, setRenamingGroup] = useState<string | null>(null);
    const renameRef = useRef<HTMLInputElement>(null);
    const [, runCommand] = useAsyncCommand();

    const operations: GroupOperation[] = [
        /*{
            disabled: groups => groups.length !== 1,
            onClick: (groups) => setRenamingGroup(groups[0].group),
            icon: "rename",
            text: "Rename"
        },
         */
        {
            disabled: groups => groups.length === 0 || !allowManagement,
            onClick: (groups) => promptDeleteGroups(groups),
            icon: "trash",
            text: "Delete",
            color: "red"
        }
    ];


    if (group) return <GroupView/>;

    let content = (
        <>
            {groupList.data.items.length === 0 ? <Heading.h3>You have no groups to manage.</Heading.h3> : null}
            <List>
                {groupList.data.items.map(g => (<>
                    <ListRow
                        key={g.group}
                        left={
                            renamingGroup !== g.group ? g.group : (
                                <NamingField
                                    onCancel={() => setRenamingGroup(null)}
                                    onSubmit={renameGroup}
                                    inputRef={renameRef}
                                />
                            )
                        }
                        navigate={() => history.push(`/projects/view/${g.group}`)}
                        leftSub={
                            <Text ml="4px" color="gray" fontSize={0}>
                                <Icon color="gray" mt="-2px" size="10" name="projects"/> {g.numberOfMembers}
                            </Text>
                        }
                        right={
                            <ClickableDropdown
                                width="125px"
                                left="-105px"
                                trigger={(
                                    <Icon
                                        onClick={preventDefault}
                                        mr="10px"
                                        name="ellipsis"
                                        size="1em"
                                        rotation={90}
                                    />
                                )}
                            >
                                <GroupOperations groupOperations={operations} selectedGroups={[g]}/>
                            </ClickableDropdown>
                        }
                        isSelected={false}
                    />
                </>))}

                {creatingGroup ?
                    <ListRow
                        left={
                            <NamingField
                                onSubmit={createGroup}
                                onCancel={() => setCreatingGroup(false)}
                                inputRef={createGroupRef}
                            />
                        }
                        leftSub={
                            <Text ml="4px" color="gray" fontSize={0}>
                                <Icon color="gray" mt="-2px" size="10" name="projects"/> 0
                            </Text>
                        }
                        right={<div/>}
                        isSelected={false}
                        select={() => undefined}
                    /> : null}
            </List>

            {!allowManagement ? null : (
                <Flex justifyContent={"center"}>
                    <Button width={"50%"} onClick={() => setCreatingGroup(true)}>New Group</Button>
                </Flex>
            )}
        </>
    );
    return <>
        <BreadCrumbsBase>
            <li><span>Groups</span></li>
        </BreadCrumbsBase>

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
                {groups.map(g => <Text key={g.group} fontSize="12px">{g.group}</Text>)}
            </>,
            onConfirm: async () => {
                await runCommand(deleteGroup({groups: groups.map(it => it.group)}));
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

    async function renameGroup() {
        const oldGroupName = renamingGroup;
        if (!oldGroupName) return;
        const newGroupName = renameRef.current?.value;
        if (!newGroupName) return;

        await runCommand(updateGroupName({oldGroupName, newGroupName}));
        fetchGroupList(groupListParams);
    }
}

const NamingField: React.FunctionComponent<{
    onCancel: () => void;
    inputRef: MutableRefObject<HTMLInputElement | null>;
    onSubmit: (e: React.SyntheticEvent) => void;
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
            <Input
                pt="0px"
                pb="0px"
                pr="0px"
                pl="0px"
                noBorder
                fontSize={20}
                maxLength={1024}
                onKeyDown={keyDown}
                borderRadius="0px"
                type="text"
                width="100%"
                autoFocus
                ref={props.inputRef}
            />
        </form>
    );
}

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
                ml="-17px"
                mr="-17px"
                cursor="pointer"
                pl="15px">
                <span onClick={() => op.onClick(props.selectedGroups, Client)}>
                    <Icon size={16} mr="1em" color={op.color} name={op.icon}/>{op.text}
                </span>
            </Box>
        );
    }

    return <>{props.groupOperations.map(GroupOp)}</>;
}

export default GroupList;

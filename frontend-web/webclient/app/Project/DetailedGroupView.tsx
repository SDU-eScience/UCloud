import * as React from "react";
import {MainContainer} from "MainContainer/MainContainer";
import {Text, Input, Box, Flex, Button, Card, Icon, List} from "ui-components";
import * as Heading from "ui-components/Heading";
import {defaultAvatar, AvatarType} from "UserSettings/Avataaar";
import * as Pagination from "Pagination";
import {Page} from "Types";
import {useCloudAPI, APICallParameters, useAsyncCommand} from "Authentication/DataHook";
import {
    listGroupMembersRequest,
    addGroupMember,
    removeGroupMemberRequest,
    ListGroupMembersRequestProps,
    projectRoleToString
} from "./api";
import {Client} from "Authentication/HttpClientInstance";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {errorMessageOrDefault} from "UtilityFunctions";
import {GridCardGroup} from "ui-components/Grid";
import {usePromiseKeeper} from "PromiseKeeper";
import {Spacer} from "ui-components/Spacer";
import {Avatar} from "AvataaarLib";
import {addStandardDialog, addStandardInputDialog} from "UtilityComponents";
import {emptyPage} from "DefaultObjects";
import {SnackType} from "Snackbar/Snackbars";
import {useHistory} from "react-router";
import {dialogStore} from "Dialog/DialogStore";
import {buildQueryString} from "Utilities/URIUtilities";
import {ListRow} from "ui-components/List";
import {ProjectMember, ProjectRole, changeRoleInProject} from "Project";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {useEffect} from "react";
import {useAvatars} from "AvataaarLib/hook";
import styled from "styled-components";

interface DetailedGroupViewProps {
    name: string;
}

function DetailedGroupView({name}: DetailedGroupViewProps): JSX.Element {
    const [activeGroup, fetchActiveGroup, params] = useCloudAPI<Page<string>, ListGroupMembersRequestProps>(
        listGroupMembersRequest({group: name ?? "", itemsPerPage: 25, page: 0}),
        emptyPage
    );

    const [allGroupMembers, setAllGroupMembers] = React.useState<Set<string>>(new Set());

    async function fetchEveryGroupMember(page: number): Promise<void> {
        try {
            const set = page === 0 ? new Set<string>() : allGroupMembers;
            const request = listGroupMembersRequest({group: name ?? "", itemsPerPage: 100, page});
            const response = await promises.makeCancelable(Client.get<Page<string>>(request.path!)).promise;
            response.response.items.forEach(member => set.add(member));
            setAllGroupMembers(new Set(set));
            if (response.response.pageNumber < response.response.pagesInTotal - 1) fetchEveryGroupMember(page + 1);
        } catch (err) {
            snackbarStore.addFailure(errorMessageOrDefault(err, "Failed to fetch group members."), false);
        }
    }


    const history = useHistory();
    const promises = usePromiseKeeper();

    const reload = (): void => {
        fetchActiveGroup({...params});
        fetchEveryGroupMember(0);
    };

    React.useEffect(() => {
        if (name) {
            fetchActiveGroup(listGroupMembersRequest({group: name ?? "", itemsPerPage: 25, page: 0}));
            fetchEveryGroupMember(0);
        }
    }, [name]);

    if (activeGroup.error) return <MainContainer main={
        <Text fontSize={"24px"}>Could not fetch &apos;{name}&apos;.</Text>
    }/>;

    return <MainContainer
        main={
            <Pagination.List
                loading={activeGroup.loading}
                onPageChanged={(newPage, page) => fetchActiveGroup(listGroupMembersRequest({
                    group: name,
                    itemsPerPage: page.itemsPerPage,
                    page: newPage
                }))}
                customEmptyPage={<Text>No members in group.</Text>}
                page={activeGroup.data}
                pageRenderer={page =>
                    <GroupMembers
                        members={page.items.map(it => ({role: ProjectRole.USER, username: it}))}
                        promptRemoveMember={promptRemoveMember}
                        reload={reload}
                        project={Client.projectId ?? ""}
                        allowManagement
                        allowRoleManagement={false}
                    />
                }
            />
        }
        sidebar={<Box>
            <Button onClick={promptAddMember} mb="5px" color="green" width="100%">Add member</Button>
            <Button onClick={renameGroup} width="100%" mb="5px">Rename group</Button>
            <Button onClick={promptDeleteGroup} width="100%" color="red">Delete group</Button>
        </Box>}
        headerSize={120}
        header={<>
            <Box>
                <Text
                    style={{wordBreak: "break-word"}}
                    fontSize="25px"
                    width="100%"
                >{name}</Text>
                <Heading.h5>Members: {activeGroup.data.itemsInTotal}</Heading.h5>
            </Box>
        </>}
    />;

    function promptAddMember(): void {
        dialogStore.addDialog(<AddMemberPrompt group={name} existingMembers={allGroupMembers} addMember={member =>
            setAllGroupMembers(new Set([...allGroupMembers, member]))
        }/>, reload);
    }

    async function renameGroup(e: React.SyntheticEvent): Promise<void> {
        e.preventDefault();

        const result = await addStandardInputDialog({
            confirmText: "Rename",
            cancelText: "Cancel",
            defaultValue: "",
            placeholder: "Group name...",
            title: `Rename ${name}`,
            addToFront: false,
            validationFailureMessage: "Name can't be empty.",
            validator: val => !!val
        });

        if ("cancelled" in result) return;

        try {
            await promises.makeCancelable(Client.post("/projects/groups/update-name", {
                oldGroupName: name, newGroupName: result.result
            })).promise;
            snackbarStore.addSuccess("Group renamed", false);
            history.push(`/projects/groups/${encodeURI(result.result)}`);
        } catch (err) {
            snackbarStore.addFailure(errorMessageOrDefault(err, "An error occurred renaming group"), false);
        }
    }

    function promptDeleteGroup(): void {
        addStandardDialog({
            title: "Delete group?",
            message: `Do you want to delete ${name}`,
            onConfirm: () => deleteGroup(),
            confirmText: "Delete"
        });
    }

    async function deleteGroup(): Promise<void> {
        try {
            await promises.makeCancelable(Client.delete("/projects/groups", {groups: [name]})).promise;
            snackbarStore.addSuccess(`Group '${name}' deleted`, true);
            history.push("/projects/groups/");
        } catch (e) {
            snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred deleting group"), false);
        }
    }

    function promptRemoveMember(member: string): void {
        addStandardDialog({
            title: "Remove member?",
            message: `Do you want to remove ${member} from the group ${name}?`,
            onConfirm: () => removeMember(member),
            cancelText: "Cancel",
            confirmText: "Remove"
        });
    }

    async function removeMember(member: string): Promise<void> {
        const {path, payload} = removeGroupMemberRequest({group: name, memberUsername: member});
        try {
            await promises.makeCancelable(Client.delete(path!, payload)).promise;
            reload();
        } catch (err) {
            snackbarStore.addFailure(errorMessageOrDefault(err, "Failed to remove member."), false);
        }
    }
}

interface AddMemberPromptProps {
    group: string;
    existingMembers: Set<string>;
    addMember: (name: string) => void;
}

function AddMemberPrompt(props: AddMemberPromptProps): JSX.Element {
    const textRef = React.useRef<HTMLInputElement>(null);
    const [projectMembers, setParams] = useCloudAPI<Page<string>>(membershipSearch("", 0), emptyPage);
    const promises = usePromiseKeeper();

    const [newlyAdded, setNewlyAdded] = React.useState<Set<string>>(new Set());

    const ref = React.useRef<number>(-1);
    const onKeyUp = React.useCallback(() => {
        if (ref.current !== -1) {
            window.clearTimeout(ref.current);
        }
        ref.current = (window.setTimeout(() => {
            setParams(membershipSearch(textRef.current?.value ?? "", projectMembers.data.pageNumber));
        }, 500));

    }, [textRef.current, setParams]);

    return (
        <Box width="400px" maxHeight="90vh">
            <Input onKeyUp={onKeyUp} ref={textRef}/>
            <Pagination.List
                pageRenderer={page =>
                    <List>
                        {page.items.map(member =>
                            <ListRow
                                key={member}
                                isSelected={false}
                                select={() => undefined}
                                navigate={() => undefined}
                                left={member}
                                right={<Button disabled={props.existingMembers.has(member) || newlyAdded.has(member)}
                                               onClick={() => addMember(member)} color="green">Add to group</Button>}
                            />
                        )}
                    </List>
                }
                page={projectMembers.data}
                loading={projectMembers.loading}
                customEmptyPage={"No users found. Users must be added to project, before being able to join a group."}
                onPageChanged={newPage => setParams(membershipSearch(textRef.current?.value ?? "", newPage))}
            />
        </Box>
    );

    async function addMember(member: string): Promise<void> {
        const {path, payload} = addGroupMember({group: props.group, memberUsername: member});
        try {
            await promises.makeCancelable(Client.put(path!, payload)).promise;
            snackbarStore.addSuccess(`${member} added to project.`, false);
            props.addMember(member);
            newlyAdded.add(member);
            setNewlyAdded(new Set(newlyAdded));
        } catch (err) {
            snackbarStore.addFailure(errorMessageOrDefault(err, "Failed to add member."), false);
        }
    }
}


function membershipSearch(query: string, page: number): APICallParameters {
    return {
        method: "GET",
        path: buildQueryString("/projects/membership/search", {query, itemsPerPage: 100, page})
    };
}

export function GroupMembers(props: Readonly<{
    members: ProjectMember[];
    promptRemoveMember(member: string): void;
    allowManagement: boolean;
    allowRoleManagement: boolean;
    reload: () => void;
    project: string;
}>): JSX.Element {
    const [isLoading, runCommand] = useAsyncCommand();
    const avatars = useAvatars();

    useEffect(() => {
        const usernames = props.members.map(it => it.username);
        if (usernames.length === 0) return;
        avatars.updateCache(usernames);
    }, [props.members]);

    return (
        <Flex>
            {props.members.map(member =>
                <MemberBox>
                    <Avatar
                        style={{width: "64px", height: "64px", margin: "4px"}}
                        avatarStyle="Circle"
                        {...avatars.cache[member.username] ?? defaultAvatar}
                    />

                    <Flex flexDirection={"column"} m={8} flexGrow={1}>
                        <Flex alignItems={"center"}>
                            <Box flexGrow={1}>{member.username}</Box>
                            {!props.allowManagement || member.role === ProjectRole.PI ? null :
                                <Icon
                                    cursor="pointer"
                                    mr="8px"
                                    ml="8px"
                                    color="red"
                                    name="close"
                                    onClick={() => props.promptRemoveMember(member.username)} size="20px"
                                />
                            }
                        </Flex>

                        {!props.allowRoleManagement || member.role === ProjectRole.PI ? projectRoleToString(member.role) :
                            <ClickableDropdown
                                chevron
                                trigger={projectRoleToString(member.role)}
                                onChange={async value => {
                                    try {
                                        await runCommand(changeRoleInProject({
                                            projectId: props.project,
                                            member: member.username,
                                            newRole: value
                                        }));
                                        props.reload();
                                    } catch (err) {
                                        snackbarStore.addFailure(errorMessageOrDefault(err, "Failed to update role."), false);
                                    }
                                }}
                                options={[
                                    {text: "User", value: ProjectRole.USER},
                                    {text: "Admin", value: ProjectRole.ADMIN}
                                ]}
                            />}
                    </Flex>
                </MemberBox>
            )}
        </Flex>
    );
}

const MemberBox = styled(Flex)`
    min-width: 200px;
    align-items: center;
    border-radius: 8px;
    
    &:hover {
        background-color: var(--lightBlue);
        transition: background-color 0.2s;
    }
`;

export default DetailedGroupView;

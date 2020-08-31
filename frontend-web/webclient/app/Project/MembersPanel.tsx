import {useAsyncCommand} from "Authentication/DataHook";
import {deleteMemberInProject, inviteMember, listOutgoingInvites, ProjectRole, rejectInvite} from "Project/index";
import * as React from "react";
import {useRef} from "react";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {errorMessageOrDefault, preventDefault} from "UtilityFunctions";
import {Button, Flex, Icon, Input, Absolute, Label, Relative} from "ui-components";
import {addStandardDialog, addStandardInputDialog} from "UtilityComponents";
import {useProjectManagementStatus} from "Project/index";
import {addGroupMember} from "Project";
import {MembersList} from "Project/MembersList";
import * as Pagination from "Pagination";
import styled from "styled-components";

const SearchContainer = styled(Flex)`
    flex-wrap: wrap;
    
    form {
        flex-grow: 1;
        flex-basis: 350px;
        display: flex;
        margin-right: 10px;
        margin-bottom: 10px;
    }
`;

const MembersPanel: React.FunctionComponent = () => {
    const {
        projectId, projectMembers, groupId, fetchGroupMembers, groupMembersParams,
        setProjectMemberParams, projectMemberParams, memberSearchQuery, setMemberSearchQuery, allowManagement,
        outgoingInvites, outgoingInvitesParams, fetchOutgoingInvites, projectRole, reloadProjectStatus
    } = useProjectManagementStatus({isRootComponent: false});
    const [isLoading, runCommand] = useAsyncCommand();
    const reloadMembers = (): void => {
        setProjectMemberParams(projectMemberParams);
        fetchOutgoingInvites(outgoingInvitesParams);
        reloadProjectStatus();
    };

    const newMemberRef = useRef<HTMLInputElement>(null);

    const onSubmit = async (e: React.FormEvent): Promise<void> => {
        e.preventDefault();
        const inputField = newMemberRef.current!;
        const username = inputField.value;
        try {
            await runCommand(inviteMember({
                projectId,
                usernames: [username]
            }));
            inputField.value = "";
            reloadMembers();
        } catch (err) {
            snackbarStore.addFailure(errorMessageOrDefault(err, "Failed adding new member"), false);
        }
    };

    return <>
        <SearchContainer>
            {!allowManagement ? null : (
                <form onSubmit={onSubmit}>
                    <Input
                        id="new-project-member"
                        placeholder="Username"
                        autoComplete="off"
                        disabled={isLoading}
                        ref={newMemberRef}
                        onChange={e => {
                            newMemberRef.current!.value = e.target.value;
                        }}
                        rightLabel
                    />
                    <Button
                        asSquare
                        color={"green"}
                        type={"button"}
                        title={"Bulk invite"}
                        onClick={async () => {
                            try {
                                const res = await addStandardInputDialog({
                                    title: "Bulk invite",
                                    type: "textarea",
                                    confirmText: "Invite users",
                                    width: "450px",
                                    help: (<>Enter usernames in the box below. One username per line.</>)
                                });

                                const usernames = res.result
                                    .split("\n")
                                    .map(it => it.trim())
                                    .filter(it => it.length > 0);

                                await runCommand(inviteMember({projectId, usernames}));
                                reloadMembers();
                            } catch (ignored) {
                                // Ignored
                            }
                        }}
                    >
                        <Icon name="open" />
                    </Button>
                    <Button attached type={"submit"}>Add</Button>
                </form>
            )}
            <form onSubmit={preventDefault}>
                <Input
                    id="project-member-search"
                    placeholder="Enter username to search..."
                    pr="30px"
                    autoComplete="off"
                    disabled={isLoading}
                    value={memberSearchQuery}
                    onChange={e => {
                        setMemberSearchQuery(e.target.value);
                    }}
                />
                <Relative>
                    <Absolute right="6px" top="10px">
                        <Label htmlFor="project-member-search">
                            <Icon name="search" size="24" />
                        </Label>
                    </Absolute>
                </Relative>
            </form>
        </SearchContainer>

        <MembersList
            members={projectMembers.data.items}
            onRemoveMember={async member => addStandardDialog({
                title: "Remove member",
                message: `Remove ${member}?`,
                onConfirm: async () => {
                    await runCommand(deleteMemberInProject({
                        projectId: projectId,
                        member
                    }));

                    reloadMembers();
                }
            })}
            reload={reloadMembers}
            projectId={projectId}
            projectRole={projectRole}
            allowRoleManagement={allowManagement}
            onAddToGroup={!(allowManagement && !!groupId) ? undefined : async (memberUsername) => {
                await runCommand(addGroupMember({group: groupId, memberUsername}));
                fetchGroupMembers(groupMembersParams);
            }}
        />

        {groupId ? null :
            <Pagination.List
                loading={outgoingInvites.loading}
                page={outgoingInvites.data}
                onPageChanged={(newPage) => {
                    fetchOutgoingInvites(listOutgoingInvites({ ...outgoingInvitesParams.parameters, page: newPage }));
                }}
                customEmptyPage={<></>}
                pageRenderer={() => (
                    <MembersList
                        isOutgoingInvites
                        members={outgoingInvites.data.items.map(it => ({
                            username: it.username,
                            role: ProjectRole.USER
                        }))}
                        onRemoveMember={async (member) => {
                            await runCommand(rejectInvite({ projectId, username: member }));
                            reloadMembers();
                        }}
                        projectRole={projectRole}
                        allowRoleManagement={false}
                        projectId={projectId}
                        showRole={false}
                    />
                )}
            />
        }
    </>;
};

export default MembersPanel;

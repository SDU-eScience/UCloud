import {useAsyncCommand} from "Authentication/DataHook";
import {addMemberInProject, deleteMemberInProject, ProjectRole, roleInProject} from "Project/index";
import * as React from "react";
import {useRef} from "react";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {errorMessageOrDefault, preventDefault} from "UtilityFunctions";
import {BreadCrumbsBase} from "ui-components/Breadcrumbs";
import {Box, Button, Flex, Icon, Input, Truncate} from "ui-components";
import {addStandardDialog} from "UtilityComponents";
import {useProjectManagementStatus} from "Project/View";
import {addGroupMember} from "Project";
import {MembersList} from "Project/MembersList";
import styled from "styled-components";

const SearchContainer = styled(Flex)`
    flex-wrap: wrap;
    
    form {
        flex-grow: 1;
        flex-basis: 300px;
        display: flex;
    }
    
    form {
        margin-right: 10px;
        margin-bottom: 10px;
    }
`;

const MembersPanel: React.FunctionComponent = props => {
    const {
        projectId, projectMembers, group, fetchGroupMembers, groupMembersParams,
        setProjectMemberParams, projectMemberParams, memberSearchQuery, setMemberSearchQuery, allowManagement
    } = useProjectManagementStatus();
    const [isLoading, runCommand] = useAsyncCommand();
    const reloadMembers = () => {
        setProjectMemberParams(projectMemberParams);
    };

    const newMemberRef = useRef<HTMLInputElement>(null);

    const onSubmit = async (e: React.FormEvent): Promise<void> => {
        e.preventDefault();
        const inputField = newMemberRef.current!;
        const username = inputField.value;
        try {
            await runCommand(addMemberInProject({
                projectId,
                member: {
                    username,
                    role: ProjectRole.USER
                }
            }));
            inputField.value = "";
            reloadMembers();
        } catch (err) {
            snackbarStore.addFailure(errorMessageOrDefault(err, "Failed adding new member"), false);
        }
    };

    return <>
        <BreadCrumbsBase>
            <li><Truncate width={"500px"}>Members of {projectId}</Truncate></li>
        </BreadCrumbsBase>
        <SearchContainer>
            {!allowManagement ? null : (
                <form onSubmit={onSubmit}>
                    <Input
                        id="new-project-member"
                        placeholder="Username"
                        disabled={isLoading}
                        ref={newMemberRef}
                        onChange={e => {
                            newMemberRef.current!.value = e.target.value;
                        }}
                        rightLabel
                    />
                    <Button attached>Add</Button>
                </form>
            )}

            <form onSubmit={preventDefault}>
                <Input
                    id="project-member-search"
                    placeholder="Enter username to search..."
                    disabled={isLoading}
                    value={memberSearchQuery}
                    onChange={e => {
                        setMemberSearchQuery(e.target.value);
                    }}
                    rightLabel
                />
                <Button attached><Icon name={"search"}/></Button>
            </form>
        </SearchContainer>

        <MembersList
            members={projectMembers.data.items}
            onRemoveMember={async member => addStandardDialog({
                title: "Remove member",
                message: `Remove ${member}?`,
                onConfirm: async () => {
                    await runCommand(deleteMemberInProject({
                        projectId,
                        member
                    }));

                    reloadMembers();
                }
            })}
            reload={reloadMembers}
            projectMembers={projectId}
            allowManagement={allowManagement}
            allowRoleManagement={allowManagement}
            onAddToGroup={!(allowManagement && !!group) ? undefined : async (memberUsername) => {
                await runCommand(addGroupMember({group, memberUsername}));
                fetchGroupMembers(groupMembersParams);
            }}
        />
    </>;
}

export default MembersPanel;
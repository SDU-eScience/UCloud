import {useAsyncCommand} from "Authentication/DataHook";
import {
    addMemberInProject,
    deleteMemberInProject,
    ProjectRole,
    roleInProject,
    viewProject
} from "Project/index";
import * as React from "react";
import {MutableRefObject, useRef} from "react";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {errorMessageOrDefault} from "UtilityFunctions";
import {BreadCrumbsBase} from "ui-components/Breadcrumbs";
import {Box, Button, Flex, Icon, Input} from "ui-components";
import {GroupMembers} from "Project/DetailedGroupView";
import {addStandardDialog} from "UtilityComponents";
import {useProjectManagementStatus} from "Project/View";
import {addGroupMember} from "Project/api";

const Members: React.FunctionComponent = props => {
    const {
        projectId, projectMembers, group, fetchGroupMembers, groupMembersParams,
        setProjectMemberParams, projectMemberParams, memberSearchQuery, setMemberSearchQuery
    } = useProjectManagementStatus();
    const [isLoading, runCommand] = useAsyncCommand();
    const reloadMembers = () => {
        setProjectMemberParams(projectMemberParams);
    };

    const newMemberRef = useRef<HTMLInputElement>(null);

    // TODO
    const role = roleInProject(projectMembers.data.items);
    const allowManagement = true; //role === ProjectRole.PI || role === ProjectRole.ADMIN;

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
            <li><span>Members of {projectId}</span></li>
        </BreadCrumbsBase>
        {!allowManagement ? null : (
            <Flex>
                <Box flexGrow={1}>
                    <form onSubmit={onSubmit} style={{display: "flex"}}>
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
                        <Button attached mr={2}>Add</Button>
                    </form>
                </Box>

                <Box flexGrow={1} ml={2}>
                    <Input
                        id="new-project-member"
                        placeholder="Enter username to search..."
                        disabled={isLoading}
                        value={memberSearchQuery}
                        onChange={e => {
                            setMemberSearchQuery(e.target.value);
                        }}
                        rightLabel
                    />
                </Box>
                <Button attached><Icon name={"search"}/></Button>
            </Flex>
        )}

        <GroupMembers
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

export default Members;
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
import {Box, Button, Flex, Input} from "ui-components";
import {GroupMembers} from "Project/DetailedGroupView";
import {addStandardDialog} from "UtilityComponents";
import {useProjectManagementStatus} from "Project/View";
import {addGroupMember} from "Project/api";

const Members: React.FunctionComponent = props => {
    const {
        projectId, project, group, fetchGroupMembers, groupMembersParams,
        setProjectParams, projectMemberParams, memberSearchQuery, setMemberSearchQuery
    } = useProjectManagementStatus();
    const [isLoading, runCommand] = useAsyncCommand();
    const reloadMembers = () => {
        setProjectParams(projectMemberParams);
    };

    const newMemberRef = useRef<HTMLInputElement>(null);

    // TODO
    const role = roleInProject(project.data.items);
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
            <form onSubmit={onSubmit}>
                <Flex>
                    <Box flexGrow={1}>
                        <Input
                            id="new-project-member"
                            placeholder="Username"
                            ref={newMemberRef}
                            disabled={isLoading}
                            value={memberSearchQuery}
                            onChange={e => {
                                newMemberRef.current!.value = e.target.value;
                                setMemberSearchQuery(e.target.value);
                            }}
                            rightLabel
                        />
                    </Box>
                    <Button attached>Add</Button>
                </Flex>
            </form>
        )}

        <GroupMembers
            members={project.data.items}
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
            project={projectId}
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
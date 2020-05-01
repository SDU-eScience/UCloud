import {APICallState, useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {addMemberInProject, deleteMemberInProject, Project, ProjectRole, roleInProject} from "Project/index";
import * as React from "react";
import {useRef} from "react";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {errorMessageOrDefault} from "UtilityFunctions";
import {searchPreviousSharedUsers, ServiceOrigin} from "Shares";
import {BreadCrumbsBase} from "ui-components/Breadcrumbs";
import {Dropdown, DropdownContent} from "ui-components/Dropdown";
import {Button, Flex, Input, Label} from "ui-components";
import {GroupMembers} from "Project/DetailedGroupView";
import {addStandardDialog} from "UtilityComponents";

interface ProjectMembersProps {
    id: string;
    project: APICallState<Project>;
    reload: () => void;
}

const Members: React.FunctionComponent<ProjectMembersProps> = props => {
    const {id, project, reload} = props;
    const [isLoading, runCommand] = useAsyncCommand();
    const newMemberRef = useRef<HTMLInputElement>(null);

    const role = roleInProject(project.data);
    const allowManagement = role === ProjectRole.PI || role === ProjectRole.ADMIN;

    const onSubmit = async (e: React.FormEvent): Promise<void> => {
        e.preventDefault();
        const inputField = newMemberRef.current!;
        const username = inputField.value;
        try {
            await runCommand(addMemberInProject({
                projectId: id,
                member: {
                    username,
                    role: ProjectRole.USER
                }
            }));
            inputField.value = "";
            reload();
        } catch (err) {
            snackbarStore.addFailure(errorMessageOrDefault(err, "Failed adding new member"), false);
        }
    };

    /* Contact book */
    const SERVICE = ServiceOrigin.PROJECT_SERVICE;
    const ref = React.useRef<number>(-1);
    const [contacts, setFetchArgs,] = useCloudAPI<{ contacts: string[] }>(
        searchPreviousSharedUsers("", SERVICE),
        {contacts: []}
    );

    const onKeyUp = React.useCallback(() => {
        if (ref.current !== -1) {
            window.clearTimeout(ref.current);
        }
        ref.current = (window.setTimeout(() => {
            setFetchArgs(searchPreviousSharedUsers(newMemberRef.current!.value, SERVICE));
        }, 500));

    }, [newMemberRef.current, setFetchArgs]);

    return <>
        <BreadCrumbsBase>
            <li><span>Members of {project.data.title}</span></li>
        </BreadCrumbsBase>
        {!allowManagement ? null : (
            <form onSubmit={onSubmit}>
                <Dropdown fullWidth hover={false}>
                    <Label htmlFor={"new-project-member"}>Add new member</Label>
                    <Flex mb="6px">
                        <Input
                            onKeyUp={onKeyUp}
                            id="new-project-member"
                            placeholder="Username"
                            ref={newMemberRef}
                            width="350px"
                            disabled={isLoading}
                            rightLabel
                        />
                        <Button attached>Add</Button>
                    </Flex>
                </Dropdown>
                <DropdownContent
                    hover={false}
                    colorOnHover={false}
                    width="350px"
                    visible={contacts.data.contacts.length > 0}
                >
                    {contacts.data.contacts.map(it => (
                        <div
                            key={it}
                            onClick={() => {
                                newMemberRef.current!.value = it;
                                setFetchArgs(searchPreviousSharedUsers("", SERVICE));
                            }}
                        >
                            {it}
                        </div>
                    ))}
                </DropdownContent>
            </form>
        )}

        <GroupMembers
            members={project.data.members}
            promptRemoveMember={async member => addStandardDialog({
                title: "Remove member",
                message: `Remove ${member}?`,
                onConfirm: async () => {
                    await runCommand(deleteMemberInProject({
                        projectId: id,
                        member
                    }));

                    reload();
                }
            })}
            reload={reload}
            project={project.data.id}
            allowManagement={allowManagement}
            allowRoleManagement={allowManagement}
        />
    </>;
}

export default Members;
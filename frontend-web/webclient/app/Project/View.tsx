import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {UserAvatar} from "AvataaarLib/UserAvatar";
import {LoadingMainContainer} from "MainContainer/MainContainer";
import {
    addMemberInProject,
    changeRoleInProject,
    deleteMemberInProject,
    emptyProject,
    Project,
    ProjectMember,
    ProjectRole,
    roleInProject,
    viewProject
} from "Project/index";
import * as React from "react";
import {useEffect, useRef, useState} from "react";
import {useParams} from "react-router";
import {Button, Flex, Input, Label} from "ui-components";
import Box from "ui-components/Box";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {defaultAvatar} from "UserSettings/Avataaar";

const View: React.FunctionComponent = () => {
    const {id} = useParams<{id: string}>();
    const [project, setProjectParams] = useCloudAPI<Project>(viewProject({id}), emptyProject(id));
    const role = roleInProject(project.data);
    const allowManagement = role === ProjectRole.PI;
    const newMemberRef = useRef<HTMLInputElement>(null);
    const [isCreatingNewMember, createNewMember] = useAsyncCommand();

    const reload = () => setProjectParams(viewProject({id}));

    useEffect(() => reload(), [id]);

    const onSubmit = async e => {
        e.preventDefault();
        const inputField = newMemberRef.current!;
        const username = inputField.value;

        await createNewMember(addMemberInProject({
            projectId: id,
            member: {
                username,
                role: ProjectRole.USER
            }
        }));

        inputField.value = "";
        reload();
    };

    return (
        <LoadingMainContainer
            headerSize={0}
            header={null}
            sidebar={null}
            loading={project.loading && project.data.members.length === 0}
            error={project.error ? project.error.why : undefined}
            main={(
                <>
                    <div>
                        <form onSubmit={onSubmit}>
                            <Label htmlFor={"new-project-member"}>Add new member</Label>
                            <Input
                                id="new-project-member"
                                placeholder="Username"
                                ref={newMemberRef}
                                disabled={isCreatingNewMember}
                            />
                        </form>
                    </div>

                    {project.data.members.map((e, idx) => (
                        <ViewMember
                            key={idx}
                            project={project.data}
                            member={e}
                            allowManagement={allowManagement}
                            onActionComplete={() => reload()}
                        />
                    ))
                    }
                </>
            )}
        />
    );
};

const ViewMember: React.FunctionComponent<{
    project: Project,
    member: ProjectMember,
    allowManagement: boolean,
    onActionComplete: () => void
}> = props => {
    const [isLoading, runCommand] = useAsyncCommand();
    const [role, setRole] = useState<ProjectRole>(props.member.role);

    const deleteMember = async () => {
        await runCommand(deleteMemberInProject({
            projectId: props.project.id,
            member: props.member.username
        }));

        props.onActionComplete();
    };

    return (
        <Box mt={16}>
            <Flex>
                <UserAvatar avatar={defaultAvatar} />
                <Box flexGrow={1}>
                    {props.member.username} <br />
                    {!props.allowManagement ? role : (
                        <ClickableDropdown
                            chevron
                            trigger={role}
                            onChange={async (value: ProjectRole) => {
                                setRole(value);

                                await runCommand(changeRoleInProject({
                                    projectId: props.project.id,
                                    member: props.member.username,
                                    newRole: value
                                }));

                                props.onActionComplete();
                            }}
                            options={[
                                {text: "User", value: ProjectRole.USER},
                                {text: "Data Steward", value: ProjectRole.DATA_STEWARD},
                                {text: "Admin", value: ProjectRole.ADMIN}
                            ]}
                        />
                    )}
                </Box>
                {!props.allowManagement ? null : (
                    <Box flexShrink={0}>
                        <Button
                            color={"red"}
                            mr={8}
                            disabled={isLoading}
                            onClick={deleteMember}
                        >
                            Remove
                        </Button>
                    </Box>
                )}
            </Flex>
        </Box>
    );
};

export default View;

import * as React from "react";
import {useEffect, useRef, useState} from "react";
import {match} from "react-router";
import {LoadingMainContainer} from "MainContainer/MainContainer";
import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {
    addMemberInProject, changeRoleInProject, deleteMemberInProject,
    emptyProject,
    Project,
    ProjectMember,
    ProjectRole,
    roleInProject,
    viewProject
} from "Project/index";
import Box from "ui-components/Box";
import {UserAvatar} from "Navigation/Header";
import {defaultAvatar} from "UserSettings/Avataaar";
import Flex from "ui-components/Flex";
import Button from "ui-components/Button";
import Input from "ui-components/Input";
import Label from "ui-components/Label";
import ClickableDropdown from "ui-components/ClickableDropdown";

const View: React.FunctionComponent<{ match: match<{ id: string }> }> = props => {
    const id = props.match.params.id;
    const [project, setProjectParams] = useCloudAPI<Project>(viewProject({id}), emptyProject(id));
    const role = roleInProject(project.data);
    const allowManagement = role === ProjectRole.PI;
    const newMemberRef = useRef<HTMLInputElement>(null);
    const [isCreatingNewMember, createNewMember] = useAsyncCommand();

    const reload = () => setProjectParams(viewProject({id}));

    useEffect(() => reload(), [id]);

    const onSubmit = async e => {
        e.preventDefault();
        let inputField = newMemberRef.current!;
        let username = inputField.value;
        console.log(username);

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

    return <LoadingMainContainer
        headerSize={0}
        header={null}
        sidebar={null}
        loading={project.loading && project.data.members.length === 0}
        error={project.error ? project.error.why : undefined}
        main={
            <>
                <Box>
                    <form onSubmit={onSubmit}>
                        <Label htmlFor={"new-project-member"}>Add new member</Label>
                        <Input id={"new-project-member"} placeholder={"Username"} ref={newMemberRef}
                               disabled={isCreatingNewMember}/>
                    </form>
                </Box>

                {project.data.members.map((e, idx) => (
                    <ViewMember
                        key={idx}
                        project={project.data}
                        member={e}
                        allowManagement={allowManagement}
                        onActionComplete={() => reload()}/>
                ))
                }
            </>
        }
    />
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

    return <Box mt={16}>
        <Flex>
            <UserAvatar avatar={defaultAvatar}/>
            <Box flexGrow={1}>
                {props.member.username} <br/>
                {!props.allowManagement ? role :
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
                            {"text": "User", value: ProjectRole.USER},
                            {"text": "Data Steward", value: ProjectRole.DATA_STEWARD},
                            {"text": "Admin", value: ProjectRole.ADMIN}
                        ]}
                    />
                }
            </Box>
            {!props.allowManagement ? null :
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
            }
        </Flex>
    </Box>;
};

export default View;
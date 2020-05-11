import {changeRoleInProject, ProjectMember, ProjectRole, projectRoleToString} from "Project/index";
import {useAsyncCommand} from "Authentication/DataHook";
import {useAvatars} from "AvataaarLib/hook";
import {useEffect} from "react";
import {GridCardGroup} from "ui-components/Grid";
import {Avatar} from "AvataaarLib";
import {defaultAvatar} from "UserSettings/Avataaar";
import {Flex, Icon, Truncate} from "ui-components";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {errorMessageOrDefault} from "UtilityFunctions";
import styled from "styled-components";
import * as React from "react";
import {IconName} from "ui-components/Icon";

export function MembersList(props: Readonly<{
    members: ProjectMember[];
    onAddToGroup?: (member: string) => void;
    onRemoveMember(member: string): void;
    allowManagement: boolean;
    allowRoleManagement: boolean;
    reload?: () => void;
    showRole?: boolean;
    projectId: string;
}>): JSX.Element {
    const [, runCommand] = useAsyncCommand();
    const avatars = useAvatars();

    useEffect(() => {
        const usernames = props.members.map(it => it.username);
        avatars.updateCache(usernames);
    }, [props.members]);

    return (
        <GridCardGroup minmax={260}>
            {props.members.map(member =>
                <MemberBox key={member.username}>
                    <Avatar
                        style={{width: "48px", height: "48px", margin: "4px", flexShrink: 0}}
                        avatarStyle="Circle"
                        {...avatars.cache[member.username] ?? defaultAvatar}
                    />

                    <Flex flexDirection={"column"} m={8}>
                        <Flex alignItems={"center"}>
                            <Truncate width={"125px"} title={member.username}>{member.username}</Truncate>
                            {!props.onAddToGroup ? null :
                                <ActionButton
                                    color={"green"}
                                    icon={"arrowDown"}
                                    rotation={270}
                                    title={"Add to group"}
                                    onClick={() => props.onAddToGroup!(member.username)}
                                />
                            }
                            {!props.allowManagement || member.role === ProjectRole.PI ? null :
                                <ActionButton
                                    color={"red"}
                                    icon={"close"}
                                    title={"Remove from project"}
                                    onClick={() => props.onRemoveMember(member.username)}
                                />
                            }
                        </Flex>

                        {props.showRole === false ? null :
                            !props.allowRoleManagement || member.role === ProjectRole.PI ? projectRoleToString(member.role)
                                :
                                <ClickableDropdown
                                    chevron
                                    trigger={projectRoleToString(member.role)}
                                    onChange={async value => {
                                        try {
                                            await runCommand(changeRoleInProject({
                                                projectId: props.projectId,
                                                member: member.username,
                                                newRole: value
                                            }));
                                            if (props.reload) props.reload();
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
        </GridCardGroup>

    );
}

const ActionButton: React.FunctionComponent<{
    icon: IconName,
    title: string,
    onClick: () => void,
    color: string
    rotation?: number
}> = props => {
    return (
        <Icon
            cursor="pointer"
            mr="8px"
            ml="8px"
            color={props.color}
            name={props.icon}
            title={props.title}
            onClick={props.onClick}
            rotation={props.rotation}
            size="20px"
        />
    );
}

const MemberBox = styled(Flex)`
    width: 260px;
    align-items: center;
    border-radius: 8px;
    margin-right: 8px;
    
    &:hover {
        background-color: var(--lightGray);
        transition: background-color 0.2s;
    }
`;
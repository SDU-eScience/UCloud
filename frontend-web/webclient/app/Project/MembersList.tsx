import {useCloudCommand} from "@/Authentication/DataHook";
import {useAvatars} from "@/AvataaarLib/hook";
import * as React from "react";
import {useEffect} from "react";
import styled from "styled-components";
import {Flex, Icon, Text, Box, Button, RadioTile, RadioTilesContainer} from "@/ui-components";
import {IconName} from "@/ui-components/Icon";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {errorMessageOrDefault} from "@/UtilityFunctions";
import {addStandardDialog} from "@/UtilityComponents";
import {UserAvatar} from "@/AvataaarLib/UserAvatar";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {Client} from "@/Authentication/HttpClientInstance";
import ProjectAPI, {isAdminOrPI, OldProjectRole, ProjectGroup, ProjectMember, projectStringToRole} from "./Api";
import {bulkRequestOf} from "@/DefaultObjects";

export function MembersList(props: Readonly<{
    members: ProjectMember[];
    groups: ProjectGroup[];
    onAddToGroup?: (member: string) => void;
    onRemoveMember(member: string): void;
    allowRoleManagement: boolean;
    projectRole: OldProjectRole;
    reload?: () => void;
    isOutgoingInvites?: boolean;
    showRole?: boolean;
    projectId: string;
}>): JSX.Element {
    const [, runCommand] = useCloudCommand();
    const avatars = useAvatars();
    const allowManagement = isAdminOrPI(props.projectRole);

    useEffect(() => {
        const usernames = props.members.map(it => it.username);
        avatars.updateCache(usernames);
    }, [props.members]);

    const options: {text: string; icon: IconName; value: OldProjectRole}[] = [
        {text: "User", icon: "user", value: OldProjectRole.USER},
        {text: "Admin", icon: "userAdmin", value: OldProjectRole.ADMIN}
    ];

    if (props.projectRole === OldProjectRole.PI) {
        options.push({text: "PI", icon: "userPi", value: OldProjectRole.PI});
    }

    return (<>
        {props.members.map(member =>
            <React.Fragment key={member.username}>
                <Flex alignItems="center" mb="16px" mt="16px">
                    <UserAvatar avatar={avatars.avatar(member.username)} mr="10px" />
                    {!props.isOutgoingInvites ?
                        <div>
                            <Text bold>{member.username}</Text>
                            {memberOfAnyGroup(member.username, props.groups) ? null : (
                                <Text color="red">
                                    <Icon name="warning" size={20} mr="6px" />
                                    Not a member of any group
                                </Text>
                            )}
                        </div> :
                        <div>
                            <Text bold>{member.username}</Text>
                            Invited to join
                        </div>
                    }

                    <Box flexGrow={1} />

                    {props.showRole === false ? null :
                        !props.allowRoleManagement || member.role === OldProjectRole.PI ?
                            <RadioTilesContainer height="48px">
                                <RadioTile
                                    name={member.username}
                                    icon={roleToIcon(member.role)}
                                    height={40}
                                    labeled
                                    label={member.role}
                                    fontSize="0.5em"
                                    checked
                                    onChange={() => undefined}
                                />
                            </RadioTilesContainer> :
                            <RadioTilesContainer height="48px">
                                {options.map(role =>
                                    <RadioTile
                                        key={role.text}
                                        name={member.username}
                                        icon={role.icon}
                                        height={40}
                                        labeled
                                        label={role.text}
                                        fontSize={"0.5em"}
                                        checked={role.value === member.role}
                                        onChange={async event => {
                                            try {
                                                if (event.currentTarget.value === "PI") {
                                                    addStandardDialog({
                                                        title: "Transfer PI Role",
                                                        message: "Are you sure you wish to transfer the PI role? " +
                                                            "A project can only have one PI. " +
                                                            "Your own user will be demoted to admin.",
                                                        onConfirm: async () => {
                                                            await runCommand({
                                                                ...ProjectAPI.changeRole(bulkRequestOf({
                                                                    username: member.username,
                                                                    role: OldProjectRole.PI
                                                                })),
                                                                projectOverride: props.projectId
                                                            });

                                                            if (props.reload) props.reload();
                                                        },
                                                        confirmText: "Transfer PI role"
                                                    });
                                                } else {
                                                    await runCommand({
                                                        ...ProjectAPI.changeRole(bulkRequestOf({
                                                            username: member.username,
                                                            role: projectStringToRole(event.currentTarget.value)
                                                        })),
                                                        projectOverride: props.projectId
                                                    });
                                                    if (props.reload) props.reload();
                                                }
                                            } catch (err) {
                                                snackbarStore.addFailure(
                                                    errorMessageOrDefault(err, "Failed to update role."), false
                                                );
                                            }
                                        }}
                                    />
                                )}
                            </RadioTilesContainer>
                    }

                    <Flex alignItems={"center"}>
                        {!props.onAddToGroup ? !allowManagement || member.role === OldProjectRole.PI ? null :
                            <ConfirmationButtonStyling>
                                {member.username == Client.username ? null : <ConfirmationButton
                                    icon={"close"}
                                    actionText="Remove"
                                    onAction={() => props.onRemoveMember(member.username)}
                                />}
                            </ConfirmationButtonStyling> :
                            <Button ml="8px" color="green" height="35px" width="35px" onClick={() => props.onAddToGroup!(member.username)}>
                                <Icon
                                    color="white"
                                    name="arrowDown"
                                    rotation={-90}
                                    width="1em"
                                    title="Add to group"
                                />
                            </Button>
                        }
                    </Flex>
                </Flex>
            </React.Fragment>
        )}
    </>);
}

function roleToIcon(role: OldProjectRole): "user" | "userAdmin" | "userPi" {
    switch (role) {
        case OldProjectRole.ADMIN:
            return "userAdmin";
        case OldProjectRole.PI:
            return "userPi";
        case OldProjectRole.USER:
            return "user";
    }
}
const ConfirmationButtonStyling = styled(Box)`
    margin-left: 3px;
 
    & > button {
        min-width: 175px;
        font-size: 12px;
    }

    & ${Icon} {
        height: 12px;
        width: 12px;
    }
`;

function memberOfAnyGroup(username: string, groups: ProjectGroup[]): boolean {
    return groups.some(it => it.status.members?.includes(username));
}
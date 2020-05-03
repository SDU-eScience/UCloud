import * as React from "react";
import {MainContainer} from "MainContainer/MainContainer";
import {Text, Flex, Icon, Truncate, Link} from "ui-components";
import {defaultAvatar} from "UserSettings/Avataaar";
import * as Pagination from "Pagination";
import {APICallParameters, useAsyncCommand} from "Authentication/DataHook";
import {
    listGroupMembersRequest,
    removeGroupMemberRequest,
    projectRoleToString
} from "./api";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {errorMessageOrDefault} from "UtilityFunctions";
import {GridCardGroup} from "ui-components/Grid";
import {Avatar} from "AvataaarLib";
import {addStandardDialog} from "UtilityComponents";
import {buildQueryString} from "Utilities/URIUtilities";
import {ProjectMember, ProjectRole, changeRoleInProject} from "Project";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {useEffect} from "react";
import {useAvatars} from "AvataaarLib/hook";
import styled from "styled-components";
import {BreadCrumbsBase} from "ui-components/Breadcrumbs";
import {useProjectManagementStatus} from "Project/View";

const DetailedGroupView: React.FunctionComponent = props => {
    const {projectId, group, groupMembers, fetchGroupMembers, groupMembersParams} = useProjectManagementStatus();
    const activeGroup = groupMembers;
    const fetchActiveGroup = fetchGroupMembers;
    const [isLoading, runCommand] = useAsyncCommand();

    if (!group || activeGroup.error) return <MainContainer main={
        <Text fontSize={"24px"}>Could not fetch &apos;{group}&apos;.</Text>
    }/>;

    return <>
        <BreadCrumbsBase>
            <li><span><Link to={`/projects/view/${projectId}`}>Groups</Link></span></li>
            <li><span>{group}</span></li>
        </BreadCrumbsBase>
        <Pagination.List
            loading={activeGroup.loading}
            onPageChanged={(newPage, page) => fetchActiveGroup(listGroupMembersRequest({
                group,
                itemsPerPage: page.itemsPerPage,
                page: newPage
            }))}
            customEmptyPage={<Text>No members in group.</Text>}
            page={activeGroup.data}
            pageRenderer={page =>
                <GroupMembers
                    members={page.items.map(it => ({role: ProjectRole.USER, username: it}))}
                    onRemoveMember={promptRemoveMember}
                    project={projectId}
                    allowManagement
                    allowRoleManagement={false}
                    showRole={false}
                />
            }
        />
    </>;

    function promptRemoveMember(member: string): void {
        addStandardDialog({
            title: "Remove member?",
            message: `Do you want to remove ${member} from the group ${group}?`,
            onConfirm: () => removeMember(member),
            cancelText: "Cancel",
            confirmText: "Remove"
        });
    }

    async function removeMember(member: string): Promise<void> {
        if (group === undefined) return;

        await runCommand(removeGroupMemberRequest({group: group!, memberUsername: member}));
        fetchGroupMembers(groupMembersParams);
    }
}

export function GroupMembers(props: Readonly<{
    members: ProjectMember[];
    onAddToGroup?: (member: string) => void;
    onRemoveMember(member: string): void;
    allowManagement: boolean;
    allowRoleManagement: boolean;
    reload?: () => void;
    showRole?: boolean;
    project: string;
}>): JSX.Element {
    const [, runCommand] = useAsyncCommand();
    const avatars = useAvatars();

    useEffect(() => {
        const usernames = props.members.map(it => it.username);
        if (usernames.length === 0) return;
        avatars.updateCache(usernames);
    }, [props.members]);

    return (
        <GridCardGroup minmax={260}>
            {props.members.map(member =>
                <MemberBox>
                    <Avatar
                        style={{width: "48px", height: "48px", margin: "4px", flexShrink: 0}}
                        avatarStyle="Circle"
                        {...avatars.cache[member.username] ?? defaultAvatar}
                    />

                    <Flex flexDirection={"column"} m={8}>
                        <Flex alignItems={"center"}>
                            <Truncate width={"125px"} title={member.username}>{member.username}</Truncate>
                            {!props.onAddToGroup ? null :
                                <Icon
                                    cursor={"pointer"}
                                    mr={"8px"}
                                    ml={"8px"}
                                    color={"green"}
                                    name={"arrowDown"}
                                    rotation={270}
                                    title={"Add to group"}
                                    size={"20px"}
                                    onClick={() => props.onAddToGroup!(member.username)}
                                />
                            }
                            {!props.allowManagement || member.role === ProjectRole.PI ? null :
                                <Icon
                                    cursor="pointer"
                                    mr="8px"
                                    ml="8px"
                                    color="red"
                                    name="close"
                                    title={"Remove from project"}
                                    onClick={() => props.onRemoveMember(member.username)} size="20px"
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
                                                projectId: props.project,
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

export default DetailedGroupView;

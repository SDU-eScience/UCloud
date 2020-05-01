import * as React from "react";
import {MainContainer} from "MainContainer/MainContainer";
import {Text, Flex, Icon, Truncate, Link} from "ui-components";
import {defaultAvatar} from "UserSettings/Avataaar";
import * as Pagination from "Pagination";
import {Page} from "Types";
import {useCloudAPI, APICallParameters, useAsyncCommand} from "Authentication/DataHook";
import {
    listGroupMembersRequest,
    removeGroupMemberRequest,
    ListGroupMembersRequestProps,
    projectRoleToString
} from "./api";
import {Client} from "Authentication/HttpClientInstance";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {errorMessageOrDefault} from "UtilityFunctions";
import {GridCardGroup} from "ui-components/Grid";
import {usePromiseKeeper} from "PromiseKeeper";
import {Avatar} from "AvataaarLib";
import {addStandardDialog} from "UtilityComponents";
import {emptyPage} from "DefaultObjects";
import {useParams} from "react-router";
import {buildQueryString} from "Utilities/URIUtilities";
import {ProjectMember, ProjectRole, changeRoleInProject} from "Project";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {useEffect} from "react";
import {useAvatars} from "AvataaarLib/hook";
import styled from "styled-components";
import {BreadCrumbsBase} from "ui-components/Breadcrumbs";

const DetailedGroupView: React.FunctionComponent = props => {
    const locationParams = useParams<{ id: string, group: string }>();
    const id = decodeURIComponent(locationParams.id);
    const group = decodeURIComponent(locationParams.group);

    const [activeGroup, fetchActiveGroup, params] = useCloudAPI<Page<string>, ListGroupMembersRequestProps>(
        listGroupMembersRequest({group, itemsPerPage: 25, page: 0}),
        emptyPage
    );

    const promises = usePromiseKeeper();

    const reload = (): void => {
        fetchActiveGroup({...params});
    };

    useEffect(() => {
        fetchActiveGroup(listGroupMembersRequest({group, itemsPerPage: 25, page: 0}));
    }, [group]);

    if (activeGroup.error) return <MainContainer main={
        <Text fontSize={"24px"}>Could not fetch &apos;{group}&apos;.</Text>
    }/>;

    return <>
        <BreadCrumbsBase>
            <li><span><Link to={`/projects/view/${id}`}>Groups</Link></span></li>
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
                    promptRemoveMember={promptRemoveMember}
                    reload={reload}
                    project={Client.projectId ?? ""}
                    allowManagement
                    allowRoleManagement={false}
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
        const {path, payload} = removeGroupMemberRequest({group, memberUsername: member});
        try {
            await promises.makeCancelable(Client.delete(path!, payload)).promise;
            reload();
        } catch (err) {
            snackbarStore.addFailure(errorMessageOrDefault(err, "Failed to remove member."), false);
        }
    }
}

function membershipSearch(query: string, page: number): APICallParameters {
    return {
        method: "GET",
        path: buildQueryString("/projects/membership/search", {query, itemsPerPage: 100, page})
    };
}

export function GroupMembers(props: Readonly<{
    members: ProjectMember[];
    promptRemoveMember(member: string): void;
    allowManagement: boolean;
    allowRoleManagement: boolean;
    reload: () => void;
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
                            <Truncate width={"160px"} title={member.username}>{member.username}</Truncate>
                            {!props.allowManagement || member.role === ProjectRole.PI ? null :
                                <Icon
                                    cursor="pointer"
                                    mr="8px"
                                    ml="8px"
                                    color="red"
                                    name="close"
                                    onClick={() => props.promptRemoveMember(member.username)} size="20px"
                                />
                            }
                        </Flex>

                        {!props.allowRoleManagement || member.role === ProjectRole.PI ? projectRoleToString(member.role) :
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
                                        props.reload();
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

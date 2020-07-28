import * as React from "react";
import {Text, Link, Truncate, Flex} from "ui-components";
import * as Pagination from "Pagination";
import {useAsyncCommand} from "Authentication/DataHook";
import {
    listGroupMembersRequest,
    removeGroupMemberRequest,
} from "Project";
import {addStandardDialog} from "UtilityComponents";
import {ProjectRole} from "Project";
import {useProjectManagementStatus} from "Project/index";
import {MembersList} from "Project/MembersList";

const GroupView: React.FunctionComponent = () => {
    const {
        projectId, groupId, groupMembers, groupDetails, fetchGroupMembers, groupMembersParams,
        membersPage, projectRole, projectDetails
    } = useProjectManagementStatus();
    const activeGroup = groupMembers;
    const fetchActiveGroup = fetchGroupMembers;
    const [, runCommand] = useAsyncCommand();

    const header = (
        <Flex>
            <Link to={`/project/members/-/${membersPage ?? ""}`}><Text fontSize={"25px"}>Groups</Text></Link>
            <Text mx="8px" fontSize="25px">/</Text>
            <Flex width={"100%"}><Truncate fontSize="25px" width={1}>{groupDetails.data.groupTitle}</Truncate></Flex>
        </Flex>
    );

    if (!groupId || activeGroup.error) {
        return (
            <>
                {header}
                Could not fetch {groupId}!
            </>
        );
    }

    return <>
        {header}
        <Pagination.List
            loading={activeGroup.loading}
            onPageChanged={(newPage, page) => fetchActiveGroup(listGroupMembersRequest({
                group: groupId,
                itemsPerPage: page.itemsPerPage,
                page: newPage
            }))}
            customEmptyPage={(
                <Text>
                    No members in group.
                    You can add members by clicking on the green arrow in the
                    &apos;Members of {projectDetails.data.title}&apos; panel.
                </Text>
            )}
            page={activeGroup.data}
            pageRenderer={page =>
                <MembersList
                    members={page.items.map(it => ({role: ProjectRole.USER, username: it}))}
                    onRemoveMember={promptRemoveMember}
                    projectId={projectId}
                    projectRole={projectRole}
                    allowRoleManagement={false}
                    showRole={false}
                />
            }
        />
    </>;

    function promptRemoveMember(member: string): void {
        addStandardDialog({
            title: "Remove member?",
            message: `Do you want to remove ${member} from the group ${groupDetails.data.groupTitle}?`,
            onConfirm: () => removeMember(member),
            cancelText: "Cancel",
            confirmText: "Remove"
        });
    }

    async function removeMember(member: string): Promise<void> {
        if (groupId === undefined) return;

        await runCommand(removeGroupMemberRequest({group: groupId!, memberUsername: member}));
        fetchGroupMembers(groupMembersParams);
    }
};

export default GroupView;

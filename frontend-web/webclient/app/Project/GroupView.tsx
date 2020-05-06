import * as React from "react";
import {MainContainer} from "MainContainer/MainContainer";
import {Text, Link} from "ui-components";
import * as Pagination from "Pagination";
import {useAsyncCommand} from "Authentication/DataHook";
import {
    listGroupMembersRequest,
    removeGroupMemberRequest,
} from "Project";
import {addStandardDialog} from "UtilityComponents";
import {ProjectRole} from "Project";
import {BreadCrumbsBase} from "ui-components/Breadcrumbs";
import {useProjectManagementStatus} from "Project/View";
import {MembersList} from "Project/MembersList";
import {errorMessageOrDefault} from "UtilityFunctions";

const GroupView: React.FunctionComponent = props => {
    const {
        projectId, group, groupMembers, fetchGroupMembers, groupMembersParams,
        allowManagement
    } = useProjectManagementStatus();
    const activeGroup = groupMembers;
    const fetchActiveGroup = fetchGroupMembers;
    const [, runCommand] = useAsyncCommand();

    const header = (
        <BreadCrumbsBase>
            <li><span><Link to={`/projects/view`}>Groups</Link></span></li>
            <li><span>{group}</span></li>
        </BreadCrumbsBase>
    );

    if (!group || activeGroup.error) {
        return (
            <>
                {header}
                Could not fetch {group}!
            </>
        );
    }

    return <>
        {header}
        <Pagination.List
            loading={activeGroup.loading}
            onPageChanged={(newPage, page) => fetchActiveGroup(listGroupMembersRequest({
                group,
                itemsPerPage: page.itemsPerPage,
                page: newPage
            }))}
            customEmptyPage={(
                <Text>
                    No members in group.
                    You can add members by clicking on the green arrow in the 'Members of {projectId}' panel.
                </Text>
            )}
            page={activeGroup.data}
            pageRenderer={page =>
                <MembersList
                    members={page.items.map(it => ({role: ProjectRole.USER, username: it}))}
                    onRemoveMember={promptRemoveMember}
                    projectMembers={projectId}
                    allowManagement={allowManagement}
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

export default GroupView;

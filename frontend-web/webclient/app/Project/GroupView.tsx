import * as React from "react";
import Spinner from "LoadingIcon/LoadingIcon";
import {Text, Link, Truncate, Flex, Button, Input, Box, Icon} from "ui-components";
import * as Pagination from "Pagination";
import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {
    listGroupMembersRequest, listRepositoryFiles,
    removeGroupMemberRequest,
    updateGroupName,
} from "Project";
import {addStandardDialog, ConfirmCancelButtons, ShakingBox} from "UtilityComponents";
import {ProjectRole} from "Project";
import {useProjectManagementStatus} from "Project/index";
import {MembersList} from "Project/MembersList";
import * as Heading from "ui-components/Heading";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {File} from "Files";
import {emptyPage} from "DefaultObjects";
import {useEffect} from "react";
import {Client} from "Authentication/HttpClientInstance";
import {fileTablePage} from "Utilities/FileUtilities";
import {EmbeddedFileTable} from "Files/FileTable";

const GroupView: React.FunctionComponent = () => {
    const {
        projectId, groupId, groupMembers, groupDetails, fetchGroupMembers, groupMembersParams,
        membersPage, projectRole, projectDetails, fetchGroupDetails, groupDetailsParams
    } = useProjectManagementStatus({isRootComponent: false});
    const activeGroup = groupMembers;
    const renameRef = React.useRef<HTMLInputElement>(null);
    const fetchActiveGroup = fetchGroupMembers;
    const [, runCommand] = useAsyncCommand();
    const [renamingGroup, setRenamingGroup] = React.useState<boolean>(false);

    async function renameGroup(): Promise<void> {
        if (!groupId) return;
        const newGroupName = renameRef.current?.value;
        if (!newGroupName) return;

        const success = await runCommand(updateGroupName({groupId, newGroupName}));

        if (!success) {
            snackbarStore.addFailure("Failed to rename project group", true);
            return;
        }

        fetchGroupDetails(groupDetailsParams);
        setRenamingGroup(false);
        snackbarStore.addSuccess("Project group renamed", true);
    }

    const header = (
        <form onSubmit={e => {
            e.preventDefault();
            renameGroup();
        }}>
            <Flex>
                <Link to={`/project/members/-/${membersPage ?? ""}`}><Text fontSize={"25px"}>Groups</Text></Link>
                <Text mx="8px" fontSize="25px">/</Text>
                {renamingGroup ? (
                    <Flex width={"100%"}>
                        <Input
                            pt="0px"
                            pb="0px"
                            pr="0px"
                            pl="0px"
                            noBorder
                            fontSize={20}
                            maxLength={1024}
                            borderRadius="0px"
                            type="text"
                            width="100%"
                            ref={renameRef}
                            autoFocus
                            defaultValue={groupDetails.data.groupTitle}
                        />
                    </Flex>
                ) : (
                    <Flex width={"100%"}>
                        <Truncate fontSize="25px" width={1}>{groupDetails.data.groupTitle}</Truncate>
                    </Flex>
                )}

                {renamingGroup ? (
                    <Box mt={1}>
                        <ConfirmCancelButtons
                            confirmText="Save"
                            cancelText="Cancel"
                            onConfirm={() => {
                                renameGroup();
                            }}
                            onCancel={() => {
                                setRenamingGroup(false);
                            }}
                        />
                    </Box>
                ) : (
                    <Button onClick={() => setRenamingGroup(true)}>Rename</Button>
                )}
            </Flex>
        </form>
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
                <Text mt={40} textAlign="center">
                    <Heading.h4>No members in group</Heading.h4>
                    You can add members by clicking on the green arrow in the
                    &apos;Members of {projectDetails.data.title}&apos; panel.
                </Text>
            )}
            page={activeGroup.data}
            pageRenderer={page =>
                <>
                    <MembersList
                        members={page.items.map(it => ({role: ProjectRole.USER, username: it}))}
                        onRemoveMember={promptRemoveMember}
                        projectId={projectId}
                        projectRole={projectRole}
                        allowRoleManagement={false}
                        showRole={false}
                    />
                    <GroupPermissions projectId={projectId} groupId={groupId}/>
                </>
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

const GroupPermissions: React.FunctionComponent<{ projectId: string, groupId: string }> = props => {
    const [repoFiles, fetchRepoFiles, repoParams] = useCloudAPI<Page<File>>(
        {noop: true},
        emptyPage
    );

    useEffect(() => {
        fetchRepoFiles(listRepositoryFiles({itemsPerPage: -1, page: 0}));
    }, [props.projectId, props.groupId]);

    const reposWithPermissions = [] as File[];
    outer: for (const repo of repoFiles.data.items) {
        const safeAcl = repo.acl ?? [];
        for (const aclEntry of safeAcl) {
            const hasAccess = aclEntry.rights.length > 0 &&
                typeof aclEntry.entity === "object" &&
                "projectId" in aclEntry.entity &&
                aclEntry.entity.group === props.groupId &&
                aclEntry.entity.projectId === props.projectId;

            if (hasAccess) reposWithPermissions.push(repo);
            continue outer;
        }
    }

    return <Box mt={32}>
        <Heading.h4>File Permissions</Heading.h4>
        {repoFiles.loading ? <Spinner/> : null}
        {reposWithPermissions.length > 0 || repoFiles.loading || repoParams.noop ? null : (
            <>
                <ShakingBox className={"shaking"}>
                    <Heading.h5>This group cannot use any files!</Heading.h5>
                </ShakingBox>
                <ul>
                    <li>A group needs permission to use any files</li>
                    <li>
                        You, as a project administrator, must assign permissions to top-level directories of
                        a project
                    </li>
                    <li>
                        You can assign permissions by clicking on a file and selecting
                        &quot;<Icon name={"properties"} size={16}/> Permissions&quot;
                    </li>
                </ul>

                <Link to={fileTablePage(Client.activeHomeFolder)}>
                    <Button fullWidth>Start assigning permissions</Button>
                </Link>
            </>
        )}
        {reposWithPermissions.length === 0 || repoFiles.loading ? null : (
            <>
                <Heading.h5>This group has access to the following:</Heading.h5>

                <EmbeddedFileTable
                    includeVirtualFolders={false}
                    disableNavigationButtons={true}
                    page={{
                        items: reposWithPermissions,
                        itemsInTotal: reposWithPermissions.length,
                        pageNumber: 0,
                        itemsPerPage: reposWithPermissions.length,
                        pagesInTotal: 1
                    }}
                />
            </>
        )}
    </Box>;
};

export default GroupView;

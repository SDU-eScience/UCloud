import * as React from "react";
import {Text, Link, Truncate, Flex, Button, Input, Box, Icon} from "@/ui-components";
import * as Pagination from "@/Pagination";
import {useCloudCommand} from "@/Authentication/DataHook";
import {
    listGroupMembersRequest,
    removeGroupMemberRequest,
    updateGroupName,
} from "@/Project";
import {ConfirmCancelButtons} from "@/UtilityComponents";
import {ProjectRole} from "@/Project";
import {useProjectManagementStatus} from "@/Project/index";
import {MembersList} from "@/Project/MembersList";
import * as Heading from "@/ui-components/Heading";
import {snackbarStore} from "@/Snackbar/SnackbarStore";

const GroupView: React.FunctionComponent = () => {
    const {
        allowManagement, projectId, groupId, groupMembers, groupDetails, fetchGroupMembers, groupMembersParams,
        membersPage, projectRole, projectDetails, fetchGroupDetails, groupDetailsParams
    } = useProjectManagementStatus({isRootComponent: false});
    const activeGroup = groupMembers;
    const renameRef = React.useRef<HTMLInputElement>(null);
    const fetchActiveGroup = fetchGroupMembers;
    const [, runCommand] = useCloudCommand();
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
                <Link to={`/project/members/-/${membersPage ?? ""}`}>
                    <Button mt="4px" width="42px" height="34px"><Icon rotation={90} name="arrowDown" /></Button>
                </Link>
                <Text mx="8px" fontSize="25px">|</Text>
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

                {allowManagement ?
                    renamingGroup ? (
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
                        )
                    : null}
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
                        onRemoveMember={removeMember}
                        projectId={projectId}
                        projectRole={projectRole}
                        allowRoleManagement={false}
                        showRole={false}
                    />
                </>
            }
        />
    </>;

    async function removeMember(member: string): Promise<void> {
        if (groupId === undefined) return;

        await runCommand(removeGroupMemberRequest({group: groupId!, memberUsername: member}));
        fetchGroupMembers(groupMembersParams);
    }
};

export default GroupView;

import * as React from "react";
import {Text, Link, Truncate, Flex, Button, Input, Box, Icon} from "@/ui-components";
import {useCloudCommand} from "@/Authentication/DataHook";
import {ConfirmCancelButtons} from "@/UtilityComponents";
import {MembersList} from "@/Project/MembersList";
import * as Heading from "@/ui-components/Heading";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import ProjectAPI, {isAdminOrPI, ProjectMember, useGroupIdAndMemberId, useProjectFromParams} from "@/Project/Api";
import {bulkRequestOf} from "@/DefaultObjects";

// UNUSED (Used by unused component)
const GroupView: React.FunctionComponent = () => {
    const renameRef = React.useRef<HTMLInputElement>(null);
    const [, runCommand] = useCloudCommand();
    const [renamingGroup, setRenamingGroup] = React.useState<boolean>(false);

    const [groupId, membersPage] = useGroupIdAndMemberId();

    async function renameGroup(): Promise<void> {
        const newTitle = renameRef.current?.value;
        if (!newTitle) return;
        if (!groupId) return;

        const success = await runCommand(ProjectAPI.renameGroup(bulkRequestOf({group: groupId, newTitle})));

        if (!success) {
            snackbarStore.addFailure("Failed to rename project group", true);
            return;
        }

        setRenamingGroup(false);
        snackbarStore.addSuccess("Project group renamed", true);
    }

    // TODO(Jonas): Is this always correct?
    const {project, projectId, reload} = useProjectFromParams("");
    const allowManagement = isAdminOrPI(project?.status.myRole);

    const group = project?.status.groups?.find(it => it.id === groupId);
    const members = React.useMemo(() => {
        return (group?.status.members?.map(m => project?.status.members?.find(it => it.username === m)).filter(it => it) ?? []) as ProjectMember[]
    }, [project]);


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
                            inputRef={renameRef}
                            autoFocus
                            defaultValue={group?.specification.title}
                        />
                    </Flex>
                ) : (
                    <Flex width={"100%"}>
                        <Truncate fontSize="25px" width={1}>{group?.specification.title}</Truncate>
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

    if (!groupId) {
        return (
            <>
                {header}
                Could not fetch {groupId}!
            </>
        );
    }

    return <>
        {header}
        {group?.status.members?.length !== 0 ? null :
            <Text mt={40} textAlign="center">
                <Heading.h4>No members in group</Heading.h4>
                You can add members by clicking on the green arrow in the
                &apos;Members of {project?.specification.title}&apos; panel.
            </Text>
        }
        <MembersList
            members={members}
            onRemoveMember={removeMember}
            projectId={projectId}
            projectRole={project?.status.myRole!}
            allowRoleManagement={false}
            groups={project?.status.groups!}
            showRole={false}
        />
    </>;

    async function removeMember(member: string): Promise<void> {
        if (!groupId) return;

        await runCommand(ProjectAPI.deleteGroupMember(bulkRequestOf({group: groupId, username: member})));
        reload();
    }
};

export default GroupView;

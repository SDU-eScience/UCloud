import * as React from "react";
import {Text, Link, Truncate, Flex, Button, Input, Box, Icon} from "@/ui-components";
import {useCloudCommand} from "@/Authentication/DataHook";
import {ConfirmCancelButtons} from "@/UtilityComponents";
import {MembersList} from "@/Project/MembersList";
import * as Heading from "@/ui-components/Heading";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import ProjectAPI, {isAdminOrPI, ProjectMember} from "@/Project/Api";
import {useParams} from "react-router";
import {useProject} from "./cache";
import {bulkRequestOf} from "@/DefaultObjects";

const GroupView: React.FunctionComponent = () => {
    const renameRef = React.useRef<HTMLInputElement>(null);
    const [, runCommand] = useCloudCommand();
    const [renamingGroup, setRenamingGroup] = React.useState<boolean>(false);

    const locationParams = useParams<{group: string; member?: string}>();
    const groupId = locationParams.group ? decodeURIComponent(locationParams.group) : undefined;
    const membersPage = locationParams.member ? decodeURIComponent(locationParams.member) : undefined;

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
    const project = useProject();
    const allowManagement = isAdminOrPI(project.fetch().status.myRole);

    const group = project.fetch().status.groups?.find(it => it.id === groupId);
    const members = React.useMemo(() => {
        return (group?.status.members?.map(m => project.fetch().status.members?.find(it => it.username === m)).filter(it => it) ?? []) as ProjectMember[]
    }, [project.fetch()]);


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
                &apos;Members of {project.fetch().specification.title}&apos; panel.
            </Text>
        }
        <MembersList
            members={members}
            onRemoveMember={removeMember}
            projectId={project.fetch().id}
            projectRole={project.fetch().status.myRole!}
            allowRoleManagement={false}
            groups={project.fetch().status.groups!}
            showRole={false}
        />
    </>;

    async function removeMember(member: string): Promise<void> {
        if (!groupId) return;

        await runCommand(ProjectAPI.deleteGroupMember(bulkRequestOf({group: groupId, username: member})));
        project.reload();
    }
};

export default GroupView;

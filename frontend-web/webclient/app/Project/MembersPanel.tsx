import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import * as React from "react";
import {useRef} from "react";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {errorMessageOrDefault, preventDefault} from "@/UtilityFunctions";
import {Button, Flex, Icon, Input, Absolute, Label, Relative, Text, Tooltip, Box} from "@/ui-components";
import {addStandardInputDialog} from "@/UtilityComponents";
import {MembersList} from "@/Project/MembersList";
import * as Pagination from "@/Pagination";
import styled from "styled-components";
import {getCssVar} from "@/Utilities/StyledComponentsUtilities";
import ProjectAPI, {isAdminOrPI, OldProjectRole, ProjectInvite, useGroupIdAndMemberId} from "@/Project/Api";
import {useProject} from "./cache";
import {bulkRequestOf, emptyPageV2} from "@/DefaultObjects";
import {PageV2} from "@/UCloud";

const SearchContainer = styled(Flex)`
    flex-wrap: wrap;
    
    form {
        flex-grow: 1;
        flex-basis: 350px;
        display: flex;
        margin-right: 10px;
        margin-bottom: 10px;
    }
`;

const MembersPanel: React.FunctionComponent = () => {
    const project = useProject();
    const fetchedProject = project.fetch();
    const myRole = fetchedProject.status.myRole ?? OldProjectRole.USER;
    const projectId = fetchedProject.id;
    const allowManagement = isAdminOrPI(myRole);
    const [isLoading, runCommand] = useCloudCommand();
    const [memberSearchQuery, setMemberSearchQuery] = React.useState("")

    const [outgoingInvites, fetchOutgoingInvites] = useCloudAPI<PageV2<ProjectInvite>>(
        ProjectAPI.browseInvites({itemsPerPage: 25, filterType: "OUTGOING"}),
        emptyPageV2
    );

    const [groupId] = useGroupIdAndMemberId();

    const reloadMembers = (): void => {
        fetchOutgoingInvites(ProjectAPI.browseInvites({itemsPerPage: 25, filterType: "OUTGOING"}));
        project.reload();
    };

    const newMemberRef = useRef<HTMLInputElement>(null);

    const onSubmit = async (e: React.FormEvent): Promise<void> => {
        e.preventDefault();
        const inputField = newMemberRef.current!;
        const recipient = inputField.value.trim();
        try {
            await runCommand(ProjectAPI.createInvite(bulkRequestOf({
                recipient
            })));
            inputField.value = "";
            reloadMembers();
        } catch (err) {
            snackbarStore.addFailure(errorMessageOrDefault(err, "Failed adding new member"), false);
        }
    };

    const [showId, setShowId] = React.useState(true);

    return <>
        <SearchContainer>
            {!allowManagement ? null : (
                <form onSubmit={onSubmit}>
                    <Relative left="120px" top="8px">
                        {showId && allowManagement ?
                            <Tooltip tooltipContentWidth="160px" trigger={
                                <Circle>
                                    <Text mt="-3px" ml="5px">?</Text>
                                </Circle>
                            }>
                                <Text color="black" fontSize={12}>Your username can be found at the bottom of the sidebar next to <Icon name="id" />.</Text>
                            </Tooltip> : null}
                    </Relative>
                    <Input
                        id="new-project-member"
                        placeholder="Username"
                        autoComplete="off"
                        disabled={isLoading}
                        ref={newMemberRef}
                        onChange={e => {
                            const shouldShow = e.target.value === "";
                            if (showId !== shouldShow) setShowId(shouldShow);
                        }}
                        rightLabel
                    />
                    <Button
                        asSquare
                        color="green"
                        type="button"
                        title="Bulk invite"
                        onClick={async () => {
                            try {
                                const res = await addStandardInputDialog({
                                    title: "Bulk invite",
                                    type: "textarea",
                                    confirmText: "Invite users",
                                    width: "450px",
                                    help: (<>Enter usernames in the box below. One username per line.</>)
                                });

                                const usernames = res.result
                                    .split("\n")
                                    .map(it => it.trim())
                                    .filter(it => it.length > 0);

                                await runCommand(ProjectAPI.createInvite(bulkRequestOf(...usernames.map(it => ({recipient: it})))));
                                reloadMembers();
                            } catch (ignored) {
                                // Ignored
                            }
                        }}
                    >
                        <Icon name="open" />
                    </Button>
                    <Button attached type={"submit"}>Add</Button>
                </form>
            )}
            <form onSubmit={preventDefault}>
                <Input
                    id="project-member-search"
                    placeholder="Search existing project members..."
                    pr="30px"
                    autoComplete="off"
                    disabled={isLoading}
                    value={memberSearchQuery}
                    onChange={e => setMemberSearchQuery(e.target.value)}
                />
                <Relative>
                    <Absolute right="6px" top="10px">
                        <Label htmlFor="project-member-search">
                            <Icon name="search" size="24" />
                        </Label>
                    </Absolute>
                </Relative>
            </form>
        </SearchContainer>

        <MembersList
            members={fetchedProject.status.members?.filter(it => it.username.includes(memberSearchQuery)) ?? []}
            onRemoveMember={async username => {
                await runCommand(ProjectAPI.deleteMember(bulkRequestOf({
                    username
                })));

                reloadMembers();
            }}
            groups={fetchedProject.status.groups ?? []}
            reload={reloadMembers}
            projectId={projectId}
            projectRole={myRole}
            allowRoleManagement={allowManagement}
            onAddToGroup={!(allowManagement && !!groupId) ? undefined : async username => {
                await runCommand(ProjectAPI.createGroupMember(bulkRequestOf({group: groupId, username})));
                project.reload();
            }}
        />

        {groupId ? null :
            <Pagination.ListV2
                loading={outgoingInvites.loading}
                page={outgoingInvites.data}
                onLoadMore={() => 
                    ProjectAPI.browseInvites({itemsPerPage: outgoingInvites.data.itemsPerPage, next: outgoingInvites.data.next, filterType: "OUTGOING"})
                }
                customEmptyPage={<></>}
                pageRenderer={() => (
                    <MembersList
                        isOutgoingInvites
                        members={outgoingInvites.data.items.map(it => ({
                            username: it.recipient,
                            role: OldProjectRole.USER
                        }))}
                        groups={fetchedProject.status.groups ?? []}
                        onRemoveMember={async (member) => {
                            await runCommand(ProjectAPI.deleteInvite(bulkRequestOf({project: projectId, username: member})));
                            reloadMembers();
                        }}
                        projectRole={myRole}
                        allowRoleManagement={false}
                        projectId={projectId}
                        showRole={false}
                    />
                )}
            />
        }
    </>;
};

const Circle = styled(Box)`
  border-radius: 500px;
  width: 20px;
  height: 20px;
  border: 1px solid ${getCssVar("black")};
  margin: 4px 4px 4px 2px;
  cursor: pointer;
`;

export default MembersPanel;

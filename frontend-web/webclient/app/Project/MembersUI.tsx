import * as React from "react";
import {EventHandler, MouseEvent} from "react";
import {ProjectInvite} from "@/Project/Api";
import {OldProjectRole, Project, ProjectGroup, ProjectMember, ProjectRole} from "@/Project";
import {Spacer} from "@/ui-components/Spacer";
import {Box, Button, Flex, Icon, Input, List, MainContainer, RadioTile, RadioTilesContainer} from "@/ui-components";
import {UtilityBar} from "@/Navigation/UtilityBar";
import {injectStyle} from "@/Unstyled";
import {ListRow} from "@/ui-components/List";
import * as Heading from "@/ui-components/Heading";
import {AvatarForUser} from "@/AvataaarLib/UserAvatar";
import {doNothing} from "@/UtilityFunctions";
import {heroUserPlus} from "@/ui-components/icons";
import {Operations, ShortcutKey} from "@/ui-components/Operation";
import {dialogStore} from "@/Dialog/DialogStore";

export const TwoColumnLayout = injectStyle("two-column-layout", k => `
    ${k} {
        display: flex;
        flex-direction: row;
        flex-wrap: wrap;
        width: 100%;
    }

    ${k} > * {
        flex-basis: 100%;
    }

    @media screen and (min-width: 1200px) {
        ${k} > .left {
            border-right: 2px solid var(--borderColor, #f00);
            height: 100%;
            flex: 1;
            margin-right: 16px;
            padding-right: 16px;
        }

        ${k} > .right {
            flex: 1;
            height: 100%;
            overflow-y: auto;
            overflow-x: hidden;
        }
    }
`);

export const MembersContainer: React.FunctionComponent<{
    onInvite: (username: string) => void;
    onSearch: (newFilter: string) => void;
    onCreateLink: () => void;
    onAddToGroup: (username: string, groupId: string) => void;
    onRemoveFromGroup: (username: string, groupId: string) => void;
    onRemoveFromProject: (username: string) => void;
    onCreateGroup: (groupTitle: string) => void;
    onDeleteGroup: (groupId: string) => void;
    onChangeRole: (username: string, newRole: ProjectRole) => void;
    onRefresh: () => void;

    invitations: ProjectInvite[];
    project: Project;
}> = props => {
    const members: ProjectMember[] = props.project.status.members ?? [];
    const groups: ProjectGroup[] = props.project.status.groups ?? [];

    // event handlers
    function handleInvite(username: string) {
        props.onInvite(username);
    }

    function handleSearch(event: React.SyntheticEvent) {
        const value = (event.target as HTMLInputElement).value;
        props.onSearch(value);
    }

    function handleCreateLink() {

    }

    function handleAddToGroup() {

    }

    function handleRemoveFromGroup() {

    }

    function handleCreateGroup() {

    }

    function handleDeleteGroup(groupId: string) {

    }

    function handleChangeRole (username: string, newRole: ProjectRole) {
        props.onChangeRole(username, newRole);
    }

    function handleRefresh () {

    }

    console.log(props.project);

    const isPi = props.project.status.myRole === OldProjectRole.PI;

    return <MainContainer
        header={<Spacer
            left={<Heading.h3>{props.project.specification.title}</Heading.h3>}
            right={<Flex marginRight={"16px"} height={"26px"}><UtilityBar/></Flex>}
        />}
        main={<div className={TwoColumnLayout}>
            <div className={"left"}>
                <Heading.h3 paddingBottom={"5px"} marginBottom={"8px"}>Members</Heading.h3>
                <Flex gap={"8px"} marginBottom={"8px"}>
                    <Input type="text" placeholder="Add by username ..." />
                    <Button><Icon name={"heroUserPlus"} /></Button>
                    <Button color={"successMain"} onClick={() => {
                        dialogStore.addDialog(
                            <LinkInviteCard />,
                            doNothing,
                        )
                    }}>
                        <Icon name={"heroLink"} />
                    </Button>
                </Flex>
                <Input type="search" placeholder="Search existing project members ..." marginBottom={"8px"} onChange={handleSearch}/>
                <Box height={"calc(100vh - 220px)"} overflowY={"auto"}>
                    <List>
                        {members.map(member => <MemberCard isPi={isPi} member={member} key={member.username} handleChangeRole={handleChangeRole}/>)}
                    </List>
                </Box>
            </div>

            <div className={"right"}>
                <Flex marginBottom={"8px"}>
                    <Heading.h3>Groups</Heading.h3>
                    <Box flexGrow={1}></Box>
                    <Button>New group</Button>
                </Flex>
                <Box height={"calc(100vh - 100px)"} overflowY={"auto"}>
                    <List>
                        {groups.map(group => <GroupCard group={group} key={group.id}/>)}
                    </List>
                </Box>
            </div>
        </div>}
    />;
}

const LinkInviteCard: React.FunctionComponent<{}> = props => {
    return <div>Hello!</div>;
}

const MemberCard: React.FunctionComponent<{
    isPi: boolean;
    member: ProjectMember;
    handleChangeRole: (username: string, newRole: ProjectRole) => void;
}> = props => {
    const role = props.member.role;
    console.log(props.member, role, role === OldProjectRole.ADMIN)
    return <ListRow
        disableSelection
        left={<Flex alignItems={"center"}>
            <AvatarForUser username={props.member.username} height={"35px"} width={"35px"}/>
            {props.member.username}
        </Flex>}
        right={<Flex alignItems={"center"} gap={"8px"}>
            <RadioTilesContainer>
                {(props.isPi || role === OldProjectRole.PI) &&
                    <RadioTile fontSize={"8px"} checked={role === OldProjectRole.PI} height={35} icon={"heroTrophy"}
                               label={"PI"} name={"PI" + props.member.username}
                               onChange={() => props.handleChangeRole(props.member.username, OldProjectRole.PI)}/>
                }
                {(role !== OldProjectRole.PI) &&
                    <>
                        <RadioTile fontSize={"8px"} checked={role === OldProjectRole.ADMIN} height={35}
                                   icon={"heroBriefcase"}
                                   label={"Admin"}
                                   name={"Admin" + props.member.username}
                                   onChange={() => props.handleChangeRole(props.member.username, OldProjectRole.ADMIN)}/>
                        <RadioTile fontSize={"8px"} checked={role === OldProjectRole.USER} height={35}
                                   icon={"heroTrash"}
                                   label={"User"} name={"User" + props.member.username}
                                   onChange={() => props.handleChangeRole(props.member.username, OldProjectRole.USER)}/>
                    </>
                }
            </RadioTilesContainer>
            <Button color={"errorMain"}>Remove</Button>
        </Flex>}
    />;
}

const GroupCard: React.FunctionComponent<{ group: ProjectGroup }> = props => {
    const openFn: React.MutableRefObject<(left: number, top: number) => void> = {current: doNothing};
    const onContextMenu: EventHandler<MouseEvent<never>> = e => {
        e.stopPropagation();
        e.preventDefault();
        openFn.current(e.clientX, e.clientY);
    };

    return <ListRow
        disableSelection

        onContextMenu={onContextMenu}
        left={<>
            {props.group.specification.title}
        </>}
        right={<>
            <Flex alignItems={"center"}>
                <Icon name={"heroUser"}/>
                <Box width={"24px"} textAlign={"center"}>
                    {props.group.status.members?.length}
                </Box>
            </Flex>
            <Operations
                location={"IN_ROW"}
                operations={[
                    {
                        confirm: false,
                        text: "Rename",
                        icon: "rename",
                        enabled: () => true,
                        onClick: doNothing,
                        shortcut: ShortcutKey.U
                    },
                    {
                        confirm: true,
                        color: "errorMain",
                        text: "Delete",
                        icon: "heroTrash",
                        enabled: () => true,
                        onClick: doNothing,
                        shortcut: ShortcutKey.U
                    }
                ]}
                selected={[]}
                extra={null}
                entityNameSingular={"Group"}
                row={42}
                openFnRef={openFn}
                forceEvaluationOnOpen
            />
        </>}
    />;
}
import * as React from "react";
import {EventHandler, MouseEvent, useState} from "react";
import {ProjectInvite} from "@/Project/Api";
import {OldProjectRole, Project, ProjectGroup, ProjectMember, ProjectRole} from "@/Project";
import {Spacer} from "@/ui-components/Spacer";
import {
    Box,
    Button,
    Flex,
    Icon,
    Input,
    Link,
    List,
    MainContainer,
    RadioTile,
    RadioTilesContainer
} from "@/ui-components";
import {UtilityBar} from "@/Navigation/UtilityBar";
import {injectStyle} from "@/Unstyled";
import {ListRow} from "@/ui-components/List";
import * as Heading from "@/ui-components/Heading";
import {AvatarForUser} from "@/AvataaarLib/UserAvatar";
import {doNothing} from "@/UtilityFunctions";
import {heroUserPlus} from "@/ui-components/icons";
import {Operations, ShortcutKey} from "@/ui-components/Operation";
import {dialogStore} from "@/Dialog/DialogStore";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";

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
    onRenameGroup: (groupId: string, newTitle: string) => void;

    invitations: ProjectInvite[];
    project: Project;
    activeGroup: ProjectGroup | null;
}> = props => {
    const members: ProjectMember[] = props.project.status.members ?? [];
    const groups: ProjectGroup[] = props.project.status.groups ?? [];

    const [username, setUsername] = useState("");
    const [newGroup, setNewGroup] = useState(false);
    const [newGroupName, setNewGroupName] = useState("");
    const [renameGroupId, setRenameGroupId] = useState("");
    const [renameGroupName, setRenameGroupName] = useState("");

    // event handlers
    function handleInvite(event: React.SyntheticEvent) {
        event.preventDefault();
        props.onInvite(username);
        setUsername("");
    }

    function handleSearch(event: React.SyntheticEvent) {
        const value = (event.target as HTMLInputElement).value;
        props.onSearch(value);
    }

    function handleCreateLink() {

    }

    function handleAddToGroup(username: string, groupId: string) {
        props.onAddToGroup(username, groupId);
    }

    function handleRemoveFromGroup(username: string, groupId: string) {
        props.onRemoveFromGroup(username, groupId);
    }

    function handleRemoveFromProject(username: string) {
        props.onRemoveFromProject(username);
    }

    function handleCreateGroup(event: React.SyntheticEvent) {
        event.preventDefault();
        props.onCreateGroup(newGroupName);
        setNewGroupName("");
        setNewGroup(false);
    }

    function handleDeleteGroup(groupId: string) {
        props.onDeleteGroup(groupId);
    }

    function handleChangeRole(username: string, newRole: ProjectRole) {
        props.onChangeRole(username, newRole);
    }

    function handleRenameGroup(event: React.SyntheticEvent) {
        event.preventDefault();
        props.onRenameGroup(renameGroupId, renameGroupName);
        setRenameGroupId("");
        setRenameGroupName("");
    }

    useSetRefreshFunction(props.onRefresh);

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
                    <form action="#" onSubmit={handleInvite} style={{display: "flex", gap: "8px", width: "100%"}}>
                        <Input
                            value={username}
                            type="text"
                            placeholder="Add by username ..."
                            style={{flexGrow: 1}}
                            onChange={(event) => {
                                setUsername((event.target as HTMLInputElement).value);
                            }}
                        />
                        <Button type={"submit"}><Icon name={"heroUserPlus"}/></Button>
                    </form>
                    <Button color={"successMain"} onClick={() => {
                        dialogStore.addDialog(
                            <LinkInviteCard/>,
                            doNothing,
                        )
                    }}
                    >
                        <Icon name={"heroLink"}/>
                    </Button>
                </Flex>
                <Input type="search" placeholder="Search existing project members ..." marginBottom={"8px"}
                       onChange={handleSearch}/>
                <Box height={"calc(100vh - 220px)"} overflowY={"auto"}>
                    <List>
                        {members.map(member =>
                            <MemberCard
                                isPi={isPi}
                                member={member}
                                key={member.username}
                                handleChangeRole={handleChangeRole}
                                handleRemoveFromProject={handleRemoveFromProject}
                                handleAddToGroup={handleAddToGroup}
                                activeGroup={props.activeGroup}
                            />)}
                    </List>
                </Box>
            </div>

            <div className={"right"}>
                {props.activeGroup ?
                    <>
                        <Flex gap={"8px"} marginBottom={"8px"}>
                            <Link to={"?"} color={"textPrimary"}><Heading.h3>Groups</Heading.h3></Link>
                            <Heading.h3>/ {props.activeGroup.specification.title}</Heading.h3>
                        </Flex>
                        <List>
                            {props.activeGroup.status.members!.map(member => <ActiveGroupCard
                                member={member}
                                key={member}
                                handleRemoveFromGroup={handleRemoveFromGroup}
                                activeGroup={props.activeGroup!}
                            />)}
                        </List>
                    </> : <>
                        <Flex marginBottom={"8px"}>
                            <Heading.h3>Groups</Heading.h3>
                            <Box flexGrow={1}></Box>
                            {newGroup ? <>
                                    <form
                                        action="#"
                                        style={{display: "flex", gap: "8px"}}
                                        onSubmit={handleCreateGroup}
                                    >
                                        <Input
                                            placeholder={"New group name ..."}
                                            autoFocus={true}
                                            value={newGroupName}
                                            onChange={(event) => {
                                                setNewGroupName((event.target as HTMLInputElement).value);
                                            }}
                                        />
                                        <Button type={"submit"}>Create</Button>
                                        <Button
                                            type={"button"}
                                            color={"errorMain"}
                                            onClick={() => {
                                                setNewGroup(false);
                                            }}>Cancel</Button>
                                    </form>
                                </> :
                                <>
                                    <Button
                                        onClick={() => {
                                            setNewGroup(true);
                                        }}>New group</Button>
                                </>}
                        </Flex>
                        <Box height={"calc(100vh - 100px)"} overflowY={"auto"}>
                            <List>
                                {groups.map(group =>
                                    <GroupCard
                                        group={group}
                                        key={group.id}
                                        handleDeleteGroup={handleDeleteGroup}
                                        isRenaming={renameGroupId === group.id}
                                        setRename={setRenameGroupName}
                                        renameGroup={renameGroupName}
                                        handleRenameGroup={handleRenameGroup}
                                        handleStartRenaming={(groupId) => {
                                            setRenameGroupId(groupId);
                                        }}
                                    />)}
                            </List>
                        </Box>
                    </>
                }
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
    handleRemoveFromProject: (username: string) => void;
    activeGroup: ProjectGroup | null;
    handleAddToGroup: (username: string, groupId: string) => void;
}> = props => {
    const role = props.member.role;
    let isInActiveGroup = false;
    if (props.activeGroup) {
        isInActiveGroup = props.activeGroup.status.members!.some((member) => member === props.member.username);
    }
    return <ListRow
        disableSelection
        left={<Flex alignItems={"center"} padding={"4px 0"}>
            <AvatarForUser username={props.member.username} height={"35px"} width={"35px"}/>
            {props.member.username}
        </Flex>}
        right={<Flex alignItems={"center"} gap={"8px"}>
            {props.activeGroup ? <>
                    <Button
                        disabled={isInActiveGroup}
                        color={"successMain"}
                        onClick={() => props.handleAddToGroup(props.member.username, props.activeGroup!.id)}><Icon
                        name={"heroArrowRight"}/></Button>
                </> :
                <>
                    <RadioTilesContainer>
                        {(props.isPi || role === OldProjectRole.PI) &&
                            <RadioTile fontSize={"6px"} checked={role === OldProjectRole.PI} height={35}
                                       icon={"heroTrophy"}
                                       label={"PI"} name={"PI" + props.member.username}
                                       onChange={() => props.handleChangeRole(props.member.username, OldProjectRole.PI)}/>
                        }
                        {(role !== OldProjectRole.PI) &&
                            <>
                                <RadioTile fontSize={"6px"} checked={role === OldProjectRole.ADMIN} height={35}
                                           icon={"heroBriefcase"}
                                           label={"Admin"}
                                           name={"Admin" + props.member.username}
                                           onChange={() => props.handleChangeRole(props.member.username, OldProjectRole.ADMIN)}/>
                                <RadioTile fontSize={"6px"} checked={role === OldProjectRole.USER} height={35}
                                           icon={"heroUsers"}
                                           label={"User"} name={"User" + props.member.username}
                                           onChange={() => props.handleChangeRole(props.member.username, OldProjectRole.USER)}/>
                            </>
                        }
                    </RadioTilesContainer>
                    <Button color={"errorMain"}
                            onClick={() => props.handleRemoveFromProject(props.member.username)}>Remove</Button>
                </>}

        </Flex>}
    />;
}

const GroupCard: React.FunctionComponent<{
    group: ProjectGroup;
    handleStartRenaming: (groupId: string) => void;
    handleDeleteGroup: (groupId: string) => void;
    isRenaming: boolean;
    renameGroup: string;
    setRename: (newGroupName: string) => void;
    handleRenameGroup: (event: React.SyntheticEvent) => void;
}> = props => {
    const openFn: React.MutableRefObject<(left: number, top: number) => void> = {current: doNothing};
    const onContextMenu: EventHandler<MouseEvent<never>> = e => {
        e.stopPropagation();
        e.preventDefault();
        openFn.current(e.clientX, e.clientY);
    };

    return <ListRow
        disableSelection
        onContextMenu={onContextMenu}
        left={props.isRenaming ?
            <>
                <form
                    action="#"
                    style={{display: "flex", gap: "8px"}}
                    onSubmit={props.handleRenameGroup}
                >
                    <Input
                        value={props.renameGroup}
                        autoFocus={true}
                        onChange={(event) => {
                            props.setRename((event.target as HTMLInputElement).value);
                        }}
                    />
                    <Button type={"submit"}>Create</Button>
                    <Button
                        type={"button"}
                        color={"errorMain"}
                        onClick={() => {
                            props.handleStartRenaming("");
                        }}>Cancel</Button>
                </form>
            </> : <>
                <Link to={"?groupId=" + props.group.id} color={"textPrimary"} marginLeft={"8px"}>
                    {props.group.specification.title}
                </Link>
            </>
        }
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
                        onClick: () => {
                            props.handleStartRenaming(props.group.id);
                            props.setRename(props.group.specification.title);
                        },
                        shortcut: ShortcutKey.U
                    },
                    {
                        confirm: true,
                        color: "errorMain",
                        text: "Delete",
                        icon: "heroTrash",
                        enabled: () => true,
                        onClick: () => props.handleDeleteGroup(props.group.id),
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

const ActiveGroupCard: React.FunctionComponent<{
    member: string;
    handleRemoveFromGroup: (username: string, groupId: string) => void;
    activeGroup: ProjectGroup;
}> = props => {
    return <ListRow
        left={<Flex alignItems={"center"}>
            <AvatarForUser username={props.member} height={"35px"} width={"35px"}/>
            {props.member}
        </Flex>}
        right={<Flex>
            <Button color={"errorMain"}
                    onClick={() => props.handleRemoveFromGroup(props.member, props.activeGroup.id)}>Remove</Button>
        </Flex>}
    />
}
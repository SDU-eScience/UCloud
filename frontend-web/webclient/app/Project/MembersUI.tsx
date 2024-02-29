import * as React from "react";
import {EventHandler, MouseEvent, useState} from "react";
import {ProjectInvite, ProjectInviteLink} from "@/Project/Api";
import {OldProjectRole, Project, ProjectGroup, ProjectMember, ProjectRole} from "@/Project";
import {Spacer} from "@/ui-components/Spacer";
import {
    Box,
    Button,
    Checkbox,
    Flex,
    Icon,
    Input,
    Link,
    List,
    MainContainer,
    RadioTile,
    RadioTilesContainer, Select
} from "@/ui-components";
import {UtilityBar} from "@/Navigation/UtilityBar";
import {injectStyle} from "@/Unstyled";
import {ListRow} from "@/ui-components/List";
import * as Heading from "@/ui-components/Heading";
import {AvatarForUser} from "@/AvataaarLib/UserAvatar";
import {doNothing, timestampUnixMs} from "@/UtilityFunctions";
import {heroUserPlus} from "@/ui-components/icons";
import {Operations, ShortcutKey} from "@/ui-components/Operation";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import ReactModal from "react-modal";
import {defaultModalStyle} from "@/Utilities/ModalUtilities";
import {CardClass} from "@/ui-components/Card";

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
    
    @media screen and (max-width: 1200px) {
        ${k} {
            --newGroupButtonWidth: 111px;
        }
        ${k} > .left {
        
        }
        ${k} > .right {
            margin-top: 32px;
        }
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
    onAddToGroup: (username: string, groupId: string) => void;
    onRemoveFromGroup: (username: string, groupId: string) => void;
    onRemoveFromProject: (username: string) => void;
    onCreateGroup: (groupTitle: string) => void;
    onDeleteGroup: (groupId: string) => void;
    onChangeRole: (username: string, newRole: ProjectRole) => void;
    onRefresh: () => void;
    onRemoveInvite: (username: string) => void;
    onCreateInviteLink: () => void;
    onUpdateLinkRole: (linkId: string, role: ProjectRole) => void;
    onDeleteLink: (linkId: string) => void;
    onLinkGroupsUpdated: (linkId: string, groupIds: string[]) => void;
    onRenameGroup: (groupId: string, newTitle: string) => void;
    inviteLinks: ProjectInviteLink[];
    invitations: ProjectInvite[];
    project: Project;
    activeGroup: ProjectGroup | null;
    onDuplicate: (groupId: string) => void;
    onSortUpdated: (name: string) => void;
    currentSortOption: string;
}> = props => {
    const members: ProjectMember[] = props.project.status.members ?? [];
    const groups: ProjectGroup[] = props.project.status.groups ?? [];

    const [username, setUsername] = useState("");
    const [newGroup, setNewGroup] = useState(false);
    const [newGroupName, setNewGroupName] = useState("");
    const [renameGroupId, setRenameGroupId] = useState("");
    const [renameGroupName, setRenameGroupName] = useState("");
    const [isShowingInviteLinks, setIsShowingInviteLinks] = useState(false);

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

    const isPi = props.project.status.myRole === OldProjectRole.PI;

    return <MainContainer
        header={<Spacer
            left={<Heading.h3>{props.project.specification.title}</Heading.h3>}
            right={<Flex marginRight={"16px"} height={"26px"}><UtilityBar/></Flex>}
        />}
        main={<div className={TwoColumnLayout}>
            <ReactModal
                isOpen={isShowingInviteLinks}
                onRequestClose={() => setIsShowingInviteLinks(false)}
                style={defaultModalStyle}
                shouldCloseOnEsc
                ariaHideApp={false}
                onAfterOpen={() => undefined}
                className={CardClass}
            >
                <LinkInviteCard
                    links={props.inviteLinks}
                    groups={groups}
                    onCreateInviteLink={props.onCreateInviteLink}
                    onDeleteLink={props.onDeleteLink}
                    onLinkGroupsUpdated={props.onLinkGroupsUpdated}
                    onUpdateLinkRole={props.onUpdateLinkRole}
                />
            </ReactModal>

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
                        <Button type={"submit"}
                                disabled={username === ""}><Icon name={"heroUserPlus"}/></Button>
                    </form>
                    <Button color={"successMain"} onClick={() => {
                        setIsShowingInviteLinks(true);
                    }}
                    >
                        <Icon name={"heroLink"}/>
                    </Button>
                </Flex>
                <Flex height={"35px"} gap={"8px"} marginBottom={"8px"}>
                    <Input type="search" placeholder="Search existing project members ..." marginBottom={"8px"}
                           onChange={handleSearch} style={{flexShrink: "1"}}/>
                    <Select
                        name="sort"
                        height={"35px"}
                        width={"111px"}
                        flexShrink={0}
                        flexBasis={111}
                        value={props.currentSortOption}
                        onChange={(event) => props.onSortUpdated(event.target.value)}>
                        <option disabled={true} value={""}>Sort by</option>
                        <option value={"name"}>Name</option>
                        <option value={"role"}>Role</option>
                    </Select>
                </Flex>
                <Box height={"calc(100vh - 220px)"} overflowY={"auto"}>
                    <List>
                        {props.invitations.map((invite) => {
                            return <ListRow key={invite.recipient}
                                            left={
                                                <Flex alignItems={"center"} padding={"4px 0"}>
                                                    <AvatarForUser username={invite.recipient} height={"35px"}
                                                                   width={"35px"}/>
                                                    <div>{invite.recipient} has been invited
                                                        to {invite.projectTitle} by {invite.invitedBy}</div>
                                                </Flex>
                                            }
                                            right={
                                                <Flex alignItems={"center"} padding={"4px 0"}>
                                                    <Box flexGrow={1}/>
                                                    <Button
                                                        width={"88px"}
                                                        color={"errorMain"}
                                                        onClick={() => props.onRemoveInvite(invite.recipient)}
                                                        disabled={props.activeGroup !== null}>Remove</Button>
                                                </Flex>
                                            }/>
                        })}
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
                        <Box height={"calc(100vh - 135px)"} overflowY={"auto"}>
                            <List>
                                {props.activeGroup.status.members!.map(member => <ActiveGroupCard
                                    member={member}
                                    key={member}
                                    handleRemoveFromGroup={handleRemoveFromGroup}
                                    activeGroup={props.activeGroup!}
                                />)}
                            </List>
                        </Box>
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
                                        <Button
                                            type={"submit"}
                                            disabled={newGroupName === ""}>Create</Button>
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
                                        width={"var(--newGroupButtonWidth, auto)"}
                                        onClick={() => {
                                            setNewGroup(true);
                                        }}>New group</Button>
                                </>}
                        </Flex>
                        <Box height={"calc(100vh - 135px)"} overflowY={"auto"}>
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
                                        onDuplicate={props.onDuplicate}
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

const LinkInviteCard: React.FunctionComponent<{
    links: ProjectInviteLink[];
    groups: ProjectGroup[];
    onCreateInviteLink: () => void;
    onUpdateLinkRole: (linkId: string, role: ProjectRole) => void;
    onDeleteLink: (linkId: string) => void;
    onLinkGroupsUpdated: (linkId: string, groupIds: string[]) => void;
}> = props => {
    const [activeLinkId, setActiveLinkId] = useState<string | null>(null);
    const activeLink = props.links.find(it => it.token === activeLinkId);

    function daysLeftToTimestamp(timestamp: number): number {
        return Math.floor((timestamp - timestampUnixMs()) / 1000 / 3600 / 24);
    }

    function inviteLinkFromToken(token: string): string {
        return window.location.origin + "/app/projects/invite/" + token;
    }

    return <>
        {activeLink ? <>
            <Flex gap={"8px"} marginBottom={"8px"} height={"35px"} alignItems={"center"}>
                <Link to={"?"}
                      onClick={() => {
                          setActiveLinkId(null);
                      }}
                      color={"textPrimary"}
                >
                    <Heading.h3>Invite with link</Heading.h3>
                </Link>
                <Heading.h3>/ Settings</Heading.h3>
            </Flex>
            <Flex gap={"8px"} marginBottom={"16px"} alignItems={"center"}>
                <div>Assign new members to role</div>
                <Box flexGrow={1}/>
                <RadioTilesContainer>
                    <RadioTile fontSize={"6px"} checked={activeLink.roleAssignment === OldProjectRole.ADMIN} height={35}
                               icon={"heroBriefcase"}
                               label={"Admin"}
                               name={"Admin"}
                               onChange={() => props.onUpdateLinkRole(activeLink.token, OldProjectRole.ADMIN)}/>
                    <RadioTile fontSize={"6px"} checked={activeLink.roleAssignment === OldProjectRole.USER} height={35}
                               icon={"heroUsers"}
                               label={"User"}
                               name={"User"}
                               onChange={() => props.onUpdateLinkRole(activeLink.token, OldProjectRole.USER)}/>
                </RadioTilesContainer>
            </Flex>
            <Flex gap={"8px"} marginBottom={"8px"} alignItems={"center"}>
                <div>Assign new members to groups</div>
            </Flex>
            <Flex flexDirection={"column"} maxHeight={"264px"} overflowY={"auto"} marginBottom={"5px"}>
                <List>
                    {props.groups.map(group => {
                            if (group.specification.title === "All users") return null;

                        let handleWrapperClick = () => {
                            const isChecked = activeLink.groupAssignment.some(element => element === group.id);
                            const groupAssignment = (activeLink?.groupAssignment ?? []).filter(it => it !== group.id);
                            if (!isChecked) groupAssignment.push(group.id);
                            props.onLinkGroupsUpdated(activeLink.token, groupAssignment);
                        };
                        return <ListRow
                                key={group.id}
                                select={handleWrapperClick}
                                left={<div style={{marginLeft: "8px"}}>{group.specification.title}</div>}
                                right={<>
                                    <Checkbox
                                        checked={activeLink.groupAssignment.some(element => element === group.id)}
                                        handleWrapperClick={handleWrapperClick}
                                    />
                                </>}
                            />
                        }
                    )}
                </List>
            </Flex>
        </> : <>
            <Flex alignItems={"center"} paddingBottom={"8px"}>
                <Heading.h3>Invite with link</Heading.h3>
                <Box flexGrow={1}></Box>
                <Button onClick={() => props.onCreateInviteLink()}>Create link</Button>
            </Flex>

            {props.links.length === 0 ? <>
                <Flex alignItems={"center"} justifyContent={"center"} paddingTop={"32px"}>
                    <Heading.h3>Create a link to invite collaborators to this project</Heading.h3>
                </Flex>
            </> : null}

            {props.links.map(link =>
                <Box key={link.token}>
                    <Flex padding={"8px 0px"} gap={"8px"}>
                        <Input value={inviteLinkFromToken(link.token)} onChange={doNothing} readOnly={true}></Input>
                        <Button onClick={() => {
                            setActiveLinkId(link.token);
                        }} width={"48px"}><Icon name={"heroCog6Tooth"}/></Button>
                        <Button
                            onClick={() => props.onDeleteLink(link.token)}
                            width={"48px"}
                            color={"errorMain"}
                        >
                            <Icon name={"heroTrash"}/>
                        </Button>
                    </Flex>
                    <div style={{marginBottom: "8px", color: "var(--textSecondary)"}}>This link will automatically
                        expire in {daysLeftToTimestamp(link.expires)} days
                    </div>
                </Box>
            )}
        </>}
    </>
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
                            width={"88px"}
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
    onDuplicate: (groupId: string) => void;
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
                    <Button type={"submit"}>Rename</Button>
                    <Button
                        marginRight={"16px"}
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
            <Flex
                alignItems={"center"}
                margin={"7px 0"}>
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
                        icon: "heroPencilSquare",
                        enabled: () => true,
                        onClick: () => {
                            props.handleStartRenaming(props.group.id);
                            props.setRename(props.group.specification.title);
                        },
                        shortcut: ShortcutKey.F
                    },
                    {
                        confirm: false,
                        text: "Duplicate",
                        icon: "heroDocumentDuplicate",
                        enabled: () => true,
                        onClick: () => props.onDuplicate(props.group.id),
                        shortcut: ShortcutKey.I
                    },
                    {
                        confirm: true,
                        color: "errorMain",
                        text: "Delete",
                        icon: "heroTrash",
                        enabled: () => true,
                        onClick: () => props.handleDeleteGroup(props.group.id),
                        shortcut: ShortcutKey.E
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
                    width={"88px"}
                    onClick={() => props.handleRemoveFromGroup(props.member, props.activeGroup.id)}>Remove</Button>
        </Flex>}
    />
}
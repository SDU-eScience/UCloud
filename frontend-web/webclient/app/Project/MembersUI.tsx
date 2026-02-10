import * as React from "react";
import {EventHandler, MouseEvent, useCallback, useEffect, useLayoutEffect, useRef, useState} from "react";
import {ProjectInvite, ProjectInviteLink, projectRoleToStringIcon} from "@/Project/Api";
import {isAdminOrPI, OldProjectRole, Project, ProjectGroup, ProjectMember, ProjectRole} from "@/Project";
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
    RadioTilesContainer,
    Select,
    Truncate
} from "@/ui-components";
import {UtilityBar} from "@/Navigation/UtilityBar";
import {injectStyle} from "@/Unstyled";
import {ListRow} from "@/ui-components/List";
import * as Heading from "@/ui-components/Heading";
import {AvatarForUser} from "@/AvataaarLib/UserAvatar";
import {copyToClipboard, doNothing, timestampUnixMs} from "@/UtilityFunctions";
import {Operations, ShortcutKey} from "@/ui-components/Operation";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import ReactModal from "react-modal";
import {defaultModalStyle} from "@/Utilities/ModalUtilities";
import {CardClass} from "@/ui-components/Card";
import {TooltipV2} from "@/ui-components/Tooltip";
import {Client} from "@/Authentication/HttpClientInstance";
import {addStandardDialog} from "@/UtilityComponents";
import {SimpleRichItem, SimpleRichSelect} from "@/ui-components/RichSelect";
import BaseLink from "@/ui-components/BaseLink";

export const TwoColumnLayout = injectStyle("two-column-layout", k => `
    ${k} {
        display: flex;
        flex-direction: row;
        flex-wrap: wrap;
        width: 100%;
        height: calc(100vh - 72px - 16px - 16px);
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
    onSelectedExpiry: (linkId: string, expiry: number) => void;
}> = props => {
    const members: ProjectMember[] = props.project.status.members ?? [];
    const groups: ProjectGroup[] = props.project.status.groups ?? [];

    const [newGroup, setNewGroup] = useState(false);
    const [newGroupName, setNewGroupName] = useState("");
    const [renameGroupId, setRenameGroupId] = useState("");
    const [renameGroupName, setRenameGroupName] = useState("");
    const [isShowingInviteLinks, setIsShowingInviteLinks] = useState(false);

    // event handlers
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

    return <MainContainer
        header={<Spacer mt="4.5px"
            left={<h3 className="title">{props.project.specification.title}</h3>}
            right={<Flex height={"26px"}><UtilityBar /></Flex>}
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
                    onSelectedExpiry={props.onSelectedExpiry}
                    onInvite={props.onInvite}
                />
            </ReactModal>

            <div className={"left"}>
                <Flex marginBottom={"8px"} justifyContent={"space-between"}>
                    <Heading.h3 paddingBottom={"5px"} marginBottom={"8px"}>Members</Heading.h3>
                    <Button color={"successMain"} onClick={() => {
                        setIsShowingInviteLinks(true);
                    }}
                        width={"111px"}
                        disabled={props.project.status.myRole === OldProjectRole.USER}
                    >
                        <Icon name={"heroLink"} mr={"5px"}/>
                        Invite
                    </Button>
                </Flex>
                <Flex height={"35px"} gap={"8px"} marginBottom={"8px"}>
                    <Input type="search" placeholder="Search existing project members ..." marginBottom={"8px"}
                        onChange={handleSearch} style={{flexShrink: "1"}} />
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
                                            width={"35px"} />
                                        <Flex>
                                            <Truncate
                                                title={invite.recipient}
                                                maxWidth={200}
                                            >
                                                {invite.recipient}
                                            </Truncate>
                                            &nbsp;has been invited by {invite.invitedBy}
                                        </Flex>
                                    </Flex>
                                }
                                right={
                                    <Flex alignItems={"center"} padding={"4px 0"}>
                                        <Box flexGrow={1} />
                                        <Button
                                            width={"88px"}
                                            color={"errorMain"}
                                            onClick={() => props.onRemoveInvite(invite.recipient)}
                                            disabled={props.activeGroup !== null}>Remove</Button>
                                    </Flex>
                                } />
                        })}
                        {members.map(member =>
                            <MemberCard
                                myRole={props.project.status.myRole}
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
                            <BaseLink href={"#"} color={"textPrimary"}>
                                <Heading.h3>Groups</Heading.h3>
                            </BaseLink>
                            <Heading.h3>
                                <Flex gap={"8px"}>
                                    /
                                    <Truncate
                                        title={props.activeGroup.specification.title}
                                        width={400}
                                    >
                                        {props.activeGroup.specification.title}
                                    </Truncate>
                                </Flex>
                            </Heading.h3>
                        </Flex>
                        <Box height={"calc(100vh - 135px)"} overflowY={"auto"}>
                            <List>
                                {props.activeGroup.status.members!.map(member => <ActiveGroupCard
                                    myRole={props.project.status.myRole}
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
                            </> : isAdminOrPI(props.project.status.myRole) ?
                                <Button
                                    width={"var(--newGroupButtonWidth, auto)"}
                                    onClick={() => {
                                        setNewGroup(true);
                                    }}>New group</Button>
                                : null}
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

function useFocusRestoreWithin(
    containerRef: React.RefObject<HTMLElement | null>,
    deps: React.DependencyList
) {
    const lastFocusedRef = React.useRef<HTMLElement | null>(null);

    React.useEffect(() => {
        const el = containerRef.current;
        if (!el) return;

        const onFocusIn = (e: FocusEvent) => {
            const target = e.target as HTMLElement | null;
            if (target && el.contains(target)) lastFocusedRef.current = target;
        };

        document.addEventListener("focusin", onFocusIn);
        return () => document.removeEventListener("focusin", onFocusIn);
    }, [containerRef]);

    React.useLayoutEffect(() => {
        const container = containerRef.current;
        if (!container) return;

        const active = document.activeElement as HTMLElement | null;
        const focusIsInside = !!active && container.contains(active);

        // If focus fell back to body or escaped outside the modal, restore it.
        if (active === document.body || !focusIsInside) {
            const last = lastFocusedRef.current;
            if (last && document.contains(last)) {
                last.focus();
                return;
            }

            const firstFocusable = container.querySelector<HTMLElement>(
                [
                    "button:not([disabled])",
                    "[href]",
                    "input:not([disabled])",
                    "select:not([disabled])",
                    "textarea:not([disabled])",
                    "[tabindex]:not([tabindex='-1'])",
                ].join(",")
            );

            (firstFocusable ?? container).focus();
        }
    }, deps);
}

const LinkInviteCard: React.FunctionComponent<{
    links: ProjectInviteLink[];
    groups: ProjectGroup[];
    onCreateInviteLink: () => void;
    onUpdateLinkRole: (linkId: string, role: ProjectRole) => void;
    onDeleteLink: (linkId: string) => void;
    onLinkGroupsUpdated: (linkId: string, groupIds: string[]) => void;
    onSelectedExpiry: (linkId: string, expiry: number) => void;
    onInvite: (username: string) => void;
}> = props => {
    const [activeLinkId, setActiveLinkId] = useState<string | null>(null);
    const activeLink = props.links.find(it => it.token === activeLinkId);
    const [expiry, setExpiry] = useState<SimpleRichItem>({
        key: "30",
        value: "1 month"
    });
    const [isShowingInviteByUsername, setIsShowingInviteByUsername] = useState(false);
    const [username, setUsername] = useState("");

    function daysLeftToTimestamp(timestamp: number): number {
        return Math.ceil((timestamp - timestampUnixMs()) / 1000 / 3600 / 24);
    }

    function inviteLinkFromToken(token: string): string {
        return window.location.origin + "/app/projects/invite/" + token;
    }

    useEffect(() => {
        if (props.links === undefined || props.links.length === 0) {
            props.onCreateInviteLink();
        }
    }, []);

    const contentRef = useRef<HTMLDivElement>(null);

    useFocusRestoreWithin(contentRef, [
        activeLinkId,
        isShowingInviteByUsername,
        props.links,
        props.groups,
        expiry.key,
    ]);

    const handleInvite = useCallback((event: React.SyntheticEvent) => {
        event.preventDefault();
        props.onInvite(username);
        setUsername("");
        requestAnimationFrame(() => {
            contentRef.current?.focus();
        });
    }, [props.onInvite]);

    useEffect(() => {
        if (activeLink) {
            let daysLeft = daysLeftToTimestamp(activeLink.expires);
            let value = `${daysLeft} days`;
            switch (daysLeft) {
                case 1: value = "1 day"; break;
                case 30: value = "1 month"; break;
                case 60: value = "2 months"; break;
                case 90: value = "3 months"; break;
                case 180: value = "6 months"; break;
            }
            setExpiry({
                key: daysLeft.toString(),
                value
            });
        }
    }, [activeLink]);

    const onSelectExpiry = useCallback((item: SimpleRichItem) => {
        setExpiry(item);

        if (activeLinkId) {
            const days = parseInt(item.key);
            const expiry = timestampUnixMs() + (1000 * 3600 * 24 * days);
            props.onSelectedExpiry(activeLinkId, expiry);
        }
    }, [activeLinkId, props.onSelectedExpiry]);

    return <div ref={contentRef} tabIndex={-1} style={{outline: "0"}}>
        {activeLink ? <>
            <Flex gap={"8px"} marginBottom={"8px"} height={"35px"} alignItems={"center"}>
                <BaseLink href={"#"}
                    onClick={ev => {
                        ev.preventDefault();
                        setActiveLinkId(null);
                    }}
                    color={"textPrimary"}
                >
                    <Heading.h3>Invite with link</Heading.h3>
                </BaseLink>
                <Heading.h3>/ Settings</Heading.h3>
            </Flex>
            <Flex gap={"8px"} marginBottom={"16px"} alignItems={"center"}>
                <div>Assign new members to role</div>
                <Box flexGrow={1} />
                <RadioTilesContainer>
                    <RadioTile fontSize={"6px"} checked={activeLink.roleAssignment === OldProjectRole.ADMIN} height={35}
                        icon={"heroBriefcase"}
                        label={"Admin"}
                        name={"Admin"}
                        onChange={() => props.onUpdateLinkRole(activeLink.token, OldProjectRole.ADMIN)} />
                    <RadioTile fontSize={"6px"} checked={activeLink.roleAssignment === OldProjectRole.USER} height={35}
                        icon={"heroUsers"}
                        label={"User"}
                        name={"User"}
                        onChange={() => props.onUpdateLinkRole(activeLink.token, OldProjectRole.USER)} />
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
                            left={
                            <div style={{marginLeft: "8px"}}>
                                <Truncate
                                    title={group.specification.title}
                                    width={500}
                                >
                                    {group.specification.title}
                                </Truncate>

                            </div>}
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
            <Flex gap={"8px"} marginBottom={"8px"} alignItems={"center"} justifyContent={"space-between"}>
                <div>Set invite link expiration</div>
                <SimpleRichSelect
                    items={
                        [
                            {key: "7", value: "7 days"},
                            {key: "14", value: "14 days"},
                            {key: "30", value: "1 month"},
                            {key: "60", value: "2 months"},
                            {key: "90", value: "3 months"},
                            {key: "180", value: "6 months"},
                        ]
                    }
                    onSelect={onSelectExpiry}
                    selected={expiry}
                    dropdownWidth={"156px"}
                />
            </Flex>
        </> : isShowingInviteByUsername ? <>
            <Flex gap={"8px"} marginBottom={"8px"} height={"35px"} alignItems={"center"}>
                <BaseLink href={"#"}
                      onClick={ev => {
                          ev.preventDefault();
                          setIsShowingInviteByUsername(false);
                      }}
                      color={"textPrimary"}
                >
                    <Heading.h3>Invite </Heading.h3>
                </BaseLink>
                <Heading.h3>/ Invite by username</Heading.h3>
            </Flex>
            <form action="#" onSubmit={handleInvite}>
                <Flex maxHeight={"264px"} overflowY={"auto"} marginBottom={"5px"}>
                    <Input
                        autoFocus={true}
                        placeholder={"Add by username"}
                        value={username}
                        onChange={(event) => {
                            setUsername((event.target as HTMLInputElement).value);
                        }}>
                    </Input>
                    <Button ml={"8px"} disabled={username === ""}>Send</Button>
                </Flex>
            </form>
        </> : <>
            <Flex alignItems={"center"} paddingBottom={"8px"} gap={"8px"}>
                <Heading.h3>Invite with link</Heading.h3>
                <Box flexGrow={1}></Box>
                <Button onClick={() => props.onCreateInviteLink()}>Create link</Button>
                <TooltipV2 tooltip={"Invite by username"}>
                    <Button onClick={() => {setIsShowingInviteByUsername(true)}} width={"48px"}>
                        <Icon name={"heroUserPlus"} />
                    </Button>
                </TooltipV2>
            </Flex>

            {props.links.length === 0 ? <>
                <Flex alignItems={"center"} justifyContent={"center"} paddingTop={"32px"}>
                    <Heading.h3>Create a link to invite collaborators to this project</Heading.h3>
                </Flex>
            </> : null}

            {props.links.map(link =>
                <Box key={link.token}>
                    <Flex padding={"8px 0px"} gap={"8px"}>
                        <Input
                            value={inviteLinkFromToken(link.token)}
                            onChange={doNothing}
                            readOnly={true}
                            style={{cursor:"pointer"}}
                            onClick={() => {
                                copyToClipboard({
                                    value: inviteLinkFromToken(link.token),
                                    message: "Invite link copied to clipboard"
                                })
                            }}
                        >
                        </Input>
                        <Button
                            onClick={() =>
                                copyToClipboard({
                                    value: inviteLinkFromToken(link.token),
                                    message: "Invite link copied to clipboard"
                                })
                            }
                            width={"48px"}
                        >
                            <Icon name={"heroDocumentDuplicate"}/>
                        </Button>
                        <Button
                            onClick={() => {
                                setActiveLinkId(link.token);
                            }}
                            width={"48px"}
                        >
                            <Icon name={"heroCog6Tooth"} />
                        </Button>
                        <Button
                            onClick={() => props.onDeleteLink(link.token)}
                            width={"48px"}
                            color={"errorMain"}
                        >
                            <Icon name={"heroTrash"} />
                        </Button>
                    </Flex>
                    <div style={{marginBottom: "8px", color: "var(--textSecondary)"}}>This link will automatically
                        expire in {daysLeftToTimestamp(link.expires)} days
                    </div>
                </Box>
            )}
        </>}
    </div>
}

const MemberCard: React.FunctionComponent<{
    myRole: OldProjectRole | null | undefined;
    member: ProjectMember;
    handleChangeRole: (username: string, newRole: ProjectRole) => void;
    handleRemoveFromProject: (username: string) => void;
    activeGroup: ProjectGroup | null;
    handleAddToGroup: (username: string, groupId: string) => void;
}> = props => {
    const role = props.member.role;
    const amIPI = props.myRole === OldProjectRole.PI;
    const amIUser = props.myRole === OldProjectRole.USER;
    const isInActiveGroup = props.activeGroup ?
        props.activeGroup.status.members!.some((member) => member === props.member.username) : false;

    const isUserAdminRole = role === OldProjectRole.ADMIN;
    const isUserUserRole = role === OldProjectRole.USER;
    const isUserPIRole = role === OldProjectRole.PI;

    return <ListRow
        disableSelection
        left={<Flex alignItems={"center"} padding={"4px 0"}>
            <AvatarForUser username={props.member.username} height={"35px"} width={"35px"} />
            <Truncate
                title={props.member.username}
                width={350}
            >
                {props.member.username}
            </Truncate>
        </Flex>}
        right={<Flex alignItems={"center"} gap={"8px"}>
            {props.activeGroup && isAdminOrPI(props.myRole) ? <>
                <Button
                    disabled={isInActiveGroup}
                    color={"successMain"}
                    onClick={() => props.handleAddToGroup(props.member.username, props.activeGroup!.id)}><Icon
                        name={"heroArrowRight"} /></Button>
            </> :
                <>
                    <RadioTilesContainer>
                        {amIUser ? <RadioTile
                            fontSize="6px"
                            checked height={35}
                            icon={projectRoleToStringIcon(props.member.role)}
                            label={props.member.role} name={props.member.role + props.member.username}
                            onChange={() => {}}/>
                         : null}


                        {amIPI || role === OldProjectRole.PI && !amIUser ?
                            <RadioTile fontSize={"6px"} checked={role === OldProjectRole.PI} height={35}
                                icon={"heroTrophy"}
                                label={"PI"} name={"PI" + props.member.username}
                                onChange={() => {
                                    if (!amIPI) return;
                                    addStandardDialog({
                                        title: "Transfer PI role to " + props.member.username + "?",
                                        message: "This will transfer the PI role to " + props.member.username + ". You cannot revert this change yourself.",
                                        onConfirm: () => props.handleChangeRole(props.member.username, OldProjectRole.PI),
                                        addToFront: true,
                                        confirmText: "Transfer",
                                        cancelText: "Cancel"
                                    })
                                }} />
                            : null}
                        {role !== OldProjectRole.PI && !amIUser ?
                            <>
                                {isAdminOrPI(props.myRole) ? <RadioTile fontSize={"6px"} checked={isUserAdminRole} height={35}
                                    icon={"heroBriefcase"}
                                    label={"Admin"}
                                    name={"Admin" + props.member.username}
                                    onChange={() => props.handleChangeRole(props.member.username, OldProjectRole.ADMIN)} /> : null}
                                <RadioTile fontSize={"6px"} checked={isUserUserRole} height={35}
                                    icon={"heroUsers"}
                                    label={"User"} name={"User" + props.member.username}
                                    onChange={() => props.handleChangeRole(props.member.username, OldProjectRole.USER)} />
                            </> : null}
                    </RadioTilesContainer>
                    {isUserPIRole ? <TooltipV2 tooltip="PI role must be transfered to remove member">
                        <Button color={"errorMain"} width={"88px"} disabled>Remove</Button>
                    </TooltipV2> :
                        <Button color={"errorMain"}
                            width={"88px"}
                            disabled={!isAdminOrPI(props.myRole) && props.member.username !== Client.username}
                            onClick={() => props.handleRemoveFromProject(props.member.username)}>{Client.activeUsername === props.member.username ? "Leave" : "Remove"}</Button>}
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
    const openFn: React.RefObject<(left: number, top: number) => void> = {current: doNothing};
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
                <Link to={"?groupId=" + props.group.id} color={"textPrimary"}>
                    <Truncate
                        title={props.group.specification.title}
                        width={400}
                        alignItems={"center"}
                        marginLeft={"8px"}
                    >
                        {props.group.specification.title}
                    </Truncate>
                </Link>
            </>
        }
        right={<>
            <Flex
                alignItems={"center"}
                margin={"7px 0"}>
                <Icon name={"heroUser"} />
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
                        confirm: false,
                        text: "Copy ID",
                        icon: "id",
                        enabled: () => Client.userIsAdmin,
                        onClick: () => copyToClipboard({ value: props.group.id, message: "Copied group ID to clipboard" }),
                        shortcut: ShortcutKey.C
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
    myRole: ProjectRole | undefined | null;
    member: string;
    handleRemoveFromGroup: (username: string, groupId: string) => void;
    activeGroup: ProjectGroup;
}> = props => {
    return <ListRow
        left={<Flex alignItems={"center"}>
            <AvatarForUser username={props.member} height={"35px"} width={"35px"} />
            <Truncate
                title={props.member}
                width={400}
            >
                {props.member}
            </Truncate>
        </Flex>}
        right={<Flex>
            {isAdminOrPI(props.myRole) ? <Button color={"errorMain"}
                width={"88px"}
                onClick={() => props.handleRemoveFromGroup(props.member, props.activeGroup.id)}>Remove</Button> : null}
        </Flex>}
    />
}
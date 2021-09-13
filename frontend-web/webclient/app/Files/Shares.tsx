import * as React from "react";
import * as Pagination from "@/Pagination";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {file, PageV2, provider} from "@/UCloud";
import Share = file.orchestrator.Share;
import sharesApi = file.orchestrator.shares;
import filesApi = file.orchestrator.files;
import {useProjectId} from "@/Project";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import MainContainer from "@/MainContainer/MainContainer";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {SidebarPages, useSidebarPage} from "@/ui-components/Sidebar";
import {
    Box,
    Card,
    Flex,
    FtIcon,
    Icon, Link,
    RadioTile,
    RadioTilesContainer,
    SelectableText,
    SelectableTextWrapper, Text
} from "@/ui-components";
import Warning from "@/ui-components/Warning";
import {PageRenderer} from "@/Pagination/PaginationV2";
import styled from "styled-components";
import * as Heading from "@/ui-components/Heading";
import {getFilenameFromPath} from "@/Utilities/FileUtilities";
import {FilePermission} from "./";
import Input from "../ui-components/Input";
import Button from "../ui-components/Button";
import {defaultErrorHandler, extensionFromPath, preventDefault, useDidMount} from "@/UtilityFunctions";
import {AvatarType, defaultAvatar} from "@/UserSettings/Avataaar";
import {UserAvatar} from "@/AvataaarLib/UserAvatar";
import {getCssVar} from "@/Utilities/StyledComponentsUtilities";
import {useAvatars} from "@/AvataaarLib/hook";
import {groupBy} from "@/Utilities/CollectionUtilities";
import {bulkRequestOf} from "@/DefaultObjects";
import UFile = file.orchestrator.UFile;
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import ResourceAclEntry = provider.ResourceAclEntry;
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {buildQueryString} from "@/Utilities/URIUtilities";

const Tab: React.FunctionComponent<{ selected: boolean, onClick: () => void }> = props => {
    return <SelectableText
        mr="1em"
        cursor="pointer"
        selected={props.selected}
        fontSize={3}
        onClick={props.onClick}
    >
        {props.children}
    </SelectableText>
};

const Shares: React.FunctionComponent = () => {
    const [scrollGeneration, setScrollGeneration] = useState(0);
    const [shares, fetchShares] = useCloudAPI<PageV2<Share> | null>({noop: true}, null);
    const projectId = useProjectId();
    const [sharedByMe, setSharedByMe] = useState(false);

    const reload = useCallback(() => {
        fetchShares(sharesApi.browse({itemsPerPage: 100, sharedByMe}));
        setScrollGeneration(prev => prev + 1);
    }, [projectId, sharedByMe]);

    const loadMore = useCallback(() => {
        if (shares.data?.next) {
            fetchShares(sharesApi.browse({next: shares.data?.next, itemsPerPage: 100, sharedByMe}));
        }
    }, [shares, sharedByMe]);

    const pageRenderer = useCallback<PageRenderer<Share>>(items => {
        const sharesByPath = groupBy(items, it => it.path);

        return Object.values(sharesByPath).map(it => (
            <GroupedShareCard key={it[0].path} path={it[0].path} sharedByMe={sharedByMe} shares={it} reload={reload}/>
        ));
    }, [sharedByMe, reload]);

    useEffect(() => {
        reload();
    }, [reload]);

    useTitle("Shares");
    useRefreshFunction(reload);
    useSidebarPage(SidebarPages.Shares);

    let main: JSX.Element;
    if (projectId != null) {
        main = <Box mb={"10px"}>
            <Warning warning={"All shares are personal and not related to your active project."}/>
        </Box>;
    } else if (!shares.data) {
        if (shares.error) {
            main = <>{shares.error.statusCode}: {shares.error.why}</>;
        } else {
            main = <HexSpin/>;
        }
    } else {
        main = <>
            <Pagination.ListV2
                page={shares.data}
                onLoadMore={loadMore}
                infiniteScrollGeneration={scrollGeneration}
                loading={shares.loading}
                pageRenderer={pageRenderer}
                customEmptyPage={
                    <Heading.h3 textAlign={"center"}>
                        No shares
                        <br/>
                        {sharedByMe ?
                            <small>You can create a new share by clicking 'Share' on one of your files.</small> :
                            <small>Files shared will appear here.</small>
                        }
                    </Heading.h3>
                }
            />
        </>;
    }

    return <MainContainer
        main={main}
        headerSize={55}
        header={<SelectableTextWrapper>
            <Tab selected={!sharedByMe} onClick={() => setSharedByMe(false)}>Shared with me</Tab>
            <Tab selected={sharedByMe} onClick={() => setSharedByMe(true)}>Shared by me</Tab>
        </SelectableTextWrapper>}
    />;
};

const FilePermissionTiles: React.FunctionComponent<{
    selected?: FilePermission;
    onChange: (permission: FilePermission) => void;
}> = props => {
    return <form onSubmit={preventDefault}>
        <RadioTilesContainer height={48}>
            <RadioTile
                label={"Read"}
                onChange={() => props.onChange("READ")}
                icon={"search"}
                name={"READ"}
                checked={props.selected === "READ"}
                height={40}
                fontSize={"0.5em"}
            />
            <RadioTile
                label={"Edit"}
                onChange={() => props.onChange("WRITE")}
                icon={"edit"}
                name={"WRITE"}
                checked={props.selected === "WRITE"}
                height={40}
                fontSize={"0.5em"}
            />
        </RadioTilesContainer>
    </form>;
};

export const EmbeddedShareCard: React.FunctionComponent<{
    path: string;
}> = ({path}) => {
    const [shares, fetchShares] = useCloudAPI<PageV2<Share> | null>({noop: true}, null);
    const [scrollGeneration, setScrollGeneration] = useState(0);

    const reload = useCallback(() => {
        fetchShares(sharesApi.browse({itemsPerPage: 100, sharedByMe: true, filterPath: path}));
        setScrollGeneration(prev => prev + 1);
    }, [path]);

    useEffect(reload, [reload]);

    const loadMore = useCallback(() => {
        if (shares.data?.next) {
            fetchShares(sharesApi.browse({
                itemsPerPage: 100, sharedByMe: true, filterPath: path,
                next: shares.data.next
            }));
        }
    }, [shares.data?.next, path]);

    const pageRenderer = useCallback<PageRenderer<Share>>(items => {
        const sharesByPath = groupBy(items, it => it.path);

        const values = Object.values(sharesByPath);
        if (values.length === 0) {
            return <GroupedShareCard key={path} path={path} sharedByMe={true} shares={[]} reload={reload}/>
        } else {
            return values.map(it => (
                <GroupedShareCard key={it[0].path} path={it[0].path} sharedByMe={true} shares={it} reload={reload}/>
            ));
        }
    }, []);

    if (shares.data === null) {
        if (shares.error) {
            return <>{shares.error.statusCode}: {shares.error.why}</>;
        } else {
            return <HexSpin/>;
        }
    } else {
        return <Pagination.ListV2
            page={shares.data}
            onLoadMore={loadMore}
            infiniteScrollGeneration={scrollGeneration}
            loading={shares.loading}
            customEmptyPage={<>{pageRenderer([])}</>}
            pageRenderer={pageRenderer}
        />;
    }
};

interface ShareWithAclEntry {
    sharedWith: string;
    sharedBy?: string;
    aclEntry?: ResourceAclEntry<FilePermission>;
    share?: Share;
}

const GroupedShareCard: React.FunctionComponent<{
    sharedByMe: boolean;
    shares: Share[];
    path: string;
    reload: () => void;
}> = props => {
    const avatars = useAvatars();

    const [commandLoading, invokeCommand] = useCloudCommand();
    const [file, fetchFile] = useCloudAPI<UFile | null>(
        {noop: true},
        null
    );
    const didMount = useDidMount();
    useEffect(() => {
        if (!didMount) {
            fetchFile(filesApi.retrieve({path: props.path, includePermissions: true, allowUnsupportedInclude: true}));
        }
    }, [didMount]);

    const [activePermission, setActivePermission] = useState<FilePermission>("READ");

    const usernameRef = useRef<HTMLInputElement>(null);

    const sharesWithPermissions: ShareWithAclEntry[] = useMemo(() => {
        const result: ShareWithAclEntry[] = [];
        if (file.data !== null && file.data?.permissions?.others) {
            for (const aclEntry of file.data.permissions.others) {
                const entity = aclEntry?.entity;
                if (!entity) continue;
                if (entity.type !== "user") continue;

                result.push({aclEntry, sharedWith: entity.username});
            }
        }
        for (const share of props.shares) {
            let didAdd = false;
            for (const entry of result) {
                const entity = entry.aclEntry?.entity;
                if (!entity) continue;

                if (entity.type === "user" && entity.username === share.sharedWith) {
                    entry.share = share;
                    entry.sharedBy = share.sharedBy;
                    didAdd = true;
                    break;
                }
            }

            if (!didAdd) {
                result.push({share, sharedWith: share.sharedWith, sharedBy: share.sharedBy});
            }
        }

        return result;
    }, [file.data, props.shares]);

    const reload = useCallback(() => {
        fetchFile(filesApi.retrieve({path: props.path, includePermissions: true, allowUnsupportedInclude: true}));
        props.reload();
    }, [props.reload]);

    const createShare = useCallback(async (e?: React.SyntheticEvent) => {
        e?.preventDefault();
        if (commandLoading) return;
        if (!usernameRef.current) return;
        if (!file.data || file.data?.permissions?.others == null) {
            snackbarStore.addFailure("Unable to create a share. Try reloading the page.", false);
            return;
        }

        let normalizedPermissions: FilePermission[];
        switch (activePermission) {
            case "READ":
                normalizedPermissions = ["READ"]
                break;
            case "WRITE":
                normalizedPermissions = ["READ", "WRITE"];
                break;
            default:
                snackbarStore.addFailure("Unable to create a share. Try reloading the page.", false);
                return;
        }

        try {
            await invokeCommand(filesApi.updateAcl(bulkRequestOf({
                path: props.path,
                newAcl: [
                    ...file.data.permissions.others,
                    {entity: {type: "user", username: usernameRef.current.value}, permissions: normalizedPermissions}
                ]
            })), {defaultErrorHandler: false});

            await invokeCommand(sharesApi.create(bulkRequestOf({
                sharedWith: usernameRef.current!.value,
                path: props.path
            })), {defaultErrorHandler: false});
            reload();
            usernameRef.current.value = "";
        } catch (e) {
            defaultErrorHandler(e);
        }
    }, [commandLoading, reload, file.data, activePermission]);

    useEffect(() => {
        const usernames = new Set<string>();
        for (const share of props.shares) {
            usernames.add(share.sharedWith);
            usernames.add(share.sharedBy);
        }
        avatars.updateCache(Array.from(usernames));
    }, [props.shares]);

    if (file.data === null) return <HexSpin/>;

    return <ShareCardBase
        title={
            <>
                <Box ml={"3px"} mr={"10px"}>
                    <FtIcon
                        fileIcon={{
                            type: file.data?.type ?? "DIRECTORY",
                            ext: extensionFromPath(props.path),
                        }}
                        iconHint={file.data?.icon}
                    />
                </Box>

                <Link to={buildQueryString("/files/", {path: props.path})}>
                    <Heading.h4>{getFilenameFromPath(props.path, [])}</Heading.h4>
                </Link>

                <Box ml={"auto"}/>

                {props.sharedByMe ?
                    `${props.shares.length} ${props.shares.length !== 1 ?
                        "collaborators" : "collaborator"}` : null
                }
            </>
        }
        body={
            <>
                {!props.sharedByMe ? null : <>
                    <form onSubmit={createShare}>
                        <Flex mb={"16px"} alignItems={"center"}>
                            <Flex flex={"1 0 auto"} mr={8}>
                                <Flex flex={"1 0 auto"} zIndex={1} mr={8}>
                                    <Input
                                        ref={usernameRef}
                                        disabled={commandLoading}
                                        placeholder={"Username"}
                                    />
                                </Flex>
                                <FilePermissionTiles selected={activePermission} onChange={setActivePermission}/>
                            </Flex>
                            <Box width={"200px"}>
                                <Button fullWidth type={"submit"}>
                                    <Icon name={"share"} size={"1em"} mr={".7em"}/>
                                    Share
                                </Button>
                            </Box>
                        </Flex>
                    </form>
                </>}
                {sharesWithPermissions.map(share => {
                    const usernameForAvatar = props.sharedByMe ? share.sharedWith : share.sharedBy;
                    const avatar = (usernameForAvatar ? avatars.cache[usernameForAvatar] : undefined) ?? defaultAvatar;
                    return (
                        <ShareRow
                            file={file.data!}
                            key={share.sharedWith}
                            shareWithEntry={share}
                            sharedByMe={props.sharedByMe}
                            avatar={avatar}
                            reload={reload}
                        />
                    );
                })}
            </>
        }
    />;
};

export const ShareCardBase: React.FunctionComponent<{
    title?: JSX.Element | string | null;
    body?: JSX.Element | null;
    bottom?: JSX.Element | null;
}> = props => (
    <Card overflow={"hidden"} height={"auto"} width={1} boxShadow={"sm"} borderWidth={1} borderRadius={6} mb={12}>
        <BorderedFlex
            bg="lightGray"
            color="darkGray"
            px={3}
            py={2}
            alignItems="center"
        >
            {props.title}
        </BorderedFlex>
        <Box px={3} pt={3}>
            {props.body}
        </Box>
        {props.bottom}
    </Card>
);

function simplifyPermission(permissions: FilePermission[]): FilePermission | undefined {
    if (permissions.indexOf("WRITE") !== -1) {
        return "WRITE";
    } else if (permissions.indexOf("READ") !== -1) {
        return "READ";
    } else {
        return undefined;
    }
}

function unsimplifyPermissions(permission?: FilePermission): FilePermission[] {
    if (!permission) return [];
    else if (permission === "WRITE") return ["READ", "WRITE"];
    else if (permission === "READ") return ["READ"];
    else return [];
}

const BorderedFlex = styled(Flex)`
  border-radius: 6px 6px 0 0;
`;

const ShareRow: React.FunctionComponent<{
    file: UFile;
    shareWithEntry: ShareWithAclEntry;
    sharedByMe: boolean;
    avatar: AvatarType;
    reload: () => void;
}> = ({shareWithEntry, sharedByMe, avatar, file, reload}) => {
    const {share, aclEntry, sharedWith, sharedBy} = shareWithEntry;
    if (!share && !aclEntry) return null;

    const [commandLoading, invokeCommand] = useCloudCommand();

    const permissions = aclEntry?.permissions;
    const simplePermission = !permissions ? undefined : simplifyPermission(permissions);

    const accept = useCallback(async () => {
        if (commandLoading) return;
        if (!share) return;

        await invokeCommand(sharesApi.approve(bulkRequestOf({
            path: share.path
        })));

        reload();
    }, [commandLoading, share, reload]);

    const revoke = useCallback(async () => {
        if (commandLoading) return;
        if (sharedByMe && aclEntry) {
            const acl = file.permissions?.others;
            if (acl == null) {
                snackbarStore.addFailure("Unable to revoke share. Try reloading the page.", false);
                return;
            }

            const success = await invokeCommand(filesApi.updateAcl(bulkRequestOf({
                path: file.path,
                newAcl: acl.filter(it =>
                    it.entity.type === "user" && it.entity.username !== aclEntry.entity["username"]
                )
            }))) != null;

            if (!success) return;
        }

        await invokeCommand(sharesApi.remove(bulkRequestOf({
            path: file.path,
            sharedWith: sharedByMe ? share?.sharedWith : undefined
        })));

        reload();
    }, [commandLoading, share, aclEntry, sharedByMe, file, reload]);

    const updatePermission = useCallback(async (newPermission: FilePermission) => {
        if (commandLoading) return;
        if (sharedByMe && aclEntry) {
            const acl = file.permissions?.others;
            if (acl == null) {
                snackbarStore.addFailure("Unable to update share. Try reloading the page.", false);
                return;
            }

            const success = await invokeCommand(filesApi.updateAcl(bulkRequestOf({
                path: file.path,
                newAcl: acl.filter(it =>
                    it.entity.type === "user" && it.entity.username !== aclEntry.entity["username"]
                ).concat([{
                    entity: {
                        type: "user",
                        username: aclEntry.entity["username"]
                    },
                    permissions: unsimplifyPermissions(newPermission)
                }])
            }))) != null;

            if (!success) return;
        }

        reload();
    }, [commandLoading, share, aclEntry, sharedByMe, file, reload]);

    return <Flex alignItems={"center"} mb={"16px"}>
        <UserAvatar avatar={avatar} mr={"10px"}/>

        <div>
            <Text bold>{sharedByMe ? sharedWith : sharedBy}</Text>
            {share && share.approved && simplePermission ?
                <><Icon size={20} color={getCssVar("green")} name="check"/> The share has been accepted.</>
                : null
            }
            {share && !share.approved && simplePermission ?
                <>The share has not yet been accepted.</>
                : null
            }
            {!share && simplePermission ?
                <>
                    This share is no longer needed, but the user still has access. You can click the revoke button if
                    this user no longer should have permissions.
                </> : null
            }
            {share && !simplePermission ?
                <>
                    This file might have been deleted by the share owner. You can click the revoke button to remove
                    this entry.
                </> : null
            }
        </div>

        <Box flexGrow={1}/>

        {!sharedByMe && share && !share.approved ? <>
            <Box flexShrink={1}>
                <Button
                    color="red"
                    mx="8px"
                    onClick={revoke}
                >
                    <Icon name="close" size="1em" mr=".7em"/>Reject
                </Button>
                <Button
                    color="green"
                    onClick={accept}
                >
                    <Icon name="check" size="1em" mr=".7em"/>Accept
                </Button>
            </Box>
        </> : null}

        {sharedByMe || (!simplePermission) || (!share) || (!sharedByMe && share.approved) ? <>
            {simplePermission ? <FilePermissionTiles selected={simplePermission} onChange={updatePermission}/> : null}
            <ConfirmationButton
                actionText={"Revoke"}
                icon={"close"}
                onAction={revoke}
                ml={"8px"}
                color={"red"}
                width={"150px"}
            />
        </> : null}
    </Flex>;
};

export default Shares;

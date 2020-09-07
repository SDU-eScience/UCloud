import {APICallState, callAPI, mapCallState, useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import {UserAvatar} from "AvataaarLib/UserAvatar";
import {emptyPage} from "DefaultObjects";
import {File, FileType} from "Files";
import {loadingAction} from "Loading";
import {MainContainer} from "MainContainer/MainContainer";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setActivePage, updatePageTitle} from "Navigation/Redux/StatusActions";
import {PaginationButtons} from "Pagination";
import * as Pagination from "Pagination";
import {usePromiseKeeper} from "PromiseKeeper";
import * as React from "react";
import {useEffect, useRef, useState} from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled from "styled-components";
import {AccessRight, AccessRights, singletonToPage} from "Types";
import {Box, Card, Flex, Icon, SelectableText, SelectableTextWrapper, Text} from "ui-components";
import Button from "ui-components/Button";
import ClickableDropdown from "ui-components/ClickableDropdown";
import * as Heading from "ui-components/Heading";
import Input, {InputLabel} from "ui-components/Input";
import Link from "ui-components/Link";
import {SidebarPages} from "ui-components/Sidebar";
import {Spacer} from "ui-components/Spacer";
import {TextSpan} from "ui-components/Text";
import {AvatarType, defaultAvatar} from "UserSettings/Avataaar";
import {fileTablePage, getFilenameFromPath, getParentPath, isDirectory, statFileQuery} from "Utilities/FileUtilities";
import {addStandardDialog, FileIcon} from "UtilityComponents";
import {defaultErrorHandler, iconFromFilePath} from "UtilityFunctions";
import {ListProps, ListSharesParams, loadAvatars, MinimalShare, SharesByPath, ShareState} from ".";
import {acceptShare, createShare, findShare, listShares, revokeShare, updateShare} from "./index";
import Warning from "ui-components/Warning";
import {Toggle} from "ui-components/Toggle";
import {useProjectStatus} from "Project/cache";
import {getProjectNames} from "Utilities/ProjectUtilities";
import {getCssVar} from "Utilities/StyledComponentsUtilities";

export const List: React.FunctionComponent<ListProps & ListOperations> = props => {
    const initialFetchParams = props.byPath === undefined ?
        listShares({sharedByMe: false, itemsPerPage: 25, page: 0}) : findShare(props.byPath);

    const [avatars, setAvatarParams, avatarParams] = useCloudAPI<{avatars: Record<string, AvatarType>}>(
        loadAvatars({usernames: new Set([])}), {avatars: {}}
    );

    let sharedByMe = false;
    // Start of real data
    const [response, setFetchParams, params] = props.byPath === undefined ?
        useCloudAPI<Page<SharesByPath>>(initialFetchParams, emptyPage) :
        useCloudAPI<SharesByPath | null>(initialFetchParams, null);

    const page = props.byPath === undefined ?
        response as APICallState<Page<SharesByPath>> :
        mapCallState(response as APICallState<SharesByPath | null>, item => singletonToPage(item));
    // End of real data

    // Need dummy data? Remove the comments!
    // const [params, setFetchParams] = useState(listShares({sharedByMe, itemsPerPage: 100, page: 0}));
    // const items = receiveDummyShares(params.parameters!.itemsPerPage, params.parameters!.page);
    // const page: APICallState<Page<SharesByPath>> = {loading: false, data: items, error: undefined};
    // End of dummy data

    if (props.byPath !== undefined && page.data.items.length > 0) {
        sharedByMe = page.data.items[0].sharedByMe;
    } else {
        const listParams = params as APICallParameters<ListSharesParams>;
        if (listParams.parameters !== undefined) {
            sharedByMe = listParams.parameters.sharedByMe;
        }
    }

    React.useEffect(() => {
        props.setGlobalLoading(page.loading);
    }, [page.loading]);

    const refresh = (): void => setFetchParams({...params, reloadId: Math.random()});

    useEffect(() => {
        if (!props.innerComponent) {
            props.setActivePage();
            props.updatePageTitle();
            props.setRefresh(refresh);
        }

        return () => {
            if (!props.innerComponent) {
                // Revert reload action
                props.setGlobalLoading(false);
                props.setRefresh(undefined);
            }
        };
    }, [params]);

    useEffect(() => {
        const usernames: Set<string> = new Set(page.data.items.map(group =>
            group.shares.map(share => group.sharedByMe ? share.sharedWith : group.sharedBy)
        ).reduce((acc, val) => acc.concat(val), []));

        if (JSON.stringify(Array.from(avatarParams.parameters!.usernames)) !== JSON.stringify(Array.from(usernames))) {
            setAvatarParams(loadAvatars({usernames}));
        }
    }, [page]);

    const header = props.byPath !== undefined ? <ProjectSharesWarning /> : (
        <>
            <ProjectSharesWarning />
            <SelectableTextWrapper>
                <SelectableText
                    mr="1em"
                    cursor="pointer"
                    fontSize={3}
                    selected={!sharedByMe}
                    onClick={() => setFetchParams(listShares({sharedByMe: false, itemsPerPage: 25, page: 0}))}
                >
                    Shared with Me
            </SelectableText>
                <SelectableText
                    mr="1em"
                    cursor="pointer"
                    selected={sharedByMe}
                    fontSize={3}
                    onClick={() => setFetchParams(listShares({sharedByMe: true, itemsPerPage: 25, page: 0}))}
                >
                    Shared by Me
                </SelectableText>
            </SelectableTextWrapper>
        </>
    );

    const shares = page.data.items.filter(it => it.sharedByMe === sharedByMe || props.byPath !== undefined);
    const simple = !!props.simple;
    const projectNames = getProjectNames(useProjectStatus());
    const main = (
        <Pagination.List
            loading={page.loading}
            page={page.data}
            customEmptyPage={simple ? (
                <div>
                    No shares for <b>{getFilenameFromPath(props.byPath!, projectNames)}</b>
                </div>
            ) : <NoShares sharedByMe={sharedByMe} />}
            onPageChanged={(pageNumber, {itemsPerPage}) => setFetchParams(listShares({
                page: pageNumber,
                sharedByMe,
                itemsPerPage
            }))}
            pageRenderer={() => (
                <>
                    {props.innerComponent ? header : null}
                    {shares.map(it => (
                        <GroupedShareCardWrapper
                            key={it.path}
                            refresh={refresh}
                            sharedByMe={sharedByMe}
                            avatars={avatars.data.avatars}
                            shareByPath={it}
                            simple={simple}
                        />
                    ))}
                </>
            )}
        />
    );

    if (simple) return main;

    return (
        <MainContainer
            headerSize={55 + (Client.hasActiveProject ? 65 : 0)}
            header={props.innerComponent ? null : header}
            main={main}
            sidebar={null}
        />
    );
};

function GroupedShareCardWrapper(p: {
    shareByPath: SharesByPath;
    simple: boolean;
    refresh: () => void;
    sharedByMe: boolean;
    avatars: Record<string, AvatarType>;
}): JSX.Element {
    const [pageNumber, setPage] = useState(0);
    const pageSize = 5;

    React.useEffect(() => {
        if (pageNumber >= Math.ceil(p.shareByPath.shares.length / pageSize)) {
            setPage(pageNumber - 1);
        }
    });


    return (
        <GroupedShareCard
            simple={p.simple}
            onUpdate={p.refresh}
            groupedShare={p.shareByPath}
            key={p.shareByPath.path}
        >
            {p.shareByPath.shares.slice(pageSize * pageNumber, pageSize * pageNumber + pageSize).map(share => (
                <ShareRow
                    avatar={p.avatars[p.sharedByMe ? share.sharedWith : p.shareByPath.sharedBy] ?? defaultAvatar}
                    path={p.shareByPath.path}
                    simple={p.simple}
                    key={share.sharedWith}
                    sharedBy={p.shareByPath.sharedBy}
                    onUpdate={p.refresh}
                    share={share}
                    sharedByMe={p.sharedByMe}
                />
            ))}
            <PaginationButtons
                totalPages={(Math.ceil(p.shareByPath.shares.length / pageSize))}
                currentPage={pageNumber}
                toPage={setPage}
            />
        </GroupedShareCard>
    );
}

const NoShares = ({sharedByMe}: {sharedByMe: boolean}): JSX.Element => (
    <Heading.h3 textAlign="center">
        No shares
        <br />
        {sharedByMe ?
            <small>You can create a new share by clicking 'Share' on one of your files.</small> :
            <small>Files shared will appear here.</small>
        }
    </Heading.h3>
);


function guessFileType(path: string): FileType {
    if (!path.includes(".")) return "DIRECTORY";
    else return "FILE";
}

interface ListEntryProperties {
    groupedShare: SharesByPath;
    onUpdate: () => void;
    simple: boolean;
}

const GroupedShareCard: React.FunctionComponent<ListEntryProperties> = props => {
    const {groupedShare} = props;

    const [isCreatingShare, setIsCreatingShare] = useState(false);
    const [newShareRights, setNewShareRights] = useState(AccessRights.READ_RIGHTS);
    const [confirmRevokeAll, setConfirmRevokeAll] = useState(false);
    const [fileType, setFileType] = useState<FileType>("DIRECTORY");
    const project = useProjectStatus();
    const projectNames = getProjectNames(project);

    const promises = usePromiseKeeper();
    const newShareUsername = useRef<HTMLInputElement>(null);

    React.useEffect(() => {
        Client.get<File>(statFileQuery(groupedShare.path))
            .then(({response}) => setFileType(response.fileType))
            .catch(() => setFileType(guessFileType(groupedShare.path)));
    }, []);

    const doCreateShare = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
        if (!isCreatingShare) {
            event.preventDefault();

            const username = newShareUsername.current!.value;
            if (username.length === 0) {
                snackbarStore.addFailure("Please fill out the username.", false);
                return;
            }

            try {
                setIsCreatingShare(true);
                await promises.makeCancelable(
                    callAPI(createShare(groupedShare.path, username, newShareRights))
                ).promise;
                newShareUsername.current!.value = "";
                props.onUpdate();
            } catch (e) {
                defaultErrorHandler(e);
            } finally {
                if (!promises.canceledKeeper) setIsCreatingShare(false);
            }
        }
    };

    const [isLoading, sendCommand] = useAsyncCommand();

    const revokeAll = async (): Promise<void> => {
        const revoke = async (): Promise<void> => {
            await Promise.all(groupedShare.shares.filter(it => inCancelableState(it.state))
                .map(async ({sharedWith}) => await sendCommand(revokeShare({path: groupedShare.path, sharedWith}))));
            props.onUpdate();
        };
        if (!props.simple) {
            addStandardDialog({
                title: "Revoke?",
                message: `Remove all shares for ${getFilenameFromPath(groupedShare.path, projectNames)}?`,
                onConfirm: revoke
            });
        } else {
            revoke();
            setConfirmRevokeAll(false);
        }
    };

    const {path} = groupedShare;
    const sharedByMe = groupedShare.sharedByMe;
    const folderLink = (groupedShare.shares[0].state === ShareState.ACCEPTED) || sharedByMe ? (
        <Link
            to={fileTablePage(!sharedByMe && !isDirectory({fileType}) ?
                Client.sharesFolder : isDirectory({fileType}) ?
                    path : getParentPath(path))}
        >
            {getFilenameFromPath(path, projectNames)}
        </Link>
    ) : <Text>{getFilenameFromPath(groupedShare.path, projectNames)}</Text>;
    return (
        <ShareCardBase
            title={<>
                <Box ml="3px" mr="10px">
                    <FileIcon
                        key={fileType}
                        fileIcon={iconFromFilePath(groupedShare.path, fileType)}
                    />
                </Box>
                <Heading.h4> {folderLink} </Heading.h4>
                <Box ml="auto" />
                {groupedShare.sharedByMe ?
                    `${groupedShare.shares.length} ${groupedShare.shares.length > 1 ?
                        "collaborators" : "collaborator"}` : sharePermissionsToText(groupedShare.shares[0].rights)}</>}
            body={<>
                {!groupedShare.sharedByMe || props.simple ? null : (
                    <form onSubmit={doCreateShare}>
                        <Flex mb="16px" alignItems="center">
                            <Flex flex="1 0 auto">
                                <Flex flex="1 0 auto" zIndex={1}>
                                    <Input
                                        disabled={isCreatingShare}
                                        rightLabel
                                        placeholder={"Username"}
                                        ref={newShareUsername}
                                    />
                                </Flex>
                                <InputLabel rightLabel backgroundColor="lightBlue" width="125px">
                                    <ClickableDropdown
                                        left={"-16px"}
                                        chevron
                                        width="125px"
                                        trigger={sharePermissionsToText(newShareRights)}
                                    >
                                        <OptionItem
                                            onClick={() => setNewShareRights(AccessRights.READ_RIGHTS)}
                                            text={CAN_VIEW_TEXT}
                                        />
                                        <OptionItem
                                            onClick={() => setNewShareRights(AccessRights.WRITE_RIGHTS)}
                                            text={CAN_EDIT_TEXT}
                                        />
                                    </ClickableDropdown>
                                </InputLabel>
                            </Flex>
                            <Box ml={"12px"} width="150px">
                                <Button fullWidth type="submit">
                                    <Icon name="share" size="1em" mr=".7em" />
                                    Share
                                </Button>
                            </Box>
                        </Flex>
                    </form>
                )}
                {props.children}
            </>}
            bottom={!(groupedShare.sharedByMe &&
                groupedShare.shares.some(it => inCancelableState(it.state)) &&
                groupedShare.shares.length > 1) ? null : (
                    <Spacer
                        left={<Box />}
                        right={(
                            <Button
                                type="button"
                                onClick={() => props.simple && !confirmRevokeAll ?
                                    setConfirmRevokeAll(true) : revokeAll()}
                                disabled={isLoading}
                                color={confirmRevokeAll ? "red" : "blue"}
                                mb="8px"
                                mr="16px"
                            >
                                {!confirmRevokeAll ? "Remove all" : "Confirm remove all"}
                            </Button>
                        )}
                    />
                )}
        />
    );
};

interface ShareCardBaseProps {
    title: JSX.Element | string | null;
    body: JSX.Element | null;
    bottom: JSX.Element | null;
}

export const ShareCardBase: React.FC<ShareCardBaseProps> = props => (
    <Card overflow="hidden" height="auto" width={1} boxShadow="sm" borderWidth={1} borderRadius={6} mb={12}>
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

const BorderedFlex = styled(Flex)`
    borderRadius: 6px 6px 0px 0px;
`;

function inCancelableState(state: ShareState): boolean {
    return state !== ShareState.UPDATING;
}

export const ShareRow: React.FunctionComponent<{
    share: MinimalShare;
    path: string;
    sharedByMe: boolean;
    sharedBy: string;
    avatar: AvatarType;
    onUpdate: () => void;
    revokeAsIcon?: boolean;
    simple: boolean;
}> = ({share, sharedByMe, onUpdate, path, sharedBy, ...props}) => {
    const [isLoading, sendCommand] = useAsyncCommand();

    const sendCommandAndUpdate = async (call: APICallParameters): Promise<void> => {
        await sendCommand(call);
        onUpdate();
    };

    const doAccept = (): Promise<void> => sendCommandAndUpdate(acceptShare(path));
    const doRevoke = (e: React.MouseEvent<HTMLButtonElement, MouseEvent>): void => {
        e.preventDefault();
        if (props.simple) sendCommandAndUpdate(revokeShare({path, sharedWith: share.sharedWith}));
        else addStandardDialog({
            title: "Revoke?",
            message: "Remove share?",
            onConfirm: () => sendCommandAndUpdate(revokeShare({path, sharedWith: share.sharedWith}))
        });
    };
    const doUpdate = (newRights: AccessRight[]): Promise<void> =>
        sendCommandAndUpdate(updateShare({path, rights: newRights, sharedWith: share.sharedWith}));

    let permissionsBlock: JSX.Element | string | null = null;

    if (share.state === ShareState.UPDATING) {
        permissionsBlock = null;
    } else if (!sharedByMe) {
        if (share.state === ShareState.REQUEST_SENT) {
            permissionsBlock = (
                <Box flexShrink={1}>
                    <Button
                        color="red"
                        mx="8px"
                        onClick={doRevoke}
                    >
                        <Icon name="close" size="1em" mr=".7em" />Reject
                    </Button>
                    <Button
                        color="green"
                        onClick={doAccept}
                    >
                        <Icon name="check" size="1em" mr=".7em" />Accept
                    </Button>
                </Box>
            );
        } else {
            permissionsBlock = (
                <Button
                    color="red"
                    ml="16px"
                    onClick={doRevoke}
                >
                    <Icon name="close" size="1em" mr=".7em" />Reject
                </Button>
            );
        }
    } else {
        const hasWriteRights = share.rights.indexOf(AccessRight.WRITE) !== -1;
        permissionsBlock = (
            <>
                <Flex>
                    <Text mr="5px">View</Text>
                    <Toggle
                        scale={1.3}
                        disabledColor="green"
                        onChange={() => doUpdate(hasWriteRights ? AccessRights.READ_RIGHTS : AccessRights.WRITE_RIGHTS)}
                        checked={hasWriteRights}
                    />
                    <Text ml="5px">Edit</Text>
                </Flex>
                {props.revokeAsIcon ? (
                    <Icon
                        name="close"
                        size="1em"
                        mr=".7em"
                        ml=".7em"
                        color="red"
                        cursor="pointer"
                        onClick={() => sendCommandAndUpdate(revokeShare({
                            path,
                            sharedWith: share.sharedWith
                        }))}
                    />
                ) : (
                        <Button color="red" ml="16px" onClick={doRevoke}>
                            <Icon name="close" size="1em" mr=".7em" />
                            Revoke
                        </Button>
                    )}
            </>
        );
    }

    return (
        <Flex alignItems="center" mb="16px">
            <UserAvatar avatar={props.avatar} mr="10px" />

            <div>
                <Text bold>{sharedByMe ? share.sharedWith : sharedBy}</Text>
                <ShareStateRow state={share.state} />
            </div>

            <Box flexGrow={1} />

            {permissionsBlock}
        </Flex>
    );
};

const OptionItem: React.FunctionComponent<{onClick: () => void; text: string; color?: string}> = props => (
    <Box cursor="pointer" width="auto" ml="-17px" pl="15px" mr="-17px" onClick={props.onClick}>
        <TextSpan color={props.color}>{props.text}</TextSpan>
    </Box>
);

const ShareStateRow: React.FunctionComponent<{state: ShareState}> = props => {
    let body: JSX.Element = <></>;

    switch (props.state) {
        case ShareState.ACCEPTED:
            body = <><Icon size={20} color={getCssVar("green")} name="check" /> The share has been accepted.</>;
            break;
        case ShareState.UPDATING:
            body = <><Icon size={20} color={getCssVar("blue")} name="refresh" /> The share is currently updating.</>;
            break;
        case ShareState.REQUEST_SENT:
            body = <>The share has not yet been accepted.</>;
            break;
    }

    return <Text>{body}</Text>;
};

const CAN_EDIT_TEXT = "Can Edit";
const CAN_VIEW_TEXT = "Can View";

function sharePermissionsToText(rights: AccessRight[]): string {
    if (rights.indexOf(AccessRight.WRITE) !== -1) return CAN_EDIT_TEXT;
    else if (rights.indexOf(AccessRight.READ) !== -1) return CAN_VIEW_TEXT;
    else return "No permissions";
}

function ProjectSharesWarning(): JSX.Element | null {
    if (!Client.hasActiveProject) return null;
    return <Box mb="10px"><Warning warning="All shares are personal and not related to your active project." /></Box>;
}

const receiveDummyShares = (itemsPerPage: number, page: number) => {
    const payload = [...Array(itemsPerPage).keys()].map(i => {
        const extension = Math.floor(Math.random() * 2) === 0 ? ".png" : "";
        const path = `/home/user/SharedItem${i + page * itemsPerPage}${extension}`;
        const sharedBy = "user";
        const sharedByMe = Math.floor(Math.random() * 2) === 0;

        const shares: MinimalShare[] = [...Array(1 + Math.floor(Math.random() * 6))].map(() => {
            const states = Object.keys(ShareState);
            const state = ShareState[states[(Math.floor(Math.random() * states.length))]];
            return {
                state,
                rights: AccessRights.WRITE_RIGHTS,
                path: "/home/foo/" + (Math.random() * 100000000).toString(),
                sharedWith: "user",
                owner: sharedBy,
                createdAt: 0,
                modifiedAt: 0,
            };
        });

        return {
            path,
            sharedBy,
            sharedByMe,
            shares
        };
    });

    return ({
        itemsInTotal: 500,
        itemsPerPage,
        pageNumber: page,
        pagesInTotal: 500 / itemsPerPage,
        items: payload
    });
};

interface ListOperations {
    updatePageTitle: () => void;
    setRefresh: (f?: () => void) => void;
    setActivePage: () => void;
    setGlobalLoading: (loading: boolean) => void;
}

const mapDispatchToProps = (dispatch: Dispatch): ListOperations => ({
    updatePageTitle: () => dispatch(updatePageTitle("Shares")),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
    setActivePage: () => dispatch(setActivePage(SidebarPages.Shares)),
    setGlobalLoading: loading => dispatch(loadingAction(loading)),
});

export default connect(null, mapDispatchToProps)(List);

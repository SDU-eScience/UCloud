import * as React from "react";
import {Cloud} from "Authentication/SDUCloudObject";
import {AccessRight, Page} from "Types";
import {iconFromFilePath} from "UtilityFunctions";
import {getFilenameFromPath} from "Utilities/FileUtilities";
import LoadingIcon from "LoadingIcon/LoadingIcon";
import {setActivePage, updatePageTitle} from "Navigation/Redux/StatusActions";
import {ListProps, Share, ShareId, SharesByPath, ShareState} from ".";
import PromiseKeeper from "PromiseKeeper";
import {Box, Card, Error, Flex, Icon, Text} from "ui-components";
import * as Heading from "ui-components/Heading";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {MainContainer} from "MainContainer/MainContainer";
import {FileIcon} from "UtilityComponents";
import {SidebarPages} from "ui-components/Sidebar";
import {emptyPage, ReduxObject, SharesReduxObject} from "DefaultObjects";
import {fetchSharesByPath, receiveShares, setErrorMessage, setLoading, setShareState} from "./Redux/SharesActions";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {SearchOptions, SelectableText} from "Search/Search";
import {defaultAvatar} from "UserSettings/Avataaar";
import {UserAvatar} from "Navigation/Header";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {TextSpan} from "ui-components/Text";
import {colors} from "ui-components/theme";
import Input, {InputLabel} from "ui-components/Input";
import OutlineButton from "ui-components/OutlineButton";
import {useCloudAPI} from "Shares/DataHook";

function maybeInvoke<T>(payload: T, f?: (t: T) => void) {
    if (f) f(payload)
}

const NewList: React.FunctionComponent = props => {
    const [page, setPageFetch] = useCloudAPI<Page<SharesByPath>>({ method: "GET", path: "/shares" }, emptyPage);

    return <></>;
};

class List extends React.Component<ListProps & SharesReduxObject & SharesOperations> {
    public componentDidMount = () => {
        if (!this.props.innerComponent) {
            this.props.updatePageTitle();
            this.props.setError();
            this.props.setActivePage();
        }
        this.reload();
        if (!this.props.innerComponent) {
            this.props.setRefresh(() => {
                this.reload();
                this.props.setError();
            })
        }
    };

    public componentWillUnmount = () => {
        if (!this.props.innerComponent) {
            this.props.setRefresh();
        }
    };

    private reload(state?: boolean) {
        const {page} = this.props;
        if (!!this.props.byPath) {
            this.props.fetchSharesByPath(this.props.byPath)
        } else {
            const stateToUse = state === undefined ? this.props.sharedByMe : state;
            this.props.retrieveShares(page.pageNumber, page.itemsPerPage, stateToUse);
        }
    }

    private updateShareState(sharedByme: boolean): void {
        this.props.setSharedByMe(sharedByme);
        this.reload(sharedByme);
    }

    public render() {
        let {page, error, sharedByMe} = this.props;
        const header = (
            <SearchOptions>
                <SelectableText
                    mr="1em"
                    cursor="pointer"
                    selected={!sharedByMe}
                    onClick={() => this.updateShareState(false)}
                >
                    Shared with Me
                </SelectableText>

                <SelectableText
                    mr="1em"
                    cursor="pointer"
                    selected={sharedByMe}
                    onClick={() => this.updateShareState(true)}
                >
                    Shared by Me
                </SelectableText>
            </SearchOptions>
        );

        const ShareList = ({shares}: { shares: SharesByPath[] }) => {
            if (shares.length === 0) {
                return <NoShares/>;
            } else {
                return <>{
                    shares.map(it =>
                        <ListEntry
                            groupedShare={it}
                            key={it.path}
                            onAction={it => this.reload()}
                            onError={it => this.props.setError(it)}/>
                    )
                }</>;
            }
        };

        const main = (
            <>
                {this.props.innerComponent ? header : null}

                <Error clearError={() => this.props.setError()} error={error}/>
                {this.props.page === emptyPage && this.props.loading ? <LoadingIcon size={18}/> : null}

                <ShareList shares={page.items.filter(it => it.sharedByMe === sharedByMe)}/>
            </>
        );

        return (
            <MainContainer
                headerSize={55}
                header={this.props.innerComponent ? null : header}
                main={main}
                sidebar={null}
            />
        );
    }
}

const NoShares = () => <Heading.h3 textAlign="center">
    <small>No shares</small>
</Heading.h3>;

enum ShareAction {
    ACCEPT,
    REVOKE,
    RIGHTS,
    NEW_SHARE
}

interface ListEntryProperties {
    groupedShare: SharesByPath
    onError?: (message: string) => void
    onAction?: (action: ShareAction) => void
}

interface ListEntryState {
    isLoading: boolean
    promises: PromiseKeeper
}

interface ShareActions {
    onAccept: (share: Share) => Promise<void>
    onRevoke: (share: Share) => Promise<void>
    onCreateShare: (path: string, username: string, rights: AccessRight[]) => Promise<void>
    onPermissionsUpdated: (share: Share, rights: Set<AccessRight>) => Promise<void>
}

class ListEntry extends React.Component<ListEntryProperties, ListEntryState> implements ShareActions {
    constructor(props: ListEntryProperties) {
        super(props);
        this.state = {
            promises: new PromiseKeeper(),
            isLoading: false
        };
    }

    public render() {
        const {groupedShare} = this.props;
        const shareComponents: JSX.Element[] = groupedShare.shares.map((e) => (
            <ShareRow actions={this} key={e.id} share={e} sharedByMe={groupedShare.sharedByMe}/>
        ));

        return (<ShareCard actions={this} share={groupedShare}>{shareComponents}</ShareCard>);
    }

    async onPermissionsUpdated(share: Share, accessRights: Set<AccessRight>) {
        this.handleActionButton(ShareAction.RIGHTS, async () => {
            await updateShare(share.id, [...accessRights]);
        });
    }

    async onCreateShare(path: string, username: string, rights: AccessRight[]) {
        this.handleActionButton(ShareAction.NEW_SHARE, async () => {
            await createShare(username, path, rights);
        });
    }

    componentWillUnmount() {
        this.state.promises.cancelPromises();
    }

    private async handleActionButton(
        action: ShareAction,
        block: () => Promise<void>
    ) {
        this.setState(() => ({isLoading: true}));
        try {
            await block();
            maybeInvoke(action, this.props.onAction);
        } catch (e) {
            maybeInvoke(e.why ? e.why : "An error has occurred", this.props.onError);
        } finally {
            this.setState(() => ({isLoading: false}))
        }
    }

    async onRevoke(share: Share) {
        this.handleActionButton(ShareAction.REVOKE, async () => {
            await this.state.promises.makeCancelable(revokeShare(share.id)).promise;
        });
    }

    async onAccept(share: Share) {
        this.handleActionButton(ShareAction.ACCEPT, async () => {
            await this.state.promises.makeCancelable(acceptShare(share.id)).promise;
        });
    }
}

const ShareCard: React.FunctionComponent<{ share: SharesByPath, actions: ShareActions }> = ({share, actions, ...props}) => (
    <Card width="100%" p="10px 10px 10px 10px" mt="10px" mb="10px" height="auto">
        <Heading.h4 mb={"10px"}>
            <Flex alignItems={"center"}>
                <Box ml="3px" mr="10px">
                    <FileIcon fileIcon={iconFromFilePath(share.path, fileTypeGuess(share), Cloud.homeFolder)}/>
                </Box>
                {getFilenameFromPath(share.path)}
                <Box ml="auto"/>
                {share.shares.length} {share.shares.length > 1 ? "collaborators" : "collaborator"}
            </Flex>
        </Heading.h4>

        {!share.sharedByMe ? null :
            <Flex mb={"16px"} alignItems={"center"}>
                <Box flexGrow={1}>
                    <Flex>
                        <InputLabel leftLabel>To:</InputLabel>
                        <Box flexGrow={1}><Input leftLabel placeholder={"Username"}/></Box>
                    </Flex>
                </Box>

                <Box ml={"5px"}>
                    <ClickableDropdown
                        trigger={
                            <OutlineButton>
                                Can View <Icon name="chevronDown" size=".7em" ml=".7em"/>
                            </OutlineButton>
                        }
                    >
                        <OptionItem onClick={() => 42} text={CAN_VIEW_TEXT} />
                        <OptionItem onClick={() => 42} text={CAN_EDIT_TEXT} />
                    </ClickableDropdown>
                </Box>
            </Flex>
        }

        {props.children}
    </Card>
);

const OptionItem: React.FunctionComponent<{ onClick: () => void, text: string, color?: string }> = (props) => (
    <Box cursor="pointer" width="auto" ml="-17px" pl="15px" mr="-17px" onClick={() => props.onClick()}>
        <TextSpan color={props.color}>{props.text}</TextSpan>
    </Box>
);

const ShareStateRow: React.FunctionComponent<{ state: ShareState }> = props => {
    let body: JSX.Element = <></>;

    switch (props.state) {
        case ShareState.ACCEPTED:
            body = <><Icon size={20} color={colors.green} name={"check"}/> The share has been accepted.</>;
            break;
        case ShareState.FAILURE:
            body = <><Icon size={20} color={colors.red} name={"close"}/> An error has occurred. The share is no longer
                valid.</>;
            break;
        case ShareState.UPDATING:
            body = <><Icon size={20} color={colors.blue} name={"refresh"}/> The share is currently updating.</>;
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

const ShareRow: React.FunctionComponent<{ share: Share, sharedByMe: boolean, actions: ShareActions }> =
    ({share, sharedByMe, actions, ...props}) => (
        <Flex alignItems={"center"} mb={"16px"}>
            <UserAvatar avatar={defaultAvatar} mr={10}/>

            <Box>
                <Text bold>{share.sharedWith}</Text>
                <ShareStateRow state={share.state}/>
            </Box>

            <Box ml={"auto"}/>

            {share.state == ShareState.UPDATING ? sharePermissionsToText(share.rights) :
                <ClickableDropdown
                    left={"-66%"}
                    trigger={sharePermissionsToText(share.rights)}
                    chevron
                >
                    {!sharedByMe || share.state === ShareState.FAILURE ? null :
                        <>
                            <OptionItem
                                onClick={() => actions.onPermissionsUpdated(share, new Set([AccessRight.READ]))}
                                text={CAN_VIEW_TEXT} />

                            <OptionItem
                                onClick={() => actions.onPermissionsUpdated(share,
                                    new Set([AccessRight.READ, AccessRight.WRITE]))}
                                text={CAN_EDIT_TEXT} />
                        </>
                    }

                    <OptionItem
                        onClick={() => actions.onRevoke(share)}
                        color={colors.red}
                        text={"Remove Access"}
                    />
                </ClickableDropdown>
            }
        </Flex>
    );

interface FileTypeGuess {
    path: string
}

function fileTypeGuess({path}: FileTypeGuess) {
    const hasExtension = path.split("/").pop()!.includes(".");
    return hasExtension ? "FILE" : "DIRECTORY";
}

const acceptShare = async (shareId: ShareId): Promise<any> =>
    (await Cloud.post(`/shares/accept/${encodeURIComponent(shareId)}`)).response;

const revokeShare = async (shareId: ShareId): Promise<any> =>
    (await Cloud.post(`/shares/revoke/${encodeURIComponent(shareId)}`)).response;

const createShare = async (user: string, path: string, rights: AccessRight[]): Promise<{ id: ShareId }> =>
    (await Cloud.put(`/shares/`, {sharedWith: user, path, rights})).response;

const updateShare = async (id: ShareId, rights: AccessRight[]): Promise<any> =>
    (await Cloud.post(`/shares/`, {id, rights})).response;

interface SharesOperations {
    updatePageTitle: () => void
    setActivePage: () => void
    setError: (error?: string) => void
    retrieveShares: (page: number, itemsPerPage: number, sharedByMe: boolean) => void
    receiveShares: (page: Page<SharesByPath>) => void
    setSharedByMe: (sharedByMe: boolean) => void
    fetchSharesByPath: (path: string) => void
    setRefresh: (refresh?: () => void) => void
}

const receiveDummyShares = (itemsPerPage: number, page: number) => {
    const payload = [...Array(itemsPerPage).keys()].map(i => {
        const extension = Math.floor(Math.random() * 2) === 0 ? ".png" : "";
        const path = `/home/user/SharedItem${i + page * itemsPerPage}${extension}`;
        const sharedBy = "user";
        const sharedByMe = Math.floor(Math.random() * 2) === 0;

        const shares: Share[] = [...Array(1 + Math.floor(Math.random() * 6))].map(j => {
            const states = Object.keys(ShareState);
            const state = ShareState[states[(Math.floor(Math.random() * states.length))]];
            return {
                state,
                rights: [AccessRight.READ, AccessRight.WRITE],
                id: (Math.random() * 100000000).toString(),
                sharedWith: "user"
            }
        });

        return {
            path,
            sharedBy,
            sharedByMe,
            shares
        }
    });

    return receiveShares({
        itemsInTotal: 500,
        itemsPerPage: itemsPerPage,
        pageNumber: page,
        pagesInTotal: 500 / itemsPerPage,
        items: payload
    });
};

const mapDispatchToProps = (dispatch: Dispatch): SharesOperations => ({
    updatePageTitle: () => dispatch(updatePageTitle("Shares")),
    setActivePage: () => dispatch(setActivePage(SidebarPages.Shares)),
    setError: error => dispatch(setErrorMessage(error)),
    retrieveShares: async (page, itemsPerPage, byState) => {
        dispatch(setLoading(true));
        // dispatch(await retrieveShares(page, itemsPerPage, byState));
        dispatch(receiveDummyShares(itemsPerPage, page));
        dispatch(setLoading(false));
    },
    receiveShares: page => dispatch(receiveShares(page)),
    fetchSharesByPath: async path => {
        dispatch(await fetchSharesByPath(path))
    },
    setSharedByMe: sharedByMe => dispatch(setShareState(sharedByMe)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh))
});

const mapStateToProps = ({shares}: ReduxObject): SharesReduxObject => shares;

export default connect(mapStateToProps, mapDispatchToProps)(List);
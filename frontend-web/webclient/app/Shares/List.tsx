import * as React from "react";
import {useState} from "react";
import {Cloud} from "Authentication/SDUCloudObject";
import {AccessRight, Page} from "Types";
import {iconFromFilePath, prettierString, shareSwal} from "UtilityFunctions";
import {getFilenameFromPath} from "Utilities/FileUtilities";
import LoadingIcon from "LoadingIcon/LoadingIcon";
import {setActivePage, updatePageTitle} from "Navigation/Redux/StatusActions";
import {ListProps, Share, ShareId, SharesByPath, ShareState} from ".";
import PromiseKeeper from "PromiseKeeper";
import {Box, Button, ButtonGroup, Card, Error, Flex, Icon, Text} from "ui-components";
import * as Heading from "ui-components/Heading";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {MainContainer} from "MainContainer/MainContainer";
import {FileIcon} from "UtilityComponents";
import {SidebarPages} from "ui-components/Sidebar";
import {emptyPage, ReduxObject, SharesReduxObject} from "DefaultObjects";
import {
    fetchSharesByPath,
    receiveShares,
    retrieveShares,
    setErrorMessage,
    setLoading,
    setShareState
} from "./Redux/SharesActions";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {SearchOptions, SelectableText} from "Search/Search";
import {defaultAvatar} from "UserSettings/Avataaar";
import {UserAvatar} from "Navigation/Header";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {TextSpan} from "ui-components/Text";
import {colors} from "ui-components/theme";
import Input, {InputLabel} from "ui-components/Input";
import OutlineButton from "ui-components/OutlineButton";

function maybeInvoke<T>(payload: T, f?: (t: T) => void) {
    if (f) f(payload)
}

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

    private reload(state?: ShareState) {
        const {page} = this.props;
        if (!!this.props.byPath) {
            this.props.fetchSharesByPath(this.props.byPath)
        } else {
            this.props.retrieveShares(page.pageNumber, page.itemsPerPage, state || this.props.byState);
        }
    }

    private updateShareState(byState: ShareState): void {
        this.props.setShareState(byState);
        this.reload(byState);
    }

    public render() {
        let {page, error, byState} = this.props;
        const header = (
            <SearchOptions>
                <SelectableText
                    mr="1em"
                    cursor="pointer"
                    selected={byState === ShareState.REQUEST_SENT}
                    onClick={() => this.updateShareState(ShareState.REQUEST_SENT)}
                >
                    {prettierString(ShareState.REQUEST_SENT)}
                </SelectableText>
                <SelectableText
                    mr="1em"
                    cursor="pointer"
                    selected={byState === ShareState.ACCEPTED}
                    onClick={() => this.updateShareState(ShareState.ACCEPTED)}
                >
                    {prettierString(ShareState.ACCEPTED)}
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
                            onAccepted={e => this.onEntryAction()}
                            onRevoked={e => this.onEntryAction()}
                            onShared={e => this.onEntryAction()}
                            onRights={e => this.onEntryAction()}
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

                <Heading.h3>Shared with Me</Heading.h3>
                <ShareList shares={page.items.filter(it => !it.sharedByMe)}/>

                <Heading.h3 pt="25px">Shared by Me</Heading.h3>
                <ShareList shares={page.items.filter(it => it.sharedByMe)}/>
            </>
        );

        return (
            <MainContainer
                header={this.props.innerComponent ? null : header}
                main={main}
                sidebar={null}
            />
        );
    }

    onEntryAction() {
        this.reload();
    }
}

const NoShares = () => <Heading.h3 textAlign="center">
    <small>No shares</small>
</Heading.h3>;

interface ListEntryProperties {
    groupedShare: SharesByPath
    onError?: (message: string) => void
    onAccepted?: (share: Share) => void
    onRevoked?: (share: Share) => void
    onRights?: (share: Share) => void
    onShared?: (shareId: ShareId) => void
}

interface ListEntryState {
    isLoading: boolean
    promises: PromiseKeeper
}

class ListEntry extends React.Component<ListEntryProperties, ListEntryState> {
    constructor(props: ListEntryProperties) {
        super(props);
        this.state = {
            promises: new PromiseKeeper(),
            isLoading: false
        };
    }

    public render() {
        let share = this.props.groupedShare;

        if (!share.sharedByMe) {
            return this.renderSharedWithMe();
        } else {
            return this.renderSharedByMe();
        }
    }

    renderSharedWithMe(): JSX.Element {
        let {groupedShare} = this.props;
        let {isLoading} = this.state;
        let actualShare = groupedShare.shares[0]; // TODO Is this always true?
        let hasBeenShared = actualShare.state == ShareState.ACCEPTED;

        const message = hasBeenShared ?
            `${groupedShare.sharedBy} has shared ${groupedShare.path} with you.` :
            `${groupedShare.sharedBy} wishes to share ${groupedShare.path} with you.`;

        const icon = iconFromFilePath(groupedShare.path, fileTypeGuess(groupedShare), Cloud.homeFolder);
        return (
            <></>
            // <Card width="100%" mt="10px" mb="10px" height="auto" p="10px 10px 10px 10px">
            //     <Heading.h4>
            //         <Flex>
            //             <Box ml="3px" mr="3px"><FileIcon fileIcon={icon} /></Box>{getFilenameFromPath(groupedShare.path)}
            //             <Box ml="auto" />
            //             <AccessRightsDisplay floated disabled rights={actualShare.rights} />
            //         </Flex>
            //     </Heading.h4>
            //     <Text color="text">Shared by {groupedShare.sharedBy}</Text>
            //     <Text mt="4px" mb="4px">{message}</Text>
            //     <Flex>
            //         <Box ml="auto" />
            //         {groupedShare.shares[0].state !== ShareState.REQUEST_SENT ?
            //             <Button color="red" disabled={isLoading} onClick={() => this.onRevoke(actualShare)}>Remove</Button>
            //             : null}
            //     </Flex>
            //     {!hasBeenShared ? (
            //         <>
            //             <Divider />
            //             <Flex>
            //                 <Box ml="auto" />
            //                 <ButtonGroup width="200px">
            //                     <Button disabled={isLoading} color="green" onClick={() => this.onAccept(actualShare)}>Accept</Button>
            //                     <Button disabled={isLoading} color="red" onClick={() => this.onRevoke(actualShare)}>Reject</Button>
            //                 </ButtonGroup>
            //             </Flex>
            //         </>)
            //         : null
            //     }
            // </Card>
        );
    }

    renderSharedByMe(): JSX.Element {
        const {groupedShare} = this.props;
        const {isLoading} = this.state;
        const shareComponents: JSX.Element[] = groupedShare.shares.map((e) => (
            <ShareRow key={e.id} share={e}/>
            /*
            <Box key={e.id}>
                <Spacer m="5px 5px 5px 5px"
                    left={<Box>
                        {e.sharedWith}
                        <AccessRightsDisplay
                            read={e.rights.some(it => it === "READ")}
                            write={e.rights.some(it => it === "WRITE")}
                            rights={e.rights}
                            onAcceptChange={aR => this.onAcceptChange(e, aR)}
                        />
                    </Box>}
                    right={
                        <Button
                            height="40px"
                            color="red"
                            disabled={isLoading}
                            size="mini"
                            onClick={() => this.onRevoke(e)}
                        >
                            <Flex justifyContent="center" alignItems="center">
                                <Icon size={18} name="close" />
                                <Text ml="3px">Revoke</Text>
                            </Flex>
                        </Button>}
                />
                {i !== length - 1 ? <Divider /> : null}
            </Box>
                    */
        ));

        return (
            <ShareCard share={groupedShare}>
                {shareComponents}
            </ShareCard>
        );
    }

    async onAcceptChange(share: Share, accessRights: Set<AccessRight>) {
        this.setState(() => ({isLoading: true}));
        try {
            const it = await updateShare(share.id, [...accessRights]);
            maybeInvoke(it.id, this.props.onRights);
        } catch (e) {
            if (!e.isCanceled)
                maybeInvoke(e.response.why ? e.response.why : "An error has occured", this.props.onError)
        } finally {
            this.setState(() => ({isLoading: false}));
        }
    }

    private async onCreateShare(path: string) {
        this.setState(() => ({isLoading: true}));
        const {dismiss, value} = await shareSwal();
        if (dismiss) {
            this.setState(() => ({isLoading: false}));
            return;
        }
        const rights: AccessRight[] = [];
        (document.getElementById("read") as HTMLInputElement).checked ? rights.push(AccessRight.READ) : null;
        (document.getElementById("read_edit") as HTMLInputElement).checked ? rights.push(AccessRight.READ, AccessRight.WRITE) : null;
        try {
            const it = await createShare(value, path, rights);
            maybeInvoke(it.id, this.props.onShared);
        } catch (e) {
            if (!e.isCanceled) {
                maybeInvoke(e.response.why ? e.response.why : "An error has occured", this.props.onError);
            }
        } finally {
            this.setState(() => ({isLoading: false}))
        }
    }

    componentWillUnmount() {
        this.state.promises.cancelPromises();
    }

    async handleActionButton(
        share: Share,
        action: (id: ShareId) => Promise<any>,
        callback?: (share: Share) => void
    ) {
        this.setState(() => ({isLoading: true}));
        try {
            await this.state.promises.makeCancelable(action(share.id)).promise;
            maybeInvoke(share, callback);
        } catch (e) {
            maybeInvoke(e.why ? e.why : "An error has occured", this.props.onError);
        } finally {
            this.setState(() => ({isLoading: false}))
        }
    }

    async onRevoke(share: Share) {
        return this.handleActionButton(share, it => revokeShare(it), this.props.onRevoked);
    }

    async onAccept(share: Share) {
        return this.handleActionButton(share, it => acceptShare(it), this.props.onAccepted);
    }
}

const ShareCard: React.FunctionComponent<{ share: SharesByPath }> = ({share, ...props}) => (
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

        <Flex mb={"16px"} alignItems={"center"}>
            <Box flexGrow={1}>
                <Flex>
                    <InputLabel leftLabel>To:</InputLabel>
                    <Box flexGrow={1}><Input leftLabel placeholder={"Username"}/></Box>
                </Flex>
            </Box>

            <Box ml={"5px"}>
                <ClickableDropdown
                    trigger={<OutlineButton>Can View <Icon name="chevronDown" size=".7em" ml=".7em"/></OutlineButton>}
                >
                    <OptionItem onClick={() => 42}>Can View</OptionItem>
                    <OptionItem onClick={() => 42}>Can Edit</OptionItem>
                </ClickableDropdown>
            </Box>
        </Flex>

        {props.children}
    </Card>
);

const OptionItem: React.FunctionComponent<{ onClick: () => void }> = (props) => (
    <Box cursor="pointer" width="auto" ml="-17px" pl="15px" mr="-17px" onClick={() => props.onClick()}>
        {props.children}
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

const ShareRow: React.FunctionComponent<{ share: Share }> = ({share, ...props}) => (
    <Flex alignItems={"center"} mb={"16px"}>
        <UserAvatar avatar={defaultAvatar} mr={10}/>

        <Box>
            <Text bold>{share.sharedWith}</Text>
            <ShareStateRow state={share.state}/>
        </Box>

        <Box ml={"auto"}/>

        <ClickableDropdown
            left={"-66%"}
            trigger={
                <>{new Set(share.rights).has(AccessRight.WRITE) ? "Can Edit" : "Can View"}</>
            }
            chevron
            options={[{text: "Can View", value: AccessRight.READ}, {
                text: "Can Edit",
                value: AccessRight.WRITE
            }]}
        >
            <OptionItem onClick={() => {
            }}>Can View</OptionItem>
            <OptionItem onClick={() => {
            }}>Can Edit</OptionItem>
            <OptionItem onClick={() => {
            }}><TextSpan color={colors.red}>Revoke access</TextSpan></OptionItem>
        </ClickableDropdown>
    </Flex>
);

interface AccessRightsDisplayProps {
    disabled?: boolean
    rights?: AccessRight[]

    read?: boolean
    write?: boolean
    floated?: boolean

    onAcceptChange?: (aR: Set<AccessRight>) => void
}

const AccessRightsDisplay = (props: AccessRightsDisplayProps) => {
    const {floated} = props;
    const [accessRights, setAccessRights] = useState(new Set(props.rights));
    const {READ, WRITE} = AccessRight;
    const read = accessRights.has(READ);
    const write = accessRights.has(WRITE);
    const pendingChanges = accessRights.has(READ) !== props.read || accessRights.has(WRITE) !== props.write;
    return (
        <Flex>
            {floated ? <Box ml="auto"/> : null}
            <ButtonGroup width="280px">
                <Button
                    disabled={props.disabled}
                    color={read ? "green" : "lightGray"}
                    textColor={read ? "white" : "gray"}
                    onClick={() => {
                        read ? accessRights.delete(READ) : accessRights.add(READ);
                        setAccessRights(new Set(accessRights));
                    }}
                >
                    <Flex alignItems="center" justifyContent="center">
                        <Icon size={18} name="search"/>
                        <Text ml="5px">View</Text>
                    </Flex>
                </Button>
                <Button
                    disabled={props.disabled}
                    color={write ? "green" : "lightGray"}
                    textColor={write ? "white" : "gray"}
                    onClick={() => {
                        write ? accessRights.delete(WRITE) : accessRights.add(WRITE), accessRights;
                        setAccessRights(new Set(accessRights))
                    }}
                >
                    <Flex alignItems="center" justifyContent="center">
                        <Icon size={18} name="rename"/>
                        <Text ml="5px">Edit</Text>
                    </Flex>
                </Button>
            </ButtonGroup>
            {pendingChanges && props.onAcceptChange ? <ButtonGroup width="150px">
                <Button ml="0.5em" onClick={() => props.onAcceptChange!(accessRights)}>
                    <Flex alignItems="center" justifyContent="center">
                        <Icon size={18} name="check"/>
                        <Text ml="5px">Confirm</Text>
                    </Flex>
                </Button>
            </ButtonGroup> : null}
        </Flex>
    );
};

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
    retrieveShares: (page: number, itemsPerPage: number, byState?: ShareState) => void
    receiveShares: (page: Page<SharesByPath>) => void
    setShareState: (s: ShareState) => void
    fetchSharesByPath: (path: string) => void
    setRefresh: (refresh?: () => void) => void
}

const receiveDummyShares = (itemsPerPage: number, page: number) => {
    const payload = [...Array(itemsPerPage).keys()].map(i => {
        const extension = Math.floor(Math.random() * 2) === 0 ? ".png" : "";
        const path = `/home/user/SharedItem${i}${extension}`;
        const sharedBy = "user";
        const sharedByMe = Math.floor(Math.random() * 2) === 0;

        const shares: Share[] = [...Array(1 + Math.floor(Math.random() * 6))].map(j => {
            const states = Object.keys(ShareState);
            const state = ShareState[states[(Math.floor(Math.random() * states.length))]];
            return {
                state,
                rights: [AccessRight.READ, AccessRight.WRITE],
                id: (Math.random() | 1).toString(),
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
    setShareState: shareState => dispatch(setShareState(shareState)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh))
});

const mapStateToProps = ({shares}: ReduxObject): SharesReduxObject => shares;

export default connect(mapStateToProps, mapDispatchToProps)(List);
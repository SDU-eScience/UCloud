import * as React from "react";
import { Cloud } from "Authentication/SDUCloudObject";
import { AccessRight, Page, AccessRightValues } from "Types";
import { shareSwal, prettierString, iconFromFilePath } from "UtilityFunctions";
import { getFilenameFromPath } from "Utilities/FileUtilities";
import LoadingIcon from "LoadingIcon/LoadingIcon";
import { updatePageTitle, setActivePage } from "Navigation/Redux/StatusActions";
import { SharesByPath, Share, ShareId, ListProps, ShareState } from ".";
import PromiseKeeper from "PromiseKeeper";
import { Error, ButtonGroup, Text, Box, Flex, LoadingButton, Card, Divider, Button, Icon } from "ui-components";
import * as Heading from "ui-components/Heading";
import { connect } from "react-redux";
import { Dispatch } from "redux";
import ClickableDropdown from "ui-components/ClickableDropdown";
import { TextSpan } from "ui-components/Text";
import { MainContainer } from "MainContainer/MainContainer";
import { FileIcon } from "UtilityComponents";
import { SidebarPages } from "ui-components/Sidebar";
import { Spacer } from "ui-components/Spacer";
import { ReduxObject, SharesReduxObject } from "DefaultObjects";
import { retrieveShares, receiveShares, setErrorMessage, setShareState, fetchSharesByPath } from "./Redux/SharesActions";
import { setRefreshFunction } from "Navigation/Redux/HeaderActions";
import { useState } from "react";

class List extends React.Component<ListProps & SharesReduxObject & SharesOperations, { a: string }> {
    constructor(props: Readonly<ListProps & SharesReduxObject & SharesOperations>) {
        super(props);
        // FIXME potentially move following to a parent component
        if (!props.innerComponent) {
            this.props.updatePageTitle();
            props.setActivePage();
        }
    }

    public componentDidMount = () => {
        this.reload();
        if (!this.props.innerComponent) { this.props.setRefresh(() => this.reload()) }
    }

    public componentWillUnmount = () => {
        if (!this.props.innerComponent) { this.props.setRefresh(); }
    }

    private reload(state?: ShareState) {
        const { page } = this.props;
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
        let { page, error, byState } = this.props;
        const noSharesWith = page.items.filter(it => !it.sharedByMe).length === 0;
        const noSharesBy = page.items.filter(it => it.sharedByMe).length === 0;
        const header = (
            <Flex>
                <Box ml="auto" />
                <ClickableDropdown
                    chevron
                    width="150px"
                    trigger={<TextSpan>Shares where: {prettierString(byState)}</TextSpan>}
                    options={Object.keys(ShareState).map(v => ({ text: prettierString(v), value: v }))}
                    onChange={(it: ShareState) => this.updateShareState(it)}
                />
            </Flex>
        );
        const main = (
            <>
                {this.props.innerComponent ? header : null}
                <Error clearError={() => this.props.setError()} error={error} />
                {this.props.loading ? <LoadingIcon size={18} /> : null}
                <Heading.h3>Shared with Me</Heading.h3>
                {
                    noSharesWith ? <NoShares /> : page.items.filter(it => !it.sharedByMe).map(it =>
                        <ListEntry
                            groupedShare={it}
                            key={it.path}
                            onAccepted={e => this.onEntryAction()}
                            onRejected={e => this.onEntryAction()}
                            onRevoked={e => this.onEntryAction()}
                            onShared={e => this.onEntryAction()}
                            onRights={e => this.onEntryAction()}
                            onError={it => this.props.setError(it)} />
                    )
                }
                <Heading.h3 pt="25px">Shared by Me</Heading.h3>
                {
                    noSharesBy ? <NoShares /> : page.items.filter(it => it.sharedByMe).map(it =>
                        <ListEntry
                            groupedShare={it}
                            key={it.path}
                            onAccepted={e => this.onEntryAction()}
                            onRejected={e => this.onEntryAction()}
                            onRevoked={e => this.onEntryAction()}
                            onShared={e => this.onEntryAction()}
                            onRights={e => this.onEntryAction()}
                            onError={it => this.props.setError(it)} />
                    )
                }
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

const NoShares = () => <Heading.h3 textAlign="center"><small>No shares</small></Heading.h3>

interface ListEntryProperties {
    groupedShare: SharesByPath
    onError?: (message: string) => void
    onAccepted?: (share: Share) => void
    onRejected?: (share: Share) => void
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
        let { groupedShare } = this.props;
        let { isLoading } = this.state;
        let actualShare = groupedShare.shares[0]; // TODO Is this always true?
        let hasBeenShared = actualShare.state == ShareState.ACCEPTED;

        const message = hasBeenShared ?
            `${groupedShare.sharedBy} has shared ${groupedShare.path} with you.` :
            `${groupedShare.sharedBy} wishes to share ${groupedShare.path} with you.`

        const icon = iconFromFilePath(groupedShare.path, fileTypeGuess(groupedShare), Cloud.homeFolder);
        return (
            <Card width="100%" mt="10px" mb="10px" height="auto" p="10px 10px 10px 10px">
                <Heading.h4>
                    <Flex>
                        <Box ml="3px" mr="3px"><FileIcon fileIcon={icon} /></Box>{getFilenameFromPath(groupedShare.path)}
                        <Box ml="auto" />
                        <AccessRightsDisplay floated disabled rights={actualShare.rights} />
                    </Flex>
                </Heading.h4>
                <Text color="text">Shared by {groupedShare.sharedBy}</Text>
                <Text mt="4px" mb="4px">{message}</Text>
                <Flex>
                    <Box ml="auto" />
                    {groupedShare.shares[0].state !== ShareState.REQUEST_SENT ?
                        <LoadingButton color="red" disabled={isLoading} loading={isLoading} content="Remove" onClick={() => this.onRevoke(actualShare)} />
                        : null}
                </Flex>
                {!hasBeenShared ? (
                    <>
                        <Divider />
                        <Flex>
                            <Box ml="auto" />
                            <ButtonGroup width="200px">
                                <LoadingButton loading={isLoading} disabled={isLoading} color="green" onClick={() => this.onAccept(actualShare)} hovercolor="darkGreen" content="Accept" />
                                <LoadingButton loading={isLoading} disabled={isLoading} color="red" onClick={() => this.onReject(actualShare)} hovercolor="darkRed" content="Reject" />
                            </ButtonGroup>
                        </Flex>
                    </>)
                    : null
                }
            </Card>
        );
    }

    renderSharedByMe(): JSX.Element {
        const { groupedShare } = this.props;
        const { isLoading } = this.state;
        const shareComponents: JSX.Element[] = groupedShare.shares.map((e, i, { length }) => (
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
                        <LoadingButton
                            height="40px"
                            width="auto"
                            color="red"
                            disabled={isLoading}
                            loading={isLoading}
                            size="mini"
                            onClick={() => this.onRevoke(e)}
                        >
                            <Flex justifyContent="center" alignItems="center">
                                <Icon size={18} name="close" />
                                <Text ml="3px">Revoke</Text>
                            </Flex>
                        </LoadingButton>}
                />
                {i !== length - 1 ? <Divider /> : null}
            </Box>
        ));

        const icon = iconFromFilePath(groupedShare.path, fileTypeGuess(groupedShare), Cloud.homeFolder);

        return (
            <Card width="100%" p="10px 10px 10px 10px" mt="10px" mb="10px" height="auto">
                <Heading.h4>
                    <Flex>
                        <Box ml="3px" mr="3px"><FileIcon fileIcon={icon} /></Box>
                        {getFilenameFromPath(groupedShare.path)}
                    </Flex>
                    <TextSpan fontSize={1} ml="0.8em" mr="0.8em" color="text">Shared with {groupedShare.shares.length} collaborators</TextSpan>
                    <LoadingButton
                        size={"small"}
                        content="Share this with another user"
                        color="green"
                        disabled={isLoading}
                        loading={isLoading}
                        onClick={() => this.onCreateShare(groupedShare.path)}
                    />
                </Heading.h4>
                {shareComponents}
            </Card >
        );
    }

    maybeInvoke<T>(payload: T, f?: (t: T) => void) {
        if (f) f(payload)
    }

    async onAcceptChange(share: Share, accessRights: Set<AccessRight>) {
        this.setState(() => ({ isLoading: true }));
        await updateShare(share.id, [...accessRights])
            .then(it => this.maybeInvoke(it.id, this.props.onRights))
            .catch(e => {
                if (!e.isCanceled) {
                    this.maybeInvoke(e.response.why ? e.response.why : "An error has occured", this.props.onError)
                }
            });
        this.setState(() => ({ isLoading: false }));
    }

    onCreateShare(path: string) {
        this.setState(() => ({ isLoading: true }));
        shareSwal().then(async ({ dismiss, value }) => {
            if (dismiss) { this.setState(() => ({ isLoading: false })); return; }
            const rights: AccessRight[] = [];
            // FIXME Fix immediately when SweetAlert allows forms
            (document.getElementById("read") as HTMLInputElement).checked ? rights.push(AccessRight.READ) : null;
            (document.getElementById("read_edit") as HTMLInputElement).checked ? rights.push(AccessRight.WRITE) : null;
            await createShare(value, path, rights)
                .then(it => this.maybeInvoke(it.id, this.props.onShared))
                .catch(e => {
                    if (!e.isCanceled) {
                        this.maybeInvoke(e.response.why ? e.response.why : "An error has occured", this.props.onError);
                    }
                })
            this.setState(() => ({ isLoading: false }))
        })
    }

    componentWillUnmount() {
        this.state.promises.cancelPromises();
    }

    async onRevoke(share: Share) {
        this.setState(() => ({ isLoading: true }));

        await this.state.promises.makeCancelable(revokeShare(share.id))
            .promise
            .then(() => (this.maybeInvoke(share, this.props.onRevoked)))
            .catch(e => (this.maybeInvoke(e.why ? e.why : "An error has occured", this.props.onError)));
        this.setState(() => ({ isLoading: false }))
    }

    async onAccept(share: Share) {
        this.setState(() => ({ isLoading: true }))

        await this.state.promises.makeCancelable(acceptShare(share.id))
            .promise
            .then(() => this.maybeInvoke(share, this.props.onAccepted))
            .catch(e => this.maybeInvoke(e.why ? e.why : "An error has occured", this.props.onError))
        this.setState(() => ({ isLoading: false }));
    }

    async onReject(share: Share) {
        this.setState(() => ({ isLoading: true }))

        await this.state.promises.makeCancelable(revokeShare(share.id))
            .promise
            .then(() => this.maybeInvoke(share, this.props.onRejected))
            .catch(e => this.maybeInvoke(e.why ? e.why : "An error has occured", this.props.onError));
        this.setState(() => ({ isLoading: false }))
    }
}

interface AccessRightsDisplayProps {
    disabled?: boolean
    rights?: AccessRightValues[]

    read?: boolean
    write?: boolean
    floated?: boolean

    onAcceptChange?: (aR: Set<AccessRight>) => void
}

const AccessRightsDisplay = (props: AccessRightsDisplayProps) => {
    const { floated } = props;
    const [accessRights, setAccessRights] = useState(new Set(props.rights) as Set<AccessRight>);

    const { READ, WRITE } = AccessRight;

    const read = accessRights.has(READ);
    const write = accessRights.has(WRITE);
    const pendingChanges = accessRights.has(READ) !== props.read || accessRights.has(WRITE) !== props.write;
    return (
        <Flex>
            {floated ? <Box ml="auto" /> : null}
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
                        <Icon size={18} name="search" />
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
                        <Icon size={18} name="rename" />
                        <Text ml="5px">Edit</Text>
                    </Flex>
                </Button>
            </ButtonGroup>
            {pendingChanges && props.onAcceptChange ? <ButtonGroup width="150px">
                <Button ml="0.5em" onClick={() => props.onAcceptChange!(accessRights)}>
                    <Flex alignItems="center" justifyContent="center">
                        <Icon size={18} name="check" />
                        <Text ml="5px">Confirm</Text>
                    </Flex>
                </Button>
            </ButtonGroup> : null}
        </Flex>
    );
}

interface FileTypeGuess { path: string }
function fileTypeGuess({ path }: FileTypeGuess) {
    const hasExtension = path.split("/").pop()!.includes(".");
    return hasExtension ? "FILE" : "DIRECTORY";
}

const acceptShare = async (shareId: ShareId): Promise<any> =>
    (await Cloud.post(`/shares/accept/${encodeURIComponent(shareId)}`)).response; // FIXME Add error handling

const revokeShare = async (shareId: ShareId): Promise<any> =>
    (await Cloud.post(`/shares/revoke/${encodeURIComponent(shareId)}`)).response; // FIXME Add error handling

const createShare = async (user: string, path: string, rights: AccessRight[]): Promise<{ id: ShareId }> =>
    (await Cloud.put(`/shares/`, { sharedWith: user, path, rights })).response; // FIXME Add error handling

const updateShare = async (id: ShareId, rights: AccessRightValues[]): Promise<any> =>
    (await Cloud.post(`/shares/`, { id, rights })).response;

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

const mapDispatchToProps = (dispatch: Dispatch): SharesOperations => ({
    updatePageTitle: () => dispatch(updatePageTitle("Shares")),
    setActivePage: () => dispatch(setActivePage(SidebarPages.Shares)),
    setError: error => dispatch(setErrorMessage(error)),
    retrieveShares: async (page, itemsPerPage, byState) => dispatch(await retrieveShares(page, itemsPerPage, byState)),
    receiveShares: page => dispatch(receiveShares(page)),
    fetchSharesByPath: async path => dispatch(await fetchSharesByPath(path)),
    setShareState: shareState => dispatch(setShareState(shareState)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh))
});

const mapStateToProps = ({ shares }: ReduxObject): SharesReduxObject => shares;

export default connect(mapStateToProps, mapDispatchToProps)(List);
import * as React from "react";
import { Cloud } from "Authentication/SDUCloudObject";
import { AccessRight, Page, AccessRightValues } from "Types";
import { shareSwal, prettierString } from "UtilityFunctions";
import { getFilenameFromPath } from "Utilities/FileUtilities";
import { DefaultLoading } from "LoadingIcon/LoadingIcon";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { SharesByPath, Share, ShareId, ListProps, ListState, ShareState } from ".";
import PromiseKeeper from "PromiseKeeper";
import { Error, ButtonGroup, Text, Box, Flex, LoadingButton, Card, Divider, Button, Icon } from "ui-components";
import { sharesByPathQuery } from "Utilities/SharesUtilities";
import * as Heading from "ui-components/Heading";
import { connect } from "react-redux";
import { Dispatch } from "redux";
import ClickableDropdown from "ui-components/ClickableDropdown";
import { TextSpan } from "ui-components/Text";



class List extends React.Component<ListProps & { dispatch: Dispatch }, ListState> {
    constructor(props) {
        super(props);
        this.state = {
            promises: new PromiseKeeper(),
            shares: [],
            errorMessage: undefined,
            page: 0,
            itemsPerPage: 10,
            loading: true,
            byState: ShareState.REQUEST_SENT
        };
        // FIXME potentially move following to a parent component
        if (!props.keepTitle)
            props.dispatch(updatePageTitle("Shares"))
    }

    public componentDidMount = () => this.reload();

    public componentWillUnmount = () => this.state.promises.cancelPromises();

    reload(state?: ShareState) {
        const query = !!this.props.byPath ? sharesByPath(this.props.byPath) : retrieveShares(this.state.page, this.state.itemsPerPage, state || this.state.byState);
        this.state.promises.makeCancelable(query)
            .promise
            .then(e => this.setState({ shares: e.items, loading: false }))
            .catch(({ request }) => {
                if (!request.isCanceled) {
                    let errorMessage: string | undefined = "Unable to retrieve shares!";
                    if (request.status === 404) {
                        errorMessage = undefined;
                    }
                    this.setState(() => ({ errorMessage, loading: false }));
                }
            });
    }

    private updateShareState(state: ShareState) {
        this.setState(() => ({ byState: state }));
        this.reload(state);
    }

    public render() {
        let { shares, errorMessage, byState } = this.state;
        const noSharesWith = shares.filter(it => !it.sharedByMe).length === 0;
        const noSharesBy = shares.filter(it => it.sharedByMe).length === 0;
        return (
            <>
                <Error clearError={() => this.setState({ errorMessage: undefined })} error={errorMessage} />
                <Flex>
                    <Box ml="auto" />
                    <ClickableDropdown chevron trigger={<TextSpan>Shares where: {prettierString(byState)}</TextSpan>}>
                        {Object.keys(ShareState).map((it: ShareState) => (
                            <Text onClick={() => this.updateShareState(it)}>{prettierString(it)}</Text>
                        ))}
                    </ClickableDropdown>
                </Flex>
                <DefaultLoading loading={this.state.loading} size="big" />
                <Heading.h3>Shared with Me</Heading.h3>
                {noSharesWith ? <NoShares /> : shares.filter(it => !it.sharedByMe).map(it =>
                    <ListEntry
                        groupedShare={it}
                        key={it.path}
                        onAccepted={e => this.onEntryAction()}
                        onRejected={e => this.onEntryAction()}
                        onRevoked={e => this.onEntryAction()}
                        onShared={e => this.onEntryAction()}
                        onRights={e => this.onEntryAction()}
                        onError={it => this.setState({ errorMessage: it })} />
                )}
                <Heading.h3>Shared by Me</Heading.h3>
                {noSharesBy ? <NoShares /> : shares.filter(it => it.sharedByMe).map(it =>
                    <ListEntry
                        groupedShare={it}
                        key={it.path}
                        onAccepted={e => this.onEntryAction()}
                        onRejected={e => this.onEntryAction()}
                        onRevoked={e => this.onEntryAction()}
                        onShared={e => this.onEntryAction()}
                        onRights={e => this.onEntryAction()}
                        onError={it => this.setState(() => ({ errorMessage: it }))} />
                )}
            </>
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

        let message = (hasBeenShared) ?
            `${groupedShare.sharedBy} has shared ${groupedShare.path} with you.` :
            `${groupedShare.sharedBy} wishes to share ${groupedShare.path} with you.`

        return (
            <Card width="100%" height="auto" p="10px 10px 10px 10px">
                <Heading.h4>
                    <Flex>
                        <i style={{ marginLeft: "3px", marginRight: "3px", fontSize: "24px" }} className="fas fa-folder" /> {getFilenameFromPath(groupedShare.path)}
                        <Box ml="auto" />
                        <AccessRightsDisplay floated disabled rights={actualShare.rights} />
                    </Flex>
                </Heading.h4>
                <TextSpan color="text">Shared by {groupedShare.sharedBy}</TextSpan>
                <TextSpan mt="4px" mb="4px">{message}</TextSpan>
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
                                <LoadingButton loading={isLoading} disabled={isLoading} color="green" onClick={() => this.onAccept(actualShare)} hoverColor="darkGreen" content="Accept" />
                                <LoadingButton loading={isLoading} disabled={isLoading} color="red" onClick={() => this.onReject(actualShare)} hoverColor="darkRed" content="Reject" />
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
                <Flex m="5px 5px 5px 5px">
                    <Box>
                        <i style={{ fontSize: 20, marginRight: "5px" }} className="fas fa-user-circle" />
                    </Box>
                    <Box width="80%">
                        {e.sharedWith}
                        <AccessRightsDisplay rights={e.rights} onRightsToggle={(it) => this.onRightsToggle(e, it)} />
                    </Box>
                    <Box width="10%">
                        <LoadingButton
                            fullWidth
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
                        </LoadingButton>
                    </Box>
                </Flex>
                {i !== length - 1 ? <Divider /> : null}
            </Box>
        ));

        return (
            <Card width="100%" p="10px 10px 10px 10px" height="auto">
                <Heading.h4>
                    <i style={{ marginLeft: "3px", marginRight: "3px", fontSize: "24px" }} className="fas fa-folder" /> {getFilenameFromPath(groupedShare.path)}
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

    onRightsToggle(share: Share, right: AccessRight) {
        this.setState({ isLoading: true });

        let filtered = share.rights.filter(e => e != right);

        // Add if missing
        let hasRight = share.rights.indexOf(right) != -1;
        if (!hasRight) filtered.push(right);

        updateShare(share.id, filtered)
            .then(it => { this.maybeInvoke(it.id, this.props.onRights); this.setState({ isLoading: false }) })
            .catch(e => { if (!e.isCanceled) { this.maybeInvoke(e.response.why ? e.response.why : "An error has occured", this.props.onError); this.setState({ isLoading: false }) } })
    }

    onCreateShare(path: string) {
        this.setState({ isLoading: true });
        shareSwal().then(({ dismiss, value }) => {
            if (dismiss) { this.setState(() => ({ isLoading: false })); return; }
            const rights = [] as AccessRight[];
            // FIXME Fix immediately when SweetAlert allows forms
            (document.getElementById("read-swal") as HTMLInputElement).checked ? rights.push(AccessRight.READ) : null;
            (document.getElementById("write-swal") as HTMLInputElement).checked ? rights.push(AccessRight.WRITE) : null;
            createShare(value, path, rights)
                .then(it => {
                    this.maybeInvoke(it.id, this.props.onShared);
                    this.setState(() => ({ isLoading: false }))
                })
                .catch(e => {
                    if (!e.isCanceled) {
                        this.maybeInvoke(e.response.why ? e.response.why : "An error has occured", this.props.onError);
                        this.setState(() => ({ isLoading: false }))
                    }
                })
        })
    }

    componentWillUnmount() {
        this.state.promises.cancelPromises();
    }

    onRevoke(share: Share) {
        this.setState({ isLoading: true });

        this.state.promises.makeCancelable(revokeShare(share.id))
            .promise
            .then(() => { this.maybeInvoke(share, this.props.onRevoked); this.setState(() => ({ isLoading: false })); })
            .catch(e => { this.maybeInvoke(e.why ? e.why : "An error has occured", this.props.onError); this.setState(() => ({ isLoading: false })) });
    }

    onAccept(share: Share) {
        this.setState({ isLoading: true });

        this.state.promises.makeCancelable(acceptShare(share.id))
            .promise
            .then(() => { this.maybeInvoke(share, this.props.onAccepted); this.setState(() => ({ isLoading: false })); })
            .catch(e => { this.maybeInvoke(e.why ? e.why : "An error has occured", this.props.onError); this.setState(() => ({ isLoading: false })) })
    }

    onReject(share: Share) {
        this.setState({ isLoading: true });

        this.state.promises.makeCancelable(revokeShare(share.id))
            .promise
            .then(() => { this.maybeInvoke(share, this.props.onRejected); this.setState(() => ({ isLoading: false })); })
            .catch(e => { this.maybeInvoke(e.why ? e.why : "An error has occured", this.props.onError); this.setState(() => ({ isLoading: false })) });
    }
}

interface AccessRightsDisplayProps {
    disabled?: boolean
    rights?: AccessRightValues[]

    read?: boolean
    write?: boolean
    floated?: boolean

    className?: string

    onRightsToggle?: (aR: AccessRight) => void
}

const AccessRightsDisplay = (props: AccessRightsDisplayProps) => {
    let { read, write, floated } = props;
    if (props.rights != null) {
        read = props.rights.indexOf(AccessRight.READ) != -1;
        write = props.rights.indexOf(AccessRight.WRITE) != -1;
    }

    return (
        <Flex>
            {floated ? <Box ml="auto" /> : null}
            <ButtonGroup width="280px">
                <Button
                    disabled={props.disabled}
                    color={read ? "green" : "lightGray"}
                    textColor={read ? "white" : "gray"}
                    onClick={() => props.onRightsToggle ? props.onRightsToggle(AccessRight.READ) : 0}
                >
                    <Flex alignItems="center" justifyContent="center">
                        <Icon size={18} name="search" />
                        <Text ml="5px">Read</Text>
                    </Flex>
                </Button>
                <Button
                    disabled={props.disabled}
                    color={write ? "green" : "lightGray"}
                    textColor={write ? "white" : "gray"}
                    onClick={() => props.onRightsToggle ? props.onRightsToggle(AccessRight.WRITE) : 0}
                >
                    <Flex alignItems="center" justifyContent="center">
                        <Icon size={18} name="rename" />
                        <Text ml="5px">Write</Text>
                    </Flex>
                </Button>
            </ButtonGroup>
        </Flex>
    );
}

function retrieveShares(page: Number, itemsPerPage: Number, byState?: ShareState): Promise<Page<SharesByPath>> {
    let url = `/shares?itemsPerPage=${itemsPerPage}&page=${page}`;
    if (byState) url += `&state=${encodeURIComponent(byState)}`;
    return Cloud.get(url).then(it => it.response);
}

function acceptShare(shareId: ShareId): Promise<any> {
    return Cloud.post(`/shares/accept/${encodeURIComponent(shareId)}`).then(e => e.response); // FIXME Add error handling
}

function revokeShare(shareId: ShareId): Promise<any> {
    return Cloud.post(`/shares/revoke/${encodeURIComponent(shareId)}`).then(e => e.response); // FIXME Add error handling
}

function createShare(user: string, path: string, rights: AccessRight[]): Promise<{ id: ShareId }> {
    return Cloud.put(`/shares/`, { sharedWith: user, path, rights }).then(e => e.response); // FIXME Add error handling
}

function updateShare(id: ShareId, rights: AccessRightValues[]): Promise<any> {
    return Cloud.post(`/shares/`, { id, rights }).then(e => e.response);
}

const sharesByPath = (path: string): Promise<any> => {
    return Cloud.get(sharesByPathQuery(path)).then(e => ({ items: [e.response] }));
}

export default connect()(List);
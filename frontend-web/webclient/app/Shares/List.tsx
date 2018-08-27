import * as React from "react";
import { Cloud } from "Authentication/SDUCloudObject";
import * as PropTypes from "prop-types";
import { List as SemList, SemanticSIZES, SemanticFLOATS, Message, Header, Card, Button, Icon, ButtonGroup } from "semantic-ui-react";
import { AccessRight, Page } from "Types";
import { shareSwal } from "UtilityFunctions";
import { getFilenameFromPath } from "Utilities/FileUtilities";
import "./List.scss"
import { DefaultLoading } from "LoadingIcon/LoadingIcon";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { SharesByPath, Share, ShareId, ListProps, ListState, ListContext, ShareState } from ".";

export class List extends React.Component<ListProps, ListState> {
    constructor(props: ListProps, ctx: ListContext) {
        super(props);
        this.state = {
            shares: [],
            errorMessage: undefined,
            page: 0,
            itemsPerPage: 10,
            loading: true
        };
        // FIXME potentially move following to parent component
        if (!props.keepTitle)
            ctx.store.dispatch(updatePageTitle("Shares"))
    }

    static contextTypes = {
        store: PropTypes.object
    }

    public componentDidMount() {
        this.reload();
    }

    reload() {
        retrieveShares(this.state.page, this.state.itemsPerPage).then(e =>
            this.setState({ shares: e.items })).catch(e => {
                this.setState({ errorMessage: "Unable to retrieve shares!" });
            }).finally(() => this.setState(() => ({ loading: false })));
    }

    public render() {
        let { shares, errorMessage } = this.state;
        // FIXME This approach will not work if # of shares exceeds 10. Needs retrieve shares by path.
        this.props.byPath ? shares = shares.filter(it => it.path === this.props.byPath) : null;
        const noSharesWith = shares.filter(it => !it.sharedByMe).length === 0;
        const noSharesBy = shares.filter(it => it.sharedByMe).length === 0;
        return (
            <React.Fragment>
                {errorMessage ? <Message color="red" onDismiss={() => this.setState({ errorMessage: undefined })}>{errorMessage}</Message> : null}
                <DefaultLoading loading={this.state.loading} size="big" />
                <Header>Shared with Me</Header>
                {noSharesWith ? <NoShares /> : shares.filter(it => !it.sharedByMe).map(it =>
                    <ListEntry
                        groupedShare={it}
                        key={it.path}
                        onAccepted={e => this.onEntryAction(e)}
                        onRejected={e => this.onEntryAction(e)}
                        onRevoked={e => this.onEntryAction(e)}
                        onShared={e => this.onEntryAction(e)}
                        onRights={e => this.onEntryAction(e)}
                        onError={it => this.setState({ errorMessage: it })} />
                )}
                <Header>Shared by Me</Header>
                {noSharesBy ? <NoShares /> : shares.filter(it => it.sharedByMe).map(it =>
                    <ListEntry
                        groupedShare={it}
                        key={it.path}
                        onAccepted={e => this.onEntryAction(e)}
                        onRejected={e => this.onEntryAction(e)}
                        onRevoked={e => this.onEntryAction(e)}
                        onShared={e => this.onEntryAction(e)}
                        onRights={e => this.onEntryAction(e)}
                        onError={it => this.setState({ errorMessage: it })} />
                )}
            </React.Fragment>
        );
    }

    onEntryAction(e: any) {
        this.reload();
    }
}

const NoShares = () => <Header textAlign="center"><Header.Subheader content="No shares" /></Header>

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
}

class ListEntry extends React.Component<ListEntryProperties, ListEntryState> {
    constructor(props: ListEntryProperties) {
        super(props);
        this.state = { isLoading: false };
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
            <Card fluid>
                <Card.Content>
                    <Card.Header>
                        <Icon name='folder' /> {getFilenameFromPath(groupedShare.path)}
                        <AccessRightsDisplay size='tiny' floated='right' disabled rights={actualShare.rights} />
                    </Card.Header>
                    <Card.Meta>Shared by {groupedShare.sharedBy}</Card.Meta>
                    <Card.Description>
                        {message}
                        <Button.Group floated="right">
                            {groupedShare.shares[0].state !== ShareState.REQUEST_SENT ?
                                <Button negative icon='delete' disabled={isLoading} loading={isLoading} content="Revoke" size='mini' onClick={() => this.onRevoke(actualShare)} /> : null}
                        </Button.Group>
                    </Card.Description>
                </Card.Content>

                {!hasBeenShared ?
                    <Card.Content extra>
                        <Button.Group floated='right'>
                            <Button positive loading={isLoading} disabled={isLoading} onClick={() => this.onAccept(actualShare)}>Accept</Button>
                            <Button.Or />
                            <Button negative loading={isLoading} disabled={isLoading} onClick={() => this.onReject(actualShare)}>Reject</Button>
                        </Button.Group>
                    </Card.Content>
                    : null
                }
            </Card>
        );
    }

    renderSharedByMe(): JSX.Element {
        let { groupedShare } = this.props;
        let { isLoading } = this.state;

        let shareComponents: JSX.Element[] = groupedShare.shares.map(e => (
            <SemList.Item key={e.id}>
                <Button negative icon='delete' disabled={isLoading} loading={isLoading} content="Revoke" floated="right" size="mini" onClick={() => this.onRevoke(e)} />
                <SemList.Icon name='user circle' size="large" verticalAlign='top' />
                <SemList.Content>
                    <SemList.Header>
                        {e.sharedWith}
                    </SemList.Header>
                    <SemList.Description>
                        <AccessRightsDisplay className='ar-display-padding' size='mini' rights={e.rights} onRightsToggle={(it) => this.onRightsToggle(e, it)} />
                    </SemList.Description>
                </SemList.Content>
            </SemList.Item>
        ));

        return (
            <Card fluid>
                <Card.Content>
                    <Card.Header>
                        <Icon name='folder' /> {getFilenameFromPath(groupedShare.path)}
                    </Card.Header>
                    <Card.Meta>Shared with {groupedShare.shares.length} collaborators</Card.Meta>
                    <Card.Description className='ar-list-padding'>
                        <SemList relaxed divided>
                            {shareComponents}
                            <SemList.Item>
                                <SemList.Icon name='add' size='large' verticalAlign='middle' />
                                <SemList.Content>
                                    <SemList.Header>
                                        <Button color='green' disabled={isLoading} loading={isLoading} onClick={() => this.onCreateShare(groupedShare.path)}>
                                            Share this with another user
                                        </Button>
                                    </SemList.Header>
                                </SemList.Content>
                            </SemList.Item>
                        </SemList>
                    </Card.Description>
                </Card.Content>
            </Card>
        );
    }

    maybeInvoke<T>(payload: T, f?: (T) => void) {
        if (f) f(payload)
    }

    onRightsToggle(share: Share, right: AccessRight) {
        this.setState({ isLoading: true });

        let filtered = share.rights.filter(e => e != right);

        // Add if missing
        let hasRight = share.rights.indexOf(right) != -1;
        if (!hasRight) {
            filtered.push(right);
        }

        updateShare(share.id, filtered)
            .then(it => { console.log(it); this.maybeInvoke(it.id, this.props.onRights) })
            .catch(e => { console.log(it); this.maybeInvoke(e.response.why ? e.response.why : "An error has occured", this.props.onError) })
            .finally(() => this.setState({ isLoading: false }));
    }

    onCreateShare(path: string) {
        this.setState({ isLoading: true });
        shareSwal().then(({ dismiss, value }) => {
            if (dismiss) { this.setState(() => ({ isLoading: false })); return; }
            const rights = [] as AccessRight[];
            // FIXME Fix immediately when SweetAlert allows forms
            (document.getElementById("read-swal") as HTMLInputElement).checked ? rights.push(AccessRight.READ) : null;
            (document.getElementById("write-swal") as HTMLInputElement).checked ? rights.push(AccessRight.WRITE) : null;
            (document.getElementById("execute-swal") as HTMLInputElement).checked ? rights.push(AccessRight.EXECUTE) : null;
            createShare(value, path, rights)
                .then(it => this.maybeInvoke(it.id, this.props.onShared))
                .catch(e => this.maybeInvoke(e.response.why ? e.response.why : "An error has occured", this.props.onError))
                .finally(() => this.setState({ isLoading: false }))
        })
    }

    onRevoke(share: Share) {
        this.setState({ isLoading: true });

        revokeShare(share.id)
            .then(it => this.maybeInvoke(share, this.props.onRevoked))
            .catch(e => this.maybeInvoke(e.why ? e.why : "An error has occured", this.props.onError))
            .finally(() => this.setState({ isLoading: false }));
    }

    onAccept(share: Share) {
        this.setState({ isLoading: true });

        acceptShare(share.id)
            .then(it => this.maybeInvoke(share, this.props.onAccepted))
            .catch(e => this.maybeInvoke(e.why ? e.why : "An error has occured", this.props.onError))
            .finally(() => this.setState({ isLoading: false }));
    }

    onReject(share: Share) {
        this.setState({ isLoading: true });

        rejectShare(share.id)
            .then(it => this.maybeInvoke(share, this.props.onRejected))
            .catch(e => this.maybeInvoke(e.why ? e.why : "An error has occured", this.props.onError))
            .finally(() => this.setState({ isLoading: false }));
    }
}

interface AccessRightsDisplayProps {
    disabled?: boolean
    rights?: AccessRight[]

    read?: boolean
    write?: boolean
    execute?: boolean

    size?: SemanticSIZES
    floated?: SemanticFLOATS

    className?: string

    onRightsToggle?: (AccessRight) => void
}

const AccessRightsDisplay = (props: AccessRightsDisplayProps) => {
    let { read, write, execute, floated, size, className } = props;
    if (props.rights != null) {
        read = props.rights.indexOf(AccessRight.READ) != -1;
        write = props.rights.indexOf(AccessRight.WRITE) != -1;
        execute = props.rights.indexOf(AccessRight.EXECUTE) != -1;
    }

    return (
        <ButtonGroup floated={floated} labeled icon size={size} className={className}>
            <Button
                disabled={props.disabled}
                positive={read}
                toggle
                icon="search"
                content="Read"
                onClick={() => props.onRightsToggle ? props.onRightsToggle(AccessRight.READ) : 0}
            />

            <Button
                disabled={props.disabled}
                positive={write}
                toggle
                icon="edit"
                content="Write"
                onClick={() => props.onRightsToggle ? props.onRightsToggle(AccessRight.WRITE) : 0}
            />

            <Button
                disabled={props.disabled}
                positive={execute}
                toggle
                icon='settings'
                content='Execute'
                onClick={() => props.onRightsToggle ? props.onRightsToggle(AccessRight.EXECUTE) : 0}
            />
        </ButtonGroup>
    );
}

function retrieveShares(page: Number, itemsPerPage: Number, byState?: ShareState): Promise<Page<SharesByPath>> {
    let url = `/shares?itemsPerPage=${itemsPerPage}&page=${page}`;
    if (byState) url += `state=${byState}`
    return Cloud.get(url).then((e) => e.response); // FIXME Add error handling
}

function acceptShare(shareId: ShareId): Promise<any> {
    return Cloud.post(`/shares/accept/${shareId}`).then(e => e.response); // FIXME Add error handling
}

function rejectShare(shareId: ShareId): Promise<any> {
    return Cloud.post(`/shares/reject/${shareId}`).then(e => e.response); // FIXME Add error handling
}

function revokeShare(shareId: ShareId): Promise<any> {
    return Cloud.post(`/shares/revoke/${shareId}`).then(e => e.response); // FIXME Add error handling
}

function createShare(user: string, path: string, rights: AccessRight[]): Promise<{ id: ShareId }> {
    return Cloud.put(`/shares/`, { sharedWith: user, path, rights }).then(e => e.response); // FIXME Add error handling
}

function updateShare(id: ShareId, rights: AccessRight[]): Promise<any> {
    return Cloud.post(`/shares/`, { id, rights }).then(e => { console.log(e); return e.response }); // FIXME Add error handling
}


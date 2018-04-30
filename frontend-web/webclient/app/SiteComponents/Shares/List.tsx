import * as React from "react";
import "react-bootstrap";
import { Cloud } from "../../../authentication/SDUCloudObject"
import { List as SemList, SemanticSIZES, SemanticFLOATS, Message, Header, Card, Image, Button, Popup, Feed, Icon, Divider, SemanticICONS, Label, Container, ButtonGroup } from 'semantic-ui-react';
import { Redirect } from "react-router";
import * as moment from "moment";
import { AccessRight, Page } from "../../types/types";
import { getFilenameFromPath } from "../../UtilityFunctions";
import "./List.scss"

interface ListState {
    shares: SharesByPath[]
    errorMessage: string
}

export class List extends React.Component<any, ListState> {
    constructor(props: any) {
        super(props);
        this.state = { shares: [], errorMessage: null };
    }

    public componentWillMount() {
        this.reload();
    }

    reload() {
        retrieveShares().then(e => this.setState({ shares: e.items })).catch(e => {
            this.setState({ errorMessage: "Unable to retrieve shares!" });
        });
    }

    public render() {
        let { shares, errorMessage } = this.state;
        return (
            <Container>
                {errorMessage ? <Message color='red' onDismiss={() => this.setState({ errorMessage: null })}>{errorMessage}</Message> : null}
                <Header>Shared with Me</Header>
                {this.state.shares.map(it =>
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
            </Container>
        );
    }

    onEntryAction(e: any) {
        this.reload();
    }
}

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
                        <Button negative icon='delete' disabled={isLoading} loading={isLoading} content="Revoke" size='mini' onClick={() => this.onRevoke(actualShare)} />
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

        let shareComponents: JSX.Element[] = groupedShare.shares.map(e => {
            return (
                <SemList.Item key={e.id}>
                    <SemList.Icon name='user circle' size='large' verticalAlign='top' />
                    <SemList.Content>
                        <SemList.Header>
                            {e.sharedWith}
                        </SemList.Header>
                        <SemList.Description>
                            <AccessRightsDisplay className='ar-display-padding' size='mini' rights={e.rights} onRightsToggle={(it) => this.onRightsToggle(e, it)} />
                            {/* TODO Styling of revoke */}
                            <Button negative icon='delete' disabled={isLoading} loading={isLoading} content="Revoke" size='mini' onClick={() => this.onRevoke(e)} />
                        </SemList.Description>
                    </SemList.Content>
                </SemList.Item>
            );
        });

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
                                        <Button color='green' disabled={isLoading} loading={isLoading} onClick={() => this.onCreateShare()}>
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
            .then(it => this.maybeInvoke(it.id, this.props.onRights))
            .catch(e => this.maybeInvoke(e.response.why ? e.response.why : "An error has occured", this.props.onError))
            .then(e => this.setState({ isLoading: false }));
    }

    onCreateShare() {
        this.setState({ isLoading: true });
        createShare("user3@test.dk", "/home/jonas@hinchely.dk/Jobs", [AccessRight.READ, AccessRight.WRITE])
            .then(it => this.maybeInvoke(it.id, this.props.onShared))
            .catch(e => this.maybeInvoke(e.response.why ? e.response.why : "An error has occured", this.props.onError))
            .then(e => this.setState({ isLoading: false }));
    }

    onRevoke(share: Share) {
        this.setState({ isLoading: true });

        revokeShare(share.id)
            .then(it => this.maybeInvoke(share, this.props.onRevoked))
            .catch(e => this.maybeInvoke(e.why ? e.why : "An error has occured", this.props.onError))
            .then(e => this.setState({ isLoading: false }));
    }

    onAccept(share: Share) {
        console.log(share);
        this.setState({ isLoading: true });

        acceptShare(share.id)
            .then(it => this.maybeInvoke(share, this.props.onAccepted))
            .catch(e => this.maybeInvoke(e.why ? e.why : "An error has occured", this.props.onError))
            .then(e => this.setState({ isLoading: false }));
    }

    onReject(share: Share) {
        console.log(share);
        this.setState({ isLoading: true });

        rejectShare(share.id)
            .then(it => this.maybeInvoke(share, this.props.onRejected))
            .catch(e => this.maybeInvoke(e.why ? e.why : "An error has occured", this.props.onError))
            .then(e => this.setState({ isLoading: false }));
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
                icon='search'
                content='Read'
                onClick={() => props.onRightsToggle ? props.onRightsToggle(AccessRight.READ) : 0}
            />

            <Button
                disabled={props.disabled}
                positive={write}
                toggle
                icon='edit'
                content='Write'
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

enum ShareState {
    REQUEST_SENT = "REQUEST_SENT",
    ACCEPTED = "ACCEPTED",
    REJECTED = "REJECT",
    REVOKE = "REVOKE"
}

type ShareId = string
interface SharesByPath {
    path: string,
    sharedBy: string,
    sharedByMe: boolean,
    shares: Share[]
}

interface Share {
    id: ShareId,
    sharedWith: String,
    rights: AccessRight[],
    state: ShareState
}

function retrieveShares(byState?: ShareState, page?: Number, itemsPerPage?: Number): Promise<Page<SharesByPath>> {
    let url = "/shares?"
    if (byState) url += `state=${byState}`

    return Cloud.get(url).then((e) => e.response);
}

function acceptShare(shareId: ShareId): Promise<any> {
    return Cloud.post(`/shares/accept/${shareId}`).then(e => e.response);
}

function rejectShare(shareId: ShareId): Promise<any> {
    return Cloud.post(`/shares/reject/${shareId}`).then(e => e.response);
}

function revokeShare(shareId: ShareId): Promise<any> {
    return Cloud.post(`/shares/revoke/${shareId}`).then(e => e.response);
}

function createShare(user: string, path: string, rights: AccessRight[]): Promise<{ id: ShareId }> {
    return Cloud.put(`/shares/`, { sharedWith: user, path, rights }).then(e => e.response);
}

function updateShare(id: ShareId, rights: AccessRight[]): Promise<any> {
    return Cloud.post(`/shares/`, { id, rights }).then(e => e.response);
}
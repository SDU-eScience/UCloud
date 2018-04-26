import * as React from "react";
import "react-bootstrap";
import { Cloud } from "../../../authentication/SDUCloudObject"
import { Message, Header, Card, Image, Button, Popup, Feed, Icon, Divider, SemanticICONS, Label, Container, ButtonGroup } from 'semantic-ui-react';
import { Redirect } from "react-router";
import * as moment from "moment";
import { AccessRight, Page } from "../../types/types";
import { getFilenameFromPath } from "../../UtilityFunctions";

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
                {errorMessage ? <Message color='red'>{errorMessage}</Message> : null}
                <Header>Shared with Me</Header>
                {this.state.shares.map(it =>
                    <ListEntry
                        groupedShare={it}
                        key={it.path}
                        onError={it => this.setState({ errorMessage: it })} />
                )}
            </Container>
        );
    }
}

interface ListEntryProperties {
    groupedShare: SharesByPath
    onError?: (message: string) => void
    onAccepted?: (share: Share) => void
    onRejected?: (share: Share) => void
}

class ListEntry extends React.Component<ListEntryProperties, any> {
    constructor(props: ListEntryProperties) {
        super(props);
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
                        <AccessRightsDisplay rights={actualShare.rights} />
                    </Card.Header>
                    <Card.Meta>Shared by {groupedShare.sharedBy}</Card.Meta>
                    <Card.Description>{message}</Card.Description>
                </Card.Content>

                {!hasBeenShared ?
                    <Card.Content extra>
                        <Button.Group floated='right'>
                            <Button negative onClick={() => this.onReject(actualShare)}>Reject</Button>
                            <Button.Or />
                            <Button positive onClick={() => this.onAccept(actualShare)}>Accept</Button>
                        </Button.Group>
                    </Card.Content>
                    : null
                }
            </Card>
        );
    }

    renderSharedByMe(): JSX.Element {
        return <div />;
    }

    onAccept(share: Share) {
        console.log(share);
        acceptShare(share.id)
            .then(it => { }) // TODO Invoke handler
            .catch(e => this.setState({ errorMessage: "An error has occured" }));
    }

    onReject(share: Share) {
        console.log(share);
        rejectShare(share.id)
            .then(it => { }) // TODO Invoke handler
            .catch(e => this.setState({ errorMessage: "An error has occured" }));
    }
}

interface AccessRightsDisplayProps {
    disabled?: boolean
    rights?: AccessRight[]

    read?: boolean
    write?: boolean
    execute?: boolean

    onRightsToggle?: (AccessRight) => void
}

const AccessRightsDisplay = (props: AccessRightsDisplayProps) => {
    let { read, write, execute } = props;
    if (props.rights != null) {
        read = props.rights.indexOf(AccessRight.READ) != -1;
        write = props.rights.indexOf(AccessRight.WRITE) != -1;
        execute = props.rights.indexOf(AccessRight.EXECUTE) != -1;
    }

    return (
        <ButtonGroup labeled icon size='tiny'>
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
    return Cloud.post(`/shares/accept/${shareId}`).then(e => e.reposen);
}

function rejectShare(shareId: ShareId): Promise<any> {
    return Cloud.post(`/shares/reject/${shareId}`).then(e => e.reposen);
}

function revokeShare(shareId: ShareId): Promise<any> {
    return Cloud.post(`/shares/revoke/${shareId}`).then(e => e.reposen);
}
import * as React from "react";
import { Cloud } from "Authentication/SDUCloudObject";
import { AccessRight, Page, AccessRightValues } from "Types";
import { shareSwal, prettierString, iconFromFilePath } from "UtilityFunctions";
import { getFilenameFromPath } from "Utilities/FileUtilities";
import LoadingIcon from "LoadingIcon/LoadingIcon";
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
import { MainContainer } from "MainContainer/MainContainer";
import { FileIcon } from "UtilityComponents";
import { setsDiffer } from "Utilities/CollectionUtilities";

class List extends React.Component<ListProps & { dispatch: Dispatch }, ListState> {
    constructor(props: any) {
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
        if (!props.notInnerComponent)
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

    private updateShareState(byState: ShareState): void {
        this.setState(() => ({ byState }));
        this.reload(byState);
    }

    private updateShare(path: string, share: Share): void {
        const { shares } = this.state;
        const sharesByPath = shares.find(it => it.path === path)!
        const i = sharesByPath.shares.findIndex(it => it.id === share.id);
        shares.find(it => it.path === path)!.shares[i].pendingRightChanges = share.pendingRightChanges;
        this.setState(() => ({ shares }));
    }

    public render() {
        let { shares, errorMessage, byState } = this.state;
        const noSharesWith = shares.filter(it => !it.sharedByMe).length === 0;
        const noSharesBy = shares.filter(it => it.sharedByMe).length === 0;
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
                {this.props.notInnerComponent ? null : header}
                <Error clearError={() => this.setState({ errorMessage: undefined })} error={errorMessage} />
                {this.state.loading ? <LoadingIcon size={18} /> : null}
                <Heading.h3>Shared with Me</Heading.h3>
                {
                    noSharesWith ? <NoShares /> : shares.filter(it => !it.sharedByMe).map(it =>
                        <ListEntry
                            updateShare={() => undefined}
                            groupedShare={it}
                            key={it.path}
                            onAccepted={e => this.onEntryAction()}
                            onRejected={e => this.onEntryAction()}
                            onRevoked={e => this.onEntryAction()}
                            onShared={e => this.onEntryAction()}
                            onRights={e => this.onEntryAction()}
                            onError={it => this.setState({ errorMessage: it })} />
                    )
                }
                <Heading.h3 pt="25px">Shared by Me</Heading.h3>
                {
                    noSharesBy ? <NoShares /> : shares.filter(it => it.sharedByMe).map(it =>
                        <ListEntry
                            updateShare={(path, share) => this.updateShare(path, share)}
                            groupedShare={it}
                            key={it.path}
                            onAccepted={e => this.onEntryAction()}
                            onRejected={e => this.onEntryAction()}
                            onRevoked={e => this.onEntryAction()}
                            onShared={e => this.onEntryAction()}
                            onRights={e => this.onEntryAction()}
                            onError={it => this.setState(() => ({ errorMessage: it }))} />
                    )
                }
            </>
        );


        return (
            <MainContainer
                header={this.props.notInnerComponent ? header : null}
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
    updateShare: (path: string, share: Share) => void
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

        const icon = iconFromFilePath(groupedShare.path, fileTypeGuess(groupedShare), Cloud.homeFolder);
        return (
            <Card width="100%" height="auto" p="10px 10px 10px 10px">
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
                <Flex m="5px 5px 5px 5px">
                    <Box width="90%">
                        {e.sharedWith}
                        <AccessRightsDisplay
                            pendingChanges={e.pendingRightChanges}
                            rights={e.rights}
                            onRightsToggle={it => this.onRightsToggle(e, it)}
                            onAcceptChange={() => this.onAcceptChange(e)}
                        />
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

        const icon = iconFromFilePath(groupedShare.path, fileTypeGuess(groupedShare), Cloud.homeFolder);

        return (
            <Card width="100%" p="10px 10px 10px 10px" height="auto">
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

    onRightsToggle(share: Share, right: AccessRight) {
        const { pendingRightChanges } = share;
        let pending = pendingRightChanges || new Set(share.rights);

        pending.has(right) ? pending.delete(right) : pending.add(right);
        share.pendingRightChanges = pending;

        this.props.updateShare(this.props.groupedShare.path, share);
    }

    async onAcceptChange(share: Share) {
        const pendingRightChanges = share.pendingRightChanges!;
        this.setState(() => ({ isLoading: true }));
        await updateShare(share.id, [...pendingRightChanges])
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
            const rights = [] as AccessRight[];
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
    pendingChanges?: Set<AccessRightValues>

    read?: boolean
    write?: boolean
    floated?: boolean

    className?: string

    onRightsToggle?: (aR: AccessRight) => void
    onAcceptChange?: () => void
}

const AccessRightsDisplay = (props: AccessRightsDisplayProps) => {
    let { read, write, floated } = props;
    if (!!props.pendingChanges) {
        read = props.pendingChanges.has("READ");
        write = props.pendingChanges.has("WRITE");
    } else if (props.rights != null) {
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
                        <Text ml="5px">View</Text>
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
                        <Text ml="5px">Edit</Text>
                    </Flex>
                </Button>
            </ButtonGroup>
            {props.pendingChanges && setsDiffer(new Set(props.rights), props.pendingChanges) ?
                <ButtonGroup width="150px">
                    <Button ml="0.5em" onClick={() => props.onAcceptChange!()}>
                        <Flex alignItems="center" justifyContent="center">
                            <Icon size={18} name="check" />
                            <Text ml="5px">Confirm</Text>
                        </Flex>
                    </Button>
                </ButtonGroup>
                : null}
        </Flex>
    );
}

interface FileTypeGuess { path: string }
function fileTypeGuess({ path }: FileTypeGuess) {
    const hasExtension = path.split("/").pop()!.includes(".");
    return hasExtension ? "FILE" : "DIRECTORY";
}

async function retrieveShares(page: Number, itemsPerPage: Number, byState?: ShareState): Promise<Page<SharesByPath>> {
    let url = `/shares?itemsPerPage=${itemsPerPage}&page=${page}`;
    if (byState) url += `&state=${encodeURIComponent(byState)}`;
    return (await Cloud.get(url)).response;
}

const acceptShare = async (shareId: ShareId): Promise<any> =>
    (await Cloud.post(`/shares/accept/${encodeURIComponent(shareId)}`)).response; // FIXME Add error handling

const revokeShare = async (shareId: ShareId): Promise<any> =>
    (await Cloud.post(`/shares/revoke/${encodeURIComponent(shareId)}`)).response; // FIXME Add error handling

const createShare = async (user: string, path: string, rights: AccessRight[]): Promise<{ id: ShareId }> =>
    (await Cloud.put(`/shares/`, { sharedWith: user, path, rights })).response; // FIXME Add error handling

const updateShare = async (id: ShareId, rights: AccessRightValues[]): Promise<any> =>
    (await Cloud.post(`/shares/`, { id, rights })).response;

const sharesByPath = async (path: string): Promise<any> => ({
    items: [(await Cloud.get(sharesByPathQuery(path))).response]
});


export default connect()(List);
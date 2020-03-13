import * as React from "react";
import {Card, Icon, Button, Text, Input, Flex} from "ui-components";
import * as Heading from "ui-components/Heading";
import {MainContainer} from "MainContainer/MainContainer";
import * as Pagination from "Pagination";
import {emptyPage} from "DefaultObjects";
import {useCloudAPI, APICallParameters} from "Authentication/DataHook";
import {Page} from "Types";
import {GridCardGroup} from "ui-components/Grid";
import {useHistory, useParams} from "react-router";
import DetailedGroupView from "./DetailedGroupView";
import * as ReactModal from "react-modal";
import {defaultModalStyle} from "Utilities/ModalUtilities";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {errorMessageOrDefault} from "UtilityFunctions";
import {usePromiseKeeper} from "PromiseKeeper";
import {Client} from "Authentication/HttpClientInstance";
import {SnackType} from "Snackbar/Snackbars";

interface GroupWithSummary {
    group: string;
    numberOfMembers: number;
    members: string[];
}

const baseContext = "/projects/groups";

function groupSummaryRequest(payload: PaginationRequest): APICallParameters<PaginationRequest> {
    return {
        path: baseContext,
        method: "GET",
        payload
    };
}

interface PaginationRequest {
    itemsPerPage: number;
    page: number;
}

function GroupsOverview(): JSX.Element | null {
    // TODO -- Add groups. Remove groups.
    // File imports of users
    const history = useHistory();
    const {group} = useParams<{group?: string}>();
    const [creatingGroup, setCreatingGroup] = React.useState(false);
    const [loading, setLoading] = React.useState(false);
    const createGroupRef = React.useRef<HTMLInputElement>(null);
    const promises = usePromiseKeeper();
    const [groupSummaries, fetchSummaries, params] = useCloudAPI<Page<GroupWithSummary>>(groupSummaryRequest({
        page: 0,
        itemsPerPage: 25
    }), emptyPage);

    if (group) return <DetailedGroupView name={group} />;

    return <MainContainer
        sidebar={<Button disabled={loading} onClick={() => setCreatingGroup(true)} width="100%">New Group</Button>}
        main={(
            <Pagination.List
                loading={groupSummaries.loading}
                onPageChanged={(newPage, page) =>
                    fetchSummaries(groupSummaryRequest({page: newPage, itemsPerPage: page.itemsPerPage}))}
                page={groupSummaries.data}
                pageRenderer={page =>
                    <GridCardGroup minmax={300}>
                        {page.items.map(g => (
                            <Card
                                onClick={() => history.push(`/projects/groups/${encodeURI(g.group)}`)}
                                key={g.group}
                                overflow="hidden"
                                p="8px"
                                width={1}
                                boxShadow="sm"
                                borderWidth={1}
                                borderRadius={6}
                            >
                                <SimpleGroupView group={g} />
                            </Card>
                        ))}
                    </ GridCardGroup>
                }
                customEmptyPage={<Heading.h3>You have no groups to manage.</Heading.h3>}
            />
        )}
        additional={<ReactModal isOpen={creatingGroup} shouldCloseOnEsc shouldCloseOnOverlayClick onRequestClose={() => setCreatingGroup(false)} style={defaultModalStyle}>
            <Heading.h2>New group</Heading.h2>
            <form onSubmit={createGroup}>
                <Flex>
                    <Input placeholder="Group name..." ref={createGroupRef} />
                    <Button ml="5px">Create</Button>
                </Flex>
            </form>
        </ReactModal>}
        header={null}
    />;

    async function createGroup(e: React.SyntheticEvent): Promise<void> {
        e.preventDefault();
        try {
            setLoading(true);
            const groupName = createGroupRef.current?.value ?? "";
            if (!groupName) {
                snackbarStore.addFailure("Groupname can't be empty");
                return;
            }
            await promises.makeCancelable(Client.put(baseContext, {group: groupName})).promise;
            snackbarStore.addSnack({message: "Group created", type: SnackType.Success});
            createGroupRef.current!.value = "";
            setCreatingGroup(false);
            fetchSummaries({...params});
        } catch (err) {
            snackbarStore.addFailure(errorMessageOrDefault(err, "Could not create group."));
        } finally {
            setLoading(false);
        }
    }
}

interface GroupViewProps {
    group: GroupWithSummary;
}

function SimpleGroupView({group}: GroupViewProps): JSX.Element {
    return (
        <>
            <Text mb="8px" fontSize="25px" style={{wordBreak: "break-word"}}>{group.group}</Text>
            <Icon name="projects" /> {group.numberOfMembers}
        </>
    );
}



export default GroupsOverview;

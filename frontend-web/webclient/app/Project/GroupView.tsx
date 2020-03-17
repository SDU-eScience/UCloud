import * as React from "react";
import {Card, Icon, Button, Text, Input, Flex, Checkbox, Label} from "ui-components";
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
import {Spacer} from "ui-components/Spacer";
import {History} from "history";
import {addStandardDialog} from "UtilityComponents";

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
    const history = useHistory();
    const {group} = useParams<{group?: string}>();
    const [creatingGroup, setCreatingGroup] = React.useState(false);
    const [loading, setLoading] = React.useState(false);
    const createGroupRef = React.useRef<HTMLInputElement>(null);
    const [selectedGroups, setSelectedGroups] = React.useState<Set<string>>(new Set());
    const promises = usePromiseKeeper();
    const [groupSummaries, fetchSummaries, params] = useCloudAPI<Page<GroupWithSummary>>(groupSummaryRequest({
        page: 0,
        itemsPerPage: 25
    }), emptyPage);
    const promptDeleteGroups = React.useCallback(async () => {
        const groups = [...selectedGroups];
        if (groups.length === 0) {
            snackbarStore.addFailure("You haven't selected any groups.");
            return;
        }
        addStandardDialog({
            title: "Delete groups?",
            message: <>
                <Text mb="5px">Selected groups:</Text>
                {groups.map(g => <Text key={g} fontSize="12px">{g}</Text>)}
            </>,
            onConfirm: async () => {
                try {
                    setLoading(true);
                    await Client.delete(baseContext, {groups});
                } catch (err) {
                    snackbarStore.addFailure(errorMessageOrDefault(err, "An error occurred deleting groups"));
                } finally {
                    setLoading(false);
                }
            },
            confirmText: "Delete"
        });
    }, [selectedGroups.size]);

    if (group) return <DetailedGroupView name={group} />;

    return <MainContainer
        sidebar={<>
            <Button disabled={loading} mb="5px" onClick={() => setCreatingGroup(true)} width="100%">New Group</Button>
            <Button color="red" onClick={promptDeleteGroups} width="100%">Delete groups</Button>
        </>}
        main={(
            <Pagination.List
                loading={groupSummaries.loading}
                onPageChanged={(newPage, page) =>
                    fetchSummaries(groupSummaryRequest({page: newPage, itemsPerPage: page.itemsPerPage}))}
                page={groupSummaries.data}
                pageRenderer={page =>
                    <GridCardGroup minmax={300}>
                        {page.items.map(g => {
                            const isSelected = selectedGroups.has(g.group);
                            return (
                                <Card
                                    key={g.group}
                                    overflow="hidden"
                                    p="8px"
                                    width={1}
                                    boxShadow="sm"
                                    borderWidth={1}
                                    borderRadius={6}
                                >
                                    <SimpleGroupView
                                        group={g}
                                        history={history}
                                        isSelected={isSelected}
                                        setSelected={() => {
                                            if (selectedGroups.has(g.group)) selectedGroups.delete(g.group);
                                            else selectedGroups.add(g.group);
                                            setSelectedGroups(selectedGroups);
                                        }}
                                    />
                                </Card>
                            );
                        })}
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
    setSelected: () => void;
    isSelected?: boolean;
    history: History;
}

function SimpleGroupView({group, setSelected, isSelected, history}: GroupViewProps): JSX.Element {
    const [checked, setChecked] = React.useState(isSelected);
    React.useEffect(() => {
        setChecked(isSelected);
    }, [isSelected]);
    return (
        <>
            <Spacer
                left={<Text cursor="pointer" width="auto" onClick={() => history.push(`/projects/groups/${encodeURI(group.group)}`)} mb="8px" fontSize="25px" style={{wordBreak: "break-word"}}>{group.group}</Text>}
                right={<Label ml="20px" width="30px"><Checkbox onClick={() => {setSelected(); setChecked(!checked);}} checked={checked} /></Label>}
            />
            <Icon name="projects" /> {group.numberOfMembers}
        </>
    );
}

export default GroupsOverview;

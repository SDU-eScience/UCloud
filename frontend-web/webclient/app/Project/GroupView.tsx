import * as React from "react";
import {Button, Text, Input, List, Icon} from "ui-components";
import * as Heading from "ui-components/Heading";
import {MainContainer} from "MainContainer/MainContainer";
import {useCloudAPI} from "Authentication/DataHook";
import {Page} from "Types";
import {useHistory, useParams} from "react-router";
import DetailedGroupView from "./DetailedGroupView";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {errorMessageOrDefault} from "UtilityFunctions";
import {usePromiseKeeper} from "PromiseKeeper";
import {Client} from "Authentication/HttpClientInstance";
import {SnackType} from "Snackbar/Snackbars";
import {History} from "history";
import {addStandardDialog} from "UtilityComponents";
import {emptyPage, KeyCode} from "DefaultObjects";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {loadingAction} from "Loading";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {groupSummaryRequest} from "Project/api";
import {ListRow} from "ui-components/List";

interface GroupWithSummary {
    group: string;
    numberOfMembers: number;
    members: string[];
}

const baseContext = "/projects/groups";

function GroupsOverview(props: GroupViewOperations): JSX.Element | null {
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

    // set reload
    const reload = (): void => fetchSummaries({...params});

    React.useEffect(() => {
        props.setRefresh(reload);
    }, [reload]);

    // set loading
    React.useEffect(() => {
        props.setLoading(groupSummaries.loading);
    }, [groupSummaries.loading]);

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
                    reload();
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
            <Button color="red" disabled={selectedGroups.size === 0} onClick={promptDeleteGroups} width="100%">Delete groups</Button>
        </>}
        main={(<>
            {groupSummaries.data.items.length === 0 ? <Heading.h3>You have no groups to manage.</Heading.h3> : null}
            <List>
                {creatingGroup ?
                    <ListRow
                        left={<form onSubmit={createGroup}><Input
                            pt="0px"
                            pb="0px"
                            pr="0px"
                            pl="0px"
                            noBorder
                            fontSize={20}
                            maxLength={1024}
                            onKeyDown={e => {
                                if (e.keyCode === KeyCode.ESC) {
                                    setCreatingGroup(false);
                                }
                            }}
                            borderRadius="0px"
                            type="text"
                            width="100%"
                            autoFocus
                            ref={createGroupRef}
                        /></form>}
                        leftSub={
                            <Text ml="4px" color="gray" fontSize={0}>
                                <Icon color="gray" mt="-2px" size="10" name="projects" /> 0
                            </Text>
                        }
                        right={<div />}
                        isSelected={false}
                        select={() => undefined}
                    /> : null}
                {groupSummaries.data.items.map(g => (<>
                    <ListRow
                        key={g.group}
                        left={
                            <Text
                                cursor="pointer"
                                width="auto"
                                onClick={() => history.push(`/projects/groups/${encodeURI(g.group)}`)}
                                fontSize={20}
                                style={{wordBreak: "break-word"}}
                            >
                                {g.group}
                            </Text>
                        }
                        leftSub={
                            <Text ml="4px" color="gray" fontSize={0}>
                                <Icon color="gray" mt="-2px" size="10" name="projects" /> {g.numberOfMembers}
                            </Text>
                        }
                        right={<div />}
                        isSelected={selectedGroups.has(g.group)}
                        select={() => {
                            if (selectedGroups.has(g.group)) selectedGroups.delete(g.group);
                            else selectedGroups.add(g.group);
                            setSelectedGroups(new Set(selectedGroups));
                        }}
                    />
                </>))}
            </List>
        </>)}
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

interface GroupViewOperations {
    setLoading: (loading: boolean) => void;
    setRefresh: (refresh?: () => void) => void;
}

const mapDispatchToProps = (dispatch: Dispatch): GroupViewOperations => ({
    setLoading: loading => dispatch(loadingAction(loading)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh))
});

export default connect(null, mapDispatchToProps)(GroupsOverview);

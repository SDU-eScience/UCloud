import * as React from "react";
import {Button, Text, Input, List, Icon, Flex, Truncate, Box} from "ui-components";
import * as Heading from "ui-components/Heading";
import {MainContainer} from "MainContainer/MainContainer";
import {useCloudAPI} from "Authentication/DataHook";
import {Page, Operation} from "Types";
import {useHistory, useParams} from "react-router";
import DetailedGroupView from "./DetailedGroupView";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {errorMessageOrDefault, preventDefault, stopPropagation} from "UtilityFunctions";
import {usePromiseKeeper} from "PromiseKeeper";
import {Client} from "Authentication/HttpClientInstance";
import {SnackType} from "Snackbar/Snackbars";
import {addStandardDialog} from "UtilityComponents";
import {emptyPage, KeyCode} from "DefaultObjects";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {loadingAction} from "Loading";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {groupSummaryRequest} from "Project/api";
import {ListRow} from "ui-components/List";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {updatePageTitle} from "Navigation/Redux/StatusActions";

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

    React.useEffect(() => {
        props.setTitle();
    }, []);

    // set reload
    const reload = (): void => fetchSummaries({...params});

    React.useEffect(() => {
        props.setRefresh(reload);
    }, [reload]);

    // set loading
    React.useEffect(() => {
        props.setLoading(groupSummaries.loading);
    }, [groupSummaries.loading]);

    const operations: GroupOperation[] = [{
        disabled: groups => groups.length === 0,
        onClick: (groups) => promptDeleteGroups(groups),
        icon: "trash",
        text: "Delete",
        color: "red"
    }];

    async function promptDeleteGroups(groups: GroupWithSummary[]): Promise<void> {
        if (groups.length === 0) {
            snackbarStore.addFailure("You haven't selected any groups.");
            return;
        }
        addStandardDialog({
            title: "Delete groups?",
            message: <>
                <Text mb="5px">Selected groups:</Text>
                {groups.map(g => <Text key={g.group} fontSize="12px">{g.group}</Text>)}
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
        setSelectedGroups(new Set());
    }

    if (group) return <DetailedGroupView name={group} />;

    return <MainContainer
        sidebar={<>
            <Button disabled={loading} mb="5px" onClick={() => setCreatingGroup(true)} width="100%">New Group</Button>
            {selectedGroups.size > 0 ? `${selectedGroups.size} group${selectedGroups.size > 1 ? "s" : ""} selected` : null}
            <GroupOperations groupOperations={operations} selectedGroups={groupSummaries.data.items.filter(it => selectedGroups.has(it.group))} />
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
                        left={g.group}
                        navigate={() => history.push(`/projects/groups/${encodeURI(g.group)}`)}
                        leftSub={
                            <Text ml="4px" color="gray" fontSize={0}>
                                <Icon color="gray" mt="-2px" size="10" name="projects" /> {g.numberOfMembers}
                            </Text>
                        }
                        right={selectedGroups.size === 0 ? <div onClick={stopPropagation}><ClickableDropdown
                            width="125px"
                            left="-105px"
                            trigger={(
                                <Icon
                                    onClick={preventDefault}
                                    mr="10px"
                                    name="ellipsis"
                                    size="1em"
                                    rotation={90}
                                />
                            )}
                        >
                            <GroupOperations groupOperations={operations} selectedGroups={[g]} />
                        </ClickableDropdown></div> : null}
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
        header={<Heading.h3>Groups for {Client.projectId} </Heading.h3>}
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

type GroupOperation = Operation<GroupWithSummary>;

interface GroupOperationsProps {
    selectedGroups: GroupWithSummary[];
    groupOperations: GroupOperation[];
}

function GroupOperations(props: GroupOperationsProps): JSX.Element | null {
    if (props.groupOperations.length === 0) return null;

    function GroupOp(op: GroupOperation): JSX.Element | null {
        if (op.disabled(props.selectedGroups, Client)) return null;
        return <span onClick={() => op.onClick(props.selectedGroups, Client)}>
            <Icon size={16} mr="1em" color={op.color} name={op.icon} />{op.text}</span>;
    }

    return (
        <Flex
            ml="-17px"
            mr="-17px"
            cursor="pointer"
            pl="15px">
            {props.groupOperations.map(GroupOp)}
        </Flex>
    );
}


interface GroupViewOperations {
    setLoading: (loading: boolean) => void;
    setRefresh: (refresh?: () => void) => void;
    setTitle(): void;
}

const mapDispatchToProps = (dispatch: Dispatch): GroupViewOperations => ({
    setTitle: () => dispatch(updatePageTitle("Groups")),
    setLoading: loading => dispatch(loadingAction(loading)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh))
});

export default connect(null, mapDispatchToProps)(GroupsOverview);

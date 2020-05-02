import * as React from "react";
import * as Pagination from "Pagination";
import {Button, Text, Input, List, Icon, Flex} from "ui-components";
import * as Heading from "ui-components/Heading";
import {Operation} from "Types";
import {useHistory} from "react-router";
import DetailedGroupView from "./DetailedGroupView";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {errorMessageOrDefault, preventDefault, stopPropagation} from "UtilityFunctions";
import {usePromiseKeeper} from "PromiseKeeper";
import {Client} from "Authentication/HttpClientInstance";
import {addStandardDialog} from "UtilityComponents";
import {KeyCode} from "DefaultObjects";
import {ListRow} from "ui-components/List";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {BreadCrumbsBase} from "ui-components/Breadcrumbs";
import {useProjectManagementStatus} from "Project/View";
import {groupSummaryRequest} from "Project/api";

export interface GroupWithSummary {
    group: string;
    numberOfMembers: number;
    members: string[];
}

const baseContext = "/projects/groups";

const GroupsOverview: React.FunctionComponent = props => {
    const history = useHistory();
    const {projectId, group, groupSummaries, fetchSummaries, groupSummaryParams} = useProjectManagementStatus();

    const [creatingGroup, setCreatingGroup] = React.useState(false);
    const [, setLoading] = React.useState(false);
    const createGroupRef = React.useRef<HTMLInputElement>(null);
    const promises = usePromiseKeeper();

    const operations: GroupOperation[] = [{
        disabled: groups => groups.length === 0,
        onClick: (groups) => promptDeleteGroups(groups),
        icon: "trash",
        text: "Delete",
        color: "red"
    }];

    async function promptDeleteGroups(groups: GroupWithSummary[]): Promise<void> {
        if (groups.length === 0) {
            snackbarStore.addFailure("You haven't selected any groups.", false);
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
                    fetchSummaries({...groupSummaryParams});
                } catch (err) {
                    snackbarStore.addFailure(errorMessageOrDefault(err, "An error occurred deleting groups"), false);
                } finally {
                    setLoading(false);
                }
            },
            confirmText: "Delete"
        });
    }

    if (group) return <DetailedGroupView/>;

    // TODO Paging is missing
    return <>
        <BreadCrumbsBase>
            <li><span>Groups</span></li>
        </BreadCrumbsBase>

        <Pagination.List
            loading={groupSummaries.loading}
            page={groupSummaries.data}
            customEmptyPage={<Heading.h3>You have no groups to manage.</Heading.h3>}
            onPageChanged={(newPage, oldPage) => {
                fetchSummaries(groupSummaryRequest({page: newPage, itemsPerPage: oldPage.itemsPerPage}));
            }}
            pageRenderer={() => (<>
                    <List>
                        {groupSummaries.data.items.map(g => (<>
                            <ListRow
                                key={g.group}
                                left={g.group}
                                navigate={() => history.push(`/projects/view/${projectId}/${g.group}`)}
                                leftSub={
                                    <Text ml="4px" color="gray" fontSize={0}>
                                        <Icon color="gray" mt="-2px" size="10" name="projects"/> {g.numberOfMembers}
                                    </Text>
                                }
                                right={
                                    <ClickableDropdown
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
                                        <GroupOperations groupOperations={operations} selectedGroups={[g]}/>
                                    </ClickableDropdown>
                                }
                                isSelected={false}
                            />
                        </>))}

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
                                        <Icon color="gray" mt="-2px" size="10" name="projects"/> 0
                                    </Text>
                                }
                                right={<div/>}
                                isSelected={false}
                                select={() => undefined}
                            /> : null}
                    </List>
                    <Flex justifyContent={"center"}>
                        <Button width={"50%"} onClick={() => setCreatingGroup(true)}>New Group</Button>
                    </Flex>
                </>
            )}
        />


    </>;

    async function createGroup(e: React.SyntheticEvent): Promise<void> {
        e.preventDefault();
        try {
            setLoading(true);
            const groupName = createGroupRef.current?.value ?? "";
            if (!groupName) {
                snackbarStore.addFailure("Groupname can't be empty", false);
                return;
            }
            await promises.makeCancelable(Client.put(baseContext, {group: groupName})).promise;
            snackbarStore.addSuccess(`Group ${group} created`, true);
            createGroupRef.current!.value = "";
            setCreatingGroup(false);
            fetchSummaries({...groupSummaryParams});
        } catch (err) {
            snackbarStore.addFailure(errorMessageOrDefault(err, "Could not create group."), false);
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
            <Icon size={16} mr="1em" color={op.color} name={op.icon}/>{op.text}</span>;
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

export default GroupsOverview;

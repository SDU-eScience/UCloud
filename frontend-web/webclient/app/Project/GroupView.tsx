import * as React from "react";
import {Card, Icon, Button, Text, Heading} from "ui-components";
import {MainContainer} from "MainContainer/MainContainer";
import * as Pagination from "Pagination";
import {emptyPage} from "DefaultObjects";
import {useCloudAPI, APICallParameters} from "Authentication/DataHook";
import {Page} from "Types";
import LoadingSpinner from "LoadingIcon/LoadingIcon";
import {GridCardGroup} from "ui-components/Grid";
import {useHistory, useParams} from "react-router";
import DetailedGroupView from "./DetailedGroupView";

interface GroupWithSummary {
    group: string;
    numberOfMembers: number;
    members: string[];
}

function newGroupWithSummary(): GroupWithSummary {
    return {
        group: "Name of the group that has a pretty long name",
        numberOfMembers: (Math.random() * 50) | 0,
        members: ["Hello", "My", "Name", "Is", "Internal", "Server", "Error", "and", "this", "is", "my", "friend", "segmentation", "fault"]
    };
}

function GroupsOverview(): JSX.Element | null {
    // TODO -- Add groups. Remove groups.
    // File imports of users
    const history = useHistory();
    const {group} = useParams<{group?: string}>();
    const [groupSummaries, doFetch, params] = useCloudAPI<Page<GroupWithSummary>>({}, {...emptyPage});

    if (group) {
        return <DetailedGroupView name={group} />;
    }

    return <MainContainer
        sidebar={<Button width="100%">New Group</Button>}
        main={(
            <Pagination.List
                loading={groupSummaries.loading}
                onPageChanged={page => {throw Error("TODO");}}
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
                customEmptyPage={<Heading>You have no groups.</Heading>}
            />
        )}
        header={null}
    />;
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

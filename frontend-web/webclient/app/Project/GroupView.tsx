import * as React from "react";
import {Card, Icon, Button, Grid, Box, Flex} from "ui-components";
import {MainContainer} from "MainContainer/MainContainer";
import * as Pagination from "Pagination";
import * as Heading from "ui-components/Heading";
import * as ReactModal from "react-modal";
import {emptyPage} from "DefaultObjects";
import {useCloudAPI} from "Authentication/DataHook";
import {Page} from "Types";
import {defaultModalStyle} from "Utilities/ModalUtilities";
import {UserAvatar} from "AvataaarLib/UserAvatar";
import {defaultAvatar} from "UserSettings/Avataaar";

interface GroupWithSummary {
    group: string;
    numberOfMembers: number;
    members: string[];
}

function newGroupWithSummary(): GroupWithSummary {
    return {
        group: "Name of the group that has a pretty long name",
        numberOfMembers: (Math.random() * 50) | 0,
        members: ["Hello", "My", "Name", "Is", "Internal", "Server", "Error"]
    };
}

function GroupsOverview(): JSX.Element {
    // TODO -- Add groups. Remove groups.
    // File imports of users
    const [groups, doFetch, params] = useCloudAPI<Page<GroupWithSummary>>({}, emptyPage);
    const [activeGroup, setActiveGroup] = React.useState<GroupWithSummary | null>(null);

    if (!groups.data.items.length)
        for (let i = 0; i < 10; i++) groups.data.items.push(newGroupWithSummary());
    groups.data.itemsInTotal = 10;

    React.useEffect(() => {
        console.log("Params changed");
    }, [params]);

    return <MainContainer
        sidebar={<Button width="100%">New Group</Button>}
        main={(
            <Grid
                gridTemplateColumns="repeat(auto-fit, minmax(265px, 280px))"
                style={{overflowY: "scroll"}}
            >
                {groups.data.items.map(group => (
                    <Box onClick={() => setActiveGroup(group)} key={group.group}>
                        <SimpleGroupView group={group} />
                    </Box>
                ))}
            </Grid>
        )}
        header={null}
        additional={
            <ReactModal
                shouldCloseOnEsc
                shouldCloseOnOverlayClick
                onRequestClose={() => setActiveGroup(null)}
                style={defaultModalStyle}
                isOpen={activeGroup != null}
            >
                <DetailedGroupView group={activeGroup ?? newGroupWithSummary()} />
            </ReactModal>
        }
    />;
}

interface GroupViewProps {
    group: GroupWithSummary;
}

function SimpleGroupView({group}: GroupViewProps): JSX.Element {
    return (
        <Card backgroundColor="lightGray" borderRadius="5px" cursor="pointer" mt="5px" p="8px" width="265px" height="auto">
            <Heading.h3>{group.group}</Heading.h3>
            <Icon name="projects" /> {group.numberOfMembers}
        </Card>
    );
}

function DetailedGroupView({group}: GroupViewProps): JSX.Element {
    const [avatars, setAvatars] = React.useState([]);
    return (
        <div>
            <Heading.h4>{group.group}</Heading.h4>
            <Heading.h5>{group.numberOfMembers}</Heading.h5>
            <Box overflowY="scroll">
                {group.members.map(member =>
                    <Flex key={member}>
                        <UserAvatar avatar={defaultAvatar} /> {member}
                    </Flex>
                )}
            </Box>
        </div>
    );
}

export default GroupsOverview;

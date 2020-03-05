import * as React from "react";
import {Card, Icon, Button, Box, Flex} from "ui-components";
import {MainContainer} from "MainContainer/MainContainer";
import * as Pagination from "Pagination";
import * as Heading from "ui-components/Heading";
import {Text} from "ui-components"
import * as ReactModal from "react-modal";
import {emptyPage} from "DefaultObjects";
import {useCloudAPI} from "Authentication/DataHook";
import {Page} from "Types";
import {defaultModalStyle} from "Utilities/ModalUtilities";
import {UserAvatar} from "AvataaarLib/UserAvatar";
import {defaultAvatar} from "UserSettings/Avataaar";
import {GridCardGroup} from "ui-components/Grid";

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

function GroupsOverview(): JSX.Element {
    // TODO -- Add groups. Remove groups.
    // File imports of users
    const [groups, doFetch, params] = useCloudAPI<Page<GroupWithSummary>>({}, emptyPage);
    const [activeGroup, setActiveGroup] = React.useState<GroupWithSummary | null>(null);

    if (!groups.data.items.length) {
        for (let i = 0; i < 10; i++) groups.data.items.push(newGroupWithSummary());
        groups.data.itemsInTotal = 10;
        groups.data.items[5].group = groups.data.items[5].group.slice(0, 10);
        groups.data.items[9].group = groups.data.items[9].group.concat(groups.data.items[9].group).split(" ").join();
        groups.data.items[9].group = groups.data.items[9].group.concat(groups.data.items[9].group);
    }

    React.useEffect(() => {
        console.log("Params changed");
    }, [params]);

    return <MainContainer
        sidebar={<Button width="100%">New Group</Button>}
        main={(
            <GridCardGroup minmax={300}>
                {groups.data.items.map(group => (
                    <Card cursor="pointer" onClick={() => setActiveGroup(group)} key={group.group} overflow="hidden" p="8px" width={1} boxShadow="sm" borderWidth={1} borderRadius={6}>
                        <SimpleGroupView group={group} />
                    </Card>
                ))}
            </GridCardGroup>
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
        <>
            <Text mb="8px" fontSize="25px" style={{wordBreak: "break-word"}}>{group.group}</Text>
            <Icon name="projects" /> {group.numberOfMembers}
        </>
    );
}

function DetailedGroupView({group}: GroupViewProps): JSX.Element {
    const [avatars, setAvatars] = React.useState([]);
    return (
        <div>
            <div>
                <Text
                    style={{wordBreak: "break-word"}}
                    p="6px"
                    fontSize="25px"
                    width="100%"
                >{group.group}</Text>
                <Heading.h5>Members: {group.numberOfMembers}</Heading.h5>
            </div>
            <div>
                <Box overflowY="scroll">
                    {group.members.map(member =>
                        <Flex key={member}>
                            <UserAvatar avatar={defaultAvatar} /> <Text mt="10px">{member}</Text>
                        </Flex>
                    )}
                </Box>
            </div>
        </div>
    );
}

export default GroupsOverview;

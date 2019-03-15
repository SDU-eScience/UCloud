import * as React from "react";
import { ActivityGroup } from "Activity";
import * as Module from "Activity";
import * as moment from "moment";
import { getFilenameFromPath, replaceHomeFolder } from "Utilities/FileUtilities";
import { fileInfoPage } from "Utilities/FileUtilities";
import Icon, { IconName } from "ui-components/Icon";
import { Flex, Text, Link, Box } from "ui-components";
import Table, { TableRow, TableCell, TableBody, TableHeader, TableHeaderCell } from "ui-components/Table";
import { Dropdown } from "ui-components/Dropdown";
import { Cloud } from "Authentication/SDUCloudObject";
import styled from "styled-components";
import { EllipsedText, TextSpan } from "ui-components/Text";

export class ActivityFeedFrame extends React.PureComponent<{ containerRef?: React.RefObject<any> }> {
    /*
    shouldComponentUpdate(nextProps) {
        return this.props.children.length !== nextProps.children.length;
    }
    */

    render() {
        return <Table>
            <TableHeader>
                <TFRow>
                    <TableHeaderCell width="12em" />
                    <TableHeaderCell width="10.5em" />
                    <TableHeaderCell width="99%" />
                </TFRow>
            </TableHeader>
            <TableBody ref={this.props.containerRef}>
                {/* {activity.map((a, i) => <ActivityFeedItem key={i} activity={a} />)} */}
                {this.props.children}
            </TableBody>
        </Table>;
    }

}

export const ActivityFeed = ({ activity }: { activity: Module.Activity[] }) => null;
// export const ActivityFeed = ({ activity }: { activity: Module.Activity[] }) =>
// <ActivityFeedFrame activity={groupActivity(activity)!} />;

const OperationText: React.FunctionComponent<{ event: Module.Activity }> = props => {
    switch (props.event.type) {
        case Module.ActivityType.MOVED: {
            return <TextSpan>
                was moved to
                {" "}
                <TextSpan bold>
                    <Link to={fileInfoPage((props.event as Module.MovedActivity).newName)}>
                        <EllipsedText maxWidth={"100%"}>
                            {replaceHomeFolder((props.event as Module.MovedActivity).newName, Cloud.homeFolder)}
                        </EllipsedText>
                    </Link>
                </TextSpan>
            </TextSpan>;
        }

        case Module.ActivityType.FAVORITE: {
            const isFavorite = (props.event as Module.FavoriteActivity).favorite;
            if (isFavorite) {
                return <TextSpan>was <TextSpan bold>added to favorites</TextSpan></TextSpan>;
            } else {
                return <TextSpan>was <TextSpan bold>removed from favorites</TextSpan></TextSpan>;
            }
        }

        default: {
            return <TextSpan>was <TextSpan bold>{operationToPastTense(props.event.type)}</TextSpan></TextSpan>;
        }
    }
};

const ActivityEvent: React.FunctionComponent<{ event: Module.Activity }> = props => (
    <Text fontSize={1}>
        <TextSpan bold>
            <Link to={fileInfoPage(props.event.originalFilePath)}>
                <EllipsedText maxWidth={"100%"}>
                    {getFilenameFromPath(props.event.originalFilePath)}
                </EllipsedText>
            </Link>
        </TextSpan>
        {" "}
        <OperationText event={props.event} />
    </Text>

);

export const ActivityFeedItem = ({ activity, style }: { activity: ActivityGroup, style?: React.CSSProperties }) => (
    <TFRow style={style}>
        <TableCell>
            <Dropdown>
                <Text fontSize={1} color="text">
                    {moment(new Date(activity.newestTimestamp)).fromNow()}
                    <br />
                    {moment(new Date(activity.newestTimestamp)).format("lll")}
                </Text>
            </Dropdown>
        </TableCell>
        <TableCell>
            <Flex>
                <Icon mr="0.5em" name={eventIcon(activity.type).icon} />
                <Text fontSize={2}>{`Files ${operationToPastTense(activity.type)}`}</Text>
            </Flex>
        </TableCell>
        <TableCell>
            {activity.items.map((item, idx) =>
                <ActivityEvent key={idx} event={item} />
            )}

            {!!activity.numberOfHiddenResults ?
                <Box mt={16}>
                    <Text bold>{activity.numberOfHiddenResults} similar results were hidden</Text>
                </Box>
                :
                null
            }
        </TableCell>
    </TFRow>
);

const operationToPastTense = (operation: Module.ActivityType): string => {
    switch (operation) {
        case Module.ActivityType.DELETED: return "deleted";
        case Module.ActivityType.DOWNLOAD: return "downloaded";
        case Module.ActivityType.FAVORITE: return "favorited";
        case Module.ActivityType.INSPECTED: return "inspected";
        case Module.ActivityType.MOVED: return "moved";
        case Module.ActivityType.UPDATED: return "updated";
    }
}

interface EventIconAndColor {
    icon: IconName
}

const eventIcon = (operation: Module.ActivityType): EventIconAndColor => {
    switch (operation) {
        case Module.ActivityType.FAVORITE:
            return { icon: "starFilled" };
        case Module.ActivityType.DOWNLOAD:
            return { icon: "download" };
        case Module.ActivityType.UPDATED:
            return { icon: "refresh" };
        case Module.ActivityType.DELETED:
            return { icon: "close" };
        case Module.ActivityType.MOVED:
            return { icon: "move" };
        default:
            return { icon: "ellipsis" };
    }
}

function groupActivity(items: Module.Activity[] = []): ActivityGroup[] {
    const result: ActivityGroup[] = [];
    let currentGroup: ActivityGroup | null = null;

    const pushGroup = () => {
        if (currentGroup != null) {
            result.push(currentGroup);
            currentGroup = null;
        }
    };

    const initializeGroup = (item: Module.Activity) => {
        currentGroup = {
            type: item.type,
            newestTimestamp: item.timestamp,
            items: [item],
            numberOfHiddenResults: null
        };
    };

    items.forEach(item => {
        if (currentGroup === null) {
            initializeGroup(item);
        } else {
            if (currentGroup.type !== item.type || Math.abs(item.timestamp - currentGroup.newestTimestamp) > (1000 * 60 * 15)) {
                pushGroup();
                initializeGroup(item);
            } else {
                currentGroup.items.push(item);
            }
        }
    });

    pushGroup();
    return result;
}

const TFRow = styled(TableRow)`
    vertical-align: top;
`;

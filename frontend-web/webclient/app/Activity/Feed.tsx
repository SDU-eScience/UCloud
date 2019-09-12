import * as Module from "Activity";
import {Cloud} from "Authentication/SDUCloudObject";
import {format, formatDistanceToNow} from "date-fns/esm";
import * as React from "react";
import {Link as ReactRouterLink} from "react-router-dom";
import styled from "styled-components";
import {Box, Flex, Text} from "ui-components";
import Icon, {IconName} from "ui-components/Icon";
import Table, {TableBody, TableCell, TableHeader, TableHeaderCell, TableRow} from "ui-components/Table";
import {colors} from "ui-components/theme";
import {fileInfoPage, getFilenameFromPath, replaceHomeFolder} from "Utilities/FileUtilities";

export function ActivityFeedFrame(props: {containerRef?: React.RefObject<any>, children?: JSX.Element[]}) {
    return <Table>
        <TableHeader>
            <TFRow>
                <TableHeaderCell width="12em" />
                <TableHeaderCell width="10.5em" />
                <TableHeaderCell width="99%" />
            </TFRow>
        </TableHeader>
        <TableBody ref={props.containerRef}>
            {props.children}
        </TableBody>
    </Table >;
}

export const ActivityFeed = ({activity}: {activity: Module.Activity[]}) => (
    <ActivityFeedFrame>
        {groupActivity(activity).map((a, i) => <ActivityFeedItem key={i} activity={a} />)}
    </ActivityFeedFrame>
);

// Performance note: Don't use styled components here.
const ActivityEvent: React.FunctionComponent<{event: Module.Activity}> = props => (
    <div>
        <b>
            <ReactRouterLink to={fileInfoPage(props.event.originalFilePath)}>
                <div className="ellipsis">
                    <Text color="black">{getFilenameFromPath(props.event.originalFilePath)}</Text>
                </div>
            </ReactRouterLink>
        </b>
        {" "}
        <OperationText event={props.event} />
    </div>
);

// Performance note: Don't use styled components here.
const OperationText: React.FunctionComponent<{event: Module.Activity}> = props => {
    switch (props.event.type) {
        case Module.ActivityType.MOVED: {
            return <span>
                was moved to
                {" "}
                <b>
                    <ReactRouterLink to={fileInfoPage((props.event as Module.MovedActivity).newName)}>
                        <div className="ellipsis">
                            <Text color="black">
                                {replaceHomeFolder((props.event as Module.MovedActivity).newName, Cloud.homeFolder)}
                            </Text>
                        </div>
                    </ReactRouterLink>
                </b>
            </span>;
        }

        case Module.ActivityType.FAVORITE: {
            const isFavorite = (props.event as Module.FavoriteActivity).favorite;
            if (isFavorite) {
                return <span>was <b>added to favorites</b></span>;
            } else {
                return <span>was <b>removed from favorites</b></span>;
            }
        }

        default: {
            return <span>was <b>{operationToPastTense(props.event.type)}</b></span>;
        }
    }
};

export const ActivityFeedSpacer = (props: {height: number}) => (
    <tr style={{height: `${props.height}px`}} />
);

interface ActivityFeedProps {
    activity: Module.ActivityGroup;
}

export class ActivityFeedItem extends React.Component<ActivityFeedProps> {
    public shouldComponentUpdate(nextProps: ActivityFeedProps) {
        return this.props.activity.newestTimestamp !== nextProps.activity.newestTimestamp;
    }

    public render() {
        const {activity} = this.props;
        return <TFRow>
            <TableCell>
                <Text fontSize={1} color="text">
                    {formatDistanceToNow(new Date(activity.newestTimestamp))}
                    <br />
                    {format(new Date(activity.newestTimestamp), "d LLL yyyy HH:mm")}
                </Text>
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
        </TFRow>;
    }
}

const operationToPastTense = (operation: Module.ActivityType): string => {
    switch (operation) {
        case Module.ActivityType.DELETED:
            return "deleted";
        case Module.ActivityType.DOWNLOAD:
            return "downloaded";
        case Module.ActivityType.FAVORITE:
            return "favorited";
        case Module.ActivityType.INSPECTED:
            return "inspected";
        case Module.ActivityType.MOVED:
            return "moved";
        case Module.ActivityType.UPDATED:
            return "updated";
    }
};

interface EventIconAndColor {
    icon: IconName;
}

const eventIcon = (operation: Module.ActivityType): EventIconAndColor => {
    switch (operation) {
        case Module.ActivityType.FAVORITE:
            return {icon: "starFilled"};
        case Module.ActivityType.DOWNLOAD:
            return {icon: "download"};
        case Module.ActivityType.UPDATED:
            return {icon: "refresh"};
        case Module.ActivityType.DELETED:
            return {icon: "close"};
        case Module.ActivityType.MOVED:
            return {icon: "move"};
        default:
            return {icon: "ellipsis"};
    }
};

function groupActivity(items: Module.Activity[] = []): Module.ActivityGroup[] {
    const result: Module.ActivityGroup[] = [];
    let currentGroup: Module.ActivityGroup | null = null;

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
            if (currentGroup.type !== item.type ||
                Math.abs(item.timestamp - currentGroup.newestTimestamp) > (1000 * 60 * 15)) {
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

    & a {
        color: ${colors["text"]}
    }

    & a:hover {
        color: ${colors["textHighlight"]}
    }

    & div.ellipsis {
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        max-width: 100%;
        display: inline-block;
        vertical-align: bottom;
    }
`;

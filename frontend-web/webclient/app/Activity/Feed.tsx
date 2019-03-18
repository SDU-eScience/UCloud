import * as React from "react";
import { ActivityGroup } from "Activity";
import * as Module from "Activity";
import * as moment from "moment";
import { getFilenameFromPath, replaceHomeFolder } from "Utilities/FileUtilities";
import { fileInfoPage } from "Utilities/FileUtilities";
import Icon, { IconName } from "ui-components/Icon";
import { Flex, Text, Link, Box } from "ui-components";
import Table, { TableRow, TableCell, TableBody, TableHeader, TableHeaderCell } from "ui-components/Table";
import { Cloud } from "Authentication/SDUCloudObject";
import styled from "styled-components";
import { Link as ReactRouterLink } from "react-router-dom";
import { EllipsedText, TextSpan } from "ui-components/Text";
import { colors } from "ui-components/theme";

export class ActivityFeedFrame extends React.PureComponent<{ containerRef?: React.RefObject<any> }> {
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
                {this.props.children}
            </TableBody>
        </Table>;
    }

}

export const ActivityFeed = ({ activity }: { activity: Module.Activity[] }) => (
    <ActivityFeedFrame>
        {groupActivity(activity).map((a, i) => <ActivityFeedItem key={i} activity={a} />)}
    </ActivityFeedFrame>
);

const OperationText: React.FunctionComponent<{ event: Module.Activity }> = props => {
    switch (props.event.type) {
        case Module.ActivityType.MOVED: {
            return <span>
                was moved to
                {" "}
                <b>
                    <ReactRouterLink to={fileInfoPage((props.event as Module.MovedActivity).newName)}>
                        <div className="ellipsis">
                            {replaceHomeFolder((props.event as Module.MovedActivity).newName, Cloud.homeFolder)}
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

const ActivityEvent: React.FunctionComponent<{ event: Module.Activity }> = props => (
    <div>
        <b>
            <ReactRouterLink to={fileInfoPage(props.event.originalFilePath)}>
                <div className="ellipsis">
                    {getFilenameFromPath(props.event.originalFilePath)}
                </div>
            </ReactRouterLink>
        </b>
        {" "}
        <OperationText event={props.event} />
    </div>
);

export const ActivityFeedSpacer = (props: { height: number }) => (
    <tr style={{ height: `${props.height}px` }} />
)

interface ActivityFeedProps {
    activity: ActivityGroup
}

export class ActivityFeedItem extends React.Component<ActivityFeedProps> {
    shouldComponentUpdate(nextProps: ActivityFeedProps) {
        return this.props.activity.newestTimestamp !== nextProps.activity.newestTimestamp;
    }

    render() {
        const { activity } = this.props;
        return <TFRow>
            <TableCell>
                <Text fontSize={1} color="text">
                    {moment(new Date(activity.newestTimestamp)).fromNow()}
                    <br />
                    {moment(new Date(activity.newestTimestamp)).format("lll")}
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
        </TFRow>

    }
}

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

    & a {
        color: ${colors["text"]}
    }

    & div.ellipsis {
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        max-width: 100%;
        display: inline-block;
        vertical-align: bottom;
    }

    & a:hover {
        color: ${colors["textHighlight"]}
    }
`;

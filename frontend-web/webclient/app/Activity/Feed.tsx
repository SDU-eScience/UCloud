import * as Module from "Activity";
import {Client} from "Authentication/HttpClientInstance";
import {format, formatDistanceToNow} from "date-fns/esm";
import * as React from "react";
import {Link as ReactRouterLink} from "react-router-dom";
import styled from "styled-components";
import {Box, Flex, Text} from "ui-components";
import Icon, {IconName} from "ui-components/Icon";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "ui-components/Table";
import {fileInfoPage, getFilenameFromPath, replaceHomeFolder} from "Utilities/FileUtilities";

export const ActivityFeedFrame: React.FC<{containerRef?: React.RefObject<HTMLTableSectionElement>}> = props => {
    return (
        <Table>
            <TableHeader>
                <TFRow>
                    <TableHeaderCell width="12em" />
                    <TableHeaderCell width="99%" />
                </TFRow>
            </TableHeader>
            <tbody ref={props.containerRef}>
                {props.children}
            </tbody>
        </Table >
    );
};

export const ActivityFeed = ({activity}: {activity: Module.ActivityForFrontend[]}) => (
    <ActivityFeedFrame>
        { activity.map ((a,i) => <ActivityFeedItem key={i} activity={a} />)}
    </ActivityFeedFrame>
);

// Performance note: Don't use styled components here.
const ActivityEvent: React.FunctionComponent<{event: Module.ActivityForFrontend}> = props => (
    <div>
        <b>
            <ReactRouterLink to={fileInfoPage(props.event.activityEvent.filePath)}>
                <div className="ellipsis">
                    <Text color="black">{getFilenameFromPath(props.event.activityEvent.filePath)}</Text>
                </div>
            </ReactRouterLink>
        </b>
        {" "}
        <OperationText event={props.event} />
    </div>
);

// Performance note: Don't use styled components here.
const OperationText: React.FunctionComponent<{event: Module.ActivityForFrontend}> = props => {
    switch (props.event.type) {
        case Module.ActivityType.MOVED: {
            return (
                <span>
                    was moved to
                {" "}
                    <b>
                        <ReactRouterLink to={fileInfoPage((props.event.activityEvent as Module.MovedActivity).newName)}>
                            <div className="ellipsis">
                                <Text color="black">
                                    {replaceHomeFolder((props.event.activityEvent as Module.MovedActivity).newName, Client.homeFolder)}
                                </Text>
                            </div>
                        </ReactRouterLink>
                    </b>
                </span>
            );
        }

        case Module.ActivityType.FAVORITE: {
            const isFavorite = (props.event.activityEvent as Module.FavoriteActivity).isFavorite;
            if (isFavorite) {
                return <span>was <b>added to favorites</b></span>;
            } else {
                return <span>was <b>removed from favorites</b></span>;
            }
        }

        case Module.ActivityType.SHAREDWITH: {
            const share = (props.event.activityEvent as Module.SharedWithActivity);
            return <span> was <b>shared with {share.sharedWith} with rights({share.status})</b></span>;
        }

        case Module.ActivityType.UPDATEDACL: {
            const update = (props.event.activityEvent as Module.UpdatedACLActivity);
            return <span> had ACL for {update.rightsAndUser[0].second} updated to {update.rightsAndUser[0].first}</span>
        }

        case Module.ActivityType.USEDINAPP: {
            const used = (props.event.activityEvent as Module.SingleFileUsedActivity);
            if (used.filePath == "") {
                return <span> No files where used in {used.applicationName}:{used.applicationVersion}</span>
            }
            else {
                return <span> where used in {used.applicationName}:{used.applicationVersion}</span>
            }        }
        case Module.ActivityType.ALLUSEDINAPP: {
            const used = (props.event.activityEvent as Module.AllFilesUsedActivity);
            if (used.filePath == "") {
                return <span> No files were used in {used.applicationName}:{used.applicationVersion}</span>
            }
            else {
                return <span> were used in {used.applicationName}:{used.applicationVersion}</span>
            }
        }
        case Module.ActivityType.RECLASSIFIED: {
            const reclassify = (props.event.activityEvent as Module.ReclassifyActivity);
            return <span> changed sensitivity to {reclassify.newSensitivity} </span>
        }

        case Module.ActivityType.COPIED: {
            const copy = (props.event.activityEvent as Module.CopyActivity);
            return <span> was copied. Copy name: {copy.copyFilePath}</span>
        }

        default: {
            return <span>was <b>{operationToPastTense(props.event.type)}</b></span>;
        }
    }
};

export const ActivityFeedSpacer = (props: {height: number}): JSX.Element => (
    <tr style={{height: `${props.height}px`}} />
);

interface ActivityFeedProps {
    activity: Module.ActivityForFrontend;
}

export class ActivityFeedItem extends React.Component<ActivityFeedProps> {
    public shouldComponentUpdate(nextProps: ActivityFeedProps): boolean {
        return this.props.activity.timestamp !== nextProps.activity.timestamp;
    }

    public render(): JSX.Element {
        const {activity} = this.props;
        return (
            <TFRow>
                <TableCell>
                    <Text fontSize={1} color="text">
                        {formatDistanceToNow(new Date(activity.timestamp))}
                        <br />
                        {format(new Date(activity.timestamp), "d LLL yyyy HH:mm")}
                    </Text>
                </TableCell>
                <TableCell>
                    <Flex>
                        <Icon mr="0.5em" name={eventIcon(activity.type).icon} />
                        <ActivityEvent key={activity.type} event={activity} />
                    </Flex>
                </TableCell>
            </TFRow>
        );
    }
}

const operationToPastTense = (operation: Module.ActivityType): string => {
    switch (operation) {
        case Module.ActivityType.DELETED:
            return "deleted";
        case Module.ActivityType.DOWNLOAD:
            return "downloaded";
        case Module.ActivityType.MOVED:
            return "moved";
        case Module.ActivityType.COPIED:
            return "copied";
        case Module.ActivityType.ALLUSEDINAPP:
            return "used";
        case Module.ActivityType.DIRECTORYCREATED:
            return "directory was created";
        case Module.ActivityType.UPLOADED:
            return "uploaded";
        case Module.ActivityType.USEDINAPP:
            return "used";
        default:
            return "DEFUALTAJDJILWA"
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
        case Module.ActivityType.DELETED:
            return {icon: "close"};
        case Module.ActivityType.MOVED:
            return {icon: "move"};
        case Module.ActivityType.UPLOADED:
            return {icon: "upload"};
        case Module.ActivityType.COPIED:
            return {icon: "copy"};
        case Module.ActivityType.USEDINAPP:
            return {icon: "favIcon"};
        case Module.ActivityType.ALLUSEDINAPP:
            return {icon: "favIcon"};
        case Module.ActivityType.DIRECTORYCREATED:
            return {icon: "files"}
        case Module.ActivityType.UPDATEDACL:
            return {icon: "key"}
        case Module.ActivityType.SHAREDWITH:
            return {icon: "share"}
        case Module.ActivityType.RECLASSIFIED:
            return {icon: "sensitivity"}
        default:
            return {icon: "ellipsis"};
    }
};

const TFRow = styled(TableRow)`
    vertical-align: top;

    & a {
        color: var(--text, #f00);
    }

    & a:hover {
        color: var(--text, #f00);
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

import * as React from "react";
import { connect } from "react-redux";
import { GroupedActivity, ActivityProps, ActivityDispatchProps } from "Activity";
import * as Module from "Activity";
import * as Pagination from "Pagination";
import * as moment from "moment";
import { getFilenameFromPath, replaceHomeFolder } from "Utilities/FileUtilities";
import { ActivityReduxObject, ReduxObject } from "DefaultObjects";
import { fetchActivity, setErrorMessage, setLoading } from "./Redux/ActivityActions";
import { updatePageTitle, setActivePage } from "Navigation/Redux/StatusActions";
import { Dispatch } from "redux";
import { fileInfoPage } from "Utilities/FileUtilities";
import * as Heading from "ui-components/Heading"
import Icon, { IconName } from "ui-components/Icon";
import { Flex, Text, Link } from "ui-components";
import Table, { TableRow, TableCell, TableBody, TableHeader, TableHeaderCell } from "ui-components/Table";
import { Dropdown, DropdownContent } from "ui-components/Dropdown";
import { Cloud } from "Authentication/SDUCloudObject";
import { MainContainer } from "MainContainer/MainContainer";
import styled from "styled-components";
import { SidebarPages } from "ui-components/Sidebar";
import { setRefreshFunction } from "Navigation/Redux/HeaderActions";
import { Spacer } from "ui-components/Spacer";

class Activity extends React.Component<ActivityProps> {

    public componentDidMount() {
        this.props.setPageTitle();
        this.props.setActivePage();
        this.props.fetchActivity(0, 100);
    }

    public componentWillUnmount() {
        this.props.clearRefresh();
    }

    public render() {
        const { fetchActivity, page, error, setError, loading, groupedEntries } = this.props;

        const main = (
            <React.StrictMode>
                <Pagination.List
                    loading={loading}
                    errorMessage={error}
                    onErrorDismiss={setError}
                    pageRenderer={() => <ActivityFeedGrouped activity={groupedEntries ? groupedEntries : []} />}
                    page={page}
                    onPageChanged={pageNumber => fetchActivity(pageNumber, page.itemsPerPage)}
                />
            </React.StrictMode>
        );

        const header = (<Spacer left={<Heading.h2>File Activity</Heading.h2>} right={
            <Pagination.EntriesPerPageSelector
                onChange={itemsPerPage => fetchActivity(page.pageNumber, itemsPerPage)}
                content="Activity per page"
                entriesPerPage={page.itemsPerPage}
            />
        } />);

        return (
            <MainContainer
                main={main}
                header={header}
            />
        );
    }
}


const ActivityFeedGrouped = ({ activity }: { activity: GroupedActivity[] }) => activity.length ? (
    <Table>
        <TableHeader>
            <TFRow>
                <TableHeaderCell width="7em" />
                <TableHeaderCell width="10.5em" />
                <TableHeaderCell width="99%" />
            </TFRow>
        </TableHeader>
        <TableBody>
            {activity.map((a, i) => <TrackedFeedActivity key={i} activity={a} />)}
        </TableBody>
    </Table>
) : null;

export const ActivityFeed = ({ activity }: { activity: Module.Activity[] }) =>
    <ActivityFeedGrouped activity={groupActivity(activity)!} />;

const ActivityOperation: React.FunctionComponent<{ event: Module.Activity }> = props => {
    switch (props.event.type) {
        case Module.ActivityType.MOVED: {
            return <>
                was moved to
                {" "}
                <b><Link to={fileInfoPage((props.event as Module.MovedActivity).newName)}>
                    {replaceHomeFolder((props.event as Module.MovedActivity).newName, Cloud.homeFolder)}
                </Link></b>
            </>;
        }

        case Module.ActivityType.FAVORITE: {
            const isFavorite = (props.event as Module.FavoriteActivity).favorite;
            if (isFavorite) {
                return <>was <b>added to favorites</b></>;
            } else {
                return <>was <b>removed from favorites</b></>;
            }
        }

        default: {
            return <>was <b>{operationToPastTense(props.event.type)}</b></>;
        }
    }
};

const ActivityEvent: React.FunctionComponent<{ event: Module.Activity }> = props => (
    <Text fontSize={1}>
        <b>
            <Link to={fileInfoPage(props.event.originalFilePath)}>
                {getFilenameFromPath(props.event.originalFilePath)}
            </Link>
        </b> <ActivityOperation event={props.event} />
    </Text>

);

const TrackedFeedActivity = ({ activity }: { activity: GroupedActivity }) => (
    <TFRow>
        <TableCell>
            <Dropdown>
                <Text fontSize={1} color="text">{moment(activity.timestamp).fromNow()}</Text>
                <DropdownContent>
                    {moment(activity.timestamp).format("llll")}
                </DropdownContent>
            </Dropdown>
        </TableCell>
        <TableCell>
            <Flex>
                <Icon mr="0.5em" name={eventIcon(activity.type).icon} />
                <Text fontSize={2}>{`Files ${operationToPastTense(activity.type)}`}</Text>
            </Flex>
        </TableCell>
        <TableCell>
            {activity.entries.map((item, idx) =>
                <ActivityEvent key={idx} event={item} />
            )}
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
    icon: IconName,
    color: "blue" | "green" | "red",
    rotation?: 45
}

const eventIcon = (operation: Module.ActivityType): EventIconAndColor => {
    switch (operation) {
        case Module.ActivityType.FAVORITE:
            return { icon: "starFilled", color: "blue" };
        case Module.ActivityType.DOWNLOAD:
            return { icon: "download", color: "blue" };
        case Module.ActivityType.UPDATED:
            return { icon: "refresh", color: "green" };
        case Module.ActivityType.DELETED:
            return { icon: "close", color: "red" };
        case Module.ActivityType.MOVED:
            return { icon: "move", color: "green" };
        default:
            return { icon: "ellipsis", color: "blue" }
    }
}

function groupActivity(items: Module.Activity[] = []): GroupedActivity[] {
    const result: GroupedActivity[] = [];
    let currentGroup: GroupedActivity | null = null;

    const pushGroup = () => {
        if (currentGroup != null) {
            result.push(currentGroup);
            currentGroup = null;
        }
    };

    const initializeGroup = (item: Module.Activity) => {
        currentGroup = {
            type: item.type,
            timestamp: item.timestamp,
            entries: [item]
        };
    };

    items.forEach(item => {
        if (currentGroup === null) {
            initializeGroup(item);
        } else {
            if (currentGroup.type !== item.type || Math.abs(item.timestamp - currentGroup.timestamp) > (1000 * 60 * 15)) {
                pushGroup();
                initializeGroup(item);
            } else {
                currentGroup.entries.push(item);
            }
        }
    });

    pushGroup();
    return result;
}

const TFRow = styled(TableRow)`
    vertical-align: top;
`;

const mapStateToProps = ({ activity }: ReduxObject): ActivityReduxObject & Module.ActivityOwnProps => ({
    ...activity,
    groupedEntries: groupActivity(activity.page.items)
});

const mapDispatchToProps = (dispatch: Dispatch): ActivityDispatchProps => ({
    fetchActivity: (pageNumber, pageSize) => {
        const fetch = async () => {
            dispatch(setLoading(true));
            dispatch(await fetchActivity(pageNumber, pageSize));
        };
        fetch();
        dispatch(setRefreshFunction(fetch))
    },
    setError: error => dispatch(setErrorMessage(error)),
    setPageTitle: () => dispatch(updatePageTitle("Activity")),
    setActivePage: () => dispatch(setActivePage(SidebarPages.Activity)),
    clearRefresh: () => dispatch(setRefreshFunction())
});

export default connect(mapStateToProps, mapDispatchToProps)(Activity);
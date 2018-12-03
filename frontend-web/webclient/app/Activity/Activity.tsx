import * as React from "react";
import { connect } from "react-redux";
import { ActivityProps, Activity as ActivityType, TrackedActivity, CountedActivity, TrackedOperations, CountedOperations, ActivityDispatchProps } from "Activity";
import { Feed as SFeed } from "semantic-ui-react";
import { Page } from "Types";
import * as Pagination from "Pagination";
import * as moment from "moment";
import { getFilenameFromPath } from "Utilities/FileUtilities";
import { Link } from "react-router-dom";
import { ActivityReduxObject } from "DefaultObjects";
import { fetchActivity, setErrorMessage, setLoading } from "./Redux/ActivityActions";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { Dispatch } from "redux";
import { fileInfoPage } from "Utilities/FileUtilities";
import * as Heading from "ui-components/Heading"
import { IconName } from "ui-components/Icon";

class Activity extends React.Component<ActivityProps> {

    componentDidMount = () => {
        this.props.setPageTitle();
        this.props.fetchActivity(0, 25);
    }

    render() {
        const { fetchActivity, page, error, setError, loading } = this.props;
        return (
            <React.StrictMode>
                <Heading.h1>File Activity</Heading.h1>
                <Pagination.List
                    loading={loading}
                    errorMessage={error}
                    onErrorDismiss={setError}
                    pageRenderer={page => <ActivityFeed activity={page.items} />}
                    page={page}
                    onRefresh={() => fetchActivity(page.pageNumber, page.itemsPerPage)}
                    onItemsPerPageChanged={itemsPerPage => fetchActivity(page.pageNumber, itemsPerPage)}
                    onPageChanged={pageNumber => fetchActivity(pageNumber, page.itemsPerPage)}
                />
            </React.StrictMode>
        );
    }
}

export const ActivityFeed = ({ activity }: { activity: ActivityType[] }) => activity.length ? (
    <SFeed>
        {activity.map((a, i) => {
            switch (a.type) {
                case "tracked": {
                    return <TrackedFeedActivity key={i} activity={a} />
                }
                case "counted": {
                    return <CountedFeedActivity key={i} activity={a} />
                }
            }
        })}
    </SFeed>
) : null;

const CountedFeedActivity = ({ activity }: { activity: CountedActivity }) => (
    <SFeed.Event
        icon={eventIcon(activity.operation).icon}
        date={moment(new Date(activity.timestamp)).fromNow()}
        summary={`Files ${operationToPastTense(activity.operation)}`}
        extraText={activity.entries.map((entry, i) => !!entry.path ?
            (<div key={i}>
                <b>
                    <Link to={fileInfoPage(entry.path)}>{getFilenameFromPath(entry.path)}</Link>
                </b> was <b>{operationToPastTense(activity.operation)}</b> {entry.count === 1 ? "once" : <><b>{entry.count}</b> times</>}</div>) : null
        )}
    />
);

const TrackedFeedActivity = ({ activity }: { activity: TrackedActivity }) => (
    <SFeed.Event
        icon={eventIcon(activity.operation).icon}
        date={moment(new Date(activity.timestamp)).fromNow()}
        summary={`Files ${operationToPastTense(activity.operation)}`}
        extraText={activity.files.map((f, i) => !!f.path ?
            (<div key={i}>
                <b>
                    <Link to={fileInfoPage(f.path)}>{getFilenameFromPath(f.path)}</Link>
                </b> was <b>{operationToPastTense(activity.operation)}</b>
            </div>) : null
        )}
    />
);

const operationToPastTense = (operation: TrackedOperations | CountedOperations): string => {
    if (operation === "MOVED") return "moved";
    if (operation === "REMOVE_FAVORITE") return "unfavorited"
    if ((operation as string).endsWith("E")) return `${(operation as string).toLowerCase()}d`;
    return `${operation.toLowerCase()}ed`;
}
interface EventIconAndColor { icon: IconName, color: "blue" | "green" | "red", rotation?: 45 }
const eventIcon = (operation: TrackedOperations | CountedOperations): EventIconAndColor => {
    switch (operation) {
        case "FAVORITE": {
            return { icon: "starFilled", color: "blue" };
        }
        case "REMOVE_FAVORITE": {
            return { icon: "starEmpty", color: "blue" };
        }
        case "DOWNLOAD": {
            return { icon: "download", color: "blue" };
        }
        case "CREATE": {
            return { icon: "close", rotation: 45, color: "green" };
        }
        case "UPDATE": {
            return { icon: "refresh", color: "green" };
        }
        case "DELETE": {
            return { icon: "close", color: "red" };
        }
        case "MOVED": {
            return { icon: "move", color: "green" };
        }
    }
}

const mapStateToProps = ({ activity }): ActivityReduxObject => activity;
const mapDispatchToProps = (dispatch: Dispatch): ActivityDispatchProps => ({
    fetchActivity: async (pageNumber, pageSize) => {
        dispatch(setLoading(true));
        dispatch(await fetchActivity(pageNumber, pageSize))
    },
    setError: error => dispatch(setErrorMessage(error)),
    setPageTitle: () => dispatch(updatePageTitle("Activity"))
});

export default connect(mapStateToProps, mapDispatchToProps)(Activity);
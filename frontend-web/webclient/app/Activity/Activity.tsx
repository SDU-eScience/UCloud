import * as React from "react";
import { connect } from "react-redux";
import { ActivityProps, Activity as ActivityType, TrackedActivity, CountedActivity, TrackedOperations, CountedOperations, ActivityDispatchProps } from "Activity";
import { Feed, Icon, Segment, Header } from "semantic-ui-react";
import { Page } from "Types";
import * as Pagination from "Pagination";
import moment from "moment";
import { getFilenameFromPath } from "Utilities/FileUtilities";
import { Link } from "react-router-dom";
import { ActivityReduxObject } from "DefaultObjects";
import { fetchActivity, setErrorMessage, setLoading } from "./Redux/ActivityActions";

class Activity extends React.Component<ActivityProps> {

    componentDidMount = () => this.props.fetchActivity(0, 25);

    render() {
        const { fetchActivity, page, error, setError, loading } = this.props;
        return (
            <React.StrictMode>
                <Header as="h1" content="File Activity" />
                <Pagination.List
                    loading={loading}
                    errorMessage={error}
                    onErrorDismiss={setError}
                    pageRenderer={(page: Page<ActivityType>) => <Segment><ActivityFeed activity={page.items} /></Segment>}
                    page={page}
                    onRefresh={() => fetchActivity(page.pageNumber, page.itemsPerPage)}
                    onItemsPerPageChanged={(itemsPerPage) => fetchActivity(page.pageNumber, itemsPerPage)}
                    onPageChanged={(pageNumber) => fetchActivity(pageNumber, page.itemsPerPage)}
                />
            </React.StrictMode>
        );
    }
}

export const ActivityFeed = ({ activity }: { activity: ActivityType[] }) => activity.length ? (
    <Feed>
        {activity.map((a, i) => {
            if (a.type === "tracked") {
                return <TrackedFeedActivity key={i} activity={a} />
            } else if (a.type === "counted") {
                return <CountedFeedActivity key={i} activity={a} />
            } else { return null }
        })}
    </Feed>
) : null;

const CountedFeedActivity = ({ activity }: { activity: CountedActivity }) => (
    <Feed.Event
        icon={eventIcon2(activity.operation)}
        date={moment(new Date(activity.timestamp)).fromNow()}
        summary={`Files ${operationToPastTense(activity.operation)}`}
        extraText={activity.entries.map((entry, i) => !!entry.path ?
            (<div key={i}>
                <b>
                    <Link to={`/fileInfo/${entry.path}`}>{getFilenameFromPath(entry.path)}</Link>
                </b> was <b>{operationToPastTense(activity.operation)}</b> {entry.count === 1 ? "once" : <><b>{entry.count}</b> times</>}</div>) : null
        )}
    />
);

const TrackedFeedActivity = ({ activity }: { activity: TrackedActivity }) => (
    <Feed.Event
        icon={eventIcon2(activity.operation)}
        date={moment(new Date(activity.timestamp)).fromNow()}
        summary={`Files ${operationToPastTense(activity.operation)}`}
        extraText={activity.files.map((f, i) => !!f.path ?
            (<div key={i}>
                <b>
                    <Link to={`/fileInfo/${f.path}`}>{getFilenameFromPath(f.path)}</Link>
                </b> was <b>{operationToPastTense(activity.operation)}</b>
            </div>) : null
        )}
    />
);

const operationToPastTense = (operation: TrackedOperations | CountedOperations) => {
    if (operation === "MOVED") return "moved";
    if ((operation as string).endsWith("E")) return `${(operation as string).toLowerCase()}d`;
    return `${operation}ed`;
}

const EventIcon = ({ operation }: { operation: TrackedOperations | CountedOperations }) => {
    switch (operation) {
        case "FAVORITE": {
            return <Icon name="favorite" />
        }
        case "DOWNLOAD": {
            return <Icon name="download" />
        }
        case "CREATE": {
            return <Icon name="plus" />
        }
        case "UPDATE": {
            return <Icon name="refresh" />
        }
        case "DELETE": {
            return <Icon name="delete" />
        }
        case "MOVED": {
            return <Icon name="move" />
        }
    }
}

const eventIcon2 = (operation: TrackedOperations | CountedOperations) => {
    switch (operation) {
        case "FAVORITE": {
            return "favorite";
        }
        case "DOWNLOAD": {
            return "download";
        }
        case "CREATE": {
            return "plus";
        }
        case "UPDATE": {
            return "refresh";
        }
        case "DELETE": {
            return "delete";
        }
        case "MOVED": {
            return "move";
        }
    }
}

const mapStateToProps = ({ activity }): ActivityReduxObject => activity;
const mapDispatchToProps = (dispatch): ActivityDispatchProps => ({
    fetchActivity: (pageNumber: number, pageSize: number) => {
        dispatch(setLoading(true));
        dispatch(fetchActivity(pageNumber, pageSize))
    },
    setError: (error?: string) => dispatch(setErrorMessage(error))
});


export default connect(mapStateToProps, mapDispatchToProps)(Activity);
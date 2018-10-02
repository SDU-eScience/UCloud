import * as React from "react";
import { connect } from "react-redux";
import { ActivityProps, Activity as ActivityType, TrackedActivity, CountedActivity, TrackedOperations, CountedOperations, ActivityDispatchProps } from "Activity";
import { Feed, Icon, Segment, Header, SemanticICONS, SemanticCOLORS } from "semantic-ui-react";
import { Page } from "Types";
import * as Pagination from "Pagination";
import * as moment from "moment";
import { getFilenameFromPath } from "Utilities/FileUtilities";
import { Link } from "react-router-dom";
import { ActivityReduxObject } from "DefaultObjects";
import { fetchActivity, setErrorMessage, setLoading } from "./Redux/ActivityActions";
import { updatePageTitle } from "Navigation/Redux/StatusActions";

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
                    pageRenderer={(page: Page<ActivityType>) => <ActivityFeed activity={page.items} />}
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
            switch (a.type) {
                case "tracked": {
                    return <TrackedFeedActivity key={i} activity={a} />
                }
                case "counted": {
                    return <CountedFeedActivity key={i} activity={a} />
                }
            }
        })}
    </Feed>
) : null;

const CountedFeedActivity = ({ activity }: { activity: CountedActivity }) => (
    <Feed.Event
        icon={eventIcon(activity.operation).icon}
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

function FeedActivity({ activity }: { activity: CountedActivity | TrackedActivity }) {
    let extraText = "";
}

const TrackedFeedActivity = ({ activity }: { activity: TrackedActivity }) => (
    <Feed.Event
        icon={eventIcon(activity.operation).icon}
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
interface EventIconAndColor { icon: SemanticICONS, color: SemanticCOLORS }
const eventIcon = (operation: TrackedOperations | CountedOperations): EventIconAndColor => {
    switch (operation) {
        case "FAVORITE": {
            return { icon: "favorite", color: "blue" };
        }
        case "DOWNLOAD": {
            return { icon: "download", color: "blue" };
        }
        case "CREATE": {
            return { icon: "plus", color: "green" };
        }
        case "UPDATE": {
            return { icon: "refresh", color: "green" };
        }
        case "DELETE": {
            return { icon: "delete", color: "red" };
        }
        case "MOVED": {
            return { icon: "move", color: "green" };
        }
    }
}

const mapStateToProps = ({ activity }): ActivityReduxObject => activity;
const mapDispatchToProps = (dispatch): ActivityDispatchProps => ({
    fetchActivity: (pageNumber: number, pageSize: number) => {
        dispatch(setLoading(true));
        dispatch(fetchActivity(pageNumber, pageSize))
    },
    setError: (error?: string) => dispatch(setErrorMessage(error)),
    setPageTitle: () => dispatch(updatePageTitle("Activity"))
});

export default connect(mapStateToProps, mapDispatchToProps)(Activity);
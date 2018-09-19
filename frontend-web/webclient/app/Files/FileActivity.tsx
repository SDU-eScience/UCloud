import * as React from "react";
import { connect } from "react-redux";
import PromiseKeeper from "PromiseKeeper";
import { Cloud } from "Authentication/SDUCloudObject";
import { emptyPage } from "DefaultObjects";
import { FileActivityProps, FileActivityState, Activity, TrackedActivity, CountedActivity, TrackedOperations, CountedOperations } from "Files";
import { Feed, Icon, Segment, Header } from "semantic-ui-react";
import { Page } from "Types";
import * as Pagination from "Pagination";
import * as moment from "moment";
import { getFilenameFromPath } from "Utilities/FileUtilities";
import { Link } from "react-router-dom";

class FileActivity extends React.Component<FileActivityProps, FileActivityState> {
    constructor(props) {
        super(props)
        this.state = {
            promises: new PromiseKeeper(),
            activity: emptyPage
        }
    }

    componentDidMount() {
        this.fetchActivity(0, 25);
    }

    fetchActivity = (pageNumber: number = 0, pageSize: number = 25) => {
        this.state.promises.makeCancelable(
            Cloud.get(`/activity/stream?page=${pageNumber}&itemsPerPage=${pageSize}`)
        ).promise.then(({ response }) => this.setState(() => ({ activity: response })));
    }

    componentWillUnmount() {
        this.state.promises.cancelPromises();
    }

    render() {
        return (
            <React.StrictMode>
                <Header as="h1" content="File Activity" />
                <Pagination.List
                    pageRenderer={(page: Page<Activity>) => <Segment><ActivityFeed activity={page.items} /></Segment>}
                    page={this.state.activity}
                    onItemsPerPageChanged={() => undefined}
                    onPageChanged={() => undefined}
                />
            </React.StrictMode>
        );
    }
}

export const ActivityFeed = ({ activity }: { activity: Activity[] }) => activity.length ? (
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
                <b><Link to={`/fileInfo/${entry.path}`}>{getFilenameFromPath(entry.path)}</Link></b> was <b>{operationToPastTense(activity.operation)}</b> {entry.count === 1 ? "once" : <><b>{entry.count}</b> times</>}</div>) : null
        )}
    />
);

const TrackedFeedActivity = ({ activity }: { activity: TrackedActivity }) => (
    <Feed.Event>
        <Feed.Label>
            <EventIcon operation={activity.operation} />
        </Feed.Label>
        <Feed.Content>
            <Feed.Summary>
                Files <b>{activity.files.join(", ")}</b> {activity.files.length > 1 ? "were" : "was"} {operationToPastTense(activity.operation)}
            </Feed.Summary>
            <Feed.Meta>
                {moment(new Date(activity.timestamp)).fromNow()}
            </Feed.Meta>
        </Feed.Content>
    </Feed.Event>
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

export default connect()(FileActivity);
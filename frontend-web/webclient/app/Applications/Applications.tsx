import * as React from "react";
import { Link } from "react-router-dom";
import * as Pagination from "Pagination";
import { Card, Icon, Rating, List } from "semantic-ui-react";
import { connect } from "react-redux";
import {
    fetchApplications,
    setLoading,
    updateApplications
} from "./Redux/ApplicationsActions";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { Page } from "Types";
import { Application } from ".";
import { ApplicationsProps, ApplicationsOperations, ApplicationsStateProps } from ".";
import { setErrorMessage } from "./Redux/ApplicationsActions";
// Requires at least TS 3.0.0
import { MaterialColors } from "Assets/materialcolors.json";
import { favoriteApplicationFromPage } from "Utilities/ApplicationUtilities";
import { Cloud } from "Authentication/SDUCloudObject";
import { setPrioritizedSearch } from "Navigation/Redux/HeaderActions";

const COLORS_KEYS = Object.keys(MaterialColors);

// We need dynamic import due to nature of the import
const blurOverlay = require("Assets/Images/BlurOverlayByDan.png");

class Applications extends React.Component<ApplicationsProps> {

    componentDidMount() {
        const { props } = this;
        props.updatePageTitle();
        props.prioritizeApplicationSearch();
        props.setLoading(true);
        if (this.props.page.items.length === 0) {
            props.fetchApplications(props.page.pageNumber, props.page.itemsPerPage);
        }
    }

    render() {
        const { page, loading, fetchApplications, onErrorDismiss, updateApplications, error } = this.props;
        const favoriteApp = (app: Application) => updateApplications(favoriteApplicationFromPage(app, page, Cloud));
        return (
            <React.StrictMode>
                <Pagination.List
                    loading={loading}
                    onErrorDismiss={onErrorDismiss}
                    errorMessage={error}
                    onRefresh={() => fetchApplications(page.pageNumber, page.itemsPerPage)}
                    pageRenderer={({ items }: Page<Application>) =>
                        <Card.Group className="card-margin">
                            {items.map((app, index) => <SingleApplication key={index} app={app} favoriteApp={favoriteApp} />)}
                        </Card.Group>
                    }
                    page={page}
                    onItemsPerPageChanged={(size) => fetchApplications(0, size)}
                    onPageChanged={(pageNumber) => fetchApplications(pageNumber, page.itemsPerPage)}
                />
            </React.StrictMode >);
    }
}

interface SingleApplicationProps { app: Application, favoriteApp?: (app: Application) => void }
export function SingleApplication({ app, favoriteApp }: SingleApplicationProps) {
    const hashCode = toHashCode(app.description.info.name);
    const color = COLORS_KEYS[(hashCode % COLORS_KEYS.length)];
    const mClength = MaterialColors[color].length;
    const hex = MaterialColors[color][(hashCode % mClength)];
    const even = app.modifiedAt % 2 === 0;
    const opacity = even ? 0.3 : 1;
    const { description } = app.description;
    const image = even ? blurOverlay : `https://placekitten.com/200/200`;
    return (
        <Card>
            {/* FIXME: Move styling to .scss file where possible */}
            <div style={{
                background: hex
            }}>
                <Link to={`/appDetails/${app.description.info.name}/${app.description.info.version}/`}>
                    <div style={{
                        opacity: opacity,
                        width: "100%",
                        height: "200px",
                        backgroundImage: `url('${image}')`,
                        backgroundSize: "cover",
                        backgroundPosition: "center"
                    }} />
                </Link>
            </div>
            <Card.Content>
                <List horizontal floated="right">
                    {!!favoriteApp ? <List.Item>
                        <Rating icon={"star"} maxRating={1} rating={app.favorite ? 1 : 0} onClick={() => !!favoriteApp ? favoriteApp(app) : null} />
                    </List.Item> : null}
                    <List.Item>
                        <Link to={`/applications/${app.description.info.name}/${app.description.info.version}/`}>
                            <Icon color="green" name="play" />
                        </Link>
                    </List.Item>
                </List>
                <Card.Header
                    as={Link}
                    to={`/appDetails/${app.description.info.name}/${app.description.info.version}/`}
                    content={app.description.title}
                />
                <Card.Meta content={app.description.info.version} />
            </Card.Content>
            <Card.Content extra>
                {description.length > 72 ? `${description.slice(0, 72)}...` : description}
            </Card.Content>
        </Card>
    );
}

function toHashCode(name: string): number {
    let hash = 0;
    if (name.length == 0) {
        return hash;
    }
    for (let i = 0; i < name.length; i++) {
        let char = name.charCodeAt(i);
        hash = ((hash << 5) - hash) + char;
        hash = hash & hash; // Convert to 32bit integer
    }
    return Math.abs(hash);
}

const mapDispatchToProps = (dispatch): ApplicationsOperations => ({
    prioritizeApplicationSearch: () => dispatch(setPrioritizedSearch("applications")),
    onErrorDismiss: () => dispatch(setErrorMessage()),
    updatePageTitle: () => dispatch(updatePageTitle("Applications")),
    setLoading: (loading: boolean) => dispatch(setLoading(loading)),
    fetchApplications: (pageNumber: number, itemsPerPage: number) => dispatch(fetchApplications(pageNumber, itemsPerPage)),
    updateApplications: (applications: Page<Application>) => dispatch(updateApplications(applications))
});

const mapStateToProps = ({ applications }): ApplicationsStateProps => ({
    favCount: applications.page.items.filter(it => it.favorite).length,
    ...applications
});

export default connect(mapStateToProps, mapDispatchToProps)(Applications);
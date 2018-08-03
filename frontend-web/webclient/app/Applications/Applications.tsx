import * as React from "react";
import { Link } from "react-router-dom";
import * as Pagination from "Pagination";
import { Card, Icon, Header, Container, Image, Dropdown, Form, Rating } from "semantic-ui-react";
import { connect } from "react-redux";
import {
    fetchApplications,
    setLoading,
    updateApplications
} from "./Redux/ApplicationsActions";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import "Styling/Shared.scss";
import { Page } from "Types";
import { Application } from ".";
import { ApplicationsProps, ApplicationsOperations, ApplicationsStateProps } from ".";
import { setErrorMessage } from "./Redux/ApplicationsActions";
// Requires at least TS 3.0.0
import { MaterialColors } from "Assets/materialcolors.json";
import { favoriteApplicationFromPage } from "UtilityFunctions";
import { Cloud } from "Authentication/SDUCloudObject";

const COLORS_KEYS = Object.keys(MaterialColors);

// We need dynamic import due to nature of the import
const blurOverlay = require("Assets/Images/BlurOverlayByDan.png");

type SearchBy = "name" | "tags";

interface ApplicationState {
    searchLoading: boolean
    searchResults: Application[]
    searchBy: SearchBy
    searchText: string
}

class Applications extends React.Component<ApplicationsProps, ApplicationState> {
    constructor(props: ApplicationsProps) {
        super(props);
        this.state = {
            searchText: "",
            searchLoading: false,
            searchBy: "name",
            searchResults: []
        }
    }

    componentDidMount() {
        const { props } = this;
        props.updatePageTitle();
        props.setLoading(true);
        props.fetchApplications(props.page.pageNumber, props.page.itemsPerPage);
    }

    onSearch = (e: React.FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        this.setState(() => ({ searchLoading: true }));
        console.log(this.state.searchBy, this.state.searchText)
        setTimeout(() => this.setState(() => ({ searchLoading: false })), 2000);
    }

    render() {
        const { page, loading, fetchApplications, onErrorDismiss, updateApplications, error } = this.props;
        const { searchLoading } = this.state;
        const favoriteApp = (app: Application) => updateApplications(favoriteApplicationFromPage(app, page, Cloud));
        return (
            <React.StrictMode>
                <Container>
                    <Header as={"h3"} content="Search Applications" />
                    <Form onSubmit={(e) => this.onSearch(e)}>
                        <Form.Input
                            loading={searchLoading}
                            action={
                                <Dropdown
                                    button basic floating
                                    onChange={(_, { value }) => this.setState(() => ({ searchBy: value as SearchBy }))}
                                    options={searchOptions}
                                    defaultValue="name"
                                />
                            }
                            icon="search"
                            iconPosition="left"
                            placeholder="Search applications..."
                        />
                    </Form>
                </Container>
                <Pagination.List
                    loading={loading}
                    onErrorDismiss={onErrorDismiss}
                    errorMessage={error}
                    onRefreshClick={() => fetchApplications(page.pageNumber, page.itemsPerPage)}
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

const searchOptions = [
    { key: "name", text: "Name", value: "name" },
    { key: "tags", text: "Tags", value: "tags" }
]

interface SingleApplicationProps { app: Application, favoriteApp: (app: Application) => void }
function SingleApplication({ app, favoriteApp }: SingleApplicationProps) {
    const hashCode = toHashCode(app.description.info.name);
    const color = COLORS_KEYS[(hashCode % COLORS_KEYS.length)];
    const mClength = MaterialColors[color].length;
    const hex = MaterialColors[color][(hashCode % mClength)];
    const even = true;//app.modifiedAt % 2 === 0;
    const opacity = even ? 0.3 : 1;
    const description = app.description.description;
    const image = even ? blurOverlay : `https://placekitten.com/200/200`;
    return (
        <Card>
            <div style={{
                background: hex
            }}>
                <div style={{
                    opacity: opacity,
                    width: "100%",
                    height: "200px",
                    backgroundImage: `url('${image}')`,
                    backgroundSize: "cover",
                    backgroundPosition: "center"
                }} />
            </div>
            <Card.Content>
                <span style={{ float: "right" }}>
                    <Link to={`/applications/${app.description.info.name}/${app.description.info.version}/`}>
                        <Icon color="green" name="play" />
                    </Link>
                    <Rating icon={"star"} maxRating={1} rating={app.favorite ? 1 : 0} onClick={() => favoriteApp(app)} />
                </span>

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

function toHashCode(name: string) {
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
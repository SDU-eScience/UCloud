import * as React from "react";
import { Link } from "react-router-dom";
import * as Pagination from "Pagination";
import { Card, Button, Header, Container, Image, Input, Form, Rating } from "semantic-ui-react";
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
import { ApplicationsProps, ApplicationsOperations, ApplicationsStateProps, SearchFieldProps } from ".";
import { setErrorMessage } from "./Redux/ApplicationsActions";
import { MaterialColors } from "Assets/materialcolors.json";
import { favoriteApplicationFromPage } from "UtilityFunctions";
import { Cloud } from "Authentication/SDUCloudObject";

const COLORS_KEYS = Object.keys(MaterialColors);

// We need dynamic import due to nature of the import
const blurOverlay = require("Assets/Images/BlurOverlayByDan.png");

interface ApplicationSearchState {
    nameSearch: string
    tagSearch: string
}

interface ApplicationState extends ApplicationSearchState {
    nameSearchLoading: boolean
    tagSearchLoading: boolean
    searchResults: Application[]
}

type SearchProperties = keyof ApplicationSearchState;

class Applications extends React.Component<ApplicationsProps, ApplicationState> {
    constructor(props: ApplicationsProps) {
        super(props);
        this.state = {
            nameSearch: "",
            nameSearchLoading: false,
            tagSearch: "",
            tagSearchLoading: false,
            searchResults: []
        }
    }

    componentDidMount() {
        const { props } = this;
        props.updatePageTitle();
        props.setLoading(true);
        props.fetchApplications(props.page.pageNumber, props.page.itemsPerPage);
    }

    onSearchValueUpdate = (property: SearchProperties, value: string) => {
        this.setState(() => ({ ...this.state, [property]: value, }))
    }

    onSearch = (searchBy: SearchProperties) => {
        console.log(searchBy);
        if (searchBy === "tagSearch") {
            this.setState(() => ({ tagSearchLoading: true }));
            const tags = this.state.tagSearch.split(" ").filter(p => p);
            console.log(tags);
        } else if (searchBy === "nameSearch") {
            this.setState(() => ({ nameSearchLoading: true }));
            console.log(this.state.nameSearch);
        } else {
            console.warn(`${searchBy} as a searchProperty. Shouldn't happen.`)
        }
        setTimeout(() => this.setState(() => ({ nameSearchLoading: false, tagSearchLoading: false })), 2000);
    }

    render() {
        const { page, loading, fetchApplications, onErrorDismiss, updateApplications, error } = this.props;
        const { tagSearch, nameSearch, nameSearchLoading, tagSearchLoading } = this.state;
        const favoriteApp = (app: Application) => updateApplications(favoriteApplicationFromPage(app, page, Cloud));
        return (
            <React.StrictMode>
                <Container>
                    <Header as={"h3"} content="Search Applications" />
                    <SearchField
                        loading={nameSearchLoading}
                        onSubmit={() => this.onSearch("nameSearch")}
                        value={nameSearch}
                        icon={"search"}
                        placeholder={"Search by name..."}
                        onValueChange={(value) => this.onSearchValueUpdate("nameSearch", value)}
                    />
                    <SearchField
                        loading={tagSearchLoading}
                        onSubmit={() => this.onSearch("tagSearch")}
                        value={tagSearch}
                        icon={"search"}
                        placeholder={"Search by whitespace separated tags..."}
                        onValueChange={(value) => this.onSearchValueUpdate("tagSearch", value)}
                    />

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
            </React.StrictMode>);
    }
}

const SearchField = ({ onSubmit, icon, placeholder, value, onValueChange, loading }: SearchFieldProps) => (
    <Form onSubmit={onSubmit}>
        <Form.Input
            fluid
            style={{ marginBottom: "8px" }}
            loading={loading}
            icon={icon}
            placeholder={placeholder}
            value={value}
            onChange={(_, { value }) => onValueChange(value)}
        />
    </Form>
);

interface SingleApplicationProps { app: Application, favoriteApp: (app: Application) => void }
function SingleApplication({ app, favoriteApp }: SingleApplicationProps) {
    const hashCode = toHashCode(app.description.info.name);
    const color = COLORS_KEYS[(hashCode % COLORS_KEYS.length)];
    const mClength = MaterialColors[color].length;
    const hex = MaterialColors[color][(hashCode % mClength)];
    const even = app.modifiedAt % 2 === 0;
    const opacity = even ? 0.3 : 1;
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
                <Image floated="right">
                    <Rating icon={"star"} maxRating={1} rating={app.favorite ? 1 : 0} onClick={() => favoriteApp(app)} />
                </Image>
                <Card.Header content={app.description.title} />
                <Card.Meta content={app.description.info.version} />
            </Card.Content>
            <Card.Content extra>
                <Button.Group>
                    <Button
                        content="Run app"
                        color="green"
                        basic fluid
                        as={Link}
                        to={`/applications/${app.description.info.name}/${app.description.info.version}/`}
                    />
                    <Button
                        basic
                        content="Details"
                        color="blue"
                        as={Link}
                        to={`/appDetails/${app.description.info.name}/${app.description.info.version}/`}
                    />
                </Button.Group>
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
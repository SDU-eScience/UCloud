import * as React from "react";
import { Link } from "react-router-dom";
import * as Pagination from "Pagination";
import { Card, Button } from "semantic-ui-react";
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
import materialcolors from "Assets/TempMaterialColors";

const COLORS_KEYS = Object.keys(materialcolors);

// We need dynamic import due to nature of the import
const blurOverlay = require("Assets/Images/BlurOverlayByDan.png");


class Applications extends React.Component<ApplicationsProps> {
    constructor(props: ApplicationsProps) {
        super(props);
        props.updatePageTitle();
        props.setLoading(true);
        props.fetchApplications(props.page.pageNumber, props.page.itemsPerPage);
    }

    render() {
        const { page, loading, fetchApplications, onErrorDismiss, error } = this.props;

        return (
            <React.Fragment>
                <Pagination.List
                    loading={loading}
                    onErrorDismiss={onErrorDismiss}
                    errorMessage={error}
                    onRefreshClick={() => fetchApplications(page.pageNumber, page.itemsPerPage)}
                    pageRenderer={({ items }: Page<Application>) =>
                        <Card.Group>
                            {items.map((app, index) =>
                                <SingleApplication key={index} app={app} />
                            )}
                        </Card.Group>
                    }
                    page={page}
                    onItemsPerPageChanged={(size) => fetchApplications(0, size)}
                    onPageChanged={(pageNumber) => fetchApplications(pageNumber, page.itemsPerPage)}
                    onRefresh={() => null}
                />
            </React.Fragment>);
    }
}

// FIXME: Entirely for kitten related images
let i = 0;
interface SingleApplicationProps { app: Application }
function SingleApplication({ app }: SingleApplicationProps) {
    const hashCode = toHashCode(app.description.info.name);
    const color = COLORS_KEYS[(hashCode % COLORS_KEYS.length)];
    const mClength = materialcolors[color].length;
    const hex = materialcolors[color][(hashCode % mClength)];
    const even = app.modifiedAt % 2 === 0;
    const opacity = even ? 0.3 : 1;
    const image = even ? blurOverlay : `https://placekitten.com/${i % 2 === 0 ? "g" : ""}/${200 + i++}/200`;
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
                <Card.Header content={app.description.info.name} />
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

const mapStateToProps = ({ applications }): ApplicationsStateProps => applications;

export default connect(mapStateToProps, mapDispatchToProps)(Applications);
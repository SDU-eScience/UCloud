import * as React from "react";
import { Link } from "react-router-dom";
import * as Pagination from "Pagination";
import { Card as SCard, Icon as SIcon, Rating as SRating, List as SList } from "semantic-ui-react";
import { connect } from "react-redux";
import {
    fetchApplications,
    setLoading,
    receiveApplications
} from "./Redux/ApplicationsActions";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { Page } from "Types";
import { Application, ApplicationDescription } from ".";
import { ApplicationsProps, ApplicationsOperations, ApplicationsStateProps } from ".";
import { setErrorMessage } from "./Redux/ApplicationsActions";
// Requires at least TS 3.0.0
import { MaterialColors } from "Assets/materialcolors.json";
import { favoriteApplicationFromPage } from "Utilities/ApplicationUtilities";
import { Cloud } from "Authentication/SDUCloudObject";
import { setPrioritizedSearch } from "Navigation/Redux/HeaderActions";
import { Dispatch } from "redux";
import { CardGroup, Card, PlayIcon } from "ui-components/Card";
import { Relative, BackgroundImage, Box, Absolute, Text, Icon } from "ui-components";

const COLORS_KEYS = Object.keys(MaterialColors);

// We need dynamic import due to nature of the import
const blurOverlay = require("Assets/Images/BlurOverlayByDan.png");

class Applications extends React.Component<ApplicationsProps> {

    componentDidMount() {
        const { props } = this;
        props.updatePageTitle();
        props.prioritizeApplicationSearch();
        if (this.props.page.items.length === 0) {
            props.setLoading(true);
            props.fetchApplications(props.page.pageNumber, props.page.itemsPerPage);
        }
    }

    render() {
        const { page, loading, fetchApplications, onErrorDismiss, receiveApplications, error } = this.props;
        const favoriteApp = (app: Application) => receiveApplications(favoriteApplicationFromPage(app, page, Cloud));
        return (
            <React.StrictMode>
                <Pagination.List
                    loading={loading}
                    onErrorDismiss={onErrorDismiss}
                    errorMessage={error}
                    onRefresh={() => fetchApplications(page.pageNumber, page.itemsPerPage)}
                    pageRenderer={({ items }: Page<Application>) =>
                        <CardGroup>
                            {items.map((app, index) =>
                                <ApplicationCard key={index} appDescription={app.description} />
                            )}
                        </CardGroup>
                    }
                    page={page}
                    onItemsPerPageChanged={(size) => fetchApplications(0, size)}
                    onPageChanged={(pageNumber) => fetchApplications(pageNumber, page.itemsPerPage)}
                />
            </React.StrictMode >);
    }
}

export const ApplicationCard = ({ appDescription }: { appDescription: ApplicationDescription }) => (
    <Card height={212} width={252}>
        <Relative>
            <BackgroundImage
                height="138px"
                image="https://placekitten.com/212/138">
                <Box p={4}>
                    <Absolute top="16px" left="10px">
                        <Text fontSize={2} align="left" color="grey">
                            {appDescription.info.name}
                        </Text>
                    </Absolute>
                    <Absolute top={"34px"} left={"14px"}>
                        <Text fontSize={"xxs-small"} align="left" color="grey">
                            v {appDescription.info.version}
                        </Text>
                    </Absolute>
                    <Absolute top="10px" left="215px">
                        <Icon cursor="pointer" name="starEmpty" />
                    </Absolute>
                    <Absolute top={"86px"} left={"200px"}>
                        <Link to={`/applications/${appDescription.info.name}/${appDescription.info.version}/`}>
                            <PlayIcon />
                        </Link>
                    </Absolute>
                </Box>
            </BackgroundImage>
        </Relative>
        <Relative>
            <Text>
                {appDescription.description.slice(0, 100)}
            </Text>
        </Relative>
    </Card>
);



interface SingleApplicationProps { app: Application, favoriteApp?: (app: Application) => void }
export function SingleApplication({ app, favoriteApp }: SingleApplicationProps) {
    const hashCode = toHashCode(app.description.info.name);
    const color = COLORS_KEYS[(hashCode % COLORS_KEYS.length)];
    const mClength = MaterialColors[color].length;
    const hex = { background: MaterialColors[color][(hashCode % mClength)] };
    const even = app.modifiedAt % 2 === 0;
    const { description } = app.description;
    const image = even ? blurOverlay : `https://placekitten.com/200/200`;
    const imageStyle = {
        opacity: even ? 0.3 : 1,
        backgroundImage: `url('${image}')`
    };
    return (
        <SCard>
            <div style={hex}>
                <Link to={`/appDetails/${app.description.info.name}/${app.description.info.version}/`}>
                    <div className="app-image" style={imageStyle} />
                </Link>
            </div>
            <SCard.Content>
                <SList horizontal floated="right">
                    {!!favoriteApp ? <SList.Item>
                        <SRating icon={"star"} maxRating={1} rating={app.favorite ? 1 : 0} onClick={() => !!favoriteApp ? favoriteApp(app) : null} />
                    </SList.Item> : null}
                    <SList.Item>
                        <Link to={`/applications/${app.description.info.name}/${app.description.info.version}/`}>
                            <SIcon color="green" name="play" />
                        </Link>
                    </SList.Item>
                </SList>
                <SCard.Header
                    as={Link}
                    to={`/appDetails/${app.description.info.name}/${app.description.info.version}/`}
                    content={app.description.title}
                />
                <SCard.Meta content={app.description.info.version} />
            </SCard.Content>
            <SCard.Content extra>
                {description.length > 72 ? `${description.slice(0, 72)}...` : description}
            </SCard.Content>
        </SCard>
    );
}

function toHashCode(name: string): number {
    let hash = 0;
    if (name.length == 0) { // FIXME can this ever happen?
        return hash;
    }
    for (let i = 0; i < name.length; i++) {
        let char = name.charCodeAt(i);
        hash = ((hash << 5) - hash) + char;
        hash = hash & hash; // Convert to 32bit integer
    }
    return Math.abs(hash);
}

const mapDispatchToProps = (dispatch: Dispatch): ApplicationsOperations => ({
    prioritizeApplicationSearch: () => dispatch(setPrioritizedSearch("applications")),
    onErrorDismiss: () => dispatch(setErrorMessage()),
    updatePageTitle: () => dispatch(updatePageTitle("Applications")),
    setLoading: (loading: boolean) => dispatch(setLoading(loading)),
    fetchApplications: async (pageNumber: number, itemsPerPage: number) => dispatch(await fetchApplications(pageNumber, itemsPerPage)),
    receiveApplications: (applications: Page<Application>) => dispatch(receiveApplications(applications))
});

const mapStateToProps = ({ applications }): ApplicationsStateProps => ({
    favCount: applications.page.items.filter(it => it.favorite).length,
    ...applications
});

export default connect(mapStateToProps, mapDispatchToProps)(Applications);
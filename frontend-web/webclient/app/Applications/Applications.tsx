import * as React from "react";
import { Link } from "react-router-dom";
import * as Pagination from "Pagination";
import { Card as SCard, Rating as SRating, List as SList } from "semantic-ui-react";
import { connect } from "react-redux";
import {
    fetchApplications,
    setLoading,
    receiveApplications,
    fetchFavoriteApplications,
    setFavoritesLoading
} from "./Redux/ApplicationsActions";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { Page } from "Types";
import { Application } from ".";
import { ApplicationsProps, ApplicationsOperations } from ".";
import { setErrorMessage } from "./Redux/ApplicationsActions";
import { MaterialColors } from "Assets/materialcolors.json";
import { favoriteApplicationFromPage } from "Utilities/ApplicationUtilities";
import { Cloud } from "Authentication/SDUCloudObject";
import { setPrioritizedSearch } from "Navigation/Redux/HeaderActions";
import { Dispatch } from "redux";
import { CardGroup, Card, PlayIcon } from "ui-components/Card";
import { Relative, BackgroundImage, Box, Absolute, Text, Icon, Divider, Flex, Input } from "ui-components";
import { EllipsedText } from "ui-components/Text";
import { ReduxObject, ApplicationReduxObject } from "DefaultObjects";
import { MainContainer } from "MainContainer/MainContainer";
import * as Heading from "ui-components/Heading"
import DetailedApplicationSearch from "./DetailedApplicationSearch";

const COLORS_KEYS = Object.keys(MaterialColors);

// We need dynamic import due to nature of the import
const blurOverlay = require("Assets/Images/circuitboard-bg.png");

class Applications extends React.Component<ApplicationsProps> {

    componentDidMount() {
        const { props } = this;
        props.updatePageTitle();
        props.prioritizeApplicationSearch();
        if (this.props.page.items.length === 0) {
            props.setLoading(true);
            props.fetchApplications(props.page.pageNumber, props.page.itemsPerPage);
            props.fetchFavorites(props.page.pageNumber, props.page.itemsPerPage);
        }
    }

    render() {
        const { page, loading, fetchApplications, favorites, onErrorDismiss, receiveApplications, error } = this.props;
        const favoriteApp = (name: string, version: string) => receiveApplications(favoriteApplicationFromPage(name, version, page, Cloud));

        const main = (
            <Pagination.List
                loading={loading}
                onErrorDismiss={onErrorDismiss}
                errorMessage={error}
                onRefresh={() => fetchApplications(page.pageNumber, page.itemsPerPage)}
                pageRenderer={({ items }: Page<Application>) =>
                    <React.Fragment>
                        {favorites.items.length ?
                            <React.Fragment>
                                <Heading.h2>Favorites</Heading.h2>
                                <CardGroup>
                                    {favorites.items.map(app =>
                                        <ApplicationCard
                                            key={`${app.description.info.name}${app.description.info.version}`}
                                            favoriteApp={favoriteApp}
                                            app={app}
                                            isFavorite={app.favorite}
                                        />)}
                                </CardGroup>
                                <Divider />
                            </React.Fragment> : null}
                        <Heading.h2>Applications</Heading.h2>
                        <CardGroup>
                            {items.map((app, index) =>
                                <ApplicationCard
                                    key={index}
                                    favoriteApp={favoriteApp}
                                    app={app}
                                    isFavorite={app.favorite}
                                />
                            )}
                        </CardGroup>
                    </React.Fragment>
                }
                page={page}
                onItemsPerPageChanged={size => fetchApplications(0, size)}
                onPageChanged={pageNumber => fetchApplications(pageNumber, page.itemsPerPage)}
            />
        )

        return (
            <MainContainer
                main={main}
                sidebar={<DetailedApplicationSearch />}
            />
        );
    }
}

export const ApplicationCard = ({ app, favoriteApp, isFavorite }: { favoriteApp?: (name: string, version) => void, app: Application, isFavorite?: boolean }) => (
    <Card height={212} width={252}>
        <Relative>
            <BackgroundImage
                height="138px"
                color={hexFromAppName(app.description.title)}
                image={blurOverlay}>
                <Box p={4}>
                    <Absolute top="6px" left="10px">
                        <Text fontSize={2} align="left" color="grey">
                            {app.description.title}
                        </Text>
                    </Absolute>
                    <Absolute top={"26px"} left={"14px"}>
                        <Text fontSize={"xxs-small"} align="left" color="grey">
                            v {app.description.info.version}
                        </Text>
                    </Absolute>
                    <Absolute top="10px" left="215px">
                        <Icon
                            onClick={() => !!favoriteApp ? favoriteApp(app.description.info.name, app.description.info.version) : undefined}
                            cursor="pointer"
                            name={isFavorite ? "starFilled" : "starEmpty"}
                        />
                    </Absolute>
                    <Absolute top="112px" left="10px">
                        <EllipsedText width={180} title={`by ${app.description.authors.join(", ")}`} color="grey">
                            by {app.description.authors.join(", ")}
                        </EllipsedText>
                    </Absolute>
                    <Absolute top="86px" left="200px">
                        <Link to={`/applications/${app.description.info.name}/${app.description.info.version}/`}>
                            <PlayIcon />
                        </Link>
                    </Absolute>
                </Box>
            </BackgroundImage>
        </Relative>
        <Relative>
            <Absolute left="14px" top="6px">
                <Text>
                    {app.description.description.slice(0, 100)}
                </Text>
            </Absolute>
        </Relative>
    </Card >
);



/* interface SingleApplicationProps { app: Application, favoriteApp?: (app: Application) => void }
export function SingleApplication({ app, favoriteApp }: SingleApplicationProps) {
    const hex = hexFromAppName(app.description.info.name);
    const even = app.modifiedAt % 2 === 0;
    const { description } = app.description;
    const image = even ? blurOverlay : `https://placekitten.com/200/200`;
    const imageStyle = {
        opacity: even ? 0.3 : 1,
        backgroundImage: `url('${image}')`
    };
    return (
        <SCard>
            <div style={{ background: hex }}>
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
                            <Icon color="green" name="play" />
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
} */

function hexFromAppName(name: string): string {
    const hashCode = toHashCode(name);
    const color = COLORS_KEYS[(hashCode % COLORS_KEYS.length)];
    const mClength = MaterialColors[color].length;
    return MaterialColors[color][(hashCode % mClength)];
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
    setFavoritesLoading: (loading: boolean) => dispatch(setFavoritesLoading(loading)),
    fetchApplications: async (pageNumber: number, itemsPerPage: number) => dispatch(await fetchApplications(pageNumber, itemsPerPage)),
    receiveApplications: (applications: Page<Application>) => dispatch(receiveApplications(applications)),
    fetchFavorites: async (pageNumber: number, itemsPerPage: number) => dispatch(await fetchFavoriteApplications(pageNumber, itemsPerPage))
});

const mapStateToProps = ({ applications }: ReduxObject): ApplicationReduxObject & { favCount: number } => ({
    ...applications,
    favCount: applications.page.items.filter(it => it.favorite).length
});

export default connect(mapStateToProps, mapDispatchToProps)(Applications);
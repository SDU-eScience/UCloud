import * as React from "react";
import { Link, Image } from "ui-components";
import * as Pagination from "Pagination";
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
import { Relative, Box, Absolute, Text, Icon, Divider } from "ui-components";
import { EllipsedText } from "ui-components/Text";
import { ReduxObject, ApplicationReduxObject } from "DefaultObjects";
import { MainContainer } from "MainContainer/MainContainer";
import * as Heading from "ui-components/Heading"
import DetailedApplicationSearch from "./DetailedApplicationSearch";

const COLORS_KEYS = Object.keys(MaterialColors);

// We need dynamic import due to nature of the import
const curcuitBoard = require("Assets/Images/circuitboard-bg.png");
const blurOverlay = require("Assets/Images/blur-overlay2.png");

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
        const { page, loading, fetchApplications, favorites, onErrorDismiss, receiveApplications, ...props } = this.props;
        const favoriteApp = async (name: string, version: string) => {
            receiveApplications(await favoriteApplicationFromPage(name, version, page, Cloud));
            props.fetchFavorites(0, favorites.itemsPerPage);
        }

        const main = (
            <Pagination.List
                loading={loading}
                onErrorDismiss={onErrorDismiss}
                errorMessage={props.error}
                onRefresh={() => fetchApplications(page.pageNumber, page.itemsPerPage)}
                pageRenderer={({ items }: Page<Application>) =>
                    <React.Fragment>
                        {favorites.items.length ?
                            <React.Fragment>
                                <Heading.h2>Favorites</Heading.h2>
                                <CardGroup>
                                    {favorites.items.map(app =>
                                        <ApplicationCard
                                            key={`fav-${app.description.info.name}${app.description.info.version}`}
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
        );

        const sidebar = (<DetailedApplicationSearch />);

        return (
            <MainContainer
                main={main}
                sidebar={sidebar}
            />
        );
    }
}

interface ApplicationCardProps {
    favoriteApp?: (name: string, version: string) => void,
    app: Application,
    isFavorite?: boolean
}

export const ApplicationCard = ({ app, favoriteApp, isFavorite }: ApplicationCardProps) => (
    <Card height={212} width={252}>
        <Relative>
            <Box>
                <Box style={{ background: hexFromAppName(app.description.title) }}>
                    <Image
                        src={curcuitBoard}
                        style={{ opacity: 0.4 }}
                    />
                </Box>
                <Absolute top="6px" left="10px">
                    <Text
                        fontSize={2}
                        align="left"
                        color="white"
                    >
                        {app.description.title}
                    </Text>
                </Absolute>
                <Absolute top={"26px"} left={"14px"}>
                    <Text fontSize={"xxs-small"} align="left" color="white">
                        v {app.description.info.version}
                    </Text>
                </Absolute>
                <Absolute top="10px" left="215px">
                    <Icon
                        onClick={() => !!favoriteApp ? favoriteApp(app.description.info.name, app.description.info.version) : undefined}
                        cursor="pointer"
                        color="red"
                        name={isFavorite ? "starFilled" : "starEmpty"}
                    />
                </Absolute>
                <Absolute top="112px" left="10px">
                    <EllipsedText width={180} title={`by ${app.description.authors.join(", ")}`} color="white">
                        by {app.description.authors.join(", ")}
                    </EllipsedText>
                </Absolute>
                <Absolute top="86px" left="200px">
                    <Link to={`/applications/${app.description.info.name}/${app.description.info.version}/`}>
                        <PlayIcon />
                    </Link>
                </Absolute>
            </Box>
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

function hexFromAppName(name: string): string {
    const hashCode = toHashCode(name);
    const color = COLORS_KEYS[(hashCode % COLORS_KEYS.length)];
    const mClength = MaterialColors[color].length;
    console.warn(MaterialColors[color][(hashCode % mClength)]);
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
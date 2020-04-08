import {Client} from "Authentication/HttpClientInstance";
import {emptyPage, ReduxObject} from "DefaultObjects";
import {loadingEvent} from "LoadableContent";
import {MainContainer} from "MainContainer/MainContainer";
import {HeaderActions, setPrioritizedSearch, setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setActivePage, StatusActions, updatePageTitle} from "Navigation/Redux/StatusActions";
import * as Pagination from "Pagination";
import {useEffect, useState} from "react";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import styled from "styled-components";
import {Page} from "Types";
import {Box, Flex, Link} from "ui-components";
import Grid from "ui-components/Grid";
import * as Heading from "ui-components/Heading";
import {SidebarPages} from "ui-components/Sidebar";
import {Spacer} from "ui-components/Spacer";
import {EllipsedText} from "ui-components/Text";
import theme from "ui-components/theme";
import {favoriteApplicationFromPage, toolImageQuery} from "Utilities/ApplicationUtilities";
import {getQueryParam, getQueryParamOrElse, RouterLocationProps, } from "Utilities/URIUtilities";
import {FullAppInfo, WithAppMetadata} from ".";
import {ApplicationCard, CardToolContainer, hashF, SmallCard, Tag} from "./Card";
import Installed from "./Installed";
import * as Pages from "./Pages";
import * as Actions from "./Redux/BrowseActions";
import {Type as ReduxType} from "./Redux/BrowseObject";
import * as Favorites from "./Redux/FavoriteActions";


export const ShowAllTagItem: React.FunctionComponent<{tag?: string}> = props => (
    <Link to={props.tag ? Pages.browseByTag(props.tag) : Pages.browse()}>{props.children}</Link>
);

export interface ApplicationsOperations {
    onInit: () => void;
    fetchDefault: (itemsPerPage: number, page: number) => void;
    fetchByTag: (tag: string, itemsPerPage: number, page: number) => void;
    receiveApplications: (page: Page<FullAppInfo>) => void;
    fetchFavorites: (itemsPerPage: number, page: number) => void;
    setRefresh: (refresh?: () => void) => void;
    receiveAppsByKey: (itemsPerPage: number, page: number, tag: string) => void;
}

export interface ApplicationsProps extends ReduxType, ApplicationsOperations, RouterLocationProps {
    favorites: Page<FullAppInfo>;
}


interface ApplicationState {
    defaultTags: string[];
}

class Applications extends React.Component<ApplicationsProps, ApplicationState> {
    constructor(props: ApplicationsProps) {
        super(props);
        this.state = {
            defaultTags: [
                "BEDTools",
                "Cell Ranger",
                "HOMER",
                "Kallisto",
                "MACS2",
                "Salmon",
                "SAMtools",
            ]
        };
    }

    public async componentDidMount(): Promise<void> {
        const {props} = this;
        props.onInit();
        this.fetch();
        props.setRefresh(() => this.fetch());
    }

    public componentDidUpdate(prevProps: ApplicationsProps): void {
        if (prevProps.location !== this.props.location) {
            this.fetch();
        }
    }

    public componentWillUnmount(): void {
        this.props.setRefresh();
    }

    private get featured(): Page<FullAppInfo> {
        const {favorites} = this.props;
        if (this.props.favorites.items.length > 0) {
            const favPairs = favorites.items.map(it => ({name: it.metadata.name, version: it.metadata.version}));
            const featuredPage: Page<FullAppInfo> = this.props.applications.get("Featured") ?? emptyPage;
            const featured = {...emptyPage};
            featured.items = featuredPage.items.filter(featApp =>
                !favPairs.some(it => it.name === featApp.metadata.name && it.version === featApp.metadata.version)
            );
            featured.itemsInTotal = featured.items.length;
            return featured;
        } else {
            return this.props.applications.get("Featured") ?? emptyPage;
        }
    }

    public render(): JSX.Element {
        const featured = this.featured;
        const {favorites} = this.props;
        const main = (
            <>
                <Installed header={null} />
                <Pagination.List
                    loading={this.props.loading}
                    pageRenderer={page => (
                        <>
                            <div>
                                <Spacer
                                    pt="15px"
                                    left={<Heading.h2>Featured</Heading.h2>}
                                    right={(
                                        <ShowAllTagItem tag="Featured">
                                            <Heading.h4 pt="15px" ><strong>Show All</strong></Heading.h4>
                                        </ShowAllTagItem>
                                    )}
                                />
                            </div>
                            <Box pl="10px" style={{overflowX: "scroll"}} pb="15px">
                                <Grid
                                    pt="20px"
                                    gridTemplateRows="repeat(3, 1fr)"
                                    gridTemplateColumns="repeat(7, 1fr)"
                                    gridGap="15px"
                                    style={{gridAutoFlow: "column"}}
                                >
                                    {page.items.map(app => (
                                        <ApplicationCard
                                            key={`${app.metadata.name}-${app.metadata.version}`}
                                            onFavorite={async () => {
                                                this.props.receiveApplications(await favoriteApplicationFromPage({
                                                    name: app.metadata.name,
                                                    version: app.metadata.version,
                                                    client: Client,
                                                    page
                                                }));
                                                this.props.fetchFavorites(favorites.itemsPerPage, favorites.pageNumber);
                                            }}
                                            app={app}
                                            isFavorite={false}
                                            tags={app.tags}
                                        />
                                    ))}
                                </Grid>
                            </Box>
                        </>
                    )}
                    page={featured}
                    onPageChanged={pageNumber => this.props.history.push(this.updatePage(pageNumber))}
                />
                {this.state.defaultTags.map(tag => <ToolGroup key={tag} tag={tag} />)}
            </>
        );
        return (
            <MainContainer
                main={main}
            />
        );
    }

    private fetchFeatured(): void {
        const featured = this.props.applications.get("Featured") ?? emptyPage;
        this.props.receiveAppsByKey(featured.itemsPerPage, featured.pageNumber, "Featured");
    }

    private fetch(): void {
        this.fetchFeatured();
        this.state.defaultTags.forEach(tag => {
            const page = this.props.applications.get(tag) ?? emptyPage;
            this.props.receiveAppsByKey(page.itemsPerPage, page.pageNumber, tag);
        });
    }

    private itemsPerPage(props: ApplicationsProps = this.props): number {
        return parseInt(getQueryParamOrElse(props, "itemsPerPage", "25"), 10);
    }

    private tag(props: ApplicationsProps = this.props): string | null {
        return getQueryParam(props, "tag");
    }

    private updatePage(newPage: number): string {
        const tag = this.tag();
        if (tag === null) {
            return Pages.browse(this.itemsPerPage(), newPage);
        } else {
            return Pages.browseByTag(tag, this.itemsPerPage(), newPage);
        }
    }
}

const ScrollBox = styled(Box)`
    overflow-x: auto;
`;

const ToolGroupWrapper = styled(Flex)`
    width: 100%;
    padding-bottom: 10px;
    padding-left: 10px;
    padding-right: 10px;
    margin-top: 30px;
    background-color: var(--appCard, #f00);
    box-shadow: ${theme.shadows.sm};
    border-radius: 5px;
    background-image: url("data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTAwJSIgaGVpZ2h0PSIxMDAlIiB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciPjxkZWZzPjxwYXR0ZXJuIHZpZXdCb3g9IjAgMCBhdXRvIGF1dG8iIHg9IjAiIHk9IjAiIGlkPSJwMSIgd2lkdGg9IjU2IiBwYXR0ZXJuVHJhbnNmb3JtPSJyb3RhdGUoMTUpIHNjYWxlKDAuNSAwLjUpIiBoZWlnaHQ9IjEwMCIgcGF0dGVyblVuaXRzPSJ1c2VyU3BhY2VPblVzZSI+PHBhdGggZD0iTTI4IDY2TDAgNTBMMCAxNkwyOCAwTDU2IDE2TDU2IDUwTDI4IDY2TDI4IDEwMCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSIjYzlkM2RmNDQiIHN0cm9rZS13aWR0aD0iMS41Ij48L3BhdGg+PHBhdGggZD0iTTI4IDBMMjggMzRMMCA1MEwwIDg0TDI4IDEwMEw1NiA4NEw1NiA1MEwyOCAzNCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSIjYzlkM2RmNDQiIHN0cm9rZS13aWR0aD0iNCI+PC9wYXRoPjwvcGF0dGVybj48L2RlZnM+PHJlY3QgZmlsbD0idXJsKCNwMSkiIHdpZHRoPSIxMDAlIiBoZWlnaHQ9IjEwMCUiPjwvcmVjdD48L3N2Zz4=");
`;

const ToolImageWrapper = styled(Box)`
    display: flex;
    width: 200px;
    justify-items: center;
    justify-content: center;
    align-items: center;
    margin-right: 10px;
`;

const ToolImage = styled.img`
    max-width: 200px;
    max-height: 190px;
    margin-left: auto;
    margin-right: auto;
    margin-top: auto;
    margin-bottom: auto;
    height: auto;
    width: 100%;
`;


// eslint-disable-next-line no-underscore-dangle
const ToolGroup_ = (props: {tag: string; page: Page<FullAppInfo>; cacheBust?: string}): JSX.Element => {
    const allTags = props.page.items.map(it => it.tags);
    const tags = new Set<string>();
    allTags.forEach(list => list.forEach(tag => tags.add(tag)));
    const url = Client.computeURL("/api", toolImageQuery(props.tag.toLowerCase().replace(/\s+/g, ""), props.cacheBust));
    const [, setLoadedImage] = useState(true);
    useEffect(() => setLoadedImage(true));
    return (
        <ToolGroupWrapper>
            <ToolImageWrapper>
                <div>
                    <ToolImage src={url} />
                </div>
            </ToolImageWrapper>
            <CardToolContainer>
                <Spacer
                    alignItems="center"
                    left={<Heading.h3> {props.tag} </Heading.h3>}
                    right={(
                        <ShowAllTagItem tag={props.tag}>
                            <Heading.h5><strong> Show All</strong></Heading.h5>
                        </ShowAllTagItem>
                    )}
                />
                <ScrollBox>
                    <Grid
                        py="10px"
                        pl="10px"
                        gridTemplateRows="repeat(2, 1fr)"
                        gridTemplateColumns="repeat(9, 1fr)"
                        gridGap="8px"
                        gridAutoFlow="column"
                    >
                        {props.page.items.map(application => {
                            const [first, second, third] = getColorFromName(application.metadata.name);
                            const withoutTag = removeTagFromTitle(props.tag, application.metadata.title);
                            return (
                                <div key={application.metadata.name}>
                                    <SmallCard
                                        title={withoutTag}
                                        color1={first}
                                        color2={second}
                                        color3={third}
                                        to={Pages.viewApplication(application.metadata)}
                                        color="white"
                                    >
                                        <EllipsedText>{withoutTag}</EllipsedText>
                                    </SmallCard>
                                </div>
                            );
                        })}
                    </Grid>
                </ScrollBox>
                <Flex flexDirection="row" alignItems="flex-start">
                    {[...tags].filter(it => it !== props.tag).map(tag => (
                        <ShowAllTagItem tag={tag} key={tag}><Tag key={tag} label={tag} /></ShowAllTagItem>
                    ))}
                </Flex>
            </CardToolContainer >
        </ToolGroupWrapper>
    );
};



function removeTagFromTitle(tag: string, title: string): string {
    if (title.startsWith(tag)) {
        const titlenew = title.replace(/homerTools/g, "");
        if (titlenew.endsWith("pl")) {
            return titlenew.slice(tag.length + 2, -3);
        } else {
            return titlenew.slice(tag.length + 2);
        }
    } else {
        return title;
    }
}

const mapToolGroupStateToProps = (
    {applicationsBrowse}: ReduxObject,
    ownProps: {tag: string}
): {page: Page<WithAppMetadata>} => {
    const {applications} = applicationsBrowse;
    const page = applications.get(ownProps.tag) ?? emptyPage;
    return {page};
};

const ToolGroup = connect(mapToolGroupStateToProps)(ToolGroup_);

const mapDispatchToProps = (
    dispatch: Dispatch<Actions.Type | HeaderActions | StatusActions | Favorites.Type>
): ApplicationsOperations => ({
    onInit: () => {
        dispatch(updatePageTitle("Applications"));
        dispatch(setPrioritizedSearch("applications"));
        dispatch(setActivePage(SidebarPages.AppStore));
    },

    fetchByTag: async (tag, itemsPerPage, page) => {
        dispatch({type: Actions.Tag.RECEIVE_APP, payload: loadingEvent(true)});
        dispatch(await Actions.fetchByTag(tag, itemsPerPage, page));
    },

    fetchDefault: async (itemsPerPage, page) => {
        dispatch({type: Actions.Tag.RECEIVE_APP, payload: loadingEvent(true)});
        dispatch(await Actions.fetch(itemsPerPage, page));
    },

    fetchFavorites: async (itemsPerPage, page) => {
        dispatch(await Favorites.fetch(itemsPerPage, page));
    },

    receiveApplications: page => dispatch(Actions.receivePage(page)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),

    receiveAppsByKey: async (itemsPerPage, page, tag) =>
        dispatch(await Actions.receiveAppsByKey(itemsPerPage, page, tag))
});

function getColorFromName(name: string): [string, string, string] {
    const hash = hashF(name);
    const num = (hash >>> 22) % (theme.appColors.length - 1);
    return theme.appColors[num] as [string, string, string];
}

const mapStateToProps = ({applicationsBrowse, applicationsFavorite}: ReduxObject): ReduxType & {
    mapSize: number;
    favorites: Page<FullAppInfo>;
} => {
    return {
        ...applicationsBrowse,
        mapSize: applicationsBrowse.applications.size,
        favorites: applicationsFavorite.applications.content ?? emptyPage
    };
};

export default connect(mapStateToProps, mapDispatchToProps)(Applications);

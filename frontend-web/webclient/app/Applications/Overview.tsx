import {Client} from "Authentication/HttpClientInstance";
import {emptyPage} from "DefaultObjects";
import {loadingEvent} from "LoadableContent";
import {MainContainer} from "MainContainer/MainContainer";
import {HeaderActions, setPrioritizedSearch, setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setActivePage, StatusActions, updatePageTitle} from "Navigation/Redux/StatusActions";
import * as Pagination from "Pagination";
import {useEffect, useState} from "react";
import * as React from "react";
import {connect, useSelector} from "react-redux";
import {Dispatch} from "redux";
import styled from "styled-components";
import {Box, Flex, Link} from "ui-components";
import Grid from "ui-components/Grid";
import * as Heading from "ui-components/Heading";
import {SidebarPages} from "ui-components/Sidebar";
import {Spacer} from "ui-components/Spacer";
import {EllipsedText} from "ui-components/Text";
import theme from "ui-components/theme";
import {favoriteApplicationFromPage, toolImageQuery} from "Utilities/ApplicationUtilities";
import {RouterLocationProps} from "Utilities/URIUtilities";
import {FullAppInfo} from ".";
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
    fetchByTag: (tag: string, excludeTools: string[], itemsPerPage: number, page: number) => void;
    receiveApplications: (page: Page<FullAppInfo>) => void;
    fetchFavorites: (itemsPerPage: number, page: number) => void;
    setRefresh: (refresh?: () => void) => void;
    receiveAppsByKey: (itemsPerPage: number, page: number, tag: string, excludeTools: string[]) => void;
}

export interface ApplicationsProps extends ReduxType, ApplicationsOperations, RouterLocationProps {
    favorites: Page<FullAppInfo>;
}

function Applications(props: ApplicationsProps): JSX.Element {
    const defaultTools = [
        "BEDTools",
        "CellRanger",
        "HOMER",
        "Kallisto",
        "MACS2",
        "Salmon",
        "SAMtools",
        "Seqtk"
    ];

    const featuredTags = [
        //"Engineering",
       // "Data Analytics",
       // "Social Science",
       // "Applied Science",
       // "Natural Science",
       // "Development",
        "Bioinformatics"
    ];

    React.useEffect(() => {
        props.onInit();
        fetch();
        props.setRefresh(() => fetch());
        return () => props.setRefresh();
    }, []);

    React.useEffect(() => {
        fetch();
    }, [props.location]);

    const featured = props.applications.get("Featured") ?? emptyPage;
    const {favorites} = props;
    const favPairs = favorites.items.map(it => ({name: it.metadata.name, version: it.metadata.version}));
    const main = (
        <>
            <Installed header={null} />
            <Pagination.List
                loading={props.loading}
                pageRenderer={() => (
                    <TagGrid favorites={favPairs} omit={[]} tag={"Featured"} columns={7} rows={3}
                        setFavorite={async (name, version, page) => {
                            props.receiveApplications(await favoriteApplicationFromPage({
                                name,
                                version,
                                client: Client,
                                page
                            }));
                            props.fetchFavorites(100, favorites.pageNumber);
                        }} />
                )}
                page={featured}
                onPageChanged={pageNumber => fetchFeatured(featured.itemsPerPage, pageNumber)}
            />

            {featuredTags.map(tag =>
                <TagGrid key={tag} favorites={favPairs} tag={tag} omit={defaultTools} rows={1} columns={7}
                    setFavorite={async (name, version, page) => {
                        props.receiveApplications(await favoriteApplicationFromPage({
                            name,
                            version,
                            client: Client,
                            page
                        }));
                        props.fetchFavorites(100, favorites.pageNumber);
                    }}
                />
            )}

            {/*defaultTools.map(tag => <ToolGroup key={tag} tag={tag} />)*/}
        </>
    );
    return (<MainContainer main={main} />);

    function fetchFeatured(itemsPerPage: number, page: number): void {
        //props.receiveAppsByKey(itemsPerPage, page, "Featured", []);
    }

    function fetch(): void {
        const featuredPage = props.applications.get("Featured") ?? emptyPage;
        fetchFeatured(50, featuredPage.pageNumber);
        [...featuredTags/*, ...defaultTools*/].forEach(tag => {
            const page = props.applications.get(tag) ?? emptyPage;
            props.receiveAppsByKey(50, page.pageNumber, tag, defaultTools);
        });
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

interface TagGridProps {
    tag: string;
    omit: string[];
    setFavorite(appName: string, appVersion: string, page: Page<FullAppInfo>): void;
    columns: number;
    rows: number;
    favorites: {name: string, version: string}[]
}

function TagGrid({tag, setFavorite, favorites, columns, rows, omit}: TagGridProps): JSX.Element {
    const page = useSelector<ReduxObject, Page<FullAppInfo>>(it =>
        it.applicationsBrowse.applications.get(tag) ?? emptyPage
    );

    //const filteredItems = page.items.filter(it => !it.tags.some(_tag => omit.includes(_tag)))
    //    .filter(it => !favorites.some(fav => fav.name === it.metadata.name && it.metadata.version === fav.version));

    return (
        <>
            <div>
                <Spacer
                    pt="15px"
                    left={<Heading.h2>{tag}</Heading.h2>}
                    right={(
                        <ShowAllTagItem tag={tag}>
                            <Heading.h4 pt="15px" ><strong>Show All</strong></Heading.h4>
                        </ShowAllTagItem>
                    )}
                />
            </div>
            <Box pl="10px" style={{overflowX: "scroll"}} pb="15px">
                <Grid
                    pt="20px"
                    gridTemplateRows={`repeat(${rows}, 1fr)`}
                    gridTemplateColumns={`repeat(${columns}}, 1fr)`}
                    gridGap="15px"
                    style={{gridAutoFlow: "column"}}
                >
                    {page.items.map(app => (
                            <ApplicationCard
                                key={`${app.metadata.name}-${app.metadata.version}`}
                                onFavorite={(name, version) => setFavorite(name, version, page)}
                                colorBySpecificTag={tag}
                                app={app}
                                isFavorite={app.favorite}
                                tags={app.tags}
                            />
                        ))}
                </Grid>
            </Box>
        </>
    );
}

const ToolGroup = (props: {tag: string; cacheBust?: string}): JSX.Element => {
    const page = useSelector<ReduxObject, Page<FullAppInfo>>(it => {
        const {applications} = it.applicationsBrowse;
        return applications.get(props.tag) ?? emptyPage;
    });
    const allTags = page.items.map(it => it.tags);
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
                        {page.items.map(application => {
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
    const titlenew = title.replace(/homerTools/g, "").replace(/seqtk: /i, "");
    if (titlenew !== title) return titlenew;
    if (title.startsWith(tag)) {
        if (titlenew.endsWith("pl")) {
            return titlenew.slice(tag.length + 2, -3);
        } else {
            return titlenew.slice(tag.length + 2);
        }
    } else {
        return title;
    }
}

const mapDispatchToProps = (
    dispatch: Dispatch<Actions.Type | HeaderActions | StatusActions | Favorites.Type>
): ApplicationsOperations => ({
    onInit: () => {
        dispatch(updatePageTitle("Applications"));
        dispatch(setPrioritizedSearch("applications"));
        dispatch(setActivePage(SidebarPages.AppStore));
    },

    fetchByTag: async (tag, excludeTools, itemsPerPage, page) => {
        dispatch({type: Actions.Tag.RECEIVE_APP, payload: loadingEvent(true)});
        dispatch(await Actions.fetchByTag(tag, excludeTools, itemsPerPage, page));
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

    receiveAppsByKey: async (itemsPerPage, page, tag, excludeTools) =>
        dispatch(await Actions.
        receiveAppsByKey(itemsPerPage, page, tag, excludeTools))
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

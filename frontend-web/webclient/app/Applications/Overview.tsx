import {emptyPage} from "@/DefaultObjects";
import {MainContainer} from "@/MainContainer/MainContainer";
import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import {Box, Divider, Flex, Link} from "@/ui-components";
import Grid from "@/ui-components/Grid";
import * as Heading from "@/ui-components/Heading";
import {Spacer} from "@/ui-components/Spacer";
import {EllipsedText} from "@/ui-components/Text";
import theme from "@/ui-components/theme";
import {AppCard, ApplicationCardType, CardToolContainer, FavoriteApp, hashF, SmallCard, Tag} from "./Card";
import * as Pages from "./Pages";
import {SidebarPages, useSidebarPage} from "@/ui-components/SidebarPagesEnum";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import * as UCloud from "@/UCloud";
import {compute} from "@/UCloud";
import ApplicationSummaryWithFavorite = compute.ApplicationSummaryWithFavorite;
import AppStoreOverview = compute.AppStoreOverview;
import AppStoreSectionType = compute.AppStoreSectionType;
import {AppToolLogo} from "@/Applications/AppToolLogo";
import {ReducedApiInterface, useResourceSearch} from "@/Resource/Search";
import {injectStyle, injectStyleSimple} from "@/Unstyled";

export const ApiLike: ReducedApiInterface = {
    routingNamespace: "applications",
    titlePlural: "Applications"
};

export const ShowAllTagItem: React.FunctionComponent<{tag?: string; children: React.ReactNode;}> = props => (
    <Link to={props.tag ? Pages.browseByTag(props.tag) : Pages.browse()}>{props.children}</Link>
);

function favoriteStatusKey(app: ApplicationSummaryWithFavorite): string {
    return `${app.metadata.name}/${app.metadata.version}`;
}

type FavoriteStatus = Record<string, {override: boolean, app: ApplicationSummaryWithFavorite}>;

const ApplicationsOverview: React.FunctionComponent = () => {
    const [sections, fetchOverview] = useCloudAPI<AppStoreOverview>(
        {noop: true},
        {sections: []}
    );

    const [refreshId, setRefreshId] = useState<number>(0);

    useEffect(() => {
        fetchOverview(UCloud.compute.apps.appStoreOverview());
    }, [refreshId]);

    useResourceSearch(ApiLike);

    useTitle("Applications");
    useSidebarPage(SidebarPages.AppStore);
    const refresh = () => {
        setRefreshId(refreshId + 1);
    };
    useRefreshFunction(refresh);

    const [loadingCommand, invokeCommand] = useCloudCommand();
    const favoriteStatus = React.useRef<FavoriteStatus>({});

    const onFavorite = useCallback(async (app: ApplicationSummaryWithFavorite) => {
        if (!loadingCommand) {
            const key = favoriteStatusKey(app);
            const isFavorite = favoriteStatus.current[key]?.override ?? app.favorite;
            favoriteStatus.current[key] = {override: !isFavorite, app};
            favoriteStatus.current = {...favoriteStatus.current};
            try {
                await invokeCommand(UCloud.compute.apps.toggleFavorite({
                    appName: app.metadata.name,
                    appVersion: app.metadata.version
                }));
            } catch (e) {
                favoriteStatus.current[key].override = !favoriteStatus.current[key].override;
                favoriteStatus.current = {...favoriteStatus.current};
            }
        }
    }, [loadingCommand, favoriteStatus]);

    const [favorites, fetchFavorites] = useCloudAPI<UCloud.Page<ApplicationSummaryWithFavorite>>(
        {noop: true},
        emptyPage,
    );

    useEffect(() => {
        fetchFavorites(UCloud.compute.apps.retrieveFavorites({itemsPerPage: 100, page: 0}));
    }, [refreshId]);

    const main = (
        <>
            <Box mt="12px" />
            <TagGrid
                tag={SPECIAL_FAVORITE_TAG}
                items={favorites.data.items}
                tagBanList={[]}
                columns={7}
                rows={3}
                favoriteStatus={favoriteStatus}
                onFavorite={onFavorite}
                refreshId={refreshId}
            />
            <Divider mt="18px" />
            {sections.data.sections.map(section =>
                section.type === AppStoreSectionType.TAG ?
                    <TagGrid
                        key={section.name + section.type}
                        tag={section.name}
                        items={section.applications}
                        columns={section.columns}
                        rows={section.rows}
                        favoriteStatus={favoriteStatus}
                        onFavorite={onFavorite}
                        tagBanList={[]}
                        refreshId={refreshId}
                    />
                    :
                    <ToolGroup items={section.applications} key={section.name + section.type} tag={section.name} />
            )}
        </>
    );
    return (<div className={AppOverviewMarginPaddingHack}><MainContainer main={main} /></div>);
};

const AppOverviewMarginPaddingHack = injectStyleSimple("HACK-HACK-HACK", `
/* HACK */
    margin-top: -12px;
    padding-top: 12px;
/* HACK */
`);

const ScrollBoxClass = injectStyleSimple("scroll-box", `
    overflow-x: auto;
`);

const ToolGroupWrapperClass = injectStyleSimple("tool-group-wrapper", `
    width: 100%;
    padding-bottom: 10px;
    padding-left: 10px;
    padding-right: 10px;
    margin-top: 30px;
    background-color: var(--appCard, #f00);
    box-shadow: ${theme.shadows.sm};
    border-radius: 5px;
    background-image: url("data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTAwJSIgaGVpZ2h0PSIxMDAlIiB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciPjxkZWZzPjxwYXR0ZXJuIHZpZXdCb3g9IjAgMCBhdXRvIGF1dG8iIHg9IjAiIHk9IjAiIGlkPSJwMSIgd2lkdGg9IjU2IiBwYXR0ZXJuVHJhbnNmb3JtPSJyb3RhdGUoMTUpIHNjYWxlKDAuNSAwLjUpIiBoZWlnaHQ9IjEwMCIgcGF0dGVyblVuaXRzPSJ1c2VyU3BhY2VPblVzZSI+PHBhdGggZD0iTTI4IDY2TDAgNTBMMCAxNkwyOCAwTDU2IDE2TDU2IDUwTDI4IDY2TDI4IDEwMCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSIjYzlkM2RmNDQiIHN0cm9rZS13aWR0aD0iMS41Ij48L3BhdGg+PHBhdGggZD0iTTI4IDBMMjggMzRMMCA1MEwwIDg0TDI4IDEwMEw1NiA4NEw1NiA1MEwyOCAzNCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSIjYzlkM2RmNDQiIHN0cm9rZS13aWR0aD0iNCI+PC9wYXRoPjwvcGF0dGVybj48L2RlZnM+PHJlY3QgZmlsbD0idXJsKCNwMSkiIHdpZHRoPSIxMDAlIiBoZWlnaHQ9IjEwMCUiPjwvcmVjdD48L3N2Zz4=");
`);

const ToolImageWrapperClass = injectStyleSimple("tool-image-wrapper", `
    display: flex;
    width: 200px;
    justify-items: center;
    justify-content: center;
    align-items: center;
    margin-right: 10px;
`);

const ToolImageClass = injectStyle("tool-image", k => `
    ${k} > * {
      max-width: 200px;
      max-height: 190px;
      margin-left: auto;
      margin-right: auto;
      margin-top: auto;
      margin-bottom: auto;
      height: auto;
      width: 100%;
    }
`);

// NOTE(Dan): We don't allow new lines in tags normally. As a result, we can be pretty confident that no application
// will have this tag.
const SPECIAL_FAVORITE_TAG = "\n\nFavorites\n\n";

const TagGridTopBoxClass = injectStyle("tag-grid-top-box", k => `
    ${k} {
        border-top-left-radius: 10px;
        border-top-right-radius: 10px;
        background-color: var(--lightGray);
    }

    ${k}[data-favorite="true"] {
        background-color: var(--appStoreFavBg);
    }
`);

const TagGridBottomBoxClass = injectStyle("tag-grid-bottom-box", k => `
    ${k} {
        padding: 0px 10px 15px 10px;
        border-bottom-left-radius: 10px;
        border-bottom-right-radius: 10px;
        background-color: var(--lightGray);
    }

    ${k}[data-favorite="true"] {
        background-color: var(--appStoreFavBg);
    }

    ${k}[data-favorite="false"] {
        overflow-x: scroll;
    }
`);


interface TagGridProps {
    tag: string;
    items: ApplicationSummaryWithFavorite[];
    tagBanList?: string[];
    columns: number;
    rows: number;
    favoriteStatus: React.MutableRefObject<FavoriteStatus>;
    onFavorite: (app: ApplicationSummaryWithFavorite) => void;
    refreshId: number;
}

const TagGrid: React.FunctionComponent<TagGridProps> = (
    {tag, rows, items, tagBanList = [], favoriteStatus, onFavorite}: TagGridProps
) => {
    const showFavorites = tag == SPECIAL_FAVORITE_TAG;

    const filteredItems = React.useMemo(() => {
        let _filteredItems = items
            .filter(it => !it.tags.some(_tag => tagBanList.includes(_tag)))
            .filter(item => {
                const isFavorite = favoriteStatus.current[favoriteStatusKey(item)]?.override ?? item.favorite;
                return isFavorite === showFavorites;
            });

        if (showFavorites) {
            _filteredItems = _filteredItems.concat(Object.values(favoriteStatus.current).filter(it => it.override).map(it => it.app));
            _filteredItems = _filteredItems.filter(it => favoriteStatus.current[favoriteStatusKey(it)]?.override !== false);
        }

        // Remove duplicates (This can happen due to favorite cache)
        {
            const observed = new Set<string>();
            const newList: ApplicationSummaryWithFavorite[] = [];
            for (const item of _filteredItems) {
                const key = favoriteStatusKey(item);
                if (!observed.has(key)) {
                    observed.add(key);
                    newList.push(item);
                }
            }

            return newList;
        }
    }, [items, favoriteStatus.current]);

    if (filteredItems.length === 0) return null;

    if (showFavorites) {
        return <Flex overflowX="scroll" width="100%">
            <Flex mx="auto" mb="16px">
                {filteredItems.map(app =>
                    <FavoriteApp key={app.metadata.name + app.metadata.version} name={app.metadata.name} version={app.metadata.version} onFavorite={() => onFavorite(app)} />
                )}
            </Flex>
        </Flex>
    }

    return (
        <>
            <div className={TagGridTopBoxClass} data-favorite={showFavorites}>
                <Spacer
                    mt="15px" px="10px" alignItems={"center"}
                    left={<Heading.h2>{showFavorites ? "Favorites" : tag}</Heading.h2>}
                    right={(
                        showFavorites ? null : (
                            <ShowAllTagItem tag={tag}>
                                <Heading.h4>Show All</Heading.h4>
                            </ShowAllTagItem>
                        )
                    )}
                />
            </div>
            <div className={TagGridBottomBoxClass} data-favorite={showFavorites}>
                <Grid
                    pt="20px"
                    gridGap="10px"
                    gridTemplateRows={showFavorites ? undefined : `repeat(${rows} , 1fr)`}
                    gridTemplateColumns={showFavorites ? "repeat(auto-fill, minmax(156px, 1fr))" : "repeat(auto-fill, 156px)"}
                    style={{gridAutoFlow: showFavorites ? "row" : "column"}}
                >
                    {filteredItems.map(app =>
                        <Link key={app.metadata.name + app.metadata.version} to={Pages.run(app.metadata.name, app.metadata.version)}>
                            <AppCard
                                type={ApplicationCardType.EXTRA_TALL}
                                onFavorite={() => onFavorite(app)}
                                app={app}
                                isFavorite={app.favorite}
                                tags={app.tags}
                            />
                        </Link>
                    )}
                </Grid>
            </div>
        </>
    );
};

const ToolGroup: React.FunctionComponent<{tag: string, items: ApplicationSummaryWithFavorite[]}> = ({tag, items}) => {
    const tags = React.useMemo(() => {
        const allTags = items.map(it => it.tags);
        const t = new Set<string>();
        allTags.forEach(list => list.forEach(tag => t.add(tag)));
        return t;
    }, [items]);

    return (
        <div className={ToolGroupWrapperClass}>
            <Spacer
                alignItems="center"
                left={<Heading.h3>{tag}</Heading.h3>}
                right={(
                    <ShowAllTagItem tag={tag}>
                        <Heading.h5 bold={false} regular={true}>Show All</Heading.h5>
                    </ShowAllTagItem>
                )}
            />
            <Flex>
                <div className={ToolImageWrapperClass}>
                    <div className={ToolImageClass}>
                        <AppToolLogo size="148px" name={tag.toLowerCase().replace(/\s+/g, "")} type={"TOOL"} />
                    </div>
                </div>
                <CardToolContainer>
                    <div className={ScrollBoxClass}>
                        <Grid
                            py="10px"
                            pl="10px"
                            gridTemplateRows="repeat(2, 1fr)"
                            gridTemplateColumns="repeat(9, 1fr)"
                            gridGap="8px"
                            gridAutoFlow="column"
                        >
                            {items.map(application => {
                                const backgroundColor = getColorFromName(application.metadata.name);
                                const withoutTag = removeTagFromTitle(tag, application.metadata.title);
                                return (
                                    <SmallCard
                                        key={application.metadata.name}
                                        title={withoutTag}
                                        to={Pages.runApplication(application.metadata)}
                                        color="white"
                                    >
                                        <Box backgroundColor={backgroundColor} padding="16px" borderRadius="16px">
                                            <EllipsedText>{withoutTag}</EllipsedText>
                                        </Box>
                                    </SmallCard>
                                );
                            })}
                        </Grid>
                    </div>
                    <Flex flexDirection="row" alignItems="flex-start">
                        {[...tags].filter(it => it !== tag).map(tag => (
                            <ShowAllTagItem tag={tag} key={tag}><Tag key={tag} label={tag} /></ShowAllTagItem>
                        ))}
                    </Flex>
                </CardToolContainer>
            </Flex>
        </div>
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

function getColorFromName(name: string): string {
    const hash = hashF(name);
    const num = (hash >>> 22) % (theme.appColors.length - 1);
    return theme.appColors[num][1];
}

export default ApplicationsOverview;

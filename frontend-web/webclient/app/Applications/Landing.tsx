import {MainContainer} from "@/MainContainer/MainContainer";
import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import {Box, Button, Flex, Icon, Input, Link, theme} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {AppCard, ApplicationCardType, FavoriteApp} from "./Card";
import * as Pages from "./Pages";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import * as UCloud from "@/UCloud";
import {compute} from "@/UCloud";
import ApplicationSummaryWithFavorite = compute.ApplicationSummaryWithFavorite;
import AppStoreSections = compute.AppStoreSections;
import {ReducedApiInterface, useResourceSearch} from "@/Resource/Search";
import {injectStyle, injectStyleSimple} from "@/Unstyled";
import {useDispatch, useSelector} from "react-redux";
import {toggleAppFavorite} from "./Redux/Actions";
import {useNavigate} from "react-router";
import AppRoutes from "@/Routes";
import {TextSpan} from "@/ui-components/Text";
import favoritesImage from "@/Assets/Images/ucloud-2.png";
import featuredImage from "/Images/ucloud-1.png";
import popularImage from "/Images/ucloud-9.svg";
import {ContextSwitcher} from "@/Project/ContextSwitcher";
import ApplicationRow, {ApplicationRowContainerClass} from "./ApplicationsRow";
import { GradientWithPolygons } from "@/ui-components/GradientBackground";
import {AppSearchBox} from "./Search";

export const ApiLike: ReducedApiInterface = {
    routingNamespace: "applications",
    titlePlural: "Applications"
};

export const ShowAllTagItem: React.FunctionComponent<{tag?: string; children: React.ReactNode;}> = props => (
    <Link to={props.tag ? Pages.browseByTag(props.tag) : Pages.browse()}>{props.children}</Link>
);

function favoriteStatusKey(metadata: compute.ApplicationMetadata): string {
    return `${metadata.name}/${metadata.version}`;
}

type FavoriteStatus = Record<string, {override: boolean, app: ApplicationSummaryWithFavorite}>;

const ViewAllButtonClass = injectStyle("view-all-button", k => `
    ${k} {
        width: 200px;
        margin: 30px auto;
    }

    ${k} button {
        width: 200px;
        text-align: center;
    }

    ${k} button svg {
    }
`);

export const SecondarySidebarStickyCSSVariable = "TODO"; 

function ViewAllButton(): JSX.Element {
    const navigate = useNavigate();

    return <div className={ViewAllButtonClass}>
        <Button
            onClick={() => navigate(AppRoutes.apps.overview())}
        >
            <TextSpan pr="15px">View all</TextSpan>
            <Icon rotation={-90} name="chevronDownLight" size="18px" />
        </Button>
    </div>;
}

const AppStoreVisualClass = injectStyle("app-store-visual", k => `
    ${k} {
        margin-top: 60px;
        margin-bottom: 40px;
        justify-content: space-around;
        align-items: center;
    }

    ${k} h1 {
        text-align: center;
        color: #5c89f4;
    }

    ${k} img {
        max-height: 200px;
        transform: scaleX(-1);
    }
`);

const ApplicationsLanding: React.FunctionComponent = () => {
    const [sections, fetchSections] = useCloudAPI<AppStoreSections>(
        {noop: true},
        {sections: []}
    );

    const [refreshId, setRefreshId] = useState<number>(0);

    useEffect(() => {
        fetchSections(UCloud.compute.apps.appStoreSections({page: "LANDING"}));
    }, [refreshId]);

    useResourceSearch(ApiLike);

    const dispatch = useDispatch();

    useTitle("Applications");
    const refresh = useCallback(() => {
        setRefreshId(id => id + 1);
    }, []);
    useRefreshFunction(refresh);

    const [, invokeCommand] = useCloudCommand();
    const favoriteStatus = React.useRef<FavoriteStatus>({});

    const onFavorite = useCallback(async (app: ApplicationSummaryWithFavorite) => {
        // Note(Jonas): This used to check commandLoading (from invokeCommand), but this gets stuck at true, so removed for now.
        const key = favoriteStatusKey(app.metadata);
        const isFavorite = favoriteStatus.current[key]?.override ?? app.favorite;
        if (favoriteStatus.current[key]) {
            delete favoriteStatus.current[key]
        } else {
            favoriteStatus.current[key] = {override: !isFavorite, app};
        }
        favoriteStatus.current = {...favoriteStatus.current};
        dispatch(toggleAppFavorite(app, !isFavorite));
        try {
            await invokeCommand(UCloud.compute.apps.toggleFavorite({
                appName: app.metadata.name,
                appVersion: app.metadata.version
            }));
        } catch (e) {
            favoriteStatus.current[key].override = !favoriteStatus.current[key].override;
            favoriteStatus.current = {...favoriteStatus.current};
        }
    }, [favoriteStatus]);

    return (
        <div className={AppOverviewMarginPaddingHack}>
            <div className={GradientWithPolygons}>
                <MainContainer main={
                    <Box mx="auto" maxWidth="1340px">
                        <Flex justifyContent="right" mt="30px">
                            <AppSearchBox hidden={false} />
                            <ContextSwitcher />
                        </Flex>
                        <Box mt="12px" />

                        <Flex className={AppStoreVisualClass}>
                            <Heading.h1>Favorite Applications</Heading.h1>
                            <img src={favoritesImage} />
                        </Flex>

                        <FavoriteAppRow
                            favoriteStatus={favoriteStatus}
                            onFavorite={onFavorite}
                            refreshId={refreshId}
                        />

                        <Flex className={AppStoreVisualClass}>
                            <img src={featuredImage} />
                            <Heading.h1>Featured Applications</Heading.h1>
                        </Flex>

                        {sections.data.sections[0] ?
                            <>
                                <ApplicationRow
                                    items={sections.data.sections[0].featured.slice(0, 4)}
                                    type={ApplicationCardType.WIDE}
                                    refreshId={refreshId}
                                    scrolling={false}
                                />

                                <ApplicationRow
                                    items={sections.data.sections[0].featured.slice(4)}
                                    type={ApplicationCardType.TALL}
                                    refreshId={refreshId}
                                    scrolling={false}
                                />
                            </>
                        : <></>}


                        <Flex className={AppStoreVisualClass}>
                            <Heading.h1>Popular Applications</Heading.h1>
                            <img src={popularImage} />
                        </Flex>

                        {sections.data.sections[1] ?
                            <>
                                <ApplicationRow
                                    items={sections.data.sections[1].featured.slice(0, 4)}
                                    type={ApplicationCardType.WIDE}
                                    refreshId={refreshId}
                                    scrolling={false}
                                />

                                <ApplicationRow
                                    items={sections.data.sections[1].featured.slice(4)}
                                    type={ApplicationCardType.TALL}
                                    refreshId={refreshId}
                                    scrolling={false}
                                />
                            </>
                        : <></>}

                        <ViewAllButton />
                    </Box>
                } />
            </div>
        </div>
    );
};

const AppOverviewMarginPaddingHack = injectStyleSimple("HACK-HACK-HACK", `
/* HACK */
    margin-top: -12px;
/* HACK */
`);

interface FavoriteAppRowProps {
    favoriteStatus: React.MutableRefObject<FavoriteStatus>;
    onFavorite: (app: ApplicationSummaryWithFavorite) => void;
    refreshId: number;
}

function filterAppsByFavorite(
    items: compute.ApplicationSummaryWithFavorite[],
    favoriteStatus: React.MutableRefObject<FavoriteStatus>
): compute.ApplicationSummaryWithFavorite[] {
    let filteredItems = items.filter(item =>
        favoriteStatus.current[favoriteStatusKey(item.metadata)]?.override ?? item.favorite
    );

    filteredItems = [...filteredItems, ...Object.values(favoriteStatus.current).filter(it => it.override).map(it => it.app)];
    filteredItems = filteredItems.filter(it => favoriteStatus.current[favoriteStatusKey(it.metadata)]?.override !== false);

    // Remove duplicates (This can happen due to favorite cache)
    {
        const observed = new Set<string>();
        const newList: ApplicationSummaryWithFavorite[] = [];
        for (const item of filteredItems) {
            const key = favoriteStatusKey(item.metadata);
            if (!observed.has(key)) {
                observed.add(key);
                newList.push(item);
            }
        }
        return newList;
    }
}

const FavoriteRowContainerClass = injectStyle("favorite-row-container", k => `
    ${k} {
    }

    ${k} h4 {
    }
`);



function FavoriteAppRow({favoriteStatus, onFavorite}: FavoriteAppRowProps): JSX.Element {
    const items = useSelector<ReduxObject, compute.ApplicationSummaryWithFavorite[]>(it => it.sidebar.favorites);
    const filteredItems = React.useMemo(() =>
        filterAppsByFavorite(items, favoriteStatus),
        [items, favoriteStatus.current]
    );

    return <div className={FavoriteRowContainerClass}>
            <div className={ApplicationRowContainerClass}>
                <Flex
                    justifyContent="left"
                    gap="25px"
                    py="10px"
                >
                    {filteredItems.map(app =>
                        <Link key={app.metadata.name + app.metadata.version} to={Pages.run(app.metadata.name, app.metadata.version)}>
                            <AppCard
                                type={ApplicationCardType.TALL}
                                title={app.metadata.title}
                                logo={app.metadata.name}
                                logoType="APPLICATION"
                                description={app.metadata.description}
                            />
                        </Link>
                    )}
                </Flex>
            </div>
    </div>
}

export default ApplicationsLanding;

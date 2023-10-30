import {MainContainer} from "@/MainContainer/MainContainer";
import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import {Box, Button, Flex, Icon, Input, Link, theme} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {ApplicationCardType, FavoriteApp} from "./Card";
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
import ucloudImage from "@/Assets/Images/ucloud-2.png";
import {ContextSwitcher} from "@/Project/ContextSwitcher";
import ApplicationRow from "./ApplicationsRow";
import { GradientWithPolygons } from "@/ui-components/GradientBackground";
import {CSSVarCurrentSidebarStickyWidth} from "@/ui-components/Sidebar";
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

const FloatingButtonClass = injectStyle("floating-button", k => `
    ${k} {
        position: fixed;
        bottom: 30px;
        width: 200px;
        left: calc(50% - (100px + var(${CSSVarCurrentSidebarStickyWidth})/2) + var(${CSSVarCurrentSidebarStickyWidth}));
    }

    ${k} button {
        width: 200px;
        text-align: center;
        box-shadow: ${theme.shadows.sm};
    }
`);

export const SecondarySidebarStickyCSSVariable = "TODO"; 

function FloatingButton(): JSX.Element {
    const navigate = useNavigate();

    return <div className={FloatingButtonClass}>
        <Button
            onClick={() => navigate(AppRoutes.apps.overview())}
            borderRadius="99px"
        >
            <TextSpan pr="15px">View all</TextSpan>
            <Icon name="chevronDownLight" size="18px" />
        </Button>
    </div>;
}

const LandingDivider = injectStyle("landing-divider", k => `
    ${k} {
        margin-top: 50px;
        margin-bottom: 50px;
    }

    ${k} h1 {
        text-align: center;
        margin-top: 50px;
        color: #5c89f4;
    }

    ${k} img {
        max-height: 250px;
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
                            <AppSearchBox />
                            <ContextSwitcher />
                        </Flex>
                        <Box mt="12px" />

                        <FavoriteAppRow
                            favoriteStatus={favoriteStatus}
                            onFavorite={onFavorite}
                            refreshId={refreshId}
                        />

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


                        <Flex className={LandingDivider} justifyContent="space-around">
                            <Heading.h1>Featured<br />Applications</Heading.h1>
                            <img src={ucloudImage} />
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

                        <FloatingButton />
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

export function FavoriteAppRow({favoriteStatus, onFavorite}: FavoriteAppRowProps): JSX.Element {
    const items = useSelector<ReduxObject, compute.ApplicationSummaryWithFavorite[]>(it => it.sidebar.favorites);
    const filteredItems = React.useMemo(() =>
        filterAppsByFavorite(items, favoriteStatus),
        [items, favoriteStatus.current]
    );

    return <Flex overflowX="auto" width="100%">
        <Flex mx="auto" mb="16px">
            {filteredItems.map(app =>
                <FavoriteApp key={app.metadata.name + app.metadata.version} name={app.metadata.name} version={app.metadata.version} title={app.metadata.title} onFavorite={() => onFavorite(app)} />
            )}
        </Flex>
    </Flex>
}

export default ApplicationsLanding;

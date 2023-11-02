import {MainContainer} from "@/MainContainer/MainContainer";
import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import {Box, Button, Flex, Icon, Input, Link, Relative, theme} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {AppCard, ApplicationCardType} from "./Card";
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

export const ApiLike: ReducedApiInterface = {
    routingNamespace: "applications",
    titlePlural: "Applications"
};

function favoriteStatusKey(metadata: compute.ApplicationMetadata): string {
    return `${metadata.name}/${metadata.version}`;
}

type FavoriteStatus = Record<string, {override: boolean, app: ApplicationSummaryWithFavorite}>;

const LandingAppSearchBoxClass = injectStyle("app-search-box", k => `
    ${k} {
        margin: -35px auto 0 auto;
        width: 300px;
        position: relative;
        align-items: center;
    }

    ${k} input.search-field {
        width: 100%;
        padding-right: 2.5rem;
    }

    ${k} button {
        background: none;
        border: 0;
        padding: 0px 10px 1px 10px;
        cursor: pointer;
        position: absolute;
        right: 0;
        height: 2.4rem;
    }
`);

export const LandingAppSearchBox: React.FunctionComponent<{value?: string; hidden?: boolean}> = props => {
    const navigate = useNavigate();
    const inputRef = React.useRef<HTMLInputElement>(null);

    return <Flex className={LandingAppSearchBoxClass}>
        <Input
            className="search-field"
            defaultValue={props.value}
            inputRef={inputRef}
            placeholder="Search for applications..."
            onKeyUp={e => {
                if (e.key === "Enter") {
                    const queryCurrent = inputRef.current;
                    if (!queryCurrent) return;

                    const queryValue = queryCurrent.value;

                    if (queryValue === "") return;

                    navigate(AppRoutes.apps.search(queryValue));
                }
            }}
            autoFocus
        />
        <button>
            <Icon name="search" size={20} color="darkGray" my="auto" onClick={e => {
                const queryCurrent = inputRef.current;
                if (!queryCurrent) return;

                const queryValue = queryCurrent.value;

                if (queryValue === "") return;

                navigate(AppRoutes.apps.search(queryValue));
            }} />
        </button>
    </Flex>;
}



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
        margin: 32px 0 20px 0;
        justify-content: left;
        align-items: center;
        gap: 15px;
    }

    ${k} h1 {
        color: #5c89f4;
        font-weight: 400;
    }

    ${k} img {
        max-height: 150px;
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
                            <ContextSwitcher />
                        </Flex>
                        <LandingAppSearchBox hidden={false} />
                        <Box mt="12px" />

                        <FavoriteAppRow
                            favoriteStatus={favoriteStatus}
                            onFavorite={onFavorite}
                            refreshId={refreshId}
                        />

                        <Flex className={AppStoreVisualClass}>
                            {/* <img src={featuredImage} /> */}
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
                            {/* <img src={popularImage} /> */}
                            <Heading.h1>Popular Applications</Heading.h1>
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

function FavoriteAppRow({favoriteStatus, onFavorite}: FavoriteAppRowProps): JSX.Element {
    const items = useSelector<ReduxObject, compute.ApplicationSummaryWithFavorite[]>(it => it.sidebar.favorites);
    const filteredItems = React.useMemo(() =>
        filterAppsByFavorite(items, favoriteStatus),
        [items, favoriteStatus.current]
    );

    return filteredItems.length < 1 ? <></> : <>
        <Flex className={AppStoreVisualClass}>
            {/* <img src={favoritesImage} /> */}
            <Heading.h1>Favorite Applications</Heading.h1>
        </Flex>
        <div className={ApplicationRowContainerClass} data-space-between={items.length > 6}>
            {filteredItems.map(app =>
                <Flex>
                    <Link key={app.metadata.name + app.metadata.version} to={Pages.run(app.metadata.name, app.metadata.version)}>
                        <AppCard
                            type={ApplicationCardType.TALL}
                            title={app.metadata.title}
                            logo={app.metadata.name}
                            contentType="APPLICATION"
                            description={app.metadata.description}
                            isFavorite={true}
                        />
                    </Link>
                    <Relative top="6px" right="28px" width="0px" height="0px">
                        <Icon cursor="pointer" name="starFilled" color="blue" hoverColor="blue" size="20px" onClick={() => onFavorite(app)} />
                    </Relative>
                </Flex>
            )}
        </div>
    </>
}

export default ApplicationsLanding;

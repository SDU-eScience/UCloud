import {MainContainer} from "@/ui-components/MainContainer";
import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import {Box, Button, Flex, Icon, Input} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {AppCardStyle} from "./Card";
import {useTitle} from "@/Navigation/Redux";
import {callAPI, useCloudAPI} from "@/Authentication/DataHook";
import * as UCloud from "@/UCloud";
import {compute} from "@/UCloud";
import ApplicationSummaryWithFavorite = compute.ApplicationSummaryWithFavorite;
import AppStoreSections = compute.AppStoreSections;
import {injectStyle, injectStyleSimple} from "@/Unstyled";
import {useDispatch, useSelector} from "react-redux";
import {toggleAppFavorite} from "./Redux/Actions";
import {useNavigate} from "react-router";
import AppRoutes from "@/Routes";
import {TextSpan} from "@/ui-components/Text";
import {ContextSwitcher} from "@/Project/ContextSwitcher";
import ApplicationRow, {ApplicationGroupToRowItem, ApplicationSummaryToRowItem} from "./ApplicationsRow";
import { GradientWithPolygons } from "@/ui-components/GradientBackground";
import {displayErrorMessageOrDefault} from "@/UtilityFunctions";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";

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

    const onSearch = useCallback(() => {
        const queryCurrent = inputRef.current;
        if (!queryCurrent) return;

        const queryValue = queryCurrent.value;

        if (queryValue === "") return;

        navigate(AppRoutes.apps.search(queryValue));
    }, [inputRef.current]);

    return <Flex className={LandingAppSearchBoxClass}>
        <Input
            className="search-field"
            defaultValue={props.value}
            inputRef={inputRef}
            placeholder="Search for applications..."
            onKeyUp={e => {
                if (e.key === "Enter") {
                    onSearch()
                }
            }}
            autoFocus
        />
        <button>
            <Icon name="search" size={20} color="textSecondary" my="auto" onClick={() => onSearch()} />
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

function ViewAllButton(): React.JSX.Element {
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

    const dispatch = useDispatch();

    useTitle("Applications");
    const refresh = useCallback(() => {
        setRefreshId(id => id + 1);
    }, []);
    useSetRefreshFunction(refresh);

    const favorites = useSelector<ReduxObject, ApplicationSummaryWithFavorite[]>(it => it.sidebar.favorites);

    const onFavorite = useCallback(async (app: ApplicationSummaryWithFavorite) => {
        const favoriteApp = favorites.find(it => it.metadata.name === app.metadata.name);
        const isFavorite = favoriteApp !== undefined ? true : app.favorite;

        dispatch(toggleAppFavorite(app, !isFavorite));

        try {
            await callAPI(UCloud.compute.apps.toggleFavorite({
                appName: app.metadata.name
            }));
        } catch (e) {
            displayErrorMessageOrDefault(e, "Failed to toggle favorite");
            dispatch(toggleAppFavorite(app, !isFavorite));
        }
    }, [favorites]);

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

                        {favorites.length < 1 ? <></> : <>
                            <Flex className={AppStoreVisualClass}>
                                <Heading.h1>Favorite Applications</Heading.h1>
                            </Flex>

                            <ApplicationRow
                                cardStyle={AppCardStyle.TALL}
                                items={favorites.map(app => ApplicationSummaryToRowItem({metadata: app.metadata, favorite: true, tags: app.tags}))}
                                onFavorite={onFavorite}
                                refreshId={refreshId}
                            />
                        </>}

                        {sections.data.sections.map(section => (
                            <div key={section.id}>
                                <Flex className={AppStoreVisualClass}>
                                    <Heading.h1>{section.name}</Heading.h1>
                                </Flex>
                                <ApplicationRow
                                    items={section.featured.slice(0, 4).map(ApplicationGroupToRowItem)}
                                    onFavorite={onFavorite}
                                    cardStyle={AppCardStyle.WIDE}
                                    refreshId={refreshId}
                                />
                                <ApplicationRow
                                    items={section.featured.slice(4).map(ApplicationGroupToRowItem)}
                                    onFavorite={onFavorite}
                                    cardStyle={AppCardStyle.TALL}
                                    refreshId={refreshId}
                                />
                            </div>
                        ))}
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

export default ApplicationsLanding;

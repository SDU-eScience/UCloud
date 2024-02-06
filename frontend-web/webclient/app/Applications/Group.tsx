import * as Heading from "@/ui-components/Heading";
import React, {useCallback, useEffect} from "react";
import {useParams} from "react-router";
import {callAPI, useCloudAPI} from "@/Authentication/DataHook";
import {AppToolLogo} from "./AppToolLogo";
import {Box, Flex, Grid} from "@/ui-components";
import {AppCard, AppCardType} from "./Card";
import * as AppStore from "@/Applications/AppStoreApi";
import * as Pages from "./Pages";
import {AppSearchBox, useAppSearch} from "./Search";
import {ContextSwitcher} from "@/Project/ContextSwitcher";
import {toggleAppFavorite} from "./Redux/Actions";
import {useDispatch, useSelector} from "react-redux";
import {displayErrorMessageOrDefault, doNothing} from "@/UtilityFunctions";
import {ApplicationGroup, ApplicationSummaryWithFavorite} from "@/Applications/AppStoreApi";
import {UtilityBar} from "@/Navigation/UtilityBar";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {useTitle} from "@/Navigation/Redux";


const ApplicationsGroup: React.FunctionComponent = () => {
    const idParam = useParams<{ id: string }>().id;
    const id = parseInt(idParam ?? "-1");

    const [appGroup, fetchAppGroup] = useCloudAPI(AppStore.retrieveGroup({ id }), null);

    const dispatch = useDispatch();

    const refresh = useCallback(() => {
        fetchAppGroup(AppStore.retrieveGroup({id})).then(doNothing);
    }, [id]);

    useTitle(appGroup.data?.specification.title ?? "Application");
    useSetRefreshFunction(refresh);

    const favoriteStatus = useSelector<ReduxObject, ApplicationSummaryWithFavorite[]>(it => it.sidebar.favorites);

    const onFavorite = useCallback(async (app: ApplicationSummaryWithFavorite) => {
        const favoriteApp = favoriteStatus.find(it => it.metadata.name === app.metadata.name);
        const isFavorite = favoriteApp !== undefined ? true : app.favorite;

        dispatch(toggleAppFavorite(app, !isFavorite));

        try {
            await callAPI(AppStore.toggleStar({
                name: app.metadata.name
            }));
        } catch (e) {
            displayErrorMessageOrDefault(e, "Failed to toggle favorite");
            dispatch(toggleAppFavorite(app, !isFavorite));
        }
    }, [favoriteStatus]);

    const appSearch = useAppSearch();

    if (!appGroup.data) return <>Not found</>;

    return <Box mx="auto" maxWidth="1340px">
        <Flex justifyContent="space-between" mt="30px">
            <Heading.h2>
                <AppToolLogo name={appGroup.data?.metadata.id.toString()} type="GROUP" size="45px"/>
                {" "}
                {appGroup.data?.specification?.title}
            </Heading.h2>

            <Flex justifyContent="right">
                <UtilityBar onSearch={appSearch} />
            </Flex>
        </Flex>
        <Box mt="30px"/>
        <Grid
            width="100%"
            gridTemplateColumns={`repeat(auto-fit, minmax(430px, 1fr))`}
            gridGap="30px"
        >
            {appGroup.data?.status?.applications?.map(app => (
                <AppCard
                    key={app.metadata.name}
                    title={app.metadata.flavorName ?? app.metadata.title}
                    description={app.metadata.description}
                    logo={app.metadata.name}
                    type={AppCardType.APPLICATION}
                    link={Pages.run(app.metadata.name)}
                    onFavorite={onFavorite}
                    isFavorite={favoriteStatus.find(it => it.metadata.name === app.metadata.name) !== undefined ? true : app.favorite}
                    application={app}
                />
            ))}
        </Grid>
    </Box>;
}

export default ApplicationsGroup;

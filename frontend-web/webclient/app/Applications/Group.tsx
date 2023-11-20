import * as Heading from "@/ui-components/Heading";
import React, {useCallback, useEffect} from "react";
import {useParams} from "react-router";
import {RetrieveGroupResponse, retrieveGroup} from "./api";
import {callAPI, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {AppToolLogo} from "./AppToolLogo";
import {Box, Flex, Grid} from "@/ui-components";
import {AppCard, AppCardStyle, AppCardType} from "./Card";
import {compute} from "@/UCloud";
import * as UCloud from "@/UCloud";
import ApplicationSummaryWithFavorite = compute.ApplicationSummaryWithFavorite;
import * as Pages from "./Pages";
import {AppSearchBox} from "./Search";
import {ContextSwitcher} from "@/Project/ContextSwitcher";
import {toggleAppFavorite} from "./Redux/Actions";
import {useDispatch, useSelector} from "react-redux";
import {snackbarStore} from "@/Snackbar/SnackbarStore";


const ApplicationsGroup: React.FunctionComponent = () => {
    const {id} = useParams<{id: string}>();

    const [appGroup, fetchAppGroup] = useCloudAPI<RetrieveGroupResponse | null>(
        {noop: true},
        null
    );

    const dispatch = useDispatch();

    useEffect(() => {
        fetchAppGroup(retrieveGroup({id: id}));
    }, [id]);

    const favoriteStatus = useSelector<ReduxObject, ApplicationSummaryWithFavorite[]>(it => it.sidebar.favorites);

    const onFavorite = useCallback(async (app: ApplicationSummaryWithFavorite) => {
        const favoriteApp = favoriteStatus.find(it => it.metadata.name === app.metadata.name);
        const isFavorite = favoriteApp !== undefined ? true : app.favorite;

        dispatch(toggleAppFavorite(app, !isFavorite));

        try {
            await callAPI(UCloud.compute.apps.toggleFavorite({
                appName: app.metadata.name
            }));
        } catch (e) {
            snackbarStore.addFailure("Failed to toggle favorite", false);
            dispatch(toggleAppFavorite(app, !isFavorite));
        }
    }, [favoriteStatus]);


    if (!appGroup.data) return <>Not found</>;

    return <Box mx="auto" maxWidth="1340px">
        <Flex justifyContent="space-between" mt="30px">
            <Heading.h2>
                <AppToolLogo name={appGroup.data.group.id.toString()} type="GROUP" size="45px" />
                {" "}
                {appGroup.data.group.title}
            </Heading.h2>
            <Flex justifyContent="right">
                <AppSearchBox />
                <ContextSwitcher />
            </Flex>
        </Flex>
        <Box mt="30px" />
        <Grid
            width="100%"
            gridTemplateColumns={`repeat(auto-fill, 312px)`}
            gridGap="30px"
        >
            {appGroup.data.applications.map(app => (
                <AppCard
                    key={app.metadata.name}
                    cardStyle={AppCardStyle.WIDE}
                    title={app.metadata.title} 
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

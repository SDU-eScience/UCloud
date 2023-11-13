import MainContainer from "@/MainContainer/MainContainer";
import * as Heading from "@/ui-components/Heading";
import React, {useEffect} from "react";
import {useParams} from "react-router";
import {RetrieveGroupResponse, retrieveGroup} from "./api";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {AppToolLogo} from "./AppToolLogo";
import {Box, Flex, Grid} from "@/ui-components";
import {AppCard, AppCardStyle, AppCardType} from "./Card";
import {compute} from "@/UCloud";
import * as UCloud from "@/UCloud";
import ApplicationSummaryWithFavorite = compute.ApplicationSummaryWithFavorite;
import * as Pages from "./Pages";
import {AppSearchBox} from "./Search";
import {ContextSwitcher} from "@/Project/ContextSwitcher";
import {FavoriteStatus} from "./Landing";
import {toggleAppFavorite} from "./Redux/Actions";
import {useDispatch} from "react-redux";


const ApplicationsGroup: React.FunctionComponent = () => {
    const {id} = useParams<{id: string}>();

    const [appGroup, fetchAppGroup] = useCloudAPI<RetrieveGroupResponse | null>(
        {noop: true},
        null
    );

    const dispatch = useDispatch();
    const [, invokeCommand] = useCloudCommand();

    useEffect(() => {
        fetchAppGroup(retrieveGroup({id: id}));
    }, [id]);

    const favoriteStatus = React.useRef<FavoriteStatus>({});

    const onFavorite = React.useCallback(async (app: ApplicationSummaryWithFavorite) => {
        // Note(Jonas): This used to check commandLoading (from invokeCommand), but this gets stuck at true, so removed for now.
        const key = app.metadata.name;
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
                appName: app.metadata.name
            }));
        } catch (e) {
            favoriteStatus.current[key].override = !favoriteStatus.current[key].override;
            favoriteStatus.current = {...favoriteStatus.current};
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
                    isFavorite={favoriteStatus.current[app.metadata.name]?.override ?? app.favorite}
                    application={app}
                />
            ))}
        </Grid>
    </Box>;
}

export default ApplicationsGroup;

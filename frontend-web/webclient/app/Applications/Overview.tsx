import {MainContainer} from "@/MainContainer/MainContainer";
import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import {Box, Flex, Link} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {AppCardStyle} from "./Card";
import * as Pages from "./Pages";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import * as UCloud from "@/UCloud";
import {compute} from "@/UCloud";
import ApplicationSummaryWithFavorite = compute.ApplicationSummaryWithFavorite;
import AppStoreSections = compute.AppStoreSections;
import {ReducedApiInterface, useResourceSearch} from "@/Resource/Search";
import {injectStyleSimple} from "@/Unstyled";
import {useDispatch, useSelector} from "react-redux";
import {toggleAppFavorite} from "./Redux/Actions";
import {useLocation, useNavigate} from "react-router";
import {ContextSwitcher} from "@/Project/ContextSwitcher";
import ApplicationRow, {ApplicationGroupToRowItem} from "./ApplicationsRow";
import {AppSearchBox} from "./Search";
import {FavoriteStatus} from "./Landing";

export const ApiLike: ReducedApiInterface = {
    routingNamespace: "applications",
    titlePlural: "Applications"
};

const ApplicationsOverview: React.FunctionComponent = () => {
    const [sections, fetchSections] = useCloudAPI<AppStoreSections>(
        {noop: true},
        {sections: []}
    );

    const location = useLocation();

    useEffect(() => {
        const hash = location.hash;
        const el = hash && document.getElementById(hash.slice(1))
        if (el) {
            el.scrollIntoView({behavior: "smooth"});
        }

    })

    const [refreshId, setRefreshId] = useState<number>(0);

    useEffect(() => {
        fetchSections(UCloud.compute.apps.appStoreSections({page: "FULL"}));
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

    return (
        <div className={AppOverviewMarginPaddingHack}>
            <MainContainer main={
                <Box mx="auto" maxWidth="1340px">
                    <Flex width="100%" mt="30px" justifyContent="right">
                        <AppSearchBox />
                        <ContextSwitcher />
                    </Flex>
                    <Box mt="12px" />

                    {sections.data.sections.map(section =>
                        <div key={section.name} id={"section"+section.id.toString()}>
                            <Box mb="30px">
                                <Heading.h2>{section.name}</Heading.h2>
                                <ApplicationRow
                                    items={section.featured.map(ApplicationGroupToRowItem)}
                                    style={AppCardStyle.WIDE}
                                    refreshId={refreshId}
                                />

                                <ApplicationRow
                                    items={section.items.map(ApplicationGroupToRowItem)}
                                    style={AppCardStyle.TALL}
                                    refreshId={refreshId}
                                />
                            </Box>
                        </div>
                    )}
                </Box>
            } />
        </div>
    );
};

const AppOverviewMarginPaddingHack = injectStyleSimple("HACK-HACK-HACK", `
/* HACK */
    margin-top: -12px;
/* HACK */
`);

export default ApplicationsOverview;
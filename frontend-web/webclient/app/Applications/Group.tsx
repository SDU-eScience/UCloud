import * as Heading from "@/ui-components/Heading";
import React, {useCallback} from "react";
import {useParams} from "react-router-dom";
import {useCloudAPI} from "@/Authentication/DataHook";
import {SafeLogo} from "./AppToolLogo";
import {Box, Flex, MainContainer} from "@/ui-components";
import * as AppStore from "@/Applications/AppStoreApi";
import {useAppSearch} from "./Search";
import {doNothing} from "@/UtilityFunctions";
import {UtilityBar} from "@/Navigation/UtilityBar";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {useDiscovery} from "@/Applications/Hooks";
import {AppCard2} from "./Landing";
import {Gradient, GradientWithPolygons} from "@/ui-components/GradientBackground";
import {AppGrid} from "@/Applications/Category";

const ApplicationsGroup: React.FunctionComponent = () => {
    const idParam = useParams<{id: string}>().id;
    const id = parseInt(idParam ?? "-1");
    const [discovery] = useDiscovery();

    const [appGroup, fetchAppGroup] = useCloudAPI(AppStore.retrieveGroup({id, ...discovery}), null);

    const refresh = useCallback(() => {
        fetchAppGroup(AppStore.retrieveGroup({id, ...discovery})).then(doNothing);
    }, [id]);

    usePage(appGroup.data?.specification.title ?? "Application", SidebarTabId.APPLICATIONS);
    useSetRefreshFunction(refresh);

    const appSearch = useAppSearch();

    if (!appGroup.data) return <>Not found</>;

    return (
        <div className={Gradient}>
            <div className={GradientWithPolygons}>
                <MainContainer
                    main={<>
                        <Flex  mb="16px" justifyContent="space-between">
                            <Heading.h2>
                                <SafeLogo name={appGroup.data?.metadata.id.toString()} type="GROUP" size="45px" />
                                {" "}
                                {appGroup.data?.specification?.title}
                            </Heading.h2>

                            <Flex justifyContent="right">
                                <UtilityBar onSearch={appSearch} />
                            </Flex>
                        </Flex>
                        <Box mt="30px" />
                        <AppGrid>
                            {appGroup.data?.status?.applications?.map(app => (
                                <AppCard2
                                    key={app.metadata.name}
                                    title={app.metadata.title}
                                    isApplication
                                    description={app.metadata.description}
                                    name={app.metadata.name}
                                    fullWidth
                                    applicationName={app.metadata.name}
                                />
                            ))}
                        </AppGrid>
                    </>}
                />
            </div>
        </div>
    );
}

export default ApplicationsGroup;

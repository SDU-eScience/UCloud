import {MainContainer} from "@/ui-components/MainContainer";
import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import {Box, Flex} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {AppCardStyle} from "./Card";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {useCloudAPI} from "@/Authentication/DataHook";
import * as UCloud from "@/UCloud";
import {compute} from "@/UCloud";
import AppStoreSections = compute.AppStoreSections;
import {ReducedApiInterface} from "@/Resource/Search";
import {useLocation} from "react-router";
import {ContextSwitcher} from "@/Project/ContextSwitcher";
import ApplicationRow, {ApplicationGroupToRowItem} from "./ApplicationsRow";
import {AppSearchBox} from "./Search";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";

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

    useTitle("Applications");
    const refresh = useCallback(() => {
        setRefreshId(id => id + 1);
    }, []);
    useSetRefreshFunction(refresh);

    return (
        <MainContainer main={
            <Box mx="auto" maxWidth="1340px">
                <Flex width="100%" mt="18px" justifyContent="right">
                    <AppSearchBox />
                    <ContextSwitcher />
                </Flex>
                <Box mt="12px" />

                {sections.data.sections.map(section =>
                    <div key={section.name} id={"section" + section.id.toString()}>
                        <Box mb="30px">
                            <Heading.h2>{section.name}</Heading.h2>
                            <ApplicationRow
                                items={section.featured.map(ApplicationGroupToRowItem)}
                                cardStyle={AppCardStyle.WIDE}
                                refreshId={refreshId}
                            />

                            <ApplicationRow
                                items={section.items.map(ApplicationGroupToRowItem)}
                                cardStyle={AppCardStyle.TALL}
                                refreshId={refreshId}
                            />
                        </Box>
                    </div>
                )}
            </Box>
        } />
    );
};

export default ApplicationsOverview;
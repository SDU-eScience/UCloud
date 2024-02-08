import * as React from "react";
import {Button, Flex, Link, MainContainer} from "@/ui-components";
import * as AppStore from "@/Applications/AppStoreApi";
import {useCallback, useEffect, useState} from "react";
import {Spotlight} from "@/Applications/AppStoreApi";
import {fetchAll} from "@/Utilities/PageUtilities";
import {callAPI} from "@/Authentication/DataHook";
import {useTitle} from "@/Navigation/Redux";
import {ListRow} from "@/ui-components/List";
import AppRoutes from "@/Routes";
import * as Heading from "@/ui-components/Heading";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";

const Spotlights: React.FunctionComponent = () => {
    const [spotlights, setSpotlights] = useState<Spotlight[]>([]);

    const refresh = useCallback(() => {
        let didCancel = false;
        fetchAll(next => callAPI(AppStore.browseSpotlights({itemsPerPage: 250, next}))).then(d => {
            if (didCancel) return;
            setSpotlights(d);
        });

        return () => {
            didCancel = true;
        };
    }, []);

    useEffect(() => {
        refresh();
    }, [refresh]);

    useTitle("Spotlights");

    return <MainContainer
        main={<Flex flexDirection={"column"} margin={"0 auto"} maxWidth={"900px"} gap={"16px"}>
            <Heading.h2>Spotlights</Heading.h2>
            <Link to={AppRoutes.apps.studioSpotlightsEditor()}><Button fullWidth>Create new spotlight</Button></Link>
            {spotlights.map(s => (
                <ListRow
                    left={<>{s.title}</>}
                    right={
                        <Flex gap={"8px"}>
                            <Link to={AppRoutes.apps.studioSpotlightsEditor(s.id ?? 0)}><Button>Edit</Button></Link>
                            <ConfirmationButton color={"errorMain"} icon={"heroTrash"} onAction={async () => {
                                await callAPI(AppStore.deleteSpotlight({id: s.id ?? 0}));
                                refresh();
                            }}/>
                        </Flex>
                    }
                />
            ))}
        </Flex>}
    />;
};

export default Spotlights;
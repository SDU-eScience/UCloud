import * as React from "react";
import {Button, Flex, Link, MainContainer, Select} from "@/ui-components";
import * as AppStore from "@/Applications/AppStoreApi";
import {useCallback, useEffect, useState} from "react";
import {Spotlight} from "@/Applications/AppStoreApi";
import {emptyPageV2, fetchAll} from "@/Utilities/PageUtilities";
import {callAPI, useCloudAPI} from "@/Authentication/DataHook";
import {usePage} from "@/Navigation/Redux";
import {ListRow} from "@/ui-components/List";
import AppRoutes from "@/Routes";
import * as Heading from "@/ui-components/Heading";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {SidebarTabId} from "@/ui-components/SidebarComponents";

const Spotlights: React.FunctionComponent = () => {
    const [spotlights, setSpotlights] = useState<Spotlight[]>([]);

    const selectRef = React.useRef<HTMLSelectElement>(null);
    const [storeFront, setStoreFront] = React.useState<number>(0);
    const [storeFronts, setStoreFronts] = useCloudAPI(
        AppStore.browseStoreFronts({itemsPerPage: 250}),
        emptyPageV2
    );

    const fetchSpotlights = useCallback(() => {
        if (storeFront < 1) return;
        let didCancel = false;
        fetchAll(next => callAPI(AppStore.browseSpotlights({storeFront, itemsPerPage: 250, next}))).then(d => {
            if (didCancel) return;
            setSpotlights(d);
        });

        return () => {
            didCancel = true;
        };
    }, [storeFront]);

    useEffect(() => {
        fetchSpotlights();
    }, [storeFront]);

    usePage("Spotlights", SidebarTabId.APPLICATION_STUDIO);

    return <MainContainer
        header={
            <Flex justifyContent="space-between" mb="20px">
                <Heading.h2>Spotlights</Heading.h2>
                <Select selectRef={selectRef} width={500} onChange={() => {
                    if (!selectRef.current) return;
                    if (selectRef.current.value === "") return;
                    setStoreFront(parseInt(selectRef.current.value, 10));
                }}>
                    <option disabled selected>Select store front...</option>
                    {storeFronts.data.items.map(front => 
                        <option value={front.metadata.id}>{front.specification.title}</option>
                    )}
                </Select>
            </Flex>
        }
        main={<>
            {storeFront < 1 ? null : <>
                <Flex flexDirection={"column"} margin={"0 auto"} maxWidth={"900px"} gap={"16px"}>
                    <Link to={AppRoutes.appStudio.spotlightsEditor()}><Button fullWidth>Create new spotlight</Button></Link>
                    {spotlights.map(s => (
                        <ListRow
                            left={<>{s.title}</>}
                            right={
                                <Flex gap={"8px"}>
                                    <Link to={AppRoutes.appStudio.spotlightsEditor(s.id ?? 0)}><Button>Edit</Button></Link>
                                    <ConfirmationButton color={"errorMain"} icon={"heroTrash"} onAction={async () => {
                                        await callAPI(AppStore.deleteSpotlight({id: s.id ?? 0}));
                                        fetchSpotlights();
                                    }}/>
                                </Flex>
                            }
                        />
                    ))}
                </Flex>
            </>}
        </>}
    />;
};

export default Spotlights;
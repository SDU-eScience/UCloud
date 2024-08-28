import * as React from "react";
import {ScaffoldedForm, ScaffoldedFormObject} from "@/ui-components/ScaffoldedForm";
import {dialogStore} from "@/Dialog/DialogStore";
import {GroupSelector} from "@/Applications/Studio/GroupSelector";
import {doNothing} from "@/UtilityFunctions";
import {ApplicationGroup, CarrouselItem, updateCarrousel} from "@/Applications/AppStoreApi";
import {Box, Button, Flex, Icon, MainContainer} from "@/ui-components";
import {useCallback, useEffect, useRef, useState} from "react";
import {Hero} from "@/Applications/Landing";
import {callAPI, useCloudAPI} from "@/Authentication/DataHook";
import * as AppStore from "@/Applications/AppStoreApi";
import {emptyPageV2, fetchAll} from "@/Utilities/PageUtilities";
import {TooltipV2} from "@/ui-components/Tooltip";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import * as Heading from "@/ui-components/Heading";
import {ListRow} from "@/ui-components/List";
import {Toggle} from "@/ui-components/Toggle";

const Subscriptions: React.FunctionComponent = () => {
    usePage("Repository subscriptions", SidebarTabId.APPLICATION_STUDIO);

    const [repositories, setRepositories] = useCloudAPI(
        AppStore.browseRepositories({includePrivate: true, itemsPerPage: 250}),
        emptyPageV2
    );

    return <MainContainer
        main={<>
            <Flex justifyContent="space-between" mb="20px">
                <Heading.h2>Repository subscriptions</Heading.h2>
            </Flex>

            {repositories.data.items.map((c, idx) => {
                return <ListRow
                    key={c.metadata.id}
                    left={c.specification.title}
                    right={<>
                        <Flex gap={"8px"}>
                            <Toggle checked={false} onChange={(prevValue: boolean) => {
                                throw new Error("Function not implemented.");
                            } } />
                        </Flex>
                    </>}
                />
            })}
        </>}
    />;

};

export default Subscriptions;
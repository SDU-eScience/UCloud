import {uploadDocument} from "@/Applications/api";
import {SmallAppToolCard} from "@/Applications/Studio/SmallAppToolCard";
import {useCloudAPI} from "@/Authentication/DataHook";
import {Client} from "@/Authentication/HttpClientInstance";
import {emptyPage} from "@/DefaultObjects";
import {dialogStore} from "@/Dialog/DialogStore";
import {MainContainer} from "@/MainContainer/MainContainer";
import {useLoading, useTitle} from "@/Navigation/Redux/StatusActions";
import * as Pagination from "@/Pagination";
import * as React from "react";
import {useCallback} from "react";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import Box from "@/ui-components/Box";
import Button, {ButtonClass} from "@/ui-components/Button";
import Flex from "@/ui-components/Flex";
import * as Heading from "@/ui-components/Heading";
import {HiddenInputField} from "@/ui-components/Input";
import {SidebarPages, useSidebarPage} from "@/ui-components/SidebarPagesEnum";
import Truncate from "@/ui-components/Truncate";
import VerticalButtonGroup from "@/ui-components/VerticalButtonGroup";
import {AppToolLogo} from "../AppToolLogo";
import {usePrioritizedSearch} from "@/Utilities/SearchUtilities";
import * as UCloud from "@/UCloud";
import {setRefreshFunction} from "@/Navigation/Redux/HeaderActions";

export const Studio: React.FunctionComponent = () => {
    useTitle("Application Studio");
    useSidebarPage(SidebarPages.Admin);
    usePrioritizedSearch("applications");

    const [tools, setToolParameters, toolParameters] = useCloudAPI(
        UCloud.compute.tools.listAll({page: 0, itemsPerPage: 50}),
        emptyPage
    );

    useLoading(tools.loading);

    const refresh = useCallback(() => {
        setToolParameters(toolParameters);
    }, [toolParameters]);

    setRefreshFunction(refresh);

    if (Client.userRole !== "ADMIN") return null;

    return (
        <MainContainer
            header={<Heading.h1>Application Studio</Heading.h1>}

            sidebar={(
                <VerticalButtonGroup>
                    <label className={ButtonClass}>
                        Upload Application
                        <HiddenInputField
                            type="file"
                            onChange={async e => {
                                const target = e.target;
                                if (target.files) {
                                    const file = target.files[0];
                                    target.value = "";
                                    if (file.size > 1024 * 1024 * 5) {
                                        snackbarStore.addFailure("File exceeds 5MB. Not allowed.", false);
                                    } else {
                                        await uploadDocument({document: file, type: "APPLICATION"});
                                        refresh();
                                    }
                                    dialogStore.success();
                                }
                            }}/>
                    </label>

                    <label className={ButtonClass}>
                        Upload Tool
                        <HiddenInputField
                            type="file"
                            onChange={async e => {
                                const target = e.target;
                                if (target.files) {
                                    const file = target.files[0];
                                    target.value = "";
                                    if (file.size > 1024 * 512) {
                                        snackbarStore.addFailure("File exceeds 512KB. Not allowed.", false);
                                    } else {
                                        await uploadDocument({document: file, type: "TOOL"});
                                        refresh();
                                    }
                                    dialogStore.success();
                                }
                            }}
                        />
                    </label>
                </VerticalButtonGroup>
            )}

            main={(
                <Pagination.List
                    loading={tools.loading}
                    page={tools.data}
                    onPageChanged={page => {
                        setToolParameters(UCloud.compute.tools.listAll({page}));
                    }}
                    pageRenderer={page => (
                        <Flex flexWrap={"wrap"} justifyContent={"center"}>
                            {page.items.map(tool =>
                                <SmallAppToolCard key={tool.description.info.name}
                                                  to={`/applications/studio/t/${tool.description.info.name}`}>
                                    <Flex>
                                        <AppToolLogo type={"TOOL"} name={tool.description.info.name}/>
                                        <Box ml={8}>
                                            <Truncate width={300} cursor={"pointer"}>
                                                <b>{tool.description.title}</b>
                                            </Truncate>
                                            <Box cursor={"pointer"}>{tool.description.info.name}</Box>
                                        </Box>
                                    </Flex>
                                </SmallAppToolCard>
                            )}
                        </Flex>
                    )}
                />
            )}
        />
    );
};

export default Studio;

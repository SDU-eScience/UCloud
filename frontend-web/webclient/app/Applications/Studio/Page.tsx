import {updateLanding, updateOverview, uploadDocument} from "@/Applications/api";
import {useCloudAPI} from "@/Authentication/DataHook";
import {Client} from "@/Authentication/HttpClientInstance";
import {dialogStore} from "@/Dialog/DialogStore";
import {MainContainer} from "@/ui-components/MainContainer";
import {useLoading, useTitle} from "@/Navigation/Redux";
import * as Pagination from "@/Pagination";
import * as React from "react";
import {useCallback} from "react";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import Box from "@/ui-components/Box";
import Button, {ButtonClass} from "@/ui-components/Button";
import Flex from "@/ui-components/Flex";
import * as Heading from "@/ui-components/Heading";
import {HiddenInputField} from "@/ui-components/Input";
import Truncate from "@/ui-components/Truncate";
import {AppToolLogo} from "../AppToolLogo";
import * as UCloud from "@/UCloud";
import {Link} from "@/ui-components";
import {useNavigate} from "react-router";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {emptyPage} from "@/Utilities/PageUtilities";

export const Studio: React.FunctionComponent = () => {
    useTitle("Application Studio");

    const navigate = useNavigate();

    const [tools, setToolParameters, toolParameters] = useCloudAPI(
        UCloud.compute.tools.listAll({page: 0, itemsPerPage: 50}),
        emptyPage
    );

    useLoading(tools.loading);

    const refresh = useCallback(() => {
        setToolParameters(toolParameters);
    }, [toolParameters]);

    useSetRefreshFunction(refresh);

    if (Client.userRole !== "ADMIN") return null;

    return (
        <MainContainer
            header={<Heading.h2 style={{marginTop: "4px", marginBottom: 0}}>Application Studio</Heading.h2>}
            main={(<>
                <Flex justifyContent="right">
                    <label className={ButtonClass} style={{marginRight: "5px"}}>
                        Upload application
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
                            }} />
                    </label>

                    <label className={ButtonClass} style={{marginRight: "5px"}}>
                        Upload tool
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

                    <label className={ButtonClass} style={{marginRight: "5px"}}>
                        Update landing page
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
                                        await updateLanding({document: file});
                                        refresh();
                                    }
                                    dialogStore.success();
                                }
                            }}
                        />
                    </label>

                    <label className={ButtonClass} style={{marginRight: "5px"}}>
                        Update overview page
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
                                        await updateOverview({document: file});
                                        refresh();
                                    }
                                    dialogStore.success();
                                }
                            }}
                        />
                    </label>



                    <Button onClick={() => navigate(`/applications/studio/groups`)}>Manage groups</Button>
                </Flex>

                <Pagination.List
                    loading={tools.loading}
                    page={tools.data}
                    onPageChanged={page => {
                        setToolParameters(UCloud.compute.tools.listAll({page}));
                    }}
                    pageRenderer={page => (
                        <Flex flexWrap="wrap" justifyContent="center">
                            {page.items.map(tool =>
                                <Link
                                    key={tool.description.info.name}
                                    to={`/applications/studio/t/${tool.description.info.name}`}>
                                    <Flex style={{borderRadius: "8px", margin: "8px", padding: "4px", border: "1px solid var(--black)"}}>
                                        <div style={{borderRadius: "6px", padding: "2px", backgroundColor: "white"}}><AppToolLogo type={"TOOL"} name={tool.description.info.name} /></div>
                                        <Box ml={8}>
                                            <Truncate width={300} cursor={"pointer"}>
                                                <b>{tool.description.title}</b>
                                            </Truncate>
                                            <Box cursor={"pointer"}>{tool.description.info.name}</Box>
                                        </Box>
                                    </Flex>
                                </Link>
                            )}
                        </Flex>
                    )}
                />
            </>)}
        />
    );
};

export default Studio;

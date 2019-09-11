import {ToolReference} from "Applications";
import {listTools, uploadDocument} from "Applications/api";
import * as Actions from "Applications/Redux/BrowseActions";
import {SmallAppToolCard} from "Applications/Studio/SmallAppToolCard";
import {useCloudAPI} from "Authentication/DataHook";
import {Cloud} from "Authentication/SDUCloudObject";
import {emptyPage} from "DefaultObjects";
import {dialogStore} from "Dialog/DialogStore";
import {loadingAction} from "Loading";
import {MainContainer} from "MainContainer/MainContainer";
import {HeaderActions, setPrioritizedSearch, setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setActivePage, StatusActions, updatePageTitle} from "Navigation/Redux/StatusActions";
import * as Pagination from "Pagination";
import * as React from "react";
import {useEffect} from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Page} from "Types";
import Box from "ui-components/Box";
import Button from "ui-components/Button";
import Flex from "ui-components/Flex";
import * as Heading from "ui-components/Heading";
import {HiddenInputField} from "ui-components/Input";
import {SidebarPages} from "ui-components/Sidebar";
import Truncate from "ui-components/Truncate";
import VerticalButtonGroup from "ui-components/VerticalButtonGroup";
import {AppToolLogo} from "../AppToolLogo";

interface StudioOperations {
    onInit: () => void;
    setRefresh: (refresh?: () => void) => void;
    setLoading: (loading: boolean) => void;
}

const Studio: React.FunctionComponent<StudioOperations> = props => {
    if (Cloud.userRole !== "ADMIN") return null;

    const [tools, setToolParameters, toolParameters] =
        useCloudAPI<Page<ToolReference>>(listTools({page: 0, itemsPerPage: 50}), emptyPage);

    useEffect(() => {
        props.onInit();
        props.setRefresh(() => {
            setToolParameters(listTools({...toolParameters.parameters}));
        });

        return () => props.setRefresh();
    }, []);

    useEffect(() => {
        props.setLoading(tools.loading);
    }, [tools.loading]);

    return <MainContainer
        header={
            <Heading.h1>Application Studio</Heading.h1>
        }

        sidebar={
            <VerticalButtonGroup>
                <Button fullWidth as="label">
                    Upload Application
                    <HiddenInputField
                        type="file"
                        onChange={async e => {
                            const target = e.target;
                            if (target.files) {
                                const file = target.files[0];
                                target.value = "";
                                if (file.size > 1024 * 512) {
                                    snackbarStore.addFailure("File exceeds 512KB. Not allowed.");
                                } else {
                                    await uploadDocument({document: file, type: "APPLICATION"});
                                    setToolParameters(listTools({...toolParameters.parameters}));
                                }
                                dialogStore.success();
                            }
                        }}/>
                </Button>

                <Button fullWidth as="label">
                    Upload Tool
                    <HiddenInputField
                        type="file"
                        onChange={async e => {
                            const target = e.target;
                            if (target.files) {
                                const file = target.files[0];
                                target.value = "";
                                if (file.size > 1024 * 512) {
                                    snackbarStore.addFailure("File exceeds 512KB. Not allowed.");
                                } else {
                                    await uploadDocument({document: file, type: "TOOL"});
                                    setToolParameters(listTools({...toolParameters.parameters}));
                                }
                                dialogStore.success();
                            }
                        }}/>
                </Button>
            </VerticalButtonGroup>
        }

        main={
            <Pagination.List
                loading={tools.loading}
                page={tools.data}
                onPageChanged={page => {
                    setToolParameters(listTools({...toolParameters.parameters, page}));
                }}
                pageRenderer={page => {
                    return <Flex flexWrap={"wrap"} justifyContent={"center"}>
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
                    </Flex>;
                }}
            />
        }
    />;
};

const mapDispatchToProps = (
    dispatch: Dispatch<Actions.Type | HeaderActions | StatusActions>
): StudioOperations => ({
    onInit: () => {
        dispatch(updatePageTitle("Application Studio"));
        dispatch(setPrioritizedSearch("applications"));
        dispatch(setActivePage(SidebarPages.AppStore));
    },

    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),

    setLoading: loading => dispatch(loadingAction(loading))
});

export default connect(null, mapDispatchToProps)(Studio);

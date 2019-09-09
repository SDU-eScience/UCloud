import {ToolReference, WithAppInvocation, WithAppMetadata} from "Applications";
import {clearLogo, listApplicationsByTool, listToolsByName, uploadLogo} from "Applications/api";
import * as Actions from "Applications/Redux/BrowseActions";
import {SmallAppToolCard} from "Applications/Studio/SmallAppToolCard";
import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {Cloud} from "Authentication/SDUCloudObject";
import {emptyPage} from "DefaultObjects";
import {dialogStore} from "Dialog/DialogStore";
import {loadingAction} from "Loading";
import {MainContainer} from "MainContainer/MainContainer";
import {HeaderActions, setPrioritizedSearch, setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setActivePage, StatusActions, updatePageTitle} from "Navigation/Redux/StatusActions";
import * as Pagination from "Pagination";
import * as React from "react";
import {useEffect, useState} from "react";
import {connect} from "react-redux";
import {RouteComponentProps} from "react-router";
import {Dispatch} from "redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Page} from "Types";
import {Button, Flex, VerticalButtonGroup} from "ui-components";
import Box from "ui-components/Box";
import * as Heading from "ui-components/Heading";
import {HiddenInputField} from "ui-components/Input";
import {SidebarPages} from "ui-components/Sidebar";
import Truncate from "ui-components/Truncate";
import {AppToolLogo} from "../AppToolLogo";

interface ToolOperations {
    onInit: () => void;
    setRefresh: (refresh?: () => void) => void;
    setLoading: (loading: boolean) => void;
}

const Tool: React.FunctionComponent<RouteComponentProps & ToolOperations> = props => {
    // tslint:disable-next-line
    const name = props.match.params["name"];
    if (Cloud.userRole !== "ADMIN") return null;

    const [commandLoading, invokeCommand] = useAsyncCommand();
    const [logoCacheBust, setLogoCacheBust] = useState("" + Date.now());
    const [tool, setToolParameter, toolParameter] = useCloudAPI<Page<ToolReference>>({noop: true}, emptyPage);
    const [apps, setAppParameters, appParameters] = useCloudAPI<Page<WithAppMetadata & WithAppInvocation>>(
        {noop: true}, emptyPage
    );

    const toolTitle = tool.data.items.length > 0 ? tool.data.items[0].description.title : name;

    useEffect(() => props.onInit(), []);

    useEffect(() => {
        setToolParameter(listToolsByName({name, page: 0, itemsPerPage: 50}));
        setAppParameters(listApplicationsByTool({tool: name, page: 0, itemsPerPage: 50}));
        props.setRefresh(() => {
            setToolParameter(listToolsByName({name, page: 0, itemsPerPage: 50}));
            setAppParameters(listApplicationsByTool({tool: name, page: 0, itemsPerPage: 50}));
        });

        return () => props.setRefresh();
    }, [name]);

    useEffect(() => {
        props.setLoading(commandLoading || tool.loading || apps.loading);
    }, [commandLoading, tool.loading, apps.loading]);

    return <MainContainer
        header={
            <Heading.h1>
                <AppToolLogo type={"TOOL"} name={name} cacheBust={logoCacheBust} size={"64px"}/>
                {" "}
                {toolTitle}
            </Heading.h1>
        }

        sidebar={
            <VerticalButtonGroup>
                <Button fullWidth as="label">
                    Upload Logo
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
                                    if (await uploadLogo({name, file, type: "TOOL"})) {
                                        setLogoCacheBust("" + Date.now());
                                    }
                                }
                                dialogStore.success();
                            }
                        }}/>
                </Button>

                <Button
                    type={"button"}
                    color={"red"}
                    disabled={commandLoading}
                    onClick={async () => {
                        await invokeCommand(clearLogo({type: "TOOL", name}));
                        setLogoCacheBust("" + Date.now());
                    }}
                >
                    Remove Logo
                </Button>
            </VerticalButtonGroup>
        }

        main={
            <>
                The following applications are currently using this tool, click on any to configure them further:

                <Pagination.List
                    loading={apps.loading}
                    page={apps.data}
                    onPageChanged={newPage =>
                        setAppParameters(listApplicationsByTool({...appParameters.parameters, page: newPage}))
                    }

                    pageRenderer={page => {
                        return <Flex justifyContent={"center"} flexWrap={"wrap"}>
                            {
                                page.items.map(app => (
                                    <SmallAppToolCard to={`/applications/studio/a/${app.metadata.name}`}>
                                        <Flex>
                                            <AppToolLogo name={app.metadata.name} type={"APPLICATION"}
                                                         cacheBust={logoCacheBust}/>
                                            <Box ml={8}>
                                                <Truncate width={300} cursor={"pointer"}>
                                                    <b>
                                                        {app.metadata.title}
                                                    </b>
                                                </Truncate>
                                            </Box>
                                        </Flex>
                                    </SmallAppToolCard>
                                ))
                            }
                        </Flex>;
                    }}
                />
            </>
        }
    />;
};

const mapDispatchToProps = (
    dispatch: Dispatch<Actions.Type | HeaderActions | StatusActions>
): ToolOperations => ({
    onInit: () => {
        dispatch(updatePageTitle("Application Studio/Tools"));
        dispatch(setPrioritizedSearch("applications"));
        dispatch(setActivePage(SidebarPages.AppStore));
    },

    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),

    setLoading: loading => dispatch(loadingAction(loading))
});

export default connect(null, mapDispatchToProps)(Tool);

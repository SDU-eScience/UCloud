import {WithAllAppTags, WithAppMetadata} from "Applications";
import {clearLogo, listApplications, listByName, uploadLogo} from "Applications/api";
import {AppToolLogo} from "Applications/AppToolLogo";
import {SmallAppToolCard} from "Applications/Studio/SmallAppToolCard";
import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {Cloud} from "Authentication/SDUCloudObject";
import {emptyPage} from "DefaultObjects";
import {dialogStore} from "Dialog/DialogStore";
import {MainContainer} from "MainContainer/MainContainer";
import * as Pagination from "Pagination";
import {useEffect} from "react";
import * as React from "react";
import {useState} from "react";
import {RouteComponentProps} from "react-router";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Page} from "Types";
import {Button, Flex, VerticalButtonGroup} from "ui-components";
import Box from "ui-components/Box";
import * as Heading from "ui-components/Heading";
import {HiddenInputField} from "ui-components/Input";
import Truncate from "ui-components/Truncate";

const App: React.FunctionComponent<RouteComponentProps> = props => {
    // tslint:disable-next-line
    const name = props.match.params["name"];
    if (Cloud.userRole !== "ADMIN") return null;

    const [commandLoading, invokeCommand] = useAsyncCommand();
    const [logoCacheBust, setLogoCacheBust] = useState("" + Date.now());
    const [apps, setAppParameters, appParameters] =
        useCloudAPI<Page<WithAppMetadata & WithAllAppTags>>({noop: true}, emptyPage);

    useEffect(() => {
        setAppParameters(listByName({name, itemsPerPage: 50, page: 0}));
    }, [name]);

    const appTitle = apps.data.items.length > 0 ? apps.data.items[0].metadata.title : name;

    return <MainContainer
        header={
            <Heading.h1>
                <AppToolLogo name={name} type={"APPLICATION"} size={"64px"} cacheBust={logoCacheBust}/>
                {" "}
                {appTitle}
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
                                    if (await uploadLogo({name, file, type: "APPLICATION"})) {
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
                        await invokeCommand(clearLogo({type: "APPLICATION", name}));
                        setLogoCacheBust("" + Date.now());
                    }}
                >
                    Remove Logo
                </Button>
            </VerticalButtonGroup>
        }

        main={
            <Pagination.List
                onPageChanged={newPage => setAppParameters(listByName({...appParameters.parameters, page: newPage}))}
                loading={apps.loading}
                page={apps.data}
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
        }
    />;
};

export default App;

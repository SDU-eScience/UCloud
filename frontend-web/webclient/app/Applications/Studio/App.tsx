import {WithAllAppTags, WithAppMetadata} from "Applications";
import {
    clearLogo,
    createApplicationTag,
    deleteApplicationTag,
    listApplications,
    listByName,
    uploadLogo
} from "Applications/api";
import {AppToolLogo} from "Applications/AppToolLogo";
import {SmallAppToolCard} from "Applications/Studio/SmallAppToolCard";
import {TagStyle} from "Applications/View";
import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {Cloud} from "Authentication/SDUCloudObject";
import {emptyPage} from "DefaultObjects";
import {dialogStore} from "Dialog/DialogStore";
import {MainContainer} from "MainContainer/MainContainer";
import {useEffect, useRef} from "react";
import * as React from "react";
import {useState} from "react";
import {RouteComponentProps} from "react-router";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Page} from "Types";
import {Button, Flex, VerticalButtonGroup} from "ui-components";
import Box from "ui-components/Box";
import * as Heading from "ui-components/Heading";
import Input, {HiddenInputField} from "ui-components/Input";
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
    const tags = apps.data.items.length > 0 ? apps.data.items[0].tags : [];

    const newTagField = useRef<HTMLInputElement>(null);

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
            <Box>
                <Heading.h2>Tags</Heading.h2>
                <Box width={500}>
                    {tags.map(tag =>
                        <Flex mb={16}>
                            <Box width={400}>
                                <TagStyle key={tag}>{tag}</TagStyle>
                            </Box>
                            <Box width={100}>
                                <Button
                                    fullWidth
                                    color={"red"}
                                    type={"button"}

                                    disabled={commandLoading}
                                    onClick={async () => {
                                        await invokeCommand(deleteApplicationTag({applicationName: name, tags: [tag]}));
                                        setAppParameters(listByName({...appParameters.parameters}));
                                    }}
                                >
                                    Delete
                                </Button>
                            </Box>
                        </Flex>
                    )}

                    <form onSubmit={async e => {
                        e.preventDefault();
                        if (commandLoading) return;

                        const tagField = newTagField.current;
                        if (tagField === null) return;

                        const tagValue = tagField.value;
                        if (tagValue === "") return;

                        await invokeCommand(createApplicationTag({applicationName: name, tags: [tagValue]}));
                        setAppParameters(listByName({...appParameters.parameters}));

                        tagField.value = "";
                    }}>
                        <Flex>
                            <Box flexGrow={1}>
                                <Input ref={newTagField}/>
                            </Box>
                            <Box ml={8} width={100}>
                                <Button disabled={commandLoading} type={"submit"} fullWidth>Add tag</Button>
                            </Box>
                        </Flex>
                    </form>
                </Box>
            </Box>
        }
    />;
};

export default App;

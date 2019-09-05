import {ToolReference} from "Applications";
import {clearLogo, listTools, listToolsByName, uploadLogo} from "Applications/api";
import {ToolLogo} from "Applications/ToolLogo";
import {useAsyncCommand, useAsyncWork, useCloudAPI} from "Authentication/DataHook";
import {Cloud} from "Authentication/SDUCloudObject";
import {emptyPage} from "DefaultObjects";
import {dialogStore} from "Dialog/DialogStore";
import {MainContainer} from "MainContainer/MainContainer";
import * as React from "react";
import {useEffect, useState} from "react";
import {RouteComponentProps} from "react-router";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Page} from "Types";
import {Button, VerticalButtonGroup} from "ui-components";
import * as Heading from "ui-components/Heading";
import {HiddenInputField} from "ui-components/Input";

const Tool: React.FunctionComponent<RouteComponentProps> = props => {
    // tslint:disable-next-line
    const name = props.match.params["name"];
    if (Cloud.userRole !== "ADMIN") return null;

    const [tool, setToolParameter, toolParameter] = useCloudAPI<Page<ToolReference>>({noop: true}, emptyPage);
    const [commandLoading, invokeCommand] = useAsyncCommand();
    const [logoCacheBust, setLogoCacheBust] = useState("" + Date.now());

    const toolTitle = tool.data.items.length > 0 ? tool.data.items[0].description.title : name;

    useEffect(() => {
        setToolParameter(listToolsByName({name, page: 0, itemsPerPage: 50}));
    }, [name]);

    return <MainContainer
        header={
            <Heading.h1>
                <ToolLogo tool={name} cacheBust={logoCacheBust} size={"64px"}/>
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
                Hi, {name}!
                {tool.data.itemsInTotal}
            </>
        }
    />;
};

export default Tool;

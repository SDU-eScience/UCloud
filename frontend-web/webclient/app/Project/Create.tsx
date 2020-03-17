import {useAsyncCommand} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import {MainContainer} from "MainContainer/MainContainer";
import {createProject} from "Project/index";
import {useRef} from "react";
import * as React from "react";
import Box from "ui-components/Box";
import Button from "ui-components/Button";
import ContainerForText from "ui-components/ContainerForText";
import Input from "ui-components/Input";
import Label from "ui-components/Label";
import {snackbarStore} from "Snackbar/SnackbarStore";

const Create: React.FunctionComponent = () => {
    const [loading, invokeCommand] = useAsyncCommand();
    const title = useRef<HTMLInputElement>(null);

    const doCreateShare = async (e: React.FormEvent): Promise<void> => {
        e.preventDefault();
        if (loading) return;

        // TODO FIXME This will only work for admin accounts!!!
        if (!Client.userIsAdmin) {
            snackbarStore.addFailure("Currently requires user is admin in backend.");
            return;
        }
        await invokeCommand(createProject({
            title: title.current!.value,
            principalInvestigator: Client.username!
        }));

        // TODO Do something when command returns correctly
        // TODO We need to know if this was successful
    };

    return (
        <MainContainer
            headerSize={0}
            header={null}
            main={(
                <ContainerForText>
                    <form onSubmit={doCreateShare}>
                        <div>
                            <Label htmlFor="projectName">Title</Label>
                            <Input width="350px" ref={title} id="projectName" />
                        </div>

                        <Box mt={16}>
                            <Button
                                color="green"
                                type="submit"
                                fullWidth
                                disabled={loading}
                            >
                                Submit
                            </Button>
                        </Box>
                    </form>
                </ContainerForText>
            )}
        />
    );
};

export default Create;

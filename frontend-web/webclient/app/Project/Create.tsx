import {useAsyncCommand} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import {MainContainer} from "MainContainer/MainContainer";
import {createProject} from "Project/index";
import {useRef} from "react";
import * as React from "react";
import {Box, Button, ContainerForText, Input, Label} from "ui-components";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {useHistory} from "react-router";
import {errorMessageOrDefault} from "UtilityFunctions";
import {SnackType} from "Snackbar/Snackbars";

const Create: React.FunctionComponent = () => {
    const [loading, invokeCommand] = useAsyncCommand();
    const title = useRef<HTMLInputElement>(null);
    const history = useHistory();

    const doCreateShare = async (e: React.FormEvent): Promise<void> => {
        e.preventDefault();
        if (loading) return;

        // TODO FIXME This will only work for admin accounts!!!
        if (!Client.userIsAdmin) {
            snackbarStore.addFailure("Currently requires user is admin in backend.");
            return;
        }

        try {
            await invokeCommand(createProject({
                title: title.current!.value,
                principalInvestigator: Client.username!
            }));
            snackbarStore.addSnack({
                message: "Group created.",
                type: SnackType.Success
            });
            history.push("/projects");
        } catch (err) {
            snackbarStore.addFailure(errorMessageOrDefault(err, "Failed to create group."));
        }
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
                            <Input required width="350px" ref={title} id="projectName" />
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

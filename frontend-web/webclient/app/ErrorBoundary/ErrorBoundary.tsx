import {Client} from "Authentication/HttpClientInstance";
import {MainContainer} from "MainContainer/MainContainer";
import * as React from "react";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Box, Button, TextArea} from "ui-components";
import {errorMessageOrDefault} from "UtilityFunctions";

interface ErrorBoundaryState {
    hasError: boolean;
    error?: Error;
    errorInfo?: React.ErrorInfo;
}
export class ErrorBoundary extends React.Component<{}, ErrorBoundaryState> {
    public static getDerivedStateFromError() {
        return {hasError: true, errorSent: false};
    }

    private static redirectToDashboard() {
        Client.openLandingPage();
    }

    private ref = React.createRef<HTMLTextAreaElement>();


    constructor(props: Readonly<{}>) {
        super(props);
        this.state = {
            hasError: false
        };
    }

    public componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
        console.warn(error);
        this.setState(() => ({error, errorInfo}));
    }

    public render() {
        if (this.state.hasError) {
            return (
                <MainContainer
                    main={(
                        <Box maxWidth="435px" width="100%">
                            <div>An error occurred. Would you like to submit an error report?</div>
                            <Box mb="0.5em">
                                <TextArea
                                    placeholder="Please enter any information regarding the action you performed that caused an error"
                                    rows={5}
                                    width="100%"
                                    ref={this.ref}
                                />
                            </Box>
                            <Button mr="1em" onClick={this.submitError} color="blue">Submit</Button>
                            <Button onClick={ErrorBoundary.redirectToDashboard}>Go to dashboard</Button>

                            <Box pt="10px">We support Chrome, Edge, Firefox and Safari.
                            Outdated browsers can in some cases cause issues.
                Please keep your browser updated.</Box>
                        </Box>
                    )}
                />
            );
        }

        return this.props.children;
    }

    private submitError = async (): Promise<void> => {
        const {error, errorInfo} = this.state;
        const textAreaContent = this.ref.current ? this.ref.current.value : "None";
        try {
            await Client.post("/support/ticket", {
                message: `ERROR: ${error},\nSTACK: ${errorInfo!.componentStack},\nPathname: ${window.location.pathname},\nAdditional info: ${textAreaContent}`
            });
        } catch (e) {
            snackbarStore.addFailure(errorMessageOrDefault(e, `Failed to submit.${navigator.onLine ? "" : " You are offline."}`), false);
        }
        ErrorBoundary.redirectToDashboard();
    }
}

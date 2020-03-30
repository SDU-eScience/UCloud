import {Client} from "Authentication/HttpClientInstance";
import {SetStatusLoading} from "Navigation/Redux/StatusActions";
import * as React from "react";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Button, Divider, ExternalLink, Flex, Input, theme} from "ui-components";
import Box from "ui-components/Box";
import * as Heading from "ui-components/Heading";
import {TwoFactorSetupState} from ".";

const googlePlay = require("Assets/Images/google-play-badge.png");
const appStore = require("Assets/Images/app-store-badge.png");

interface TwoFactorSetupProps {
    loading: boolean;
    mustActivate2fa: boolean;
}

export class TwoFactorSetup extends React.Component<SetStatusLoading & TwoFactorSetupProps, TwoFactorSetupState> {
    public state = TwoFactorSetup.initialState();

    public componentDidMount() {
        this.loadStatus();
    }

    public render() {
        return (
            <React.StrictMode>
                <Heading.h2>Two Factor Authentication</Heading.h2>
                {this.props.mustActivate2fa ? (
                    <Heading.h3 color={theme.colors.red}>
                        You must activate 2FA for your account before you can continue
                    </Heading.h3>
                ) : null}
                <b>{this.displayConnectedStatus()}</b>
                <Divider />
                {!this.state.isConnectedToAccount ? this.setupPage() : undefined}
            </React.StrictMode>
        );
    }

    private async loadStatus() {
        this.props.setLoading(true);
        try {
            const res = await Client.get("2fa/status", "/auth");
            this.setState(() => ({isConnectedToAccount: res.response.connected}));
        } catch (res) {
            const why: string = res.response.why ?? "";
            snackbarStore.addFailure(`Could not fetch 2FA status. ${why}`);
        } finally {
            this.props.setLoading(false);
        }
    }

    private static initialState(): TwoFactorSetupState {
        return {
            verificationCode: "",
            isConnectedToAccount: false
        };
    }

    private displayConnectedStatus() {
        if (this.state.isConnectedToAccount === undefined) {
            return "Unknown. Could not fetch 2FA status";
        } else if (this.state.isConnectedToAccount) {
            return "You have a 2FA device connected to your account";
        } else {
            return "You do NOT have a 2FA device connected to your account";
        }
    }

    private setupPage() {
        return (
            <Box mb={16}>
                <p>
                    In order to activate 2FA on your account you must have a
                    device capable of issuing time-based one time passwords
                    (TOTP). For this we recommend the "Google Authenticator"
                    app, available for both Android and iOS.
                </p>

                <Flex>
                    <ExternalLink
                        mr="4px"
                        href="https://play.google.com/store/apps/details?id=com.google.android.apps.authenticator2&hl=en_us"
                    >
                        <img height="50px" src={googlePlay} alt={"Get it on Google Play"} />
                    </ExternalLink>

                    <ExternalLink href="https://itunes.apple.com/us/app/google-authenticator/id388497605">
                        <img height="50px" src={appStore} alt={"Download on the App Store"} />
                    </ExternalLink>
                </Flex>

                {this.state.challengeId === undefined ? (
                    <React.Fragment>
                        <p>Once you are ready click the button below to get started:</p>

                        <Button
                            color="green"
                            disabled={this.props.loading}
                            onClick={() => this.onSetupStart()}
                        >
                            Start setup
                        </Button>
                    </React.Fragment>
                ) :
                    this.displayQRCode()
                }
            </Box>
        );
    }

    private displayQRCode() {
        if (this.state.qrCode === undefined ||
            this.state.challengeId === undefined) {
            return undefined;
        }

        return (
            <div>
                <Divider />
                <h4>Step One</h4>
                <p>Open the 'Google Authenticator' app on your phone</p>
                <Divider />

                <h4>Step Two</h4>
                <p>Add a new authenticator by tapping the '+' icon.</p>
                <Divider />

                <h4>Step Three</h4>
                <p>Use the 'Scan a barcode option'</p>
                <Divider />

                <h4>Step Four</h4>
                <p>Scan the barcode below using your phone's camera</p>
                <img src={this.state.qrCode} alt="QRCode" />
                <Divider />

                <h4>Step Five</h4>
                <p>Enter the verification code from your app in the field below.</p>

                <form
                    onSubmit={e => {
                        e.preventDefault();
                        this.onVerificationSubmit();
                    }}
                >
                    <Input
                        placeholder="6-digit verification code"
                        value={this.state.verificationCode}
                        type="text"
                        onChange={({target}) =>
                            this.setState(() => ({
                                verificationCode: target.value
                            }))
                        }
                    />

                    <Button
                        mt={8}
                        color="blue"
                        type="submit"
                        disabled={this.props.loading}
                    >
                        Submit code
                    </Button>
                </form>
            </div>
        );
    }

    private onSetupStart() {
        this.props.setLoading(true);
        Client.post("2fa", undefined, "/auth").then(res => {
            this.setState(() => ({
                challengeId: res.response.challengeId,
                qrCode: res.response.qrCodeB64Data
            }));

        }).catch(() => { /* Do nothing */
        }).then(() => {
            this.props.setLoading(false);
        });
    }

    private async onVerificationSubmit(): Promise<void> {
        this.props.setLoading(true);
        try {
            await Client.post("2fa/challenge", {
                challengeId: this.state.challengeId,
                verificationCode: this.state.verificationCode
            }, "/auth");

            await Client.invalidateAccessToken();

            this.setState(() => ({isConnectedToAccount: true}));
        } catch (res) {
            const response = res.response;
            const why: string = response?.why ?? "Could not submit verification code. Try again later";
            snackbarStore.addFailure(why);
        } finally {
            this.props.setLoading(false);
        }
    }
}

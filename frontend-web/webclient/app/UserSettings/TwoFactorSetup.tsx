import * as React from "react";
import { Header, Grid, Form, Input, Button } from "semantic-ui-react";
import PromiseKeeper from "PromiseKeeper";
import { Cloud } from "Authentication/SDUCloudObject";
import { TwoFactorSetupState } from ".";

export class TwoFactorSetup extends React.Component<{}, TwoFactorSetupState> {
    constructor(props) {
        super(props);
        this.state = this.initialState();

        Cloud.overrides.push({
            path: "/auth",
            destination: {
                port: 8080
            }
        })
    }

    initialState(): TwoFactorSetupState {
        return {
            isConnecting2FA: true,
            verificationCode: ""
        };
    }

    render() {
        return (
            <React.StrictMode>
                <Header><h1>Two Factor Authentication</h1></Header>
                {this.state.isConnecting2FA ? this.setupPage() : undefined}
            </React.StrictMode>
        );
    }

    private setupPage() {
        return (
            <div>
                <div>Setup</div>
                <Button onClick={() => this.onSetupStart()}>
                    Click me
                </Button>
                {this.displayQRCode()}
            </div>
        );
    }

    private displayQRCode() {
        if (this.state.qrCode === undefined ||
            this.state.challengeId === undefined) {
            return undefined;
        }

        return (
            <div>
                <p>{this.state.challengeId}</p>
                <img src={this.state.qrCode} />

                <Form.Field
                    label="6-digit verification code"
                    control={Input}
                    value={this.state.verificationCode}
                    type="text"
                    onChange={(e, { value }) => {
                        this.setState(() => ({
                            verificationCode: value
                        }))
                    }}
                />

                <Button onClick={() => this.onVerificationSubmit()}>
                    Submit code
                </Button>
            </div>
        );
    }

    private onSetupStart() {
        Cloud.post("2fa", undefined, "/auth").then(res => {
            console.log("GOOD!");
            console.log(res);
            this.setState(() => ({
                challengeId: res.response.challengeId,
                qrCode: res.response.qrCodeB64Data
            }));

        }).catch(() => {
            console.log("BAD!");
        })
    }

    private onVerificationSubmit() {
        Cloud.post("2fa/challenge", {
            challengeId: this.state.challengeId,
            verificationCode: this.state.verificationCode
        }, "/auth").then((res) => {
            console.log("GOOD");
            console.log(res);
        }).catch(() => {
            console.log("BAD!");
        });
    }
}
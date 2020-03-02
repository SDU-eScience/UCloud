import {Client} from "Authentication/HttpClientInstance";
import PromiseKeeper from "PromiseKeeper";
import * as React from "react";
import {useRef, useState} from "react";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled from "styled-components";
import {Absolute, Box, Button, Flex, Input, Text, ExternalLink, Link} from "ui-components";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {RouterLocationProps, getQueryParam} from "Utilities/URIUtilities";
import {errorMessageOrDefault} from "UtilityFunctions";
import {Instructions} from "WebDav/Instructions";
import {SITE_DOCUMENTATION_URL, SUPPORT_EMAIL} from "../../site.config.json";
import {BG1} from "./BG1";
import {SnackType} from "Snackbar/Snackbars.js";
import {LoginBox, LoginDropdownContent, LoginExternalLink, LoginIcon, LoginText} from "Login/Login"

const bg2 = require("Assets/Images/bg2.svg");

const BackgroundImage = styled.div<{image: string}>`
    background: url(${({image}) => image}) no-repeat 40% 0%;
    background-size: cover;
    overflow: hidden;
`;

export const ResetPasswordPage: React.FC<RouterLocationProps & {initialState?: any}> = props => {
    const emailInput = useRef<HTMLInputElement>(null);
    const passwordInput = useRef<HTMLInputElement>(null);
    const passwordRepeatInput = useRef<HTMLInputElement>(null);
    const [promises] = useState(new PromiseKeeper());
    const [loading, setLoading] = useState(false);

    const resetToken = getQueryParam(props, "token");

    async function attemptSaveNewPassword(e: {preventDefault(): void}): Promise<void> {
        e.preventDefault();

        if (!(passwordInput.current?.value) || !(passwordRepeatInput.current?.value)) {
            console.log(passwordInput.current?.value)
            snackbarStore.addFailure("Invalid username or password");
            return;
        }

        if (passwordInput.current?.value !== passwordRepeatInput.current?.value) {
            snackbarStore.addFailure("Passwords does not match");
            return;
        }

        if (resetToken == null) {
            return;
        }

        try {
            setLoading(true);

            const body = {
                token: resetToken,
                newPassword: passwordInput.current!.value
            };
            const response = await promises.makeCancelable(
                fetch(Client.computeURL("/api/password/reset", `/new`), {
                    method: "POST",
                    headers: {
                        Accept: "application/json"
                    },
                    body: JSON.stringify(body)
                })
            ).promise;

            if (!response.ok) { // noinspection ExceptionCaughtLocallyJS
                throw response;
            }

        } catch (e) {
            snackbarStore.addFailure(
                errorMessageOrDefault({request: e, response: await e.json()}, "An error occurred")
            );
        } finally {
            setLoading(false);

            passwordInput.current.value = ""
            passwordRepeatInput.current.value = ""

            snackbarStore.addSnack({
                type: SnackType.Success,
                message: `Your password was changed successfully. Return to the Login page to log in with your new password`,
                lifetime: 15_000
            });
        }
    }

    async function submitResetPassword(e: {preventDefault(): void}): Promise<void> {
        e.preventDefault();
        setLoading(true);

        const body = {
            email: emailInput.current!.value
        };

        const response = await promises.makeCancelable(
            fetch("/api/password/reset", {
                method: "POST",
                headers: {
                    "Accept": "application/json",
                    "Content-type": "application/json"
                },
                body: JSON.stringify(body)
            })
        ).promise;

        emailInput.current!.value = "";
        setLoading(false);
        snackbarStore.addSnack({
            type: SnackType.Success,
            message: `If an account exists with the entered email address, you will receive an email shortly.
                Please check your inbox and follow the instructions.`,
            lifetime: 15_000
        });
    }

    return (
        <>
            <Absolute right="1em" top=".5em">
                <div>
                    {!SUPPORT_EMAIL ? null : (
                        <ClickableDropdown
                            width="224px"
                            top="36px"
                            right="5px"
                            colorOnHover={false}
                            trigger={<LoginIcon mr={"1em"} name="suggestion" />}
                        >
                            <ExternalLink href={`mailto:${SUPPORT_EMAIL}`}>
                                Need help?
                                    {" "}<b>{SUPPORT_EMAIL}</b>
                            </ExternalLink>
                        </ClickableDropdown>
                    )}
                    {!SITE_DOCUMENTATION_URL ? null : (
                        <LoginExternalLink href={SITE_DOCUMENTATION_URL}>
                            <LoginIcon name="docs" /> Docs
                        </LoginExternalLink>
                    )}
                </div>
            </Absolute>
            <Absolute top="4vw" left="8vw">
                <LoginBox width={"calc(96px + 10vw)"}>
                    <LoginIcon name="logoSdu" size="100%" />
                </LoginBox>
            </Absolute>

            <Absolute style={{overflowY: "hidden"}} bottom="0" height="50%" width="100%">
                <BG1 />
            </Absolute>

            <BackgroundImage image={bg2}>
                <Flex alignItems="top" justifyContent="center" width="100vw" height="100vh" pt="20vh">
                    {resetToken == null ? (
                        <LoginBox width="315px">
                            <LoginText fontSize={1} mt="5px">
                                To reset your password, enter your email address
                            </LoginText>
                            <LoginDropdownContent
                                overflow="visible"
                                squareTop={false}
                                cursor="pointer"
                                width="315px"
                                hover={false}
                                colorOnHover={false}
                                visible
                            >
                                <LoginBox width="100%">
                                    <form onSubmit={(e) => submitResetPassword(e)}>
                                        <Input
                                            placeholder="Email address"
                                            name="email"
                                            type="email"
                                            ref={emailInput}
                                            autoFocus required
                                        />
                                        <Button
                                            fullWidth
                                            disabled={loading}
                                            marginTop={10}
                                        >
                                            Reset password
                                            </Button>
                                    </form>
                                    <Box mt={20}>
                                        <Link to="/login">
                                            <Text fontSize={1}>Return to Login page</Text>
                                        </Link>
                                    </Box>
                                </LoginBox>
                            </LoginDropdownContent>
                        </LoginBox>
                    ) : (
                        <LoginBox width="315px">
                            <LoginText fontSize={1} mt="5px">
                                Please enter a new password
                            </LoginText>
                            <LoginDropdownContent
                                overflow="visible"
                                squareTop={false}
                                cursor="pointer"
                                width="315px"
                                hover={false}
                                colorOnHover={false}
                                visible
                            >
                                <LoginBox width="100%">
                                    <form onSubmit={(e) => attemptSaveNewPassword(e)}>
                                        <Input
                                            mb={10}
                                            type="password"
                                            placeholder="New password"
                                            ref={passwordInput}
                                            autoFocus
                                        />

                                        <Input
                                            type="password"
                                            placeholder="Repeat new password"
                                            ref={passwordRepeatInput}
                                        />
                                        <Button
                                            fullWidth
                                            disabled={loading}
                                            marginTop={10}
                                        >
                                            Save new password
                                            </Button>
                                    </form>
                                    <Box mt={20}>
                                        <Link to="/login">
                                            <Text fontSize={1}>Return to Login page</Text>
                                        </Link>
                                    </Box>
                                </LoginBox>
                            </LoginDropdownContent>
                        </LoginBox>
                    )}
                </Flex>
            </BackgroundImage>
        </>
    );
};

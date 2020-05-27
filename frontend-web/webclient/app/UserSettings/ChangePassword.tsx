import {useAsyncCommand} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import {useCallback, useRef, useState} from "react";
import * as React from "react";
import {Box, Button, Icon, Input, Label} from "ui-components";
import * as Heading from "ui-components/Heading";

enum ChangePasswordError {
    BAD_CURRENT,
    REPEATED_PASSWORD_DOES_NOT_MATCH
}

export const ChangePassword: React.FunctionComponent<{ setLoading: (loading: boolean) => void }> = props => {
    const [error, setError] = useState<ChangePasswordError | null>(null);
    const currentPassword = useRef<HTMLInputElement>(null);
    const newPassword = useRef<HTMLInputElement>(null);
    const repeatedPassword = useRef<HTMLInputElement>(null);
    const [commandLoading, invokeCommand] = useAsyncCommand();

    props.setLoading(commandLoading);
    const onSubmit = useCallback(async (e: React.SyntheticEvent) => {
        e.preventDefault();

        if (commandLoading) return;

        const current = currentPassword.current;
        const newPass = newPassword.current;
        const repeated = repeatedPassword.current;

        if (!current || !newPass || !repeated) {
            setError(ChangePasswordError.BAD_CURRENT);
            return;
        }

        if (newPass.value !== repeated.value) {
            setError(ChangePasswordError.REPEATED_PASSWORD_DOES_NOT_MATCH);
            return;
        }

        setError(null);

        const wasSuccessful = await invokeCommand({
            reloadId: Math.random(),
            method: "POST",
            path: "/auth/users/password",
            context: "",
            payload: {
                currentPassword: current.value,
                newPassword: newPass.value
            }
        }) !== null;

        if (!wasSuccessful) {
            setError(ChangePasswordError.BAD_CURRENT);
        }

        current.value = "";
        newPass.value = "";
        repeated.value = "";
    }, [commandLoading, currentPassword.current, newPassword.current, repeatedPassword.current]);

    if (Client.principalType !== "password") return null;

    return (
        <Box mb={16}>
            <Heading.h2>Change Password</Heading.h2>
            <form onSubmit={onSubmit}>
                <Box mt="0.5em" pt="0.5em">
                    <Label>
                        Current Password
                        <Input
                            ref={currentPassword}
                            type="password"
                            placeholder={"Current password"}
                        />
                        {error === ChangePasswordError.BAD_CURRENT ? <Icon name="warning" color="red" /> : null}
                    </Label>
                </Box>

                <Box mt="0.5em" pt="0.5em">
                    <Label>
                        New Password
                        <Input
                            ref={newPassword}
                            type="password"
                            placeholder="New password"
                        />
                        {error === ChangePasswordError.REPEATED_PASSWORD_DOES_NOT_MATCH ?
                            <Icon name="warning" color="red" /> : null}
                    </Label>
                </Box>

                <Box mt="0.5em" pt="0.5em">
                    <Label>
                        Repeat new password
                        <Input
                            ref={repeatedPassword}
                            type="password"
                            placeholder="Repeat password"
                        />
                        {error === ChangePasswordError.REPEATED_PASSWORD_DOES_NOT_MATCH ?
                            <Icon name="warning" color="red" /> : null}
                    </Label>
                </Box>

                <Button
                    mt={"1em"}
                    type={"submit"}
                    color="green"
                    disabled={commandLoading}
                >
                    Change password
                </Button>
            </form>
        </Box>
    );
};

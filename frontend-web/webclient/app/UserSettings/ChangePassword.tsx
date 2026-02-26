import {useCloudCommand} from "@/Authentication/DataHook";
import {Client} from "@/Authentication/HttpClientInstance";
import {useCallback, useRef, useState} from "react";
import * as React from "react";
import {Box, Icon, Input, Label} from "@/ui-components";
import {SettingsActions, SettingsSection} from "./SettingsComponents";

enum ChangePasswordError {
    BAD_CURRENT,
    REPEATED_PASSWORD_DOES_NOT_MATCH
}

export const ChangePassword: React.FunctionComponent<{setLoading: (loading: boolean) => void}> = props => {
    const [error, setError] = useState<ChangePasswordError | null>(null);
    const currentPassword = useRef<HTMLInputElement>(null);
    const newPassword = useRef<HTMLInputElement>(null);
    const repeatedPassword = useRef<HTMLInputElement>(null);
    const [commandLoading, invokeCommand] = useCloudCommand();

    props.setLoading(commandLoading);
    const onSubmit = useCallback(async (e: React.SyntheticEvent) => {
        e.preventDefault();

        if (commandLoading) return;

        const current = currentPassword.current;
        const newPass = newPassword.current;
        const repeated = repeatedPassword.current;

        if (!current?.value || !newPass?.value || !repeated?.value) {
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
        <SettingsSection id="password" title="Change password">
            <form onSubmit={onSubmit}>
                <Box mt="0.5em" pt="0.5em">
                    <Label>
                        Current password
                        <Input
                            inputRef={currentPassword}
                            type="password"
                            placeholder={"Current password"}
                        />
                        {error === ChangePasswordError.BAD_CURRENT ? <Icon name="warning" color="errorMain" /> : null}
                    </Label>
                </Box>

                <Box mt="0.5em" pt="0.5em">
                    <Label>
                        New password
                        <Input
                            inputRef={newPassword}
                            type="password"
                            placeholder="New password"
                        />
                        {error === ChangePasswordError.REPEATED_PASSWORD_DOES_NOT_MATCH ?
                            <Icon name="warning" color="errorMain" /> : null}
                    </Label>
                </Box>

                <Box mt="0.5em" pt="0.5em">
                    <Label>
                        Repeat new password
                        <Input
                            inputRef={repeatedPassword}
                            type="password"
                            placeholder="Repeat password"
                        />
                        {error === ChangePasswordError.REPEATED_PASSWORD_DOES_NOT_MATCH ?
                            <Icon name="warning" color="errorMain" /> : null}
                    </Label>
                </Box>

                <SettingsActions submitLabel="Change password" disabled={commandLoading} />
            </form>
        </SettingsSection>
    );
};

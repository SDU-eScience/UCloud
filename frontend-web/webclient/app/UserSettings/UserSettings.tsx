import {Client} from "Authentication/HttpClientInstance";
import {MainContainer} from "MainContainer/MainContainer";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setLoading, SetStatusLoading, useTitle} from "Navigation/Redux/StatusActions";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {Box, Flex} from "ui-components";
import * as Heading from "ui-components/Heading";
import {ChangePassword} from "UserSettings/ChangePassword";
import {Sessions} from "UserSettings/Sessions";
import {TwoFactorSetup} from "./TwoFactorSetup";
import {ChangeUserDetails} from "UserSettings/ChangeUserDetails";
import {ChangeEmailSettings} from "UserSettings/ChangeEmailSettings";
import {getCssVar} from "Utilities/StyledComponentsUtilities";

interface UserSettingsState {
    headerLoading: boolean;
}

const UserSettings: React.FunctionComponent<UserSettingsOperations & UserSettingsState> = props => {

    useTitle("User Settings");

    const mustActivate2fa =
        Client.userInfo?.twoFactorAuthentication === false &&
        Client.userInfo?.principalType === "password";

    return (
        <Flex>
            <MainContainer
                header={<Heading.h1>User Settings</Heading.h1>}
                main={(
                    <>
                        <SettingsBox>
                            <TwoFactorSetup
                                mustActivate2fa={mustActivate2fa}
                                loading={props.headerLoading}
                                setLoading={props.setLoading}
                            />
                        </SettingsBox>

                        {mustActivate2fa ? null : (
                            <>
                                <SettingsBox>
                                    <ChangePassword
                                        setLoading={props.setLoading}
                                    />
                                </SettingsBox>

                                <SettingsBox>
                                    <ChangeUserDetails
                                        setLoading={props.setLoading}
                                    />
                                </SettingsBox>
                                <SettingsBox>
                                    <ChangeEmailSettings
                                        setLoading={props.setLoading}
                                    />
                                </SettingsBox>
                                <SettingsBox>
                                    <Sessions
                                        setLoading={props.setLoading}
                                        setRefresh={props.setRefresh}
                                    />
                                </SettingsBox>
                            </>
                        )}

                    </>
                )}
            />
        </Flex>
    );
};

export function SettingsBox(props: React.PropsWithChildren<{}>): JSX.Element {
    return (
        <Box pl="8px" pt="5px" pb="4px" mb="24px" borderRadius="12px" backgroundColor={getCssVar("settingsBox")} width="100%">
            {props.children}
        </Box>
    )
}

interface UserSettingsOperations extends SetStatusLoading {
    setRefresh: (fn?: () => void) => void;
}

const mapDispatchToProps = (dispatch: Dispatch): UserSettingsOperations => ({
    setLoading: loading => dispatch(setLoading(loading)),
    setRefresh: fn => dispatch(setRefreshFunction(fn))
});

const mapStateToProps = ({status}: ReduxObject): UserSettingsState => ({
    headerLoading: status.loading
});

export default connect(mapStateToProps, mapDispatchToProps)(UserSettings);

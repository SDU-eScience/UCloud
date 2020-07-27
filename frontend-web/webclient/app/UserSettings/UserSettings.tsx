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

interface UserSettingsState {
    headerLoading: boolean;
}

const UserSettings: React.FunctionComponent<UserSettingsOperations & UserSettingsState> = props => {

    useTitle("User Settings");

    const mustActivate2fa =
        Client.userInfo?.twoFactorAuthentication === false &&
        Client.userInfo?.principalType === "password";

    return (
        <Flex alignItems="center" flexDirection="column">
            <Box width={0.7}>
                <MainContainer
                    header={<Heading.h1>User Settings</Heading.h1>}
                    main={(
                        <>
                            <TwoFactorSetup
                                mustActivate2fa={mustActivate2fa}
                                loading={props.headerLoading}
                                setLoading={props.setLoading}
                            />

                            {mustActivate2fa ? null : (
                                <>
                                    <ChangePassword
                                        setLoading={props.setLoading}
                                    />

                                    <ChangeUserDetails
                                        setLoading={props.setLoading}
                                    />

                                    <Sessions
                                        setLoading={props.setLoading}
                                        setRefresh={props.setRefresh}
                                    />
                                </>
                            )}

                        </>
                    )}
                />
            </Box>
        </Flex>
    );
};

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

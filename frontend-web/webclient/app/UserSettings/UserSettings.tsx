import {Client} from "@/Authentication/HttpClientInstance";
import {MainContainer} from "@/ui-components/MainContainer";
import {setLoading, useTitle} from "@/Navigation/Redux";
import * as React from "react";
import {useDispatch, useSelector} from "react-redux";
import {Box, Flex} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {ChangePassword} from "@/UserSettings/ChangePassword";
import {Sessions} from "@/UserSettings/Sessions";
import {TwoFactorSetup} from "./TwoFactorSetup";
import {ChangeOptionalUserDetails, ChangeUserDetails} from "@/UserSettings/ChangeUserDetails";
import {ChangeEmailSettings} from "@/UserSettings/ChangeEmailSettings";
import {CustomTheming} from "./CustomTheme";
import {refreshFunctionCache} from "@/Utilities/ReduxUtilities";

function UserSettings(): React.ReactNode {

    useTitle("User Settings");

    const headerLoading = useSelector(({status}: ReduxObject) => status.loading);
    const dispatch = useDispatch();

    const setHeaderLoading = React.useCallback((loading: boolean) => {
        dispatch(setLoading(loading));
    }, [dispatch]);

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
                                loading={headerLoading}
                                setLoading={setHeaderLoading}
                            />

                            {mustActivate2fa ? null : (
                                <>
                                    <ChangePassword
                                        setLoading={setHeaderLoading}
                                    />

                                    <ChangeUserDetails
                                        setLoading={setHeaderLoading}
                                    />
                                    <ChangeOptionalUserDetails />
                                    <ChangeEmailSettings
                                        setLoading={setHeaderLoading}
                                    />
                                    <Sessions
                                        setLoading={setHeaderLoading}
                                        setRefresh={fn => refreshFunctionCache.setRefreshFunction(fn ?? (() => undefined))}
                                    />
                                    <CustomTheming />
                                </>
                            )}

                        </>
                    )}
                />
            </Box>
        </Flex>
    );
}

export default UserSettings;

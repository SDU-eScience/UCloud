import {Client} from "@/Authentication/HttpClientInstance";
import {MainContainer} from "@/ui-components/MainContainer";
import {setLoading, usePage} from "@/Navigation/Redux";
import * as React from "react";
import {useDispatch, useSelector} from "react-redux";
import {Box, Flex} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {ChangePassword} from "@/UserSettings/ChangePassword";
import {Sessions} from "@/UserSettings/Sessions";
import {TwoFactorSetup} from "./TwoFactorSetup";
import {ChangeOrganizationDetails, ChangeUserDetails} from "@/UserSettings/ChangeUserDetails";
import {ChangeEmailSettings} from "@/UserSettings/ChangeEmailSettings";
import {CustomTheming} from "./CustomTheme";
import {refreshFunctionCache} from "@/Utilities/ReduxUtilities";
import {ChangeNotificationSettings} from "./ChangeNotificationSettings";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {SettingsNavSection, SettingsNavigator} from "./SettingsComponents";

function UserSettings(): React.ReactNode {

    usePage("User Settings", SidebarTabId.NONE);

    const headerLoading = useSelector(({status}: ReduxObject) => status.loading);
    const dispatch = useDispatch();

    const setHeaderLoading = React.useCallback((loading: boolean) => {
        dispatch(setLoading(loading));
    }, [dispatch]);

    const mustActivate2fa =
        Client.userInfo?.twoFactorAuthentication === false &&
        Client.userInfo?.principalType === "password";

    const sections: SettingsNavSection[] = mustActivate2fa ? [
        {id: "two-factor", label: "Two factor authentication"}
    ] : [
        {id: "two-factor", label: "Two factor authentication"},
        {id: "password", label: "Change password"},
        {id: "profile", label: "User details"},
        {id: "organization", label: "Additional information"},
        {id: "email", label: "Email settings"},
        {id: "notifications", label: "Notifications"},
        {id: "sessions", label: "Active sessions"},
    ];

    return (
        <Flex alignItems="center" flexDirection="column">
            <Box width="100%" maxWidth="980px">
                <MainContainer
                    header={<Heading.h1>User settings</Heading.h1>}
                    main={(
                        <>
                            <SettingsNavigator sections={sections} />
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

                                    <ChangeUserDetails />
                                    <ChangeOrganizationDetails />
                                    <ChangeEmailSettings
                                        setLoading={setHeaderLoading}
                                    />
                                    <ChangeNotificationSettings
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

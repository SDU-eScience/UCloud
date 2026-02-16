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
        {id: "profile", label: "User information"},
        {id: "organization", label: "Additional user information"},
        {id: "email", label: "Email settings"},
        {id: "notifications", label: "Notification settings"},
        {id: "job-report", label: "Job report settings"},
        {id: "two-factor", label: "Two factor authentication"},
        {id: "password", label: "Change password"},
        {id: "sessions", label: "Active sessions"},
    ];

    const twoFactorSetup = <TwoFactorSetup
        mustActivate2fa={mustActivate2fa}
        loading={headerLoading}
        setLoading={setHeaderLoading}
    />;
    return (
        <Flex alignItems="center" flexDirection="column">
            <Box width="100%" maxWidth="1200px" padding={"8px"}>
                <MainContainer
                    header={<Heading.h1>User settings</Heading.h1>}
                    main={(
                        <Flex gap={"24px"} flexDirection={"column"}>
                            <SettingsNavigator sections={sections}/>

                            {mustActivate2fa ? twoFactorSetup : (
                                <>
                                    <ChangeUserDetails/>
                                    <ChangeOrganizationDetails/>
                                    <ChangeEmailSettings
                                        setLoading={setHeaderLoading}
                                    />
                                    <ChangeNotificationSettings
                                        setLoading={setHeaderLoading}
                                    />

                                    {twoFactorSetup}

                                    <ChangePassword
                                        setLoading={setHeaderLoading}
                                    />

                                    <Sessions
                                        setLoading={setHeaderLoading}
                                        setRefresh={fn => refreshFunctionCache.setRefreshFunction(fn ?? (() => undefined))}
                                    />
                                    <CustomTheming/>
                                </>
                            )}

                        </Flex>
                    )}
                />
            </Box>
        </Flex>
    );
}

export default UserSettings;

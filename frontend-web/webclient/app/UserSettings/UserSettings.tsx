import {Client} from "@/Authentication/HttpClientInstance";
import {setStatusLoading, usePage} from "@/Navigation/Redux";
import * as React from "react";
import {useDispatch, useSelector} from "react-redux";
import {ChangePassword} from "@/UserSettings/ChangePassword";
import {Sessions} from "@/UserSettings/Sessions";
import {TwoFactorSetup} from "./TwoFactorSetup";
import {ChangeOrganizationDetails, ChangeUserDetails} from "@/UserSettings/ChangeUserDetails";
import {ChangeEmailSettings} from "@/UserSettings/ChangeEmailSettings";
import {CustomTheming} from "./CustomTheme";
import {refreshFunctionCache} from "@/Utilities/ReduxUtilities";
import {ChangeNotificationSettings} from "./ChangeNotificationSettings";
import {ChangeJobReportSettings} from "./ChangeJobReportSettings";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {SettingsNavSection, SettingsPage} from "@/ui-components/SettingsComponents";

function UserSettings(): React.ReactNode {

    usePage("User Settings", SidebarTabId.NONE);

    const headerLoading = useSelector(({status}: ReduxObject) => status.loading);
    const dispatch = useDispatch();

    const setHeaderLoading = React.useCallback((loading: boolean) => {
        dispatch(setStatusLoading(loading));
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
        ...(Client.userInfo?.principalType === "password" ? [{id: "password", label: "Change password"}] : []),
        {id: "sessions", label: "Active sessions"},
    ];

    const twoFactorSetup = <TwoFactorSetup
        mustActivate2fa={mustActivate2fa}
        loading={headerLoading}
        setLoading={setHeaderLoading}
    />;
    return <SettingsPage title="User settings" sections={sections}>
        {mustActivate2fa ? twoFactorSetup : <>
            <ChangeUserDetails />
            <ChangeOrganizationDetails />
            <ChangeEmailSettings setLoading={setHeaderLoading} />
            <ChangeNotificationSettings setLoading={setHeaderLoading} />
            <ChangeJobReportSettings setLoading={setHeaderLoading} />
            {twoFactorSetup}
            <ChangePassword setLoading={setHeaderLoading} />
            <Sessions
                setLoading={setHeaderLoading}
                setRefresh={fn => refreshFunctionCache.setRefreshFunction(fn ?? (() => undefined))}
            />
            <CustomTheming />
        </>}
    </SettingsPage>;
}

export default UserSettings;

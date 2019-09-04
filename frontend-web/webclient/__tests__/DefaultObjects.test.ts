import {AnalysesStateProps, RunsSortBy} from "../app/Applications";
import {DashboardStateProps} from "../app/Dashboard";
import * as Defaults from "../app/DefaultObjects";
import {SortOrder} from "../app/Files";
import {SidebarPages} from "../app/ui-components/Sidebar";

describe("Initialize Redux Objects", () => {
    test("Dashboard", () => {
        expect(Defaults.initDashboard()).toEqual({
            favoriteFiles: [],
            recentFiles: [],
            recentAnalyses: [],
            notifications: [],
            favoriteLoading: false,
            recentLoading: false,
            analysesLoading: false,
        } as DashboardStateProps);
    });

    test("Status", () =>
        expect(Defaults.initStatus()).toEqual({
            status: Defaults.DefaultStatus,
            title: "",
            page: SidebarPages.None,
            loading: false
        } as Defaults.StatusReduxObject)
    );

    test("Header", () =>
        expect(Defaults.initHeader()).toEqual({
            prioritizedSearch: "files"
        } as Defaults.HeaderSearchReduxObject)
    );

    test("Notifications", () =>
        expect(Defaults.initNotifications()).toEqual({
            items: [],
            loading: false,
            redirectTo: "",
            error: undefined
        } as Defaults.NotificationsReduxObject)
    );

    test("Analyses", () =>
        expect(Defaults.initAnalyses()).toEqual({
            page: Defaults.emptyPage,
            loading: false,
            error: undefined,
            sortBy: RunsSortBy.createdAt,
            sortOrder: SortOrder.DESCENDING
        } as AnalysesStateProps)
    );

    test("Sidebar", () =>
        expect(Defaults.initSidebar()).toEqual({
            kcCount: 0,
            pp: false,
            options: []
        } as Defaults.SidebarReduxObject)
    );

    test("Uploads", () =>
        expect(JSON.parse(JSON.stringify(Defaults.initUploads()))).toEqual(JSON.parse(JSON.stringify({
            path: "",
            uploads: [],
            loading: false,
            visible: false,
            allowMultiple: false,
            onFilesUploaded: () => null
        })) as Defaults.UploaderReduxObject)
    );

    test("Init object", () =>
        expect(Defaults.initObject()).toBeDefined()
    );
});

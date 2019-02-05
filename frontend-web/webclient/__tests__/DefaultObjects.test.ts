import * as Defaults from "DefaultObjects";
import { SortOrder, SortBy } from "Files";
import { SidebarPages } from "ui-components/Sidebar";

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
            errors: []
        })
    });

    test("Files", () => {
        const homeFolder = "/home/user@test.dk/"
        expect(JSON.parse(JSON.stringify(Defaults.initFiles(homeFolder)))).toEqual(JSON.parse(JSON.stringify({
            page: Defaults.emptyPage,
            sortOrder: SortOrder.ASCENDING,
            sortBy: SortBy.PATH,
            loading: false,
            error: undefined,
            path: "",
            invalidPath: false,
            filesInfoPath: "",
            sortingColumns: [SortBy.MODIFIED_AT, SortBy.ACL],
            fileSelectorLoading: false,
            fileSelectorShown: false,
            fileSelectorPage: Defaults.emptyPage,
            fileSelectorPath: homeFolder,
            fileSelectorCallback: () => null,
            fileSelectorError: undefined,
            disallowedPaths: []
        })))
    });

    test("Status", () =>
        expect(Defaults.initStatus()).toEqual({
            status: Defaults.DefaultStatus,
            title: "",
            page: SidebarPages.None
        })
    );

    test("Header", () =>
        expect(Defaults.initHeader()).toEqual({
            prioritizedSearch: "files"
        })
    );

    test("Notifications", () =>
        expect(Defaults.initNotifications()).toEqual({
            page: Defaults.emptyPage,
            loading: false,
            redirectTo: "",
            error: undefined
        })
    );

    test("Analyses", () =>
        expect(Defaults.initAnalyses()).toEqual({
            page: Defaults.emptyPage,
            loading: false,
            error: undefined
        })
    );

    test("Zenodo", () =>
        expect(Defaults.initZenodo()).toEqual({
            connected: false,
            loading: false,
            page: Defaults.emptyPage,
            error: undefined
        })
    );

    test("Sidebar", () =>
        expect(Defaults.initSidebar()).toEqual({
            kcCount: 0,
            pp: false,
            options: []
        })
    );

    test("Uploads", () =>
        expect(JSON.parse(JSON.stringify(Defaults.initUploads()))).toEqual(JSON.parse(JSON.stringify({
            path: "",
            uploads: [],
            loading: false,
            visible: false,
            allowMultiple: false,
            onFilesUploaded: () => null
        })))
    );
});
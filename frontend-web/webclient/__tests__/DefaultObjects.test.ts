import * as Defaults from "DefaultObjects";
import { SortOrder, SortBy } from "Files";
import Cloud from "Authentication/lib";
import { stringify } from "querystring";

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
            favoriteError: undefined,
            recentFilesError: undefined,
            recentAnalysesError: undefined
        })
    });

    test("Files", () => {
        const homeFolder = "/home/user@test.dk/"
        expect(JSON.parse(JSON.stringify(Defaults.initFiles({ homeFolder })))).toEqual(JSON.parse(JSON.stringify({
            page: Defaults.emptyPage,
            sortOrder: SortOrder.ASCENDING,
            sortBy: SortBy.PATH,
            loading: false,
            error: undefined,
            path: "",
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

    // Circular object
    test.skip("Uppy", () => {
        const cloud = new Cloud();
        expect(JSON.parse(JSON.stringify(Defaults.initUppy(cloud)))).toEqual(JSON.parse(JSON.stringify({
            uppy: Defaults.initializeUppy({ maxNumberOfFiles: 1 } as Defaults.UppyRestriction, cloud),
            uppyOpen: false
        })));
    });

    test("Status", () =>
        expect(Defaults.initStatus()).toEqual({
            status: Defaults.DefaultStatus,
            title: ""
        })
    );

    test("Applications", () =>
        expect(Defaults.initApplications()).toEqual({
            page: Defaults.emptyPage,
            loading: false,
            error: undefined
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
            open: false,
            loading: false,
            pp: false,
            options: []
        })
    );

    test("Uploads", () =>
        expect(JSON.parse(JSON.stringify(Defaults.initUploads()))).toEqual(JSON.parse(JSON.stringify({
            path: "",
            uploads: [],
            visible: false,
            allowMultiple: false,
            onFilesUploaded: () => null
        })))
    );
});
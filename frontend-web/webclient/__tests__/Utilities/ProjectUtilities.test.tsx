import * as React from "react";
import * as ProjectUtils from "../../app/Utilities/ProjectUtilities";
import {ProjectRole} from "../../app/Project";
import {create} from "react-test-renderer";
import {Client} from "../../app/Authentication/HttpClientInstance";
import {GroupWithSummary} from "../../app/Project/GroupList";
let groupCounter = 0;

function newGroupWithSummary(): GroupWithSummary {
    return {
        groupId: groupCounter.toString(),
        groupTitle: "Group " + groupCounter++,
        members: ["foo", "bar", "baz"],
        numberOfMembers: 3
    };
}

jest.mock("Authentication/DataHook", () => ({
    useCloudAPI: (path) => ([{data: groupSummaryPage, loading: false, error: undefined}])
}));

const groupSummaryPage: Page<GroupWithSummary> = {
    items: [newGroupWithSummary(), newGroupWithSummary(), newGroupWithSummary()],
    itemsInTotal: 3,
    itemsPerPage: 25,
    pageNumber: 0,
    pagesInTotal: 1
};

describe("repositoryName", () => {
    test("Too short", () => {
        expect(ProjectUtils.repositoryName("/home/foo/")).toBe("");
    });

    test("Wrong base folder", () => {
        expect(ProjectUtils.repositoryName("/home/foo/folder")).toBe("");
    });

    test("Is repo", () => {
        expect(ProjectUtils.repositoryName("/projects/foo/bar")).toBe("bar");
    });
});

describe("createRepository", () => {
    test("Success", async () => {
        const reload = jest.fn(() => 42);
        await ProjectUtils.createRepository(Client, "foo", reload);
        expect(reload.mock.calls.length).toBe(1);
    });
});

describe("renameRepository", () => {
    test("Success", async () => {
        const reload = jest.fn(() => 42);
        await ProjectUtils.renameRepository("foo", "bar", Client, reload);
        expect(reload.mock.calls.length).toBe(1);
    });
});

describe("isRepository", () => {
    test("Is", () => {
        expect(ProjectUtils.isRepository("/projects/foo/bar")).toBe(true);
    });

    test("Isn't", () => {
        expect(ProjectUtils.isRepository("/home/foo/bar")).toBe(false);
    });

    test("Too long", () => {
        expect(ProjectUtils.isRepository("/projects/foo/bar/baz")).toBe(false);
    });
});

describe("isAdminOrPI", () => {
    test("User", () => {
        expect(ProjectUtils.isAdminOrPI(ProjectRole.USER)).toBe(false);
    });

    test("Admin", () => {
        expect(ProjectUtils.isAdminOrPI(ProjectRole.ADMIN)).toBe(true);
    });

    test("PI", () => {
        expect(ProjectUtils.isAdminOrPI(ProjectRole.PI)).toBe(true);
    });
});

describe("UpdatePermissionsDialog", () => {
    test("Mount", () => {
        expect(create(<ProjectUtils.UpdatePermissionsDialog
            client={Client}
            reload={() => undefined}
            repository="undefined"
            rights={[]}
        />)).toMatchSnapshot();
    });
});

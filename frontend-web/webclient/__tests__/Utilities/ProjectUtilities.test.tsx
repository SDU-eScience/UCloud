import * as ProjectUtils from "../../app/Utilities/ProjectUtilities";
import {ProjectRole} from "../../app/Project";
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
    pageNumber: 0
};

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

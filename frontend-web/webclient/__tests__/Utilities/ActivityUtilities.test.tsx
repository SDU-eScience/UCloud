import * as ActivityUtils from "../../app/Utilities/ActivityUtilities";
import {ActivityType} from "../../app/Activity";

describe("activityQuery", () => {
    test("Without filter", () => {
        expect(ActivityUtils.activityQuery({offset: 10, scrollSize: 10})).toBe("/activity/browse?offset=10&scrollSize=10");
    });

    test("With empty filter", () => {
        expect(ActivityUtils.activityQuery({offset: 10, scrollSize: 10}, {})).toBe("/activity/browse?offset=10&scrollSize=10");
    });

    test("No scrolloffset", () => {
        expect(ActivityUtils.activityQuery({scrollSize: 10})).toBe("/activity/browse?scrollSize=10");
    });

    test("Full filter", () => {
        expect(ActivityUtils.activityQuery({offset: 25, scrollSize: 10}, {
            type: ActivityType.DELETED,
            collapseAt: 5,
            minTimestamp: new Date(1000),
            maxTimestamp: new Date(2000),
            user: "foo"
        })).toBe("/activity/browse?offset=25&scrollSize=10&type=deleted&collapseAt=5&maxTimestamp=2000&minTimestamp=1000&user=foo");
    });
});

test("activityStreamByPath", () => {
    expect(ActivityUtils.activityStreamByPath("/foo/bar/baz/")).toBe("/activity/by-path?path=%2Ffoo%2Fbar%2Fbaz%2F");
});

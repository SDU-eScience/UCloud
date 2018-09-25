import { Page } from "Types";
import { Activity } from "Activity";

export const activityPage: Page<Activity> = {
    itemsInTotal: 2,
    itemsPerPage: 25,
    pageNumber: 0,
    items: [
        {
            type: "counted",
            operation: "FAVORITE",
            entries: [{
                id: "1099513081208",
                count: 1,
                path: null
            }],
            timestamp: 1537367233647
        }, {
            type: "counted",
            operation: "FAVORITE",
            entries: [{
                id: "1099513082096",
                count: 1,
                path: "/home/jonas@hinchely.dk/Favorites/A folder the sequel(1)"
            }, {
                id: "1099513081179",
                count: 1,
                path: "/home/jonas@hinchely.dk/Favorites/AdEoA_Blackboard.pdf"
            }, {
                id: "1099513082628",
                count: 1, "path": "/home/jonas@hinchely.dk/Favorites/App Data"
            }, {
                id: "1099513080158",
                count: 17,
                path: "/home/jonas@hinchely.dk/Favorites/B"
            }, {
                id: "1099513082325",
                count: 1,
                path: "/home/jonas@hinchely.dk/Favorites/Dan's Master Thesis"
            }],
            timestamp: 1537363502687
        }],
    pagesInTotal: 0
}

test("", () => undefined);
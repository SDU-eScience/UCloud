import { Page } from "Types";
import { Activity } from "Activity";

export const activityPage: Page<Activity> = {
    itemsInTotal: 6,
    itemsPerPage: 25,
    pageNumber: 0,
    items: [
        {
            type: "tracked",
            operation: "MOVED",
            timestamp: 1538980987204,
            files: [{ id: "1099513093336", path: "/home/jonas@hinchely.dk/ABC.jpg" }],
            users: [{ username: "jonas@hinchely.dk" }]
        }, {
            type: "tracked",
            operation: "REMOVE_FAVORITE",
            timestamp: 1538980980927,
            files: [{ id: "1099513093336", path: "/home/jonas@hinchely.dk/ABC.jpg" }],
            users: [{ username: "jonas@hinchely.dk" }]
        }, {
            type: "tracked",
            operation: "DELETE",
            timestamp: 1538980980891,
            files: [{ id: "1099513095590", path: null }, { id: "1099513095591", path: null }, { id: "1099513095592", path: null }],
            users: [{ username: "jonas@hinchely.dk" }]
        }, {
            type: "tracked",
            operation: "FAVORITE",
            timestamp: 1538980980513,
            files: [{ id: "1099513093336", path: "/home/jonas@hinchely.dk/ABC.jpg" }],
            users: [{ username: "jonas@hinchely.dk" }]
        }, {
            type: "tracked",
            operation: "UPDATE",
            timestamp: 1538980980000,
            files: [{ id: "1099513095591", path: null }, { id: "1099513095592", path: null }, { id: "1099513095590", path: null }],
            users: [{ username: "jonas@hinchely.dk" }]
        }, {
            type: "tracked",
            operation: "UPDATE",
            timestamp: 1538029576000,
            files: [{ id: "1099513093336", path: "/home/jonas@hinchely.dk/ABC.jpg" }],
            users: [{ username: "jonas@hinchely.dk" }]
        }],
    pagesInTotal: 0
}

test("", () => undefined);
import { Page } from "Types";
import { Notification } from "Notifications";

export const notifications: Page<Notification> = {
    itemsInTotal: 2,
    itemsPerPage: 10,
    pageNumber: 0,
    items: [
        {
            type: "SHARE_REQUEST",
            message: "jonas@hinchely.dk has shared a file with you",
            id: 3,
            meta: {
                path: "/home/jonas@hinchely.dk/AdEoA_Blackboard.pdf",
                rights: ["EXECUTE", "WRITE"],
                shareId: 3
            },
            ts: 1535986219809,
            read: false
        }, {
            type: "SHARE_REQUEST",
            message: "jonas@hinchely.dk has shared a file with you",
            id: 2,
            meta: {
                path: "/home/jonas@hinchely.dk/AABA",
                rights: ["READ"],
                shareId: 2
            },
            ts: 1535377421247,
            read: true
        }],
    pagesInTotal: 0
}

test("Silencer", () => expect(1).toBe(1));
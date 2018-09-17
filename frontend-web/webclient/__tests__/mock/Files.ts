import { File } from "Files";
import { Page } from "Types";

export const mockFiles_SensitivityConfidential: Page<File> =
{
    itemsInTotal: 12,
    itemsPerPage: 10,
    pageNumber: 0,
    items: [
        {
            type: "FILE",
            path: "/home/user@user.telecity/Screenshot_2018-08-09 SDU-eScience SDUCloud-1.png", createdAt: 1535379900000, modifiedAt: 1535379900000, ownerName: "user@user.telecity", size: 3794, acl: [], favorited: false, sensitivityLevel: "CONFIDENTIAL", link: false, annotations: []
        }, {
            type: "DIRECTORY",
            path: "/home/user@user.telecity/AABA", createdAt: 1535026060000, modifiedAt: 1534858191000, ownerName: "user@user.telecity", size: 1, acl: [{ entity: "user3@test.dk", rights: ["WRITE", "EXECUTE"], "group": false }], favorited: true, sensitivityLevel: "CONFIDENTIAL", link: false, annotations: []
        }, {
            type: "DIRECTORY",
            path: "/home/user@user.telecity/Favorites", createdAt: 1535026061000, modifiedAt: 1534857733000, ownerName: "user@user.telecity", size: 6, acl: [], favorited: false, sensitivityLevel: "CONFIDENTIAL", link: false, annotations: []
        }, {
            type: "DIRECTORY",
            path: "/home/user@user.telecity/Jobs", createdAt: 1535026061000, modifiedAt: 1534836764000, ownerName: "user@user.telecity", size: 78, acl: [], favorited: false, sensitivityLevel: "CONFIDENTIAL", link: false, annotations: []
        }, {
            type: "DIRECTORY",
            path: "/home/user@user.telecity/A folder the sequel", createdAt: 1535380572000, modifiedAt: 1534836373000, ownerName: "user@user.telecity", size: 1, acl: [], favorited: false, sensitivityLevel: "CONFIDENTIAL", link: false, annotations: []
        }, {
            type: "DIRECTORY",
            path: "/home/user@user.telecity/B", createdAt: 1535026060000, modifiedAt: 1534244778000, ownerName: "user@user.telecity", size: 3, acl: [], favorited: true, sensitivityLevel: "CONFIDENTIAL", link: false, annotations: []
        }, {
            type: "DIRECTORY",
            path: "/home/user@user.telecity/rehjoerijhoerijh", createdAt: 1535026061000, modifiedAt: 1534170588000, ownerName: "user@user.telecity", size: 0, acl: [], favorited: false, sensitivityLevel: "CONFIDENTIAL", link: false, annotations: []
        }, {
            type: "DIRECTORY",
            path: "/home/user@user.telecity/Test", createdAt: 1535026061000, modifiedAt: 1533630132000, ownerName: "user@user.telecity", size: 0, acl: [], favorited: false, sensitivityLevel: "CONFIDENTIAL", link: false, annotations: []
        }, {
            type: "DIRECTORY",
            path: "/home/user@user.telecity/somefolder-60D6DAD4-DCF1-415E-B4D6-9BCEF028369E", createdAt: 1535026061000, modifiedAt: 1533555168000, ownerName: "user@user.telecity", size: 0, acl: [], favorited: false, sensitivityLevel: "CONFIDENTIAL", link: false, annotations: []
        }, {
            type: "DIRECTORY",
            path: "/home/user@user.telecity/somefolder-24ACCA0C-2FAA-4B6C-8F31-7F1E633A54DB", createdAt: 1535026061000, modifiedAt: 1533554955000, ownerName: "user@user.telecity", size: 0, acl: [], favorited: false, sensitivityLevel: "CONFIDENTIAL", link: false, annotations: ["P"]
        }],
    pagesInTotal: 2
};

export const newMockFile = ({
    type = "DIRECTORY",
    path,
    createdAt = new Date().getMilliseconds() - 3600 * 24,
    modifiedAt = new Date().getMilliseconds(),
    ownerName = "user3@user.dk",
    size = 128,
    acl = [],
    favorited = false,
    sensitivityLevel = "CONFIDENTIAL",
    link = false,
    annotations = []
}: File): File => ({
    type,
    path: `/home/${ownerName}/${path}`,
    createdAt,
    modifiedAt,
    size,
    acl,
    ownerName,
    favorited,
    sensitivityLevel,
    link,
    annotations
});

test("Error silencer", () =>
    expect(1).toBe(1)
);
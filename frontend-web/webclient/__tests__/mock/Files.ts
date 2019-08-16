import {SensitivityLevelMap} from "../../app/DefaultObjects";
import {File, FileType} from "../../app/Files";
import {Page} from "../../app/Types";

export const mockFilesSensitivityConfidential: Page<File> = {
    itemsInTotal: 12,
    itemsPerPage: 10,
    pageNumber: 0,
    items: [
        {
            fileType: "FILE",
            path: "/home/user@user.telecity/Screenshot_2018-08-09 SDU-eScience SDUCloud-1.png",
            createdAt: 1535379900000,
            modifiedAt: 1535379900000,
            ownerName: "user@user.telecity",
            size: 3794,
            acl: [],
            favorited: false,
            sensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            creator: "God",
            fileId: "NotG0D",
            ownSensitivityLevel: SensitivityLevelMap.SENSITIVE
        }, {
            fileType: "DIRECTORY",
            path: "/home/user@user.telecity/AABA",
            createdAt: 1535026060000,
            modifiedAt: 1534858191000,
            ownerName: "user@user.telecity",
            size: 1,
            acl: [{entity: "user3@test.dk", rights: ["WRITE", "EXECUTE"], "group": false}],
            favorited: true,
            sensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            creator: "GOD",
            fileId: "notG0D",
            ownSensitivityLevel: SensitivityLevelMap.SENSITIVE
        }, {
            fileType: "DIRECTORY",
            path: "/home/user@user.telecity/Favorites",
            createdAt: 1535026061000,
            modifiedAt: 1534857733000,
            ownerName: "user@user.telecity",
            size: 6,
            acl: [],
            favorited: false,
            sensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            creator: null,
            fileId: "HashingFunctionOfMe",
            ownSensitivityLevel: SensitivityLevelMap.CONFIDENTIAL
        }, {
            fileType: "DIRECTORY",
            path: "/home/user@user.telecity/Jobs",
            createdAt: 1535026061000,
            modifiedAt: 1534836764000,
            ownerName: "user@user.telecity",
            size: 78,
            acl: [],
            favorited: false,
            sensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            creator: null,
            fileId: null,
            ownSensitivityLevel: SensitivityLevelMap.CONFIDENTIAL
        }, {
            fileType: "DIRECTORY",
            path: "/home/user@user.telecity/A folder the sequel",
            createdAt: 1535380572000,
            modifiedAt: 1534836373000,
            ownerName: "user@user.telecity",
            size: 1,
            acl: [],
            favorited: false,
            sensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            creator: null,
            fileId: null,
            ownSensitivityLevel: SensitivityLevelMap.CONFIDENTIAL
        }, {
            fileType: "DIRECTORY",
            path: "/home/user@user.telecity/B",
            createdAt: 1535026060000,
            modifiedAt: 1534244778000,
            ownerName: "user@user.telecity",
            size: 3,
            acl: [],
            favorited: true,
            sensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            creator: null,
            fileId: null,
            ownSensitivityLevel: SensitivityLevelMap.CONFIDENTIAL
        }, {
            fileType: "DIRECTORY",
            path: "/home/user@user.telecity/rehjoerijhoerijh",
            createdAt: 1535026061000,
            modifiedAt: 1534170588000,
            ownerName: "user@user.telecity",
            size: 0,
            acl: [],
            favorited: false,
            sensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            ownSensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            creator: null,
            fileId: null
        }, {
            fileType: "DIRECTORY",
            path: "/home/user@user.telecity/Test",
            createdAt: 1535026061000,
            modifiedAt: 1533630132000,
            ownerName: "user@user.telecity",
            size: 0,
            acl: [],
            favorited: false,
            sensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            ownSensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            creator: null,
            fileId: null
        }, {
            fileType: "DIRECTORY",
            path: "/home/user@user.telecity/somefolder-60D6DAD4-DCF1-415E-B4D6-9BCEF028369E",
            createdAt: 1535026061000,
            modifiedAt: 1533555168000,
            ownerName: "user@user.telecity",
            size: 0,
            acl: [],
            favorited: false,
            sensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            creator: null,
            fileId: null,
            ownSensitivityLevel: SensitivityLevelMap.CONFIDENTIAL
        }, {
            fileType: "DIRECTORY",
            path: "/home/user@user.telecity/somefolder-24ACCA0C-2FAA-4B6C-8F31-7F1E633A54DB",
            createdAt: 1535026061000,
            modifiedAt: 1533554955000,
            ownerName: "user@user.telecity",
            size: 0,
            acl: [],
            favorited: false,
            sensitivityLevel: SensitivityLevelMap.CONFIDENTIAL,
            creator: null,
            fileId: null,
            ownSensitivityLevel: SensitivityLevelMap.CONFIDENTIAL
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
    sensitivityLevel = SensitivityLevelMap.PRIVATE
}): File => ({
    fileType: type as FileType,
    path: `/home/${ownerName}/${path}`,
    createdAt,
    modifiedAt,
    size,
    acl,
    creator: null,
    fileId: null,
    ownSensitivityLevel: sensitivityLevel,
    ownerName,
    favorited,
    sensitivityLevel
});

test("Error silencer", () =>
    expect(1).toBe(1)
);
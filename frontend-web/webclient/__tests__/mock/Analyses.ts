import { Analysis, AppState } from "Applications";
import { Page } from "Types";

export const analyses: Page<Analysis> = {
    itemsInTotal: 4,
    itemsPerPage: 25,
    pageNumber: 0,
    items: [{
        jobId: "bbcf5395-e78e-4fa7-a4e1-989fe5ce21ee",
        owner: "jonas@hinchely.dk",
        state: AppState.FAILURE,
        status: "Internal error",
        appName: "figlet-count",
        appVersion: "1.0.0",
        createdAt: 1535464968479,
        modifiedAt: 1535620309618
    }, {
        jobId: "15274bec-ef72-4ed2-97ff-2be1829a2db1",
        owner: "jonas@hinchely.dk",
        state: AppState.FAILURE,
        status: "Failure in Slurm or non-zero exit code",
        appName: "figlet",
        appVersion: "1.0.0",
        createdAt: 1535546755521,
        modifiedAt: 1535620515159
    }, {
        jobId: "3544b177-e9a7-4323-bcde-c4d6e442dc36",
        owner: "jonas@hinchely.dk",
        state: AppState.FAILURE,
        status: "Failure in Slurm or non-zero exit code",
        appName: "figlet",
        appVersion: "1.0.0",
        createdAt: 1535617409466,
        modifiedAt: 1535620515511
    }, {
        jobId: "e28defb0-d66c-4461-91f6-b09afd85480c",
        owner: "jonas@hinchely.dk",
        state: AppState.SUCCESS,
        status: "OK",
        appName: "figlet-count",
        appVersion: "1.0.0",
        createdAt: 1535986119438,
        modifiedAt: 1535986161981
    }],
    pagesInTotal: 0
};

test("Test silencer", () => {
    expect(true).toBeTruthy();
});
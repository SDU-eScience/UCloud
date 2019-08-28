import {JobState, JobWithStatus} from "../../app/Applications";
import {Page} from "../../app/Types";

export const analyses: Page<JobWithStatus> = {
    itemsInTotal: 4,
    itemsPerPage: 25,
    pageNumber: 0,
    items: [{
        name: "Thomas",
        jobId: "bbcf5395-e78e-4fa7-a4e1-989fe5ce21ee",
        owner: "jonas@hinchely.dk",
        state: JobState.FAILURE,
        status: "Internal error",
        createdAt: 1535464968479,
        modifiedAt: 1535620309618,
        failedState: null,
        expiresAt: null,
        maxTime: null,
        outputFolder: null,
        metadata: {
            authors: [],
            description: "",
            name: "",
            tags: [],
            title: "",
            version: "",
            website: ""
        }
    }, {
        name: "Johnny",
        jobId: "15274bec-ef72-4ed2-97ff-2be1829a2db1",
        owner: "jonas@hinchely.dk",
        failedState: null,
        expiresAt: null,
        maxTime: null,
        outputFolder: null,
        state: JobState.FAILURE,
        status: "Failure in Slurm or non-zero exit code",
        createdAt: 1535546755521,
        modifiedAt: 1535620515159,
        metadata: {
            authors: [],
            description: "",
            name: "",
            tags: [],
            title: "",
            version: "",
            website: ""
        }
    }, {
        name: "Fred",
        jobId: "3544b177-e9a7-4323-bcde-c4d6e442dc36",
        owner: "jonas@hinchely.dk",
        state: JobState.FAILURE,
        failedState: null,
        expiresAt: null,
        maxTime: null,
        outputFolder: null,
        status: "Failure in Slurm or non-zero exit code",
        createdAt: 1535617409466,
        modifiedAt: 1535620515511,
        metadata: {
            authors: [],
            description: "",
            name: "",
            tags: [],
            title: "",
            version: "",
            website: ""
        }
    }, {
        name: "Nathan",
        jobId: "e28defb0-d66c-4461-91f6-b09afd85480c",
        owner: "jonas@hinchely.dk",
        state: JobState.SUCCESS,
        status: "OK",
        failedState: null,
        expiresAt: null,
        maxTime: null,
        outputFolder: null,
        createdAt: 1535986119438,
        modifiedAt: 1535986161981,
        metadata: {
            authors: [],
            description: "",
            name: "",
            tags: [],
            title: "",
            version: "",
            website: ""
        }
    }],
    pagesInTotal: 0
};

test("Test silencer", () => {
    expect(true).toBeTruthy();
});

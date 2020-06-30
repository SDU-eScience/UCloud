import {ParameterTypes, RunsSortBy, JobState} from "../../app/Applications";
import * as AppUtils from "../../app/Utilities/ApplicationUtilities";
import {SortOrder} from "../../app/Files";

test("Create hpcJobQuery string", () => {
    expect(AppUtils.hpcJobQuery("job")).toBe("/hpc/jobs/job");
});

test("toolImageQuery", () => {
    expect(AppUtils.toolImageQuery("tool", "0")).toBe("/hpc/tools/logo/tool?cacheBust=0")
});

describe("hpcJobsQuery", () => {
    test("Empty", () => {
        expect(AppUtils.hpcJobsQuery(25, 0)).toBe("/hpc/jobs/?itemsPerPage=25&page=0");
    });

    test("Full", () => {
        expect(AppUtils.hpcJobsQuery(25, 0, SortOrder.DESCENDING, RunsSortBy.application, 500, 1000, JobState.RUNNING))
            .toBe("/hpc/jobs/?itemsPerPage=25&page=0&order=DESCENDING&sortBy=APPLICATION&minTimestamp=500&maxTimestamp=1000&filter=RUNNING");
    });
});

test("hpcFavoriteApp", () => {
    expect(AppUtils.hpcFavoriteApp("app", "1.2.1")).toBe("/hpc/apps/favorites/app/1.2.1");
});

describe("isFileOrDirectoryParam", () => {
    test("input_file", () => {
        expect(AppUtils.isFileOrDirectoryParam({type: "input_file"})).toBe(true);
    });

    test("input_directory", () => {
        expect(AppUtils.isFileOrDirectoryParam({type: "input_directory"})).toBe(true);
    });

    test("neither", () => {
        expect(AppUtils.isFileOrDirectoryParam({type: "foo_bar"})).toBe(false);
    });
});


describe("Extract parameters", () => {
    test("Extract Parameters for version 1", () => {
        const validParameterTypes = [
            {name: "A", type: ParameterTypes.Boolean},
            {name: "B", type: ParameterTypes.Integer},
            {name: "C", type: ParameterTypes.FloatingPoint},
            {name: "D", type: ParameterTypes.Text}
        ];
        const parameters = {A: "Yes", B: "5", C: "5.0", D: "Pilgrimage"};
        const extractedParameters = AppUtils.findKnownParameterValues({
            nameToValue: parameters,
            allowedParameterKeys: validParameterTypes,
            siteVersion: 1
        });
        expect(parameters).toEqual(extractedParameters);
    });

    test("Extract Parameters for invalid version", () => {
        const validParameterTypes = [
            {name: "A", type: ParameterTypes.Boolean},
            {name: "B", type: ParameterTypes.Integer},
            {name: "C", type: ParameterTypes.FloatingPoint},
            {name: "D", type: ParameterTypes.Text}
        ];
        const parameters = {A: "Yes", B: "5", C: "5.0", D: "Pilgrimage"};
        const extractedParameters = AppUtils.findKnownParameterValues({
            nameToValue: parameters,
            allowedParameterKeys: validParameterTypes,
            siteVersion: -1
        });
        expect(extractedParameters).toEqual({});
    });

    test("Extract Parameters with invalid parameter that is ignored", () => {
        const validParameterTypes = [
            {name: "A", type: ParameterTypes.Boolean},
            {name: "B", type: ParameterTypes.Integer},
            {name: "C", type: ParameterTypes.FloatingPoint}
        ];
        const parameters = {A: "Yes", B: "5", C: "5.0", D: "Pilgrimage"};
        const extractedParameters = AppUtils.findKnownParameterValues({
            nameToValue: parameters,
            allowedParameterKeys: validParameterTypes,
            siteVersion: 1
        });
        expect(extractedParameters).toEqual({A: "Yes", B: "5", C: "5.0"});
    });

    test("Extract Parameters with parameter that is not imported", () => {
        const validParameterTypes = [
            {name: "A", type: ParameterTypes.Boolean},
            {name: "B", type: ParameterTypes.Integer},
            {name: "C", type: ParameterTypes.FloatingPoint},
            {name: "D", type: ParameterTypes.Text}
        ];
        const parameters = {A: "Yes", B: "5", C: "5.0"};
        const extractedParameters = AppUtils.findKnownParameterValues({
            nameToValue: parameters,
            allowedParameterKeys: validParameterTypes,
            siteVersion: 1
        });
        expect(extractedParameters).toEqual(parameters);
    });

    test("Extract input file and input directory parameters", () => {
        const validParameterTypes = [
            {name: "A", type: ParameterTypes.InputFile},
            {name: "B", type: ParameterTypes.InputDirectory},
        ];
        const parameters = {
            A: "A",
            B: "B"
        };
        const extractedParameters = AppUtils.findKnownParameterValues({
            nameToValue: parameters,
            allowedParameterKeys: validParameterTypes,
            siteVersion: 1
        });
        expect(extractedParameters).toEqual(parameters);
    });

    test("Extract invalid input file and input directory parameters", () => {
        const validParameterTypes = [
            {name: "A", type: ParameterTypes.InputFile},
            {name: "B", type: ParameterTypes.InputDirectory},
        ];
        const parameters = {
            A: "A",
            B: "B"
        };
        const extractedParameters = AppUtils.findKnownParameterValues({
            nameToValue: parameters,
            allowedParameterKeys: validParameterTypes,
            siteVersion: 1
        });
        expect(extractedParameters).toEqual({A: "A", B: "B"});
    });
});

import {ParameterTypes} from "Applications";
import "jest-styled-components";
import {
    findKnownParameterValues,
    hpcApplicationsQuery,
    hpcJobQuery,
} from "Utilities/ApplicationUtilities";

describe("Application Utilities", () => {
    test("Create hpcJobQuery string", () => {
        expect(hpcJobQuery("job")).toBe("/hpc/jobs/job");
    });

    test("Create hpcApplicationsQuery string", () => {
        expect(hpcApplicationsQuery(0, 10)).toBe(`/hpc/apps?page=0&itemsPerPage=10`)
    });

    test("Extract Parameters for version 1", () => {
        const validParameterTypes = [
            {name: "A", type: ParameterTypes.Boolean},
            {name: "B", type: ParameterTypes.Integer},
            {name: "C", type: ParameterTypes.FloatingPoint},
            {name: "D", type: ParameterTypes.Text}
        ];
        const parameters = {A: "Yes", B: "5", C: "5.0", D: "Pilgrimage"};
        const extractedParameters = findKnownParameterValues({
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
        const extractedParameters = findKnownParameterValues({
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
        const extractedParameters = findKnownParameterValues({
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
        const extractedParameters = findKnownParameterValues({
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
        const extractedParameters = findKnownParameterValues({
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
        const extractedParameters = findKnownParameterValues({
            nameToValue: parameters,
            allowedParameterKeys: validParameterTypes,
            siteVersion: 1
        });
        expect(extractedParameters).toEqual({A: "A", B: "B"});
    });
});


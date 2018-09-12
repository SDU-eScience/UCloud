import { extractParameters, hpcJobQuery, hpcJobsQuery, hpcApplicationsQuery, favoriteApplicationFromPage } from "Utilities/ApplicationUtilities";
import { applicationsPage } from "../mock/Applications";
import Cloud from "Authentication/lib";
import { ParameterTypes } from "Applications";


describe("Application Utilities", () => {
    test("Create hpcJobQuery string", () => {
        expect(
            hpcJobQuery("job", 10, 10, 100, 100)
        ).toBe(
            "hpc/jobs/follow/job?stdoutLineStart=10&stdoutMaxLines=100&stderrLineStart=10&stderrMaxLines=100"
        )
    });

    test("Create hpcJobQuery string, with default values", () => {
        expect(
            hpcJobQuery("job", 10, 10)
        ).toBe(
            "hpc/jobs/follow/job?stdoutLineStart=10&stdoutMaxLines=1000&stderrLineStart=10&stderrMaxLines=1000"
        )
    });

    test("Create hpcJobsQuery string", () => {
        expect(hpcJobsQuery(10, 0)).toBe("/hpc/jobs/?itemsPerPage=10&page=0");
    });

    test("Create hpcApplicationsQuery string", () => {
        expect(hpcApplicationsQuery(0, 10)).toBe(`/hpc/apps?page=0&itemsPerPage=10`)
    });

    test("Favorite application and unfavorite application", () => {
        let resultingPage = favoriteApplicationFromPage(applicationsPage.items[0], applicationsPage, new Cloud());
        expect(resultingPage.items[0].favorite).toBe(true);
        resultingPage = favoriteApplicationFromPage(resultingPage.items[0], resultingPage, new Cloud());
        expect(resultingPage.items[0].favorite).toBe(false);
    });

    test("Extract Parameters for version 1", () => {
        const validParameterTypes = [
            { name: "A", type: ParameterTypes.Boolean },
            { name: "B", type: ParameterTypes.Integer },
            { name: "C", type: ParameterTypes.FloatingPoint },
            { name: "D", type: ParameterTypes.Text }
        ];
        const parameters = { A: true, B: 5, C: 5.0, D: "Pilgrimage" };
        const extractedParameters = extractParameters(parameters, validParameterTypes, 1);
        expect(parameters).toEqual(extractedParameters);
    });

    test("Extract Parameters for invalid version", () => {
        const validParameterTypes = [
            { name: "A", type: ParameterTypes.Boolean },
            { name: "B", type: ParameterTypes.Integer },
            { name: "C", type: ParameterTypes.FloatingPoint },
            { name: "D", type: ParameterTypes.Text }
        ];
        const parameters = { A: true, B: 5, C: 5.0, D: "Pilgrimage" };
        const extractedParameters = extractParameters(parameters, validParameterTypes, -1);
        expect(extractedParameters).toEqual({});
    });

    test("Extract Parameters with invalid parameter that is ignored", () => {
        const validParameterTypes = [
            { name: "A", type: ParameterTypes.Boolean },
            { name: "B", type: ParameterTypes.Integer },
            { name: "C", type: ParameterTypes.FloatingPoint }
        ];
        const parameters = { A: true, B: 5, C: 5.0, D: "Pilgrimage" };
        const extractedParameters = extractParameters(parameters, validParameterTypes, 1);
        expect(extractedParameters).toEqual({ A: true, B: 5, C: 5.0 });
    });

    test("Extract Parameters with parameter that is not imported", () => {
        const validParameterTypes = [
            { name: "A", type: ParameterTypes.Boolean },
            { name: "B", type: ParameterTypes.Integer },
            { name: "C", type: ParameterTypes.FloatingPoint },
            { name: "D", type: ParameterTypes.Text }
        ];
        const parameters = { A: true, B: 5, C: 5.0 };
        const extractedParameters = extractParameters(parameters, validParameterTypes, 1);
        expect(extractedParameters).toEqual(parameters);
    });

    test("Extract Parameters with parameter that has wrong type", () => {
        const validParameterTypes = [
            { name: "A", type: ParameterTypes.Boolean },
            { name: "B", type: ParameterTypes.Integer },
            { name: "C", type: ParameterTypes.FloatingPoint },
            { name: "D", type: ParameterTypes.Text }
        ];
        const parameters = { A: true, B: 5, C: "5.0" };
        const extractedParameters = extractParameters(parameters, validParameterTypes, 1);
        expect(extractedParameters).toEqual({ A: true, B: 5 });
    });

    test("Extract input file and input directory parameters", () => {
        const validParameterTypes = [
            { name: "A", type: ParameterTypes.InputFile },
            { name: "B", type: ParameterTypes.InputDirectory },
        ];
        const parameters = {
            A: { destination: "A", source: "B" },
            B: { destination: "A", source: "B" }
        };
        const extractedParameters = extractParameters(parameters, validParameterTypes, 1);
        expect(extractedParameters).toEqual(parameters);
    });

    test("Extract invalid input file and input directory parameters", () => {
        const validParameterTypes = [
            { name: "A", type: ParameterTypes.InputFile },
            { name: "B", type: ParameterTypes.InputDirectory },
        ];
        const parameters = {
            A: { destination: 5, source: "B" },
            B: 5
        };
        const extractedParameters = extractParameters(parameters, validParameterTypes, 1);
        expect(extractedParameters).toEqual({});
    });

    test("Trying to favorite application not in page", () => {
        expect(applicationsPage.items.every(it => it.favorite === false)).toBe(true);
        const newPage = favoriteApplicationFromPage({
            favorite: false,
            owner: "jonas@hinchely.dk", createdAt: 1531305600165, modifiedAt: 1531305600165, description: {
                info: { name: "No App", version: "1.0.0" },
                tool: { name: "figlet", version: "1.0.0" },
                authors: ["Dan Sebastian Thrane <dthrane@imada.sdu.dk>"],
                title: "Figlet Counter",
                description: "Count with Figlet!\n",
                invocation: [{ "type": "word", "word": "figlet-count" }, { "type": "var", "variableNames": ["n"], "prefixGlobal": "", "suffixGlobal": "", "prefixVariable": "", "suffixVariable": "", "variableSeparator": " " }],
                parameters: [{ name: "n", optional: false, defaultValue: 100, title: "Count", description: "How much should we count to?", min: null, "max": null, "step": null, "unitName": null, "type": "integer" }], "outputFileGlobs": ["stdout.txt", "stderr.txt"]
            },
            tool: {
                owner: "jonas@hinchely.dk",
                createdAt: 1531303905548,
                modifiedAt: 1531303905548,
                description: {
                    info: { name: "No App", version: "1.0.0" },
                    container: "figlet.simg",
                    defaultNumberOfNodes: 1,
                    defaultTasksPerNode: 1,
                    defaultMaxTime: { hours: 0, minutes: 1, seconds: 0 },
                    requiredModules: [],
                    authors: ["Dan Sebastian Thrane <dthrane@imada.sdu.dk>"],
                    title: "Figlet",
                    description: "Tool for rendering text.", "backend": "SINGULARITY"
                }
            }
        }, applicationsPage, new Cloud());
        expect(applicationsPage.items.every(it => it.favorite === false)).toBe(true);
    });
});


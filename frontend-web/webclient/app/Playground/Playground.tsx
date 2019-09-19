import * as React from "react";

import {ApplicationType, FullAppInfo, ParameterTypes} from "Applications";
import RangeParameter from "Applications/Widgets/RangeParameters";
import {MainContainer} from "MainContainer/MainContainer";

export default function Playground() {
    const ref = React.useRef<HTMLInputElement>(null);
    const [first] = app.invocation.parameters;
    return (<MainContainer
        main={<RangeParameter
            parameter={first as any}
            application={app}
            parameterRef={ref}
            initialSubmit={false}
        />}
    />);
}

const app: FullAppInfo = {
    metadata: {
        name: "figlet-counter",
        version: "1.1.1",
        authors: [
            "Dan Sebastian Thrane <dthrane@imada.sdu.dk>"
        ],
        title: "Figlet Count",
        description: "Render some text!\n",
        tags: []
    },
    invocation: {
        tool: {
            name: "figlet-color",
            version: "1.1.1",
            tool: {
                owner: "dthrane@imada.sdu.dk",
                createdAt: 1566991582029,
                modifiedAt: 1566991582029,
                description: {
                    info: {
                        name: "figlet-color",
                        version: "1.1.1"
                    },
                    container: "truek/figlets:1.0.1",
                    defaultNumberOfNodes: 1,
                    defaultTasksPerNode: 1,
                    defaultTimeAllocation: {
                        hours: 0,
                        minutes: 1,
                        seconds: 0
                    },
                    requiredModules: [],
                    authors: [
                        "Dan Sebastian Thrane <dthrane@imada.sdu.dk>"
                    ],
                    title: "Figlet",
                    description: "Tool for rendering text.",
                    backend: "DOCKER",
                    license: ""
                }
            }
        },
        invocation: [
            {
                type: "word",
                word: "figlet-count"
            },
            {
                type: "var",
                variableNames: [
                    "count"
                ],
                prefixGlobal: "",
                suffixGlobal: "",
                prefixVariable: "",
                suffixVariable: "",
                variableSeparator: ","
            }
        ],
        parameters: [
            {
                name: "count",
                optional: false,
                defaultValue: {min: 15, max: 50},
                title: "What should we count to?",
                description: "",
                min: 0,
                max: 200,
                unitName: null,
                type: ParameterTypes.Range
            }
        ],
        outputFileGlobs: [
            "stdout.txt",
            "stderr.txt"
        ],
        applicationType: ApplicationType.BATCH,
        allowMultiNode: false,
        shouldAllowAdditionalMounts: false,
        shouldAllowAdditionalPeers: false
    },
    favorite: true,
    tags: []
}
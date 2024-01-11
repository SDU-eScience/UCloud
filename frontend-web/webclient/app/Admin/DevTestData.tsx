import * as React from "react";
import MainContainer from "@/ui-components/MainContainer";
import {Box, Button, Flex, Input} from "@/ui-components";
import {useCallback} from "react";
import {apiUpdate, callAPI} from "@/Authentication/DataHook";
import {bulkRequestOf} from "@/UtilityFunctions";
import {Client} from "@/Authentication/HttpClientInstance";

interface TestScript {
    name: string;
    description: string;
    args: {name: string; placeholder: string; type?: string;}[];
    onInvocation: (args: string[]) => void;
}

const scripts: TestScript[] = [
    {
        name: "Accounting Charge",
        description: "Performs a single charge transaction",
        args: [
            {name: "owner", placeholder: "Username of the owner"},
            {name: "product", placeholder: "Product name"},
            {name: "category", placeholder: "Product category"},
            {name: "provider", placeholder: "Provider"},
            {name: "units", placeholder: "Units"},
            {name: "periods", placeholder: "Periods"},
        ],
        onInvocation: ([owner, product, category, provider, units, periods]) => {
            callAPI(apiUpdate(bulkRequestOf({
                payer: {
                    type: "user",
                    username: owner,
                },
                units: parseInt(units),
                periods: parseInt(periods),
                product: {id: product, category, provider},
                performedBy: Client.username,
                description: "A test charge",

            }), "/api/accounting", "charge"))
        }
    }
];

const DevTestData: React.FunctionComponent = () => {
    return <MainContainer
        main={
            <Box>
                {scripts.map(it => <TestScriptComponent key={it.name} {...it} />)}
            </Box>
        }
    />
};

const TestScriptComponent: React.FunctionComponent<TestScript> = script => {
    const onSubmit = useCallback((e: React.SyntheticEvent) => {
        e.preventDefault();
        const values = script.args.map(it =>
            document.querySelector<HTMLInputElement>(`#${script.name.replace(" ", "-")}-${it.name.replace(" ", "-")}`)!.value
        );

        script.onInvocation(values);
    }, [script]);

    return <Box>
        <Flex>
            <b>{script.name}</b>
            <form onSubmit={onSubmit}>
                {script.args.map(arg =>
                    <Input
                        type={arg.type}
                        id={script.name.replace(" ", "-") + "-" + arg.name.replace(" ", "-")}
                        placeholder={arg.placeholder}
                        key={arg.name}
                    />
                )}
                <Button type={"submit"}>Run</Button>
            </form>
        </Flex>
        <div>{script.description}</div>
    </Box>;
};

export default DevTestData;

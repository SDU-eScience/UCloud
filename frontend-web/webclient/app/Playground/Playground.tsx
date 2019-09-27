import {Cloud} from "Authentication/SDUCloudObject";
import {MainContainer} from "MainContainer/MainContainer";
import * as React from "react";
import Button from "ui-components/Button";

export const Playground: React.FunctionComponent = props => {
    return <MainContainer
        main={
            <>
                <Button
                    onClick={async () => {
                        const promises = [] as Array<Promise<any>>;
                        for (let i = 0; i < 512; i++) {
                            promises.push(Cloud.post("/rpc/test/b/suspendingProcessing"));
                            if (i % 128 === 0) {
                                await Promise.all(promises);
                                promises.length = 0;
                            }
                        }
                    }}
                >
                    Client Test
                </Button>

                <Button
                    onClick={() => {
                        Cloud.post("/files/testTask");
                    }}
                >
                    Task Test
                </Button>
            </>
        }
    />;
};

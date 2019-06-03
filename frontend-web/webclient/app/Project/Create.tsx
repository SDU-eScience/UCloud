import * as React from "react";
import {MainContainer} from "MainContainer/MainContainer";
import ContainerForText from "ui-components/ContainerForText";
import Input from "ui-components/Input";
import Box from "ui-components/Box";
import Label from "ui-components/Label";
import Button from "ui-components/Button";
import {useAsyncCommand} from "Authentication/DataHook";
import {createShare} from "Project/index";
import {useRef} from "react";
import {Cloud} from "Authentication/SDUCloudObject";

const Create: React.FunctionComponent = props => {
    const [loading, invokeCommand] = useAsyncCommand();
    const title = useRef<HTMLInputElement>(null);

    const doCreateShare = async e => {
        e.preventDefault();
        if (loading) return;

        // TODO FIXME This will only work for admin accounts!!!
        await invokeCommand(createShare({
            title: title.current!.value,
            principalInvestigator: Cloud.username!
        }));

        // TODO Do something when command returns correctly
        // TODO We need to know if this was successful
    };

    return <MainContainer
        headerSize={0}
        header={null}
        main={
            <ContainerForText>
                <form onSubmit={e => doCreateShare(e)}>
                    <Box>
                        <Label htmlFor={"projectName"}>Title</Label>
                        <Input ref={title} id={"projectName"}/>
                    </Box>

                    <Box mt={16}>
                        <Button
                            color={"green"}
                            type={"submit"}
                            fullWidth
                            disabled={loading}
                        >Submit</Button>
                    </Box>
                </form>
            </ContainerForText>
        }
    />;
};

export default Create;

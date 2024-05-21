import * as React from "react";
import * as UCloud from "@/UCloud"
import {
    Box,
    Button,
    Card,
    Flex,
} from "@/ui-components";
import Warning from "@/ui-components/Warning";
import {Widget} from "@/Applications/Jobs/Widgets";
import * as Heading from "@/ui-components/Heading";
import BaseLink from "@/ui-components/BaseLink";
import {Application, ApplicationParameter} from "@/Applications/AppStoreApi";

export function ingressResourceAllowed(app: Application): boolean {
    return !(app.invocation.allowPublicLink === false || app.invocation.applicationType !== "WEB")
}

export const IngressResource: React.FunctionComponent<{
    application: Application;
    params: ApplicationParameter[];
    errors: Record<string, string>;
    onAdd: () => void;
    onRemove: (id: string) => void;
    provider?: string;
    setErrors: (errors: Record<string, string>) => void;
}> = ({application, params, errors, onAdd, onRemove, provider, setErrors}) => {
    if (!ingressResourceAllowed(application)) return null;

    return <Card>
        <Box>
            <Flex alignItems="center">
                <Box flexGrow={1}>
                    <Heading.h4>Configure custom links to your application</Heading.h4>
                </Box>

                <Button type={"button"} ml={"5px"} lineHeight={"16px"} onClick={onAdd}>Add public link</Button>
            </Flex>

            <Box my={8}>
                {params.length !== 0 ?
                    <Box mb="6px">
                        <Warning>
                            By enabling this setting, anyone with a link can gain access to the application
                        </Warning>
                    </Box> :
                    <>
                        If your job needs to be publicly accessible via a web-browser then click{" "}
                        <BaseLink
                            href="#"
                            onClick={e => {
                                e.preventDefault();
                                onAdd();
                            }}
                        >
                            &quot;Add public link&quot;
                        </BaseLink>
                        {" "}
                        to select the correct link.
                    </>
                }
            </Box>

            {params.map(entry => (
                <Box key={entry.name} mb={"7px"}>
                    <Widget
                        provider={provider}
                        parameter={entry}
                        errors={errors}
                        setErrors={setErrors}
                        onRemove={() => {
                            onRemove(entry.name);
                        }}
                    />
                </Box>
            ))}
        </Box>
    </Card>;
}

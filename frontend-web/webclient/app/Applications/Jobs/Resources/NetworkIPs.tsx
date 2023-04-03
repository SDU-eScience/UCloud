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
import {compute} from "@/UCloud";
import ApplicationParameter = compute.ApplicationParameter;
import * as Heading from "@/ui-components/Heading";
import BaseLink from "@/ui-components/BaseLink";

export function networkIPResourceAllowed(app: UCloud.compute.Application): boolean {
    return app.invocation.allowPublicIp;
}

export const NetworkIPResource: React.FunctionComponent<{
    application: UCloud.compute.Application;
    params: ApplicationParameter[];
    errors: Record<string, string>;
    setErrors: (errors: Record<string, string>) => void;
    onAdd: () => void;
    onRemove: (id: string) => void;
    provider?: string;
}> = ({application, params, errors, onAdd, onRemove, provider, setErrors}) => {
    if (!networkIPResourceAllowed(application)) return null;

    return <Card>
        <Box>
            <Flex alignItems="center">
                <Box flexGrow={1}>
                    <Heading.h4>Attach public IP addresses to your application</Heading.h4>
                </Box>

                <Button type={"button"} ml={"5px"} lineHeight={"16px"} onClick={onAdd}>Add public IP</Button>
            </Flex>

            <Box my={8}>
                {params.length !== 0 ?
                    <Box mb="6px">
                        <Warning>
                            By enabling this setting, anyone with the IP can contact your application. <i>You</i> must take
                            action to ensure that your application is properly secured.
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
                            &quot;Add public IP&quot;
                        </BaseLink>
                        {" "}
                        to select the correct address.
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

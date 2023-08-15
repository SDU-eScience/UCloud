import * as React from "react";
import {useLocation} from "react-router";
import {buildQueryString, getQueryParam} from "@/Utilities/URIUtilities";
import {NonAuthenticatedHeader} from "@/Navigation/Header";
import {Box, Button, Input, Label} from "@/ui-components";
import {Client} from "@/Authentication/HttpClientInstance";
import {PropsWithChildren, useEffect, useRef, useState} from "react";
import {apiRetrieve, useCloudAPI} from "@/Authentication/DataHook";
import styled from "styled-components";
import {TextSpan} from "@/ui-components/Text";
import {Feature, hasFeature} from "@/Features";
import {injectStyle} from "@/Unstyled";

interface Registration {
    sessionId: string;
    firstNames?: string | null;
    lastName?: string | null;
    email?: string | null;
    organizationFullName?: string | null;
    department?: string | null;
    researchField?: string | null;
    position?: string | null;
}

const Registration: React.FunctionComponent = props => {
    const firstNames = useRef<HTMLInputElement>(null);
    const lastName = useRef<HTMLInputElement>(null);
    const email = useRef<HTMLInputElement>(null);
    const organizationFullName = useRef<HTMLInputElement>(null);
    const department = useRef<HTMLInputElement>(null);
    const researchField = useRef<HTMLInputElement>(null);
    const position = useRef<HTMLInputElement>(null);

    const [showResendButton, setShowResendButton] = useState(false);

    const location = useLocation();
    const sessionId = getQueryParam(location.search, "sessionId");
    let message = getQueryParam(location.search, "message");
    let isErrorHint = getQueryParam(location.search, "errorHint") === "true";

    const [registration] = useCloudAPI<Registration>(
        {...apiRetrieve({id: sessionId}, "/auth/registration"), unauthenticated: true},
        {sessionId: sessionId ?? ""}
    );

    if (!sessionId && !message || registration.error) {
        message = "Invalid link. Please try to login again.";
        isErrorHint = true;
    }

    useEffect(() => {
        firstNames.current!.value = registration.data.firstNames ?? "";
        lastName.current!.value = registration.data.lastName ?? "";
        email.current!.value = registration.data.email ?? "";

        if (hasFeature(Feature.ADDITIONAL_USER_INFO)) {
            organizationFullName.current!.value = registration.data.organizationFullName ?? "";
            department.current!.value = registration.data.department ?? "";
            researchField.current!.value = registration.data.researchField ?? "";
            position.current!.value = registration.data.position ?? "";
        }

        if (registration.data.email) setShowResendButton(true);
    }, [registration]);

    if (Client.isLoggedIn) {
        window.location.href = "/";
        return null;
    }

    return <>
        <NonAuthenticatedHeader />
        <Box mb="72px" />
        <Box m={[0, 0, "15px"]}>
            <div className={Styling}>
                {!message ? null : <InfoBox isError={isErrorHint}>{message}</InfoBox>}
                <form action={"/auth/registration/complete"} method={"post"}>
                    <input type={"hidden"} value={sessionId ?? ""} name={"sessionId"} />

                    <h3>Mandatory fields</h3>
                    <Label>
                        First name(s) <MandatoryField />
                        <Input inputRef={firstNames} name={"firstNames"} required placeholder={"Example: Jane"} />
                    </Label>

                    <Label>
                        Last name <MandatoryField />
                        <Input inputRef={lastName} name={"lastName"} required placeholder={"Example: Doe"} />
                    </Label>

                    <Label>
                        Email <MandatoryField />
                        <Input inputRef={email} name={"email"} required type={"email"}
                            placeholder={"Example: jane@example.com"} />
                        {!showResendButton ?
                            null :
                            <a href={buildQueryString("/auth/registration/reverify", {id: sessionId})}>
                                Send new verification email
                            </a>
                        }
                    </Label>

                    {!hasFeature(Feature.ADDITIONAL_USER_INFO) ? null : <>
                        <br />
                        <h3>Optional fields</h3>

                        <Label>
                            Full name of organization
                            <Input inputRef={organizationFullName} name={"organizationFullName"} placeholder={"Example: University of Example"} />
                        </Label>

                        <Label>
                            Department
                            <Input inputRef={department} name={"department"} placeholder={"Example: Department of Examples"} />
                        </Label>

                        <Label>
                            Position
                            <Input inputRef={position} name={"position"} placeholder={"Example: Professor"} />
                        </Label>

                        <Label>
                            Research field(s)
                            <Input inputRef={researchField} name={"researchField"} placeholder={"Example: Experimental examples"} />
                        </Label>
                    </>}

                    <Button type={"submit"}>Finish registration</Button>
                </form>
            </div>
        </Box>
    </>;
};

const Styling = injectStyle("styling", k => `
  ${k} .info-box {
    color: white;
    margin-bottom: 32px;
    padding: 16px;
    border-radius: 8px;
    width: 100%;
    text-align: center;
  }

  ${k} form {
    max-width: 600px;
    width: 100%;
    margin: 0 auto;
  }

  ${k} label, ${k} button {
    margin-top: 16px;
  }

  ${k} button {
    width: 100%;
  }
`);

const InfoBox: React.FunctionComponent<PropsWithChildren<{isError: boolean}>> = ({isError, children}) => {
    return <div className={"info-box"} style={{background: `var(--${isError ? "red" : "blue"})`, color: "white", marginBottom: "16px"}}>
        {children}
    </div>;
};

const MandatoryField: React.FunctionComponent = () => <TextSpan ml="4px" bold color="red">*</TextSpan>;

export default Registration;

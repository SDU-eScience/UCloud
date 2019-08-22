import styled from "styled-components";
import Button from "./Button";
import Flex from "./Flex";
import OutlineButton from "./OutlineButton";

const VerticalButtonGroup = styled(Flex)`
    flex-direction: column;

    //leave some space on top if buttons grow on hover
    margin-top: 4px;

    & ${Button}, & ${OutlineButton} {
        width: 100%;
        margin-bottom: 8px;
    }
`;

VerticalButtonGroup.displayName = "VerticalButtonGroup";

export default VerticalButtonGroup;

import styled from "styled-components";
import Button from "./Button";
import Flex from "./Flex";

const ButtonGroup = styled(Flex)`
    & > ${Button} {
        height: 35px;
        width: 100%;
        padding: 0 0 0 0;
        padding-left: 0px;
        padding-right: 0px;
        border-radius: 0px;
    }

    & > ${Button}:last-child {
        border-top-right-radius: 3px;
        border-bottom-right-radius: 3px;
    }

    & > ${Button}:first-child {
        border-top-left-radius: 3px;
        border-bottom-left-radius: 3px;
    }
`;

ButtonGroup.displayName = "ButtonGroup";

export default ButtonGroup;
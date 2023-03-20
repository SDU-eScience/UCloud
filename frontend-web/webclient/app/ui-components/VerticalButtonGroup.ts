import styled from "styled-components";
import Button, {ButtonClass} from "./Button";
import Flex from "./Flex";

const VerticalButtonGroup = styled(Flex)`
    height: 98%;
    flex-direction: column;

    //leave some space on top if buttons grow on hover
    margin-top: 4px;

    & .${ButtonClass} {
        width: 100%;
        margin-bottom: 8px;
    }
`;

VerticalButtonGroup.displayName = "VerticalButtonGroup";

export default VerticalButtonGroup;

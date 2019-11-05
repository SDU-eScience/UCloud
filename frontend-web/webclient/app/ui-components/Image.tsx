import styled from "styled-components";
import {space, SpaceProps} from "styled-system";

const Image = styled.img<SpaceProps>`
  max-width: 100%;
  height: auto;
  ${space}
`;

Image.displayName = "Image";

export default Image;

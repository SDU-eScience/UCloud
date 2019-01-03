import { Link as ReactRouterLink } from "react-router-dom";
import BaseLink from "./BaseLink";

const Link = BaseLink.withComponent(ReactRouterLink);
Link.defaultProps = {
  color: "text",
  hoverColor: "textHighlight"
};


export default Link;

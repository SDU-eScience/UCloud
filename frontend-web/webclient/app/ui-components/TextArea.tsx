import Input, {InputProps} from "./Input";
import * as React from "react";

export const TextArea: React.FunctionComponent<InputProps> = props => {
    return <Input {...props} as={"textarea"} />;
}

TextArea.displayName = "TextArea";

export default TextArea;

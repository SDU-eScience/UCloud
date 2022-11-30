import * as React from "react";

/* Note(Jonas): 
    Only intended for `dev` use.
    Used to see if # of renders are an issue, when making changes to a component.
*/
export function useCountRenders(label: string): number {
    const renders = React.useRef(0);

    renders.current++;
    console.log(label + ", render #%i", renders.current);

    React.useEffect(() => {
        renders.current = 0;
        return () => {renders.current = 0;}
    }, []);
    return renders.current;
}
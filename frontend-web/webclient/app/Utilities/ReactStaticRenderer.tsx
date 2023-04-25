import * as React from "react";
import {createRoot} from "react-dom/client";
import {useLayoutEffect, useRef} from "react";

export class ReactStaticRenderer {
    private fragment = document.createDocumentFragment();

    constructor(node: () => React.ReactElement) {
        const fragment = document.createDocumentFragment();
        const root = createRoot(fragment);

        const promise = new Promise<void>((resolve, reject) => {
            const Component: React.FunctionComponent<{ children: React.ReactNode }> = props => {
                const div = useRef<HTMLDivElement | null>(null);
                useLayoutEffect(() => {
                    resolve();
                }, []);

                return <div ref={div}>{props.children}</div>;
            };

            root.render(<Component>{node()}</Component>);
        });

        promise.finally(() => {
            this.fragment.append(fragment.cloneNode(true));
            root.unmount();
        });
    }

    clone(): Node {
        return this.fragment.cloneNode(true);
    }
}

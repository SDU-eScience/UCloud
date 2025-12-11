import {Input, Label, Select, TextArea} from "@/ui-components";
import * as React from "react";

function Add() {
    return <div>
        <div>
            Metadata
            <Label>
                Title
                <Input placeholder="Title..." />
            </Label>
            <Label>
                Description
                <TextArea placeholder="Description..." />
            </Label>
        </div>
        <div>
            Service provider
            <Label>
                Service provider
                <Select>
                    {options.map(it => <option>
                        {it.key}
                    </option>)}
                </Select>
            </Label>
        </div>
    </div>;
}

const options: {key: string; value: string | null}[] = []
for (let i = 0; i < 10; i += 1) {
    options.push(keyValue("key" + i, "value" + i));
}

function keyValue(key: string, value: string): {key: string; value: string | null} {
    return {key, value};
}

export default Add;
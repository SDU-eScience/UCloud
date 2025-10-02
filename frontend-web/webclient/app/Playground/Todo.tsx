import * as React from "react";
import {ChangeEvent, useCallback, useEffect, useState} from "react";

/* TODO Make a todo type that can store information about id (number), task(string we get from formState) and completed/checked state(boolean)*/
type Todo = {
  id: number;
  task: string;
  isCompleted: boolean;
};

/* TODO Make state with handlers for deleting, completing/checking, editing etc.*/
// Deleting TODOs
const handleDelete = (id: number) => {

};

// Toggle completed TODOs on/off
const handleToggleCompleted = (event) => {

};

// Editing TODOs
const handleTaskEdit = (event) => {

};

function useFormState(initialState: Record<string, string> = {}): [Record<string, string>, (name: string) => any] {
    const [formState, setFormState] = useState<Record<string, string>>(initialState);
    const onInputUpdate = useCallback((e: React.SyntheticEvent) => {
        const elem = e.target as HTMLInputElement;
        setFormState(prev => {
            return {...prev, [elem.name]: elem.value};
        });
    }, []);

    const attributeGetter = useCallback((name: string) => {
        return {
            name,
            value: formState[name] ?? "",
            onChange: onInputUpdate
        };
    }, [formState]);

    return [formState, attributeGetter];
}

export const MyTodo: React.FunctionComponent = () => {
    const [formState, registerField] = useFormState({});

    const todoAdd = useCallback((e: React.SyntheticEvent) => {
        e.preventDefault();
        console.log("1", formState);
    }, [formState]);

    /*TODO Make a todo from the fromState here?*/
    const todo: Todo = {
        id: 73,
        task: "",
        isCompleted: false
    };


    console.log("2", formState);

    return <div>
        <h3>My TODO here</h3>
        <form onSubmit={todoAdd}>
            <input type="text" placeholder="placeholder text" {...registerField("task")} />
        </form>
        <ul>
            {/*TODO Insert code in the object below to display todo tasks as list entries*/}
            {}
        </ul>
    </div>;
};
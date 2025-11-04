import * as React from "react";
import {ChangeEvent, useCallback, useEffect, useState} from "react";
import {Button, Checkbox} from "@/ui-components";
import {injectStyle} from "@/Unstyled";
import {stopPropagation} from "@/UtilityFunctions";

function useFormState(initialState: Record<string, string> = {}): [Record<string, string>, (name: string) => any, (name: string, value: string) => void] {
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

    const attributeSetter = useCallback((name: string, value: string) => {
        setFormState(prev => {
            return {...prev, [name]: value};
        })
    }, [setFormState]);

    return [formState, attributeGetter, attributeSetter];
}

type Todo = {
    id: number,
    task: string,
    isCompleted: boolean
};

let nextId = 0;

export const TodoList: React.FunctionComponent = () => {

    const [formState, registerField, setFieldValue] = useFormState({});

    const [todos, setTodos] = useState<Todo[]>([]);

    const addTodo = useCallback((event: React.SyntheticEvent) => {
        event.preventDefault()
        setTodos([
            ...todos,
            {
                id: nextId++,
                task: formState["task"],
                isCompleted: false
            }
        ]);
        setFieldValue("task", "");
    }, [todos, formState]);

    const handleDelete = useCallback((id: number) => {
        setTodos(todos.filter((todo) => id != todo.id));
    }, [todos, setTodos]);

    const handleChecked = useCallback((id: number)=> {
        setTodos(prev =>
            prev.map(todo => todo.id === id ? { ...todo, isCompleted: !todo.isCompleted } : todo
            )
        );
    }, [setTodos]);

    const [editing, setEditing] = useState<number>(-1);

    const handleActiveEditing = useCallback((id: number) => {
        setEditing(id)
    }, [setEditing]);

    const handleEditingCompleted = useCallback((id: number) => {
        setTodos(prev =>
            prev.map(todo => todo.id === id ? { ...todo, task: formState.edit } : todo
            )
        );
        setEditing(-1);
    }, [setTodos, setEditing]);

    const todoStyle = injectStyle("todo-style", k => `
    ${k} ul {
        display: block;
    }
    
    ${k} li {
        list-style: none;
    }
    
    ${k} .delete-button {
        background: red;
    }
`)
    return <>
        <div className={todoStyle}>
            <form  onSubmit={addTodo}>
                <h3>TODO list</h3>
                <input type="text" placeholder="Type your TODO here" {...registerField("task")}/>
            </form>
            <ul>
                {todos.map(todo =>
                    <li key={todo.id}>
                        {editing === todo.id ? <>
                            <Checkbox
                                checked={todo.isCompleted}
                                handleWrapperClick={() => handleChecked(todo.id)}
                                onChange={stopPropagation}/>
                            {todo.task}
                            <Button onClick={() => handleActiveEditing}>Edit todo</Button>
                            <Button
                                className="delete-button"
                                onClick={() => handleDelete(todo.id)}>
                                Delete todo
                            </Button>
                        </> : <>
                            <Checkbox
                                checked={todo.isCompleted}
                                handleWrapperClick={() => handleChecked(todo.id)}
                                onChange={stopPropagation}/>
                            {todo.task}
                            <Button onClick={() => handleEditingCompleted}>Edit todo</Button>
                            <Button
                                className="delete-button"
                                onClick={() => handleDelete(todo.id)}>
                                Delete todo
                            </Button>
                        </>}
                </li>)}
            </ul>
        </div>
    </>
};
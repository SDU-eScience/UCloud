import {createContext} from "react";
import {allOptions} from ".";
import Option from "./Option";

export interface OptionState {
  key: string;
  options: string[];
  defaultValue?: string;
  available: number;
}

export type OptionContextState = {[index: string]: OptionState}

export default class OptionContext {
  private stateChangeListeners = new Set<Function>();
  private valueChangeListeners = new Set<Function>();
  private _state: OptionContextState = {};
  private _data: {[index: string]: string} = {};
  private readonly _options: Array<Option>;

  get options() {
    return this._options;
  }

  get state() {
    return this._state;
  }

  constructor(options: Option[]) {
    this._options = options;
    for (const option of options) {
      this._state[option.key] = {
        key: option.key,
        available: 0,
        options: []
      }
    }
  }

  public addStateChangeListener(listener: () => void) {
    this.stateChangeListeners.add(listener);
  }

  public removeStateChangeListener(listener: () => void) {
    this.stateChangeListeners.delete(listener)
  }

  public addValueChangeListener(listener: (key: string, value: string) => void) {
    this.valueChangeListeners.add(listener)
  }

  public removeValueChangeListener(listener: (key: string, value: string) => void) {
    this.valueChangeListeners.delete(listener)
  }

  public optionEnter(key: string) {
    // TODO:
    const optionState = this.getOptionState(key)!;
    this.setState({
      [key]: {
        ...optionState,
        available: optionState.available + 1
      }
    });
  }

  public optionExit(key: string) {
    const optionState = this.getOptionState(key)!;
    this.setState({
      [key]: {
        ...optionState,
        available: optionState.available - 1
      }
    });
  }

  public getOptionState(key: string): OptionState | null {
    return this.state[key] || null;
  }

  public getValue(key: string): string | null {
    const optionState = this.getOptionState(key)!;
    if (!optionState) {
      return null;
    }
    const value = this._data[key];
    if (value) {
      return value;
    }
    return optionState.defaultValue || null;
  }

  public setValue(key: string, value: string) {
    for (const listener of Array.from(this.valueChangeListeners)) {
      listener(key, value);
    }
  }

  // set single source of truth
  public setData(data: {[index: string]: string}) {
    this._data = data;
    this.notifyListener();
  }

  public setDefaultValue(key: string, defaultValue: string) {
    const optionState = this.getOptionState(key)!;
    this.setState({
      [key]: {
        ...optionState,
        defaultValue
      }
    });
  }

  public setOptions(key: string, options: string[]) {
    this.setState({
      [key]: {
        ...this.state[key],
        key,
        options
      }
    })
  }

  private setState(state: OptionContextState) {
    this._state = {
      ...this.state,
      ...state
    };
    this.notifyListener();
  }

  private notifyListener() {
    for (const listener of Array.from(this.stateChangeListeners)) {
      listener();
    }
  }
}

export const OptionCtx = createContext(new OptionContext(allOptions));

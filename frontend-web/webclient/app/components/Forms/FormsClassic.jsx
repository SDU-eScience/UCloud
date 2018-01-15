import React from 'react';
import pubsub from 'pubsub-js';

class FormsClassic extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    render() {
        return (
            <section>
                <div className="container-fluid">
                    <h5 className="mt0">Classic form controls and custom variants</h5>
                    <p className="mb-lg">Standard Bootstrap form and input controls with customized UI variations</p>
                    <div className="card">
                        <div className="card-body">
                            <form method="get" action="/" className="form-horizontal">
                                <fieldset>
                                    <div className="form-group">
                                        <label className="col-sm-2 control-label">Rounded Corners</label>
                                        <div className="col-sm-10">
                                            <input type="text" className="form-control rounded"/>
                                        </div>
                                    </div>
                                </fieldset>
                                <fieldset>
                                    <div className="form-group">
                                        <label className="col-sm-2 control-label">With help</label>
                                        <div className="col-sm-10">
                                            <input type="text" className="form-control"/><span className="help-block">A block of help text that breaks onto a new line and may extend beyond one line.</span>
                                        </div>
                                    </div>
                                </fieldset>
                                <fieldset>
                                    <div className="form-group">
                                        <label htmlFor="input-id-1" className="col-sm-2 control-label">Label focus</label>
                                        <div className="col-sm-10">
                                            <input id="input-id-1" type="text" className="form-control"/>
                                        </div>
                                    </div>
                                </fieldset>
                                <fieldset>
                                    <div className="form-group">
                                        <label className="col-sm-2 control-label">Password</label>
                                        <div className="col-sm-10">
                                            <input type="password" name="password" className="form-control"/>
                                        </div>
                                    </div>
                                </fieldset>
                                <fieldset>
                                    <div className="form-group">
                                        <label className="col-sm-2 control-label">Placeholder</label>
                                        <div className="col-sm-10">
                                            <input type="text" placeholder="placeholder" className="form-control"/>
                                        </div>
                                    </div>
                                </fieldset>
                                <fieldset>
                                    <div className="form-group">
                                        <label className="col-lg-2 control-label">Disabled</label>
                                        <div className="col-lg-10">
                                            <input type="text" placeholder="Disabled input here..." disabled="" className="form-control"/>
                                        </div>
                                    </div>
                                </fieldset>
                                <fieldset>
                                    <div className="form-group">
                                        <label className="col-lg-2 control-label">Static control</label>
                                        <div className="col-lg-10">
                                            <p className="form-control-static">email@example.com</p>
                                        </div>
                                    </div>
                                </fieldset>
                                <fieldset>
                                    <div className="form-group">
                                        <label className="col-sm-2 control-label">Checkboxes and radios</label>
                                        <div className="col-sm-10">
                                            <div className="checkbox">
                                                <label>
                                                    <input type="checkbox" value=""/>Option one is this and that—be sure to include why it's great
                                                </label>
                                            </div>
                                            <div className="radio">
                                                <label>
                                                    <input id="optionsRadios1" type="radio" name="optionsRadios" value="option1" defaultChecked/>Option one is this and that—be sure to include why it's great
                                                </label>
                                            </div>
                                            <div className="radio">
                                                <label>
                                                    <input id="optionsRadios2" type="radio" name="optionsRadios" value="option2"/>Option two can be something else and selecting it will deselect option one
                                                </label>
                                            </div>
                                        </div>
                                    </div>
                                </fieldset>
                                <fieldset>
                                    <div className="form-group">
                                        <label className="col-sm-2 control-label">Inline checkboxes</label>
                                        <div className="col-sm-10">
                                            <label className="checkbox checkbox-inline">
                                                <input id="inlineCheckbox1" type="checkbox" value="option1"/> a
                                            </label>
                                            <label className="checkbox-inline">
                                                <input id="inlineCheckbox2" type="checkbox" value="option2"/> b
                                            </label>
                                            <label className="checkbox-inline">
                                                <input id="inlineCheckbox3" type="checkbox" value="option3"/> c
                                            </label>
                                        </div>
                                    </div>
                                </fieldset>
                                <fieldset className="last-child">
                                    <div className="form-group">
                                        <label className="col-sm-2 control-label">Select</label>
                                        <div className="col-sm-10">
                                            <select name="account" className="form-control">
                                                <option>Option 1</option>
                                                <option>Option 2</option>
                                                <option>Option 3</option>
                                                <option>Option 4</option>
                                            </select><br/>
                                            <div className="row">
                                                <div className="col-lg-4">
                                                    <select multiple="" className="form-control">
                                                        <option>Option 1</option>
                                                        <option>Option 2</option>
                                                        <option>Option 3</option>
                                                        <option>Option 4</option>
                                                    </select>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </fieldset>
                                <fieldset>
                                    <div className="form-group">
                                        <label className="col-sm-2 control-label">Checkboxes</label>
                                        <div className="col-sm-10">
                                            <div className="checkbox c-checkbox">
                                                <label>
                                                    <input type="checkbox" value=""/><span className="ion-checkmark-round"></span> Option one
                                                </label>
                                            </div>
                                            <div className="checkbox c-checkbox">
                                                <label>
                                                    <input type="checkbox" defaultChecked value=""/><span className="ion-checkmark-round"></span> Option two checked
                                                </label>
                                            </div>
                                            <div className="checkbox c-checkbox">
                                                <label>
                                                    <input type="checkbox" defaultChecked disabled="" value=""/><span className="ion-checkmark-round"></span> Option three checked and disabled
                                                </label>
                                            </div>
                                            <div className="checkbox c-checkbox">
                                                <label>
                                                    <input type="checkbox" disabled="" value=""/><span className="ion-checkmark-round"></span> Option four disabled
                                                </label>
                                            </div>
                                        </div>
                                        <label className="col-sm-2 control-label">Radios</label>
                                        <div className="col-sm-10">
                                            <div className="radio c-radio">
                                                <label>
                                                    <input type="radio" name="a" value="option1"/><span className="ion-record"></span> Option one
                                                </label>
                                            </div>
                                            <div className="radio c-radio">
                                                <label>
                                                    <input type="radio" name="a" value="option2" defaultChecked/><span className="ion-record"></span> Option two checked
                                                </label>
                                            </div>
                                            <div className="radio c-radio">
                                                <label>
                                                    <input type="radio" value="option2" defaultChecked disabled=""/><span className="ion-record"></span> Option three checked and disabled
                                                </label>
                                            </div>
                                            <div className="radio c-radio">
                                                <label>
                                                    <input type="radio" name="a" disabled=""/><span className="ion-record"></span> Option four disabled
                                                </label>
                                            </div>
                                        </div>
                                    </div>
                                </fieldset>
                                <fieldset>
                                    <div className="form-group">
                                        <label className="col-sm-2 control-label">Inline checkboxes</label>
                                        <div className="col-sm-10">
                                            <label className="checkbox-inline c-checkbox">
                                                <input id="inlineCheckbox10" type="checkbox" value="option1"/><span className="ion-checkmark-round"></span> a
                                            </label>
                                            <label className="checkbox-inline c-checkbox">
                                                <input id="inlineCheckbox20" type="checkbox" value="option2"/><span className="ion-checkmark-round"></span> b
                                            </label>
                                            <label className="checkbox-inline c-checkbox">
                                                <input id="inlineCheckbox30" type="checkbox" value="option3"/><span className="ion-checkmark-round"></span> c
                                            </label>
                                        </div>
                                    </div>
                                </fieldset>
                                <fieldset>
                                    <div className="form-group">
                                        <label className="col-sm-2 control-label">Rounded</label>
                                        <div className="col-sm-10">
                                            <div className="checkbox c-checkbox c-checkbox-rounded">
                                                <label>
                                                    <input id="roundedcheckbox10" type="checkbox" defaultChecked/><span className="ion-checkmark-round"></span> Lorem
                                                </label>
                                            </div>
                                            <div className="checkbox c-checkbox c-checkbox-rounded">
                                                <label>
                                                    <input id="roundedcheckbox20" type="checkbox"/><span className="ion-checkmark-round"></span> Ipsum
                                                </label>
                                            </div>
                                        </div>
                                    </div>
                                </fieldset>
                                <fieldset>
                                    <div className="form-group">
                                        <label className="col-sm-2 control-label">Inline radios</label>
                                        <div className="col-sm-10">
                                            <label className="radio-inline c-radio">
                                                <input id="inlineradio1" type="radio" name="i-radio" value="option1" defaultChecked/><span className="ion-record"></span> a
                                            </label>
                                            <label className="radio-inline c-radio">
                                                <input id="inlineradio2" type="radio" name="i-radio" value="option2"/><span className="ion-record"></span> b
                                            </label>
                                            <label className="radio-inline c-radio">
                                                <input id="inlineradio3" type="radio" name="i-radio" value="option3"/><span className="ion-record"></span> c
                                            </label>
                                        </div>
                                    </div>
                                </fieldset>
                                <fieldset>
                                    <div className="form-group">
                                        <label className="col-sm-2 control-label">Custom icons</label>
                                        <div className="col-sm-10">
                                            <label className="radio-inline c-radio">
                                                <input id="inlineradio10" type="radio" name="i-radio" value="option1" defaultChecked/><span className="ion-checkmark-round"></span> a
                                            </label>
                                            <label className="radio-inline c-radio">
                                                <input id="inlineradio20" type="radio" name="i-radio" value="option2" defaultChecked/><span className="ion-person"></span> b
                                            </label>
                                            <label className="radio-inline c-radio">
                                                <input id="inlineradio30" type="radio" name="i-radio" value="option3" defaultChecked/><span className="ion-waterdrop"></span> c
                                            </label><br/>
                                            <label className="checkbox-inline c-checkbox">
                                                <input id="inlinecheckbox10" type="checkbox" value="option1" defaultChecked/><span className="ion-star"></span> a
                                            </label>
                                            <label className="checkbox-inline c-checkbox">
                                                <input id="inlinecheckbox20" type="checkbox" value="option2" defaultChecked/><span className="ion-heart"></span> b
                                            </label>
                                            <label className="checkbox-inline c-checkbox">
                                                <input id="inlinecheckbox30" type="checkbox" value="option3" defaultChecked/><span className="ion-ios-bell"></span> c
                                            </label>
                                        </div>
                                    </div>
                                </fieldset>
                                <fieldset>
                                    <div className="form-group has-success">
                                        <label className="col-sm-2 control-label">Input with success</label>
                                        <div className="col-sm-10">
                                            <input type="text" className="form-control"/>
                                        </div>
                                    </div>
                                </fieldset>
                                <fieldset>
                                    <div className="form-group has-warning">
                                        <label className="col-sm-2 control-label">Input with warning</label>
                                        <div className="col-sm-10">
                                            <input type="text" className="form-control"/>
                                        </div>
                                    </div>
                                </fieldset>
                                <fieldset>
                                    <div className="form-group has-error">
                                        <label className="col-sm-2 control-label">Input with error</label>
                                        <div className="col-sm-10">
                                            <input type="text" className="form-control"/>
                                        </div>
                                    </div>
                                </fieldset>
                                <fieldset>
                                    <div className="form-group">
                                        <label className="col-sm-2 control-label">Control sizing</label>
                                        <div className="col-sm-10">
                                            <input type="text" placeholder=".input-lg" className="form-control input-lg"/><br/>
                                            <input type="text" placeholder="Default input" className="form-control"/><br/>
                                            <input type="text" placeholder=".input-sm" className="form-control input-sm"/>
                                        </div>
                                    </div>
                                </fieldset>
                                <fieldset>
                                    <div className="form-group">
                                        <label className="col-sm-2 control-label">Column sizing</label>
                                        <div className="col-sm-10">
                                            <div className="row">
                                                <div className="col-md-2">
                                                    <input type="text" placeholder=".col-md-2" className="form-control"/>
                                                </div>
                                                <div className="col-md-3">
                                                    <input type="text" placeholder=".col-md-3" className="form-control"/>
                                                </div>
                                                <div className="col-md-4">
                                                    <input type="text" placeholder=".col-md-4" className="form-control"/>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </fieldset>
                                <fieldset>
                                    <div className="form-group">
                                        <label className="col-sm-2 control-label">Input groups</label>
                                        <div className="col-sm-10">
                                            <div className="input-group"><span className="input-group-addon">@</span>
                                                <input type="text" placeholder="Username" className="form-control"/>
                                            </div><br/>
                                            <div className="input-group">
                                                <input type="text" className="form-control"/><span className="input-group-addon">.00</span>
                                            </div><br/>
                                            <div className="input-group"><span className="input-group-addon">$</span>
                                                <input type="text" className="form-control"/><span className="input-group-addon">.00</span>
                                            </div><br/>
                                            <div className="input-group"><span className="input-group-addon">
                                                    <input type="checkbox"/></span>
                                                <input type="text" className="form-control"/>
                                            </div><br/>
                                            <div className="input-group"><span className="input-group-addon">
                                                    <input type="radio"/></span>
                                                <input type="text" className="form-control"/>
                                            </div>
                                        </div>
                                    </div>
                                </fieldset>
                                <fieldset>
                                    <div className="form-group">
                                        <label className="col-sm-2 control-label">Button addons</label>
                                        <div className="col-sm-10">
                                            <div className="input-group"><span className="input-group-btn">
                                                    <button type="button" className="btn btn-default">Go!</button></span>
                                                <input type="text" className="form-control"/>
                                            </div><br/>
                                            <div className="input-group">
                                                <input type="text" className="form-control"/><span className="input-group-btn">
                                                    <button type="button" className="btn btn-default">Go!</button></span>
                                            </div>
                                        </div>
                                    </div>
                                </fieldset>
                                <fieldset>
                                    <div className="form-group">
                                        <label className="col-sm-2 control-label">With dropdowns</label>
                                        <div className="col-sm-10">
                                            <div className="input-group">
                                                <div className="input-group-btn">
                                                    <button type="button" data-toggle="dropdown" className="btn btn-default">Action<span className="caret"></span></button>
                                                    <ul className="dropdown-menu">
                                                        <li><a href="#">Action</a></li>
                                                        <li><a href="#">Another action</a></li>
                                                        <li><a href="#">Something else here</a></li>
                                                        <li className="divider"></li>
                                                        <li><a href="#">Separated link</a></li>
                                                    </ul>
                                                </div>
                                                <input type="text" className="form-control"/>
                                            </div><br/>
                                            <div className="input-group">
                                                <input type="text" className="form-control"/>
                                                <div className="input-group-btn">
                                                    <button type="button" data-toggle="dropdown" className="btn btn-default">Action<span className="caret"></span></button>
                                                    <ul className="dropdown-menu pull-right">
                                                        <li><a href="#">Action</a></li>
                                                        <li><a href="#">Another action</a></li>
                                                        <li><a href="#">Something else here</a></li>
                                                        <li className="divider"></li>
                                                        <li><a href="#">Separated link</a></li>
                                                    </ul>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </fieldset>
                                <fieldset>
                                    <div className="form-group">
                                        <label className="col-sm-2 control-label">Segmented</label>
                                        <div className="col-sm-10">
                                            <div className="input-group">
                                                <div className="input-group-btn">
                                                    <button type="button" tabIndex="-1" className="btn btn-default">Action</button>
                                                    <button type="button" data-toggle="dropdown" className="btn btn-default"><span className="caret"></span></button>
                                                    <ul className="dropdown-menu">
                                                        <li><a href="#">Action</a></li>
                                                        <li><a href="#">Another action</a></li>
                                                        <li><a href="#">Something else here</a></li>
                                                        <li className="divider"></li>
                                                        <li><a href="#">Separated link</a></li>
                                                    </ul>
                                                </div>
                                                <input type="text" className="form-control"/>
                                            </div><br/>
                                            <div className="input-group">
                                                <input type="text" className="form-control"/>
                                                <div className="input-group-btn">
                                                    <button type="button" tabIndex="-1" className="btn btn-default">Action</button>
                                                    <button type="button" data-toggle="dropdown" className="btn btn-default"><span className="caret"></span></button>
                                                    <ul className="dropdown-menu pull-right">
                                                        <li><a href="#">Action</a></li>
                                                        <li><a href="#">Another action</a></li>
                                                        <li><a href="#">Something else here</a></li>
                                                        <li className="divider"></li>
                                                        <li><a href="#">Separated link</a></li>
                                                    </ul>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </fieldset>
                            </form>
                        </div>
                    </div>
                </div>
            </section>
        );
    }
}

export default FormsClassic;


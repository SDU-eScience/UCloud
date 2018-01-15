import React from 'react';
import pubsub from 'pubsub-js';

import './Validation.scss';
import ValidationRun from './Validation.run';

class Validation extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    componentDidMount() {
        ValidationRun();
    }

    render() {
        return (
            <section>
                <div className="container-fluid">
                    <h5 className="mt0">jQuery Form validation</h5>
                    <p className="mb-lg">This jQuery plugin makes simple clientside form validation easy, whilst still offering plenty of customization options. It makes a good choice if you’re building something new from scratch, but also when you’re trying to integrate something into an existing application with lots of existing markup.</p>
                    {/* START row */}
                    <form id="form-register" name="registerForm" noValidate className="form-validate">
                        {/* START panel */}
                        <div className="card card-default">
                            <div className="card-heading"><small className="pull-right">* Required fields</small>
                                <div className="panel-title">Form Register</div>
                            </div>
                            <div className="card-body">
                                <div className="mda-form-group">
                                    <div className="mda-form-control">
                                        <input type="email" placeholder="mail@example.com" name="email" required="" className="form-control"/>
                                        <div className="mda-form-control-line"></div>
                                        <label className="control-label">Email Address *</label>
                                    </div>
                                </div>
                                <div className="mda-form-group">
                                    <div className="mda-form-control">
                                        <input id="id-password" type="password" name="password1" required="" className="form-control"/>
                                        <div className="mda-form-control-line"></div>
                                        <label className="control-label">Password *</label>
                                    </div>
                                </div>
                                <div className="mda-form-group">
                                    <div className="mda-form-control">
                                        <input type="password" name="confirm_match" required="" className="form-control"/>
                                        <div className="mda-form-control-line"></div>
                                        <label className="control-label">Confirm Password *</label>
                                    </div>
                                </div>
                            </div>
                            <div className="card-body">
                                <div className="clearfix">
                                    <div className="pull-left">
                                        <div className="checkbox c-checkbox">
                                            <label>
                                                <input type="checkbox" name="agreements" required=""/><span className="ion-checkmark-round"></span>I agree with the<a href="#"> terms</a>
                                            </label>
                                        </div>
                                    </div>
                                    <div className="pull-right mt-sm">
                                        <button type="submit" className="btn btn-primary btn-flat">Register</button>
                                    </div>
                                </div>
                            </div>
                        </div>
                        {/* END panel */}
                    </form>
                    <form id="form-subscribe" name="subscribeForm" noValidate className="form-validate">
                        {/* START panel */}
                        <div className="card card-default">
                            <div className="card-body">
                                <div className="form-group">
                                    <div className="mda-input-group input-group">
                                        <div className="mda-form-control">
                                            <input type="email" placeholder="mail@example.com" name="feedemail" required="" className="form-control"/>
                                            <div className="mda-form-control-line"></div>
                                            <label className="control-label">Subscribe</label>
                                        </div><span className="mda-input-group-addon"><em className="ion-email icon-lg"></em></span><span className="input-group-btn">
                                            <button type="submit" className="btn btn-default btn-circle">Go!</button></span>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </form>
                    <div className="card">
                        <div className="card-body">
                            {/* START row */}
                            <div className="row">
                                <div className="col-md-12">
                                    <form id="form-example" name="form.formValidate" noValidate className="form-validate form-horizontal">
                                        <fieldset className="b0">
                                            <legend>Basics</legend>
                                        </fieldset>
                                        <fieldset>
                                            <div className="form-group">
                                                <label className="col-sm-2 control-label">Required Text</label>
                                                <div className="col-sm-6">
                                                    <input type="text" name="sometext" required="" className="form-control"/>
                                                </div>
                                            </div>
                                        </fieldset>
                                        <fieldset>
                                            <div className="form-group">
                                                <label className="col-sm-2 control-label">Email</label>
                                                <div className="col-sm-6">
                                                    <input type="email" placeholder="mail@example.com" name="email" required="" className="form-control"/>
                                                </div>
                                            </div>
                                        </fieldset>
                                        <fieldset>
                                            <div className="form-group">
                                                <label className="col-sm-2 control-label">Checkbox &amp; Radio</label>
                                                <div className="col-sm-6">
                                                    <div className="checkbox c-checkbox">
                                                        <label>
                                                            <input type="checkbox" required="" name="checkbox"/><span className="ion-checkmark-round"></span> Option one
                                                        </label>
                                                    </div>
                                                    <div className="radio c-radio c-radio-nofont">
                                                        <label>
                                                            <input type="radio" required="" name="radio" value="option1"/><span className="ion-record"></span> Option one
                                                        </label>
                                                    </div>
                                                    <div className="radio c-radio c-radio-nofont">
                                                        <label>
                                                            <input type="radio" required="" name="radio" value="option2"/><span className="ion-record"></span> Option two checked
                                                        </label>
                                                    </div>
                                                </div>
                                            </div>
                                        </fieldset>
                                        <fieldset>
                                            <div className="form-group">
                                                <label className="col-sm-2 control-label">Select</label>
                                                <div className="col-sm-6">
                                                    <select name="select" required="" className="form-control">
                                                        <option value="">Nothing</option>
                                                        <option value="1">Option 1</option>
                                                        <option value="2">Option 2</option>
                                                        <option value="3">Option 3</option>
                                                        <option value="4">Option 4</option>
                                                    </select>
                                                </div>
                                            </div>
                                        </fieldset>
                                        <fieldset>
                                            <div className="form-group">
                                                <label className="col-sm-2 control-label">Number</label>
                                                <div className="col-sm-6">
                                                    <input type="number" name="anumber" required="" className="form-control"/>
                                                </div>
                                            </div>
                                        </fieldset>
                                        <fieldset>
                                            <div className="form-group">
                                                <label className="col-sm-2 control-label">Digits</label>
                                                <div className="col-sm-6">
                                                    <input type="text" name="digits" className="form-control"/>
                                                </div>
                                            </div>
                                        </fieldset>
                                        <fieldset className="bb0">
                                            <div className="form-group">
                                                <label className="col-sm-2 control-label">Url</label>
                                                <div className="col-sm-6">
                                                    <input type="url" name="url" placeholder="protocol://" className="form-control"/>
                                                </div>
                                            </div>
                                        </fieldset>
                                        <fieldset className="b0">
                                            <legend>Lengths</legend>
                                        </fieldset>
                                        <fieldset>
                                            <div className="form-group">
                                                <label className="col-sm-2 control-label">Min</label>
                                                <div className="col-sm-6">
                                                    <input type="text" name="min" className="form-control"/>
                                                </div>
                                            </div>
                                        </fieldset>
                                        <fieldset>
                                            <div className="form-group">
                                                <label className="col-sm-2 control-label">Max</label>
                                                <div className="col-sm-6">
                                                    <input type="text" name="max" className="form-control"/>
                                                </div>
                                            </div>
                                        </fieldset>
                                        <fieldset>
                                            <div className="form-group">
                                                <label className="col-sm-2 control-label">Minlength</label>
                                                <div className="col-sm-6">
                                                    <input type="text" name="minlength" className="form-control"/>
                                                </div>
                                            </div>
                                        </fieldset>
                                        <fieldset>
                                            <div className="form-group">
                                                <label className="col-sm-2 control-label">Maxlength</label>
                                                <div className="col-sm-6">
                                                    <input type="text" name="maxlength" className="form-control"/>
                                                </div>
                                            </div>
                                        </fieldset>
                                        <fieldset className="b0">
                                            <legend>Range</legend>
                                        </fieldset>
                                        <fieldset className="b0">
                                            <div className="form-group">
                                                <label className="col-sm-2 control-label">Length range</label>
                                                <div className="col-sm-6">
                                                    <input type="text" name="length" className="form-control"/>
                                                </div>
                                            </div>
                                        </fieldset>
                                        <fieldset className="b0">
                                            <legend>Comparison</legend>
                                        </fieldset>
                                        <fieldset className="b0">
                                            <div className="form-group">
                                                <label className="col-sm-2 control-label">Equal to</label>
                                                <div className="col-sm-6">
                                                    <div className="row">
                                                        <div className="col-xs-6">
                                                            <input id="id-source" type="password" name="match1" className="form-control"/>
                                                        </div>
                                                        <div className="col-xs-6">
                                                            <input type="password" name="confirm_match" className="form-control"/>
                                                        </div>
                                                    </div>
                                                </div>
                                            </div>
                                        </fieldset>
                                        <hr/>
                                        <div className="text-center">
                                            <button type="submit" className="btn btn-primary">Run validation</button>
                                        </div>
                                        {/* END panel */}
                                    </form>
                                </div>
                            </div>
                            {/* END row */}
                        </div>
                    </div>
                </div>
            </section>
        );
    }
}

export default Validation;

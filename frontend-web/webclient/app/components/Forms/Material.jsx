import React from 'react';
import pubsub from 'pubsub-js';

import './Material.scss';

class Material extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    render() {
        return (
            <section>
                <div className="container container-lg">
                    <div className="card">
                        <div className="card-heading">
                            <div className="card-title">Material Forms</div>
                        </div>
                        <div className="card-body bg-blue-grey-900">
                            <div className="row">
                                <div className="col-sm-4">
                                    <div className="mda-form-group">
                                        <div className="mda-form-control mda-form-control-dark">
                                            <input tabIndex="0" aria-invalid="false" className="form-control"/>
                                            <div className="mda-form-control-line"></div>
                                            <label>Title</label>
                                        </div>
                                    </div>
                                </div>
                                <div className="col-sm-4">
                                    <div className="mda-form-group">
                                        <div className="mda-form-control mda-form-control-dark">
                                            <input tabIndex="0" aria-invalid="false" className="form-control"/>
                                            <div className="mda-form-control-line"></div>
                                            <label>Email</label>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div className="card-body">
                            <div className="row">
                                <div className="col-sm-12">
                                    <div className="mda-form-group">
                                        <div className="mda-form-control">
                                            <input disabled="" tabIndex="0" aria-invalid="false" className="form-control"/>
                                            <div className="mda-form-control-line"></div>
                                            <label>Company(disabled)</label>
                                        </div>
                                    </div>
                                </div>
                                <div className="col-sm-6">
                                    <div className="mda-form-group float-label">
                                        <div className="mda-form-control">
                                            <input required="" tabIndex="0" aria-required="true" aria-invalid="true" className="form-control"/>
                                            <div className="mda-form-control-line"></div>
                                            <label>First Name</label>
                                        </div>
                                    </div>
                                </div>
                                <div className="col-sm-6">
                                    <div className="mda-form-group float-label">
                                        <div className="mda-form-control">
                                            <input required="" tabIndex="0" aria-required="true" aria-invalid="true" className="form-control"/>
                                            <div className="mda-form-control-line"></div>
                                            <label>Last Name</label>
                                        </div>
                                    </div>
                                </div>
                                <div className="col-sm-12">
                                    <div className="mda-form-group">
                                        <div className="mda-form-control">
                                            <input tabIndex="0" aria-invalid="false" className="form-control"/>
                                            <div className="mda-form-control-line"></div>
                                            <label>Address</label>
                                        </div>
                                    </div>
                                </div>
                                <div className="col-sm-12">
                                    <div className="mda-form-group">
                                        <div className="mda-form-control">
                                            <input tabIndex="0" aria-invalid="false" className="form-control"/>
                                            <div className="mda-form-control-line"></div>
                                            <label>Address 2</label>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div className="row">
                                <div className="col-sm-4">
                                    <div className="mda-form-group">
                                        <div className="mda-form-control">
                                            <input tabIndex="0" aria-invalid="false" className="form-control"/>
                                            <div className="mda-form-control-line"></div>
                                            <label>City</label>
                                        </div>
                                    </div>
                                </div>
                                <div className="col-sm-4">
                                    <div className="mda-form-group">
                                        <div className="mda-form-control">
                                            <input tabIndex="0" aria-invalid="false" className="form-control"/>
                                            <div className="mda-form-control-line"></div>
                                            <label>State</label>
                                        </div>
                                    </div>
                                </div>
                                <div className="col-sm-4">
                                    <div className="mda-form-group">
                                        <div className="mda-form-control">
                                            <input tabIndex="0" aria-invalid="false" className="form-control"/>
                                            <div className="mda-form-control-line"></div>
                                            <label>Postal Code</label>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div className="row">
                                <div className="col-sm-12">
                                    <div className="mda-form-group">
                                        <div className="mda-form-control">
                                            <textarea rows="4" aria-multiline="true" tabIndex="0" aria-invalid="false" className="form-control"></textarea>
                                            <div className="mda-form-control-line"></div>
                                            <label>Biography</label><span className="mda-form-msg right">Any message here</span>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div className="row">
                                <div className="col-sm-12">
                                    <div className="mda-form-group">
                                        <div className="mda-form-control">
                                            <select className="form-control">
                                                <option value="1">Option 1</option>
                                                <option value="2">Option 2</option>
                                                <option value="3">Option 3</option>
                                                <option value="4">Option 4</option>
                                                <option value="5">Option 5</option>
                                            </select>
                                            <div className="mda-form-control-line"></div>
                                            <label>Select</label>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div className="card">
                        <div className="card-heading bg-primary">
                            <div className="pull-right"><em className="ion-ios-browsers-outline icon-2x va-top"></em></div>
                            <div className="card-title">Material Forms</div>
                        </div>
                        <div className="card-toolbar bg-primary">Errors</div>
                        <div className="card-body">
                            <form className="form-validate">
                                <div className="mda-form-group float-label mb">
                                    <div className="mda-form-control">
                                        <input required="" tabIndex="0" aria-required="true" aria-invalid="true" className="form-control"/>
                                        <div className="mda-form-control-line"></div>
                                        <label>Client Name</label>
                                    </div><span className="mda-form-msg">Input should match 'a-zA-Z0-9' and 6-10 length</span>
                                </div>
                                <div className="mda-form-group">
                                    <div className="mda-form-control">
                                        <input tabIndex="0" aria-invalid="true" className="form-control"/>
                                        <div className="mda-form-control-line"></div>
                                        <label>Salary</label>
                                    </div><span className="mda-form-msg">Your salary must be at least 6 digits</span>
                                </div>
                            </form>
                        </div>
                    </div>
                    <div className="card">
                        <div className="card-heading bg-primary">
                            <div className="card-title">Icons</div>
                        </div>
                        <div className="card-body">
                            <div className="mda-form-group float-label mb mda-input-group">
                                <div className="mda-form-control">
                                    <input type="text" className="form-control"/>
                                    <div className="mda-form-control-line"></div>
                                    <label>Username</label>
                                </div><span className="mda-input-group-addon"><em className="ion-person icon-lg"></em></span>
                            </div>
                            <div className="mda-form-group mb mda-input-group">
                                <div className="mda-form-control">
                                    <input type="text" className="form-control"/>
                                    <div className="mda-form-control-line"></div>
                                    <label>Phone</label>
                                </div><span className="mda-input-group-addon"><em className="ion-ios-telephone text-primary icon-lg"></em></span>
                            </div>
                        </div>
                    </div>
                    {/* Switchers */}
                    <div className="card">
                        <div className="card-heading bg-primary">
                            <div className="card-title">Switches</div>
                        </div>
                        <div className="card-body">
                            <div className="row">
                                <div className="col-md-4">
                                    <div className="row mb">
                                        <div className="col-sm-8">
                                            <label className="switch">
                                                <input type="checkbox" defaultChecked/><span></span>
                                            </label>Switch
                                        </div>
                                    </div>
                                    <div className="row mb">
                                        <div className="col-sm-8">
                                            <label className="switch switch-warn">
                                                <input type="checkbox" defaultChecked/><span></span>
                                            </label>Switch warn
                                        </div>
                                    </div>
                                    <div className="row mb">
                                        <div className="col-sm-8">
                                            <label className="switch switch-warn">
                                                <input type="checkbox" disabled="true"/><span></span>
                                            </label>Switch disabled
                                        </div>
                                    </div>
                                    <div className="row mb">
                                        <div className="col-sm-8">
                                            <label className="switch switch-warn">
                                                <input type="checkbox" defaultChecked disabled="true"/><span></span>
                                            </label>Switch disabled active
                                        </div>
                                    </div>
                                </div>
                                <div className="col-md-4">
                                    <div className="row mb">
                                        <div className="col-sm-8">
                                            <label className="switch switch-primary">
                                                <input type="checkbox" defaultChecked/><span></span>
                                            </label>Switch primary
                                        </div>
                                    </div>
                                    <div className="row mb">
                                        <div className="col-sm-8">
                                            <label className="switch switch-purple">
                                                <input type="checkbox" defaultChecked/><span></span>
                                            </label>Switch purple
                                        </div>
                                    </div>
                                    <div className="row mb">
                                        <div className="col-sm-8">
                                            <label className="switch switch-info">
                                                <input type="checkbox" defaultChecked/><span></span>
                                            </label>Switch info
                                        </div>
                                    </div>
                                    <div className="row mb">
                                        <div className="col-sm-8">
                                            <label className="switch switch-success">
                                                <input type="checkbox" defaultChecked/><span></span>
                                            </label>Switch success
                                        </div>
                                    </div>
                                    <div className="row mb">
                                        <div className="col-sm-8">
                                            <label className="switch switch-warning">
                                                <input type="checkbox" defaultChecked/><span></span>
                                            </label>Switch warning
                                        </div>
                                    </div>
                                    <div className="row mb">
                                        <div className="col-sm-8">
                                            <label className="switch switch-danger">
                                                <input type="checkbox" defaultChecked/><span></span>
                                            </label>Switch danger
                                        </div>
                                    </div>
                                </div>
                                <div className="col-md-4">
                                    <div className="row mb">
                                        <div className="col-sm-8">
                                            <label className="switch switch-warn switch-primary">
                                                <input type="checkbox" defaultChecked/><span></span>
                                            </label>Switch warn primary
                                        </div>
                                    </div>
                                    <div className="row mb">
                                        <div className="col-sm-8">
                                            <label className="switch switch-warn switch-purple">
                                                <input type="checkbox" defaultChecked/><span></span>
                                            </label>Switch warn purple
                                        </div>
                                    </div>
                                    <div className="row mb">
                                        <div className="col-sm-8">
                                            <label className="switch switch-warn switch-info">
                                                <input type="checkbox" defaultChecked/><span></span>
                                            </label>Switch warn info
                                        </div>
                                    </div>
                                    <div className="row mb">
                                        <div className="col-sm-8">
                                            <label className="switch switch-warn switch-success">
                                                <input type="checkbox" defaultChecked/><span></span>
                                            </label>Switch warn success
                                        </div>
                                    </div>
                                    <div className="row mb">
                                        <div className="col-sm-8">
                                            <label className="switch switch-warn switch-warning">
                                                <input type="checkbox" defaultChecked/><span></span>
                                            </label>Switch warn warning
                                        </div>
                                    </div>
                                    <div className="row mb">
                                        <div className="col-sm-8">
                                            <label className="switch switch-warn switch-danger">
                                                <input type="checkbox" defaultChecked/><span></span>
                                            </label>Switch warn danger
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                    {/* Checkbox and radios */}
                    <div className="card">
                        <div className="card-heading bg-primary">
                            <div className="card-title">Checkboxes</div>
                        </div>
                        <div className="card-body">
                            <div className="row">
                                <div className="col-lg-4">
                                    <div className="mb">
                                        <label className="mda-checkbox">
                                            <input type="checkbox"/><em className="bg-indigo-500"></em>This is a Checkbox
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-checkbox">
                                            <input type="checkbox" defaultChecked/><em className="bg-purple-500"></em>This is a Checkbox checked
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-checkbox">
                                            <input type="checkbox" disabled=""/><em className="bg-indigo-500"></em>This is a Checkbox disabled
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-checkbox">
                                            <input type="checkbox" defaultChecked disabled=""/><em className="bg-indigo-500"></em>This is a Checkbox checked and disabled
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-checkbox">
                                            <input type="checkbox" defaultChecked/><em className="bg-blue-500 empty"></em>This is a Checkbox empty
                                        </label>
                                    </div>
                                </div>
                                <div className="col-lg-4">
                                    <div className="mb">
                                        <label className="mda-checkbox">
                                            <input type="checkbox" defaultChecked/><em className="bg-red-500"></em>This a checkbox red
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-checkbox">
                                            <input type="checkbox" defaultChecked/><em className="bg-pink-500"></em>This a checkbox pink
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-checkbox">
                                            <input type="checkbox" defaultChecked/><em className="bg-purple-500"></em>This a checkbox purple
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-checkbox">
                                            <input type="checkbox" defaultChecked/><em className="bg-deep-purple-500"></em>This a checkbox purple
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-checkbox">
                                            <input type="checkbox" defaultChecked/><em className="bg-indigo-500"></em>This a checkbox indigo
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-checkbox">
                                            <input type="checkbox" defaultChecked/><em className="bg-blue-500"></em>This a checkbox blue
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-checkbox">
                                            <input type="checkbox" defaultChecked/><em className="bg-cyan-500"></em>This a checkbox cyan
                                        </label>
                                    </div>
                                </div>
                                <div className="col-lg-4">
                                    <div className="mb">
                                        <label className="mda-checkbox">
                                            <input type="checkbox" defaultChecked/><em className="bg-teal-500"></em>This a checkbox teal
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-checkbox">
                                            <input type="checkbox" defaultChecked/><em className="bg-green-500"></em>This a checkbox green
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-checkbox">
                                            <input type="checkbox" defaultChecked/><em className="bg-lime-500"></em>This a checkbox lime
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-checkbox">
                                            <input type="checkbox" defaultChecked/><em className="bg-yellow-500"></em>This a checkbox yellow
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-checkbox">
                                            <input type="checkbox" defaultChecked/><em className="bg-amber-500"></em>This a checkbox amber
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-checkbox">
                                            <input type="checkbox" defaultChecked/><em className="bg-orange-500"></em>This a checkbox orange
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-checkbox">
                                            <input type="checkbox" defaultChecked/><em className="bg-brown-500"></em>This a checkbox brown
                                        </label>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                    {/* Radios */}
                    <div className="card">
                        <div className="card-heading bg-primary">
                            <div className="card-title">Radios</div>
                        </div>
                        <div className="card-body">
                            <div className="row">
                                <div className="col-lg-4">
                                    <div className="mb">
                                        <label className="mda-radio">
                                            <input type="radio" name="oneradio"/><em className="bg-indigo-500"></em>This is a radio
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-radio">
                                            <input type="radio" name="oneradio" defaultChecked/><em className="bg-purple-500"></em>This is a radio checked
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-radio">
                                            <input type="radio" name="otherradio" disabled=""/><em className="bg-indigo-500"></em>This is a radio disabled
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-radio">
                                            <input type="radio" name="otherradio" defaultChecked disabled=""/><em className="bg-indigo-500"></em>This is a radio checked and disabled
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-radio">
                                            <input type="radio" name="emptyradio" defaultChecked/><em className="bg-blue-500 empty"></em>This is a radio empty
                                        </label>
                                    </div>
                                </div>
                                <div className="col-lg-4">
                                    <div className="mb">
                                        <label className="mda-radio">
                                            <input type="radio" name="someradio-red" defaultChecked/><em className="bg-red-500"></em>This a radio red
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-radio">
                                            <input type="radio" name="someradio-pink" defaultChecked/><em className="bg-pink-500"></em>This a radio pink
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-radio">
                                            <input type="radio" name="someradio-purple" defaultChecked/><em className="bg-purple-500"></em>This a radio purple
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-radio">
                                            <input type="radio" name="someradio-deep-purple" defaultChecked/><em className="bg-deep-purple-500"></em>This a radio purple
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-radio">
                                            <input type="radio" name="someradio-indigo" defaultChecked/><em className="bg-indigo-500"></em>This a radio indigo
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-radio">
                                            <input type="radio" name="someradio-blue" defaultChecked/><em className="bg-blue-500"></em>This a radio blue
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-radio">
                                            <input type="radio" name="someradio-cyan" defaultChecked/><em className="bg-cyan-500"></em>This a radio cyan
                                        </label>
                                    </div>
                                </div>
                                <div className="col-lg-4">
                                    <div className="mb">
                                        <label className="mda-radio">
                                            <input type="radio" name="someradio-teal" defaultChecked/><em className="bg-teal-500"></em>This a radio teal
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-radio">
                                            <input type="radio" name="someradio-green" defaultChecked/><em className="bg-green-500"></em>This a radio green
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-radio">
                                            <input type="radio" name="someradio-lime" defaultChecked/><em className="bg-lime-500"></em>This a radio lime
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-radio">
                                            <input type="radio" name="someradio-yellow" defaultChecked/><em className="bg-yellow-500"></em>This a radio yellow
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-radio">
                                            <input type="radio" name="someradio-amber" defaultChecked/><em className="bg-amber-500"></em>This a radio amber
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-radio">
                                            <input type="radio" name="someradio-orange" defaultChecked/><em className="bg-orange-500"></em>This a radio orange
                                        </label>
                                    </div>
                                    <div className="mb">
                                        <label className="mda-radio">
                                            <input type="radio" name="someradio-brown" defaultChecked/><em className="bg-brown-500"></em>This a radio brown
                                        </label>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </section>
        );
    }
}

export default Material;



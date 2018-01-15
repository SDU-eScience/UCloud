import React from 'react';
import pubsub from 'pubsub-js';
import Nouislider from 'react-nouislider';

import FormsAdvancedRun from './FormsAdvanced.run';

class FormsAdvanced extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    componentDidMount() {
        FormsAdvancedRun();
    }

    sliderUpdateValues(values) {
        // Connecto to live values
        $('#ui-slider-value-lower').html(values[0]);
        $('#ui-slider-value-upper').html(values[1]);
    }

    render() {
        return (
            <section>
                <div className="container-fluid">
                    {/* UISELECT */}
                    <div className="card">
                        <div className="card-heading">
                            <div className="card-title">Select2</div>
                            <div className="text-muted">jQuery based replacement for select boxes</div>
                        </div>
                        <div className="card-body">
                            <div className="form-horizontal">
                                <div className="form-group">
                                    <div className="col-md-3 col-sm-4">
                                        <p className="m0">Basics</p>
                                    </div>
                                    <div className="col-md-5 col-sm-8">
                                        <select id="select2-1" className="form-control">
                                            <option value="AL">Alabama</option>
                                            <option value="AR">Arkansas</option>
                                            <option value="IL">Illinois</option>
                                            <option value="IA">Iowa</option>
                                            <option value="KS">Kansas</option>
                                            <option value="KY">Kentucky</option>
                                            <option value="LA">Louisiana</option>
                                            <option value="MN">Minnesota</option>
                                            <option value="MS">Mississippi</option>
                                            <option value="MO">Missouri</option>
                                            <option value="OK">Oklahoma</option>
                                            <option value="SD">South Dakota</option>
                                            <option value="TX">Texas</option>
                                            <option value="TN">Tennessee</option>
                                            <option value="WI">Wisconsin</option>
                                        </select>
                                    </div>
                                </div>
                                <div className="form-group">
                                    <div className="col-md-3 col-sm-4">
                                        <p className="m0">Multiple</p>
                                    </div>
                                    <div className="col-md-5 col-sm-8">
                                        <select id="select2-2" className="form-control">
                                            <optgroup label="Alaskan/Hawaiian Time Zone">
                                                <option value="AK">Alaska</option>
                                                <option value="HI">Hawaii</option>
                                            </optgroup>
                                            <optgroup label="Pacific Time Zone">
                                                <option value="CA">California</option>
                                                <option value="NV">Nevada</option>
                                                <option value="OR">Oregon</option>
                                                <option value="WA">Washington</option>
                                            </optgroup>
                                            <optgroup label="Mountain Time Zone">
                                                <option value="AZ">Arizona</option>
                                                <option value="CO">Colorado</option>
                                                <option value="ID">Idaho</option>
                                                <option value="MT">Montana</option>
                                                <option value="NE">Nebraska</option>
                                                <option value="NM">New Mexico</option>
                                                <option value="ND">North Dakota</option>
                                                <option value="UT">Utah</option>
                                                <option value="WY">Wyoming</option>
                                            </optgroup>
                                            <optgroup label="Central Time Zone">
                                                <option value="AL">Alabama</option>
                                                <option value="AR">Arkansas</option>
                                                <option value="IL">Illinois</option>
                                                <option value="IA">Iowa</option>
                                                <option value="KS">Kansas</option>
                                                <option value="KY">Kentucky</option>
                                                <option value="LA">Louisiana</option>
                                                <option value="MN">Minnesota</option>
                                                <option value="MS">Mississippi</option>
                                                <option value="MO">Missouri</option>
                                                <option value="OK">Oklahoma</option>
                                                <option value="SD">South Dakota</option>
                                                <option value="TX">Texas</option>
                                                <option value="TN">Tennessee</option>
                                                <option value="WI">Wisconsin</option>
                                            </optgroup>
                                            <optgroup label="Eastern Time Zone">
                                                <option value="CT">Connecticut</option>
                                                <option value="DE">Delaware</option>
                                                <option value="FL">Florida</option>
                                                <option value="GA">Georgia</option>
                                                <option value="IN">Indiana</option>
                                                <option value="ME">Maine</option>
                                                <option value="MD">Maryland</option>
                                                <option value="MA">Massachusetts</option>
                                                <option value="MI">Michigan</option>
                                                <option value="NH">New Hampshire</option>
                                                <option value="NJ">New Jersey</option>
                                                <option value="NY">New York</option>
                                                <option value="NC">North Carolina</option>
                                                <option value="OH">Ohio</option>
                                                <option value="PA">Pennsylvania</option>
                                                <option value="RI">Rhode Island</option>
                                                <option value="SC">South Carolina</option>
                                                <option value="VT">Vermont</option>
                                                <option value="VA">Virginia</option>
                                                <option value="WV">West Virginia</option>
                                            </optgroup>
                                        </select>
                                    </div>
                                </div>
                                <div className="form-group">
                                    <div className="col-md-3 col-sm-4">
                                        <p className="m0">Multiple Pills</p>
                                    </div>
                                    <div className="col-md-5 col-sm-8">
                                        <select id="select2-3" multiple="multiple" className="form-control">
                                            <optgroup label="Alaskan/Hawaiian Time Zone">
                                                <option value="AK" defaultValue>Alaska</option>
                                                <option value="HI">Hawaii</option>
                                            </optgroup>
                                            <optgroup label="Pacific Time Zone">
                                                <option value="CA">California</option>
                                                <option value="NV">Nevada</option>
                                                <option value="OR">Oregon</option>
                                                <option value="WA">Washington</option>
                                            </optgroup>
                                            <optgroup label="Mountain Time Zone">
                                                <option value="AZ">Arizona</option>
                                                <option value="CO">Colorado</option>
                                                <option value="ID">Idaho</option>
                                                <option value="MT">Montana</option>
                                                <option value="NE">Nebraska</option>
                                                <option value="NM">New Mexico</option>
                                                <option value="ND">North Dakota</option>
                                                <option value="UT">Utah</option>
                                                <option value="WY">Wyoming</option>
                                            </optgroup>
                                            <optgroup label="Central Time Zone">
                                                <option value="AL">Alabama</option>
                                                <option value="AR">Arkansas</option>
                                                <option value="IL">Illinois</option>
                                                <option value="IA">Iowa</option>
                                                <option value="KS">Kansas</option>
                                                <option value="KY">Kentucky</option>
                                                <option value="LA">Louisiana</option>
                                                <option value="MN">Minnesota</option>
                                                <option value="MS">Mississippi</option>
                                                <option value="MO">Missouri</option>
                                                <option value="OK">Oklahoma</option>
                                                <option value="SD">South Dakota</option>
                                                <option value="TX">Texas</option>
                                                <option value="TN">Tennessee</option>
                                                <option value="WI">Wisconsin</option>
                                            </optgroup>
                                            <optgroup label="Eastern Time Zone">
                                                <option value="CT">Connecticut</option>
                                                <option value="DE">Delaware</option>
                                                <option value="FL">Florida</option>
                                                <option value="GA">Georgia</option>
                                                <option value="IN">Indiana</option>
                                                <option value="ME">Maine</option>
                                                <option value="MD">Maryland</option>
                                                <option value="MA">Massachusetts</option>
                                                <option value="MI">Michigan</option>
                                                <option value="NH">New Hampshire</option>
                                                <option value="NJ">New Jersey</option>
                                                <option value="NY">New York</option>
                                                <option value="NC">North Carolina</option>
                                                <option value="OH">Ohio</option>
                                                <option value="PA">Pennsylvania</option>
                                                <option value="RI">Rhode Island</option>
                                                <option value="SC">South Carolina</option>
                                                <option value="VT">Vermont</option>
                                                <option value="VA">Virginia</option>
                                                <option value="WV">West Virginia</option>
                                            </optgroup>
                                        </select>
                                    </div>
                                </div>
                                <div className="form-group">
                                    <div className="col-md-3 col-sm-4">
                                        <p className="m0">Placeholders</p>
                                    </div>
                                    <div className="col-md-5 col-sm-8">
                                        <select id="select2-4" className="form-control">
                                            <optgroup label="Alaskan/Hawaiian Time Zone">
                                                <option value="AK">Alaska</option>
                                                <option value="HI">Hawaii</option>
                                            </optgroup>
                                            <optgroup label="Pacific Time Zone">
                                                <option value="CA">California</option>
                                                <option value="NV">Nevada</option>
                                                <option value="OR">Oregon</option>
                                                <option value="WA">Washington</option>
                                            </optgroup>
                                            <optgroup label="Mountain Time Zone">
                                                <option value="AZ">Arizona</option>
                                                <option value="CO">Colorado</option>
                                                <option value="ID">Idaho</option>
                                                <option value="MT">Montana</option>
                                                <option value="NE">Nebraska</option>
                                                <option value="NM">New Mexico</option>
                                                <option value="ND">North Dakota</option>
                                                <option value="UT">Utah</option>
                                                <option value="WY">Wyoming</option>
                                            </optgroup>
                                        </select>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                    {/* END UISELECT */}
                    <div className="card">
                        <div className="card-heading">Calendar</div>
                        <div className="card-body">
                            <div className="row text-center">
                                <div className="col-lg-4">
                                    <div id="example-datepicker-1" data-date="12/13/2016" className="ui-datepicker datepicker-standalone dp-theme-primary"></div>
                                </div>
                                <div className="col-lg-4">
                                    <div id="example-datepicker-2" data-date="12/13/2016" className="ui-datepicker datepicker-standalone dp-theme-info"></div>
                                </div>
                                <div className="col-lg-4">
                                    <div id="example-datepicker-3" data-date="12/13/2016" className="ui-datepicker datepicker-standalone dp-theme-pink"></div>
                                </div>
                            </div>
                            <h5>Popup</h5>
                            <div id="example-datepicker-container-4" className="rel-wrapper ui-datepicker ui-datepicker-popup dp-theme-primary">
                                <div className="mda-form-control">
                                    <input id="example-datepicker-4" type="text" data-date="12/13/2016" placeholder="Select a date.." className="form-control"/>
                                    <div className="mda-form-control-line"></div>
                                </div>
                            </div>
                            <h5>Range</h5>
                            <div id="example-datepicker-container-5" className="rel-wrapper ui-datepicker ui-datepicker-popup dp-theme-success">
                                <div id="example-datepicker-5" className="input-daterange input-group mda-input-group">
                                    <div className="mda-form-control">
                                        <input type="text" name="start" defaultValue="01/16/2016" className="form-control"/>
                                        <div className="mda-form-control-line"></div>
                                    </div><span className="input-group-addon">to</span>
                                    <div className="mda-form-control">
                                        <input type="text" name="end" defaultValue="02/12/2016" className="form-control"/>
                                        <div className="mda-form-control-line"></div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div className="card">
                        <div className="card-heading">Time picker</div>
                        <div className="card-body">
                            <div className="row">
                                <div className="col-lg-5">
                                    <div data-autoclose="true" className="mda-form-group mda-input-group clockpicker">
                                        <div className="mda-form-control">
                                            <input type="text" defaultValue="09:30" className="form-control"/>
                                            <div className="mda-form-control-line"></div>
                                        </div><span className="mda-input-group-addon"><span className="ion-clock"></span></span>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div className="card">
                        <div className="card-heading">Range Sliders</div>
                        <div className="card-body">
                            <p>Single</p>
                            <div className="mb-lg" style={{position:'relative'}}>
                                <Nouislider range={{min: 0, max: 200}} start={[40]} connect="lower"/>
                            </div>
                            <p>Range</p>
                            <div className="mb-lg">
                                <Nouislider range={{min: 0, max: 100}} connect={true} start={[25, 75]} />
                            </div>
                            <p className="clearfix"><span className="pull-left">Live values</span>
                                <span className="pull-right">
                                <strong id="ui-slider-value-lower" className="text-muted"></strong>
                                <span className="mh">-</span>
                                <strong id="ui-slider-value-upper" className="text-muted"></strong></span>
                            </p>
                            <div className="mb-lg">
                                <Nouislider onSlide={this.sliderUpdateValues} connect={true} range={{min: 0, max: 100}} start={[25, 75]} />
                            </div>
                            <p className="mv-lg">Color options</p>
                            <div className="ui-slider-success pv">
                                <Nouislider connect={true} range={{min: 0, max: 100}} start={[25, 75]} />
                            </div>
                            <div className="ui-slider-info pv">
                                <Nouislider connect={true} range={{min: 0, max: 100}} start={[25, 75]} />
                            </div>
                            <div className="ui-slider-warning pv">
                                <Nouislider connect={true} range={{min: 0, max: 100}} start={[25, 75]} />
                            </div>
                            <div className="ui-slider-danger pv">
                                <Nouislider connect={true} range={{min: 0, max: 100}} start={[25, 75]} />
                            </div>
                            <div className="ui-slider-inverse pv">
                                <Nouislider connect={true} range={{min: 0, max: 100}} start={[25, 75]} />
                            </div>
                        </div>
                    </div>
                    <div className="card">
                        <div className="card-heading">Note editors (textarea)</div>
                        <div className="card-body">
                            <div className="panel">
                                <div className="panel-body">
                                    <div className="note-area">
                                        <textarea rows="10" className="form-control" defaultValue="In elementum magna sed sapien gravida scelerisque."></textarea>
                                    </div>
                                </div>
                            </div>
                            <p>With margin</p>
                            <div className="panel">
                                <div className="panel-body">
                                    <div className="note-area note-area-margin">
                                        <textarea rows="10" className="form-control" defaultValue="Curabitur tellus mauris, fringilla sed tempus eget, convallis id orci."></textarea>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div className="card">
                        <div className="card-heading">Color pickers</div>
                        <div className="card-body">
                            <div className="row">
                                <div className="col-sm-6">
                                    <div className="panel">
                                        <div className="panel-heading">Picker 2x</div>
                                        <div className="panel-body">
                                            <div className="mda-form-control">
                                                <input id="cp-demo-basic" type="text" defaultValue="#5367ce" className="form-control"/>
                                                <div className="mda-form-control-line"></div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                                <div className="col-sm-6">
                                    <div className="panel">
                                        <div className="panel-heading">Hex format</div>
                                        <div className="panel-body">
                                            <div className="mda-form-control">
                                                <input id="cp-demo-hex" type="text" data-format="hex" defaultValue="#5367ce" className="form-control"/>
                                                <div className="mda-form-control-line"></div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div className="row">
                                <div className="col-sm-6">
                                    <div className="panel">
                                        <div className="panel-heading">Component</div>
                                        <div className="panel-body">
                                            <div id="cp-demo-component" className="mda-input-group input-group">
                                                <div className="mda-form-control">
                                                    <input type="text" defaultValue="#5367ce" className="form-control pl-sm"/>
                                                    <div className="mda-form-control-line"></div>
                                                </div><span className="input-group-addon bg-transparent b0"><i></i></span>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                                <div className="col-sm-6">
                                    <div className="panel">
                                        <div className="panel-heading">Bootstrap colors</div>
                                        <div className="panel-body">
                                            <div className="mda-form-control">
                                                <input id="cp-demo-bootstrap" type="text" data-format="hex" defaultValue="success" className="form-control"/>
                                                <div className="mda-form-control-line"></div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <br/><br/><br/><br/>
                </div>
            </section>
        );
    }
}

export default FormsAdvanced;

import React from 'react';
import pubsub from 'pubsub-js';

import './Wizard.scss';
import WizardRun from './Wizard.run';

class Wizard extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    componentDidMount() {
        WizardRun();
    }

    render() {
        return (
            <section>
                <div className="container container-md">
                    <div className="panel panel-default">
                        <div className="panel-heading">Basic Form (jquery.validate)</div>
                        <div className="panel-body">
                            <form id="example-form" action="#" className="form-validate hidden">
                                <div>
                                    <h4>Account
                                    </h4>
                                    <fieldset>
                                        <label htmlFor="userName">User name *</label>
                                        <input id="userName" name="userName" type="text" className="form-control required" />
                                        <label htmlFor="password">Password *</label>
                                        <input id="password" name="password" type="text" className="form-control required" />
                                        <label htmlFor="confirm">Confirm Password *</label>
                                        <input id="confirm" name="confirm" type="text" className="form-control required" />
                                        <p>(*) Mandatory</p>
                                    </fieldset>
                                    <h4>Profile
                                    </h4>
                                    <fieldset>
                                        <label htmlFor="name">First name *</label>
                                        <input id="name" name="name" type="text" className="form-control required" />
                                        <label htmlFor="surname">Last name *</label>
                                        <input id="surname" name="surname" type="text" className="form-control required" />
                                        <label htmlFor="email">Email *</label>
                                        <input id="email" name="email" type="text" className="form-control required email" />
                                        <label htmlFor="address">Address</label>
                                        <input id="address" name="address" type="text" className="form-control" />
                                        <p>(*) Mandatory</p>
                                    </fieldset>
                                    <h4>Hints
                                    </h4>
                                    <fieldset>
                                        <p className="lead text-center">Almost there!</p>
                                    </fieldset>
                                    <h4>Finish
                                    </h4>
                                    <fieldset>
                                        <p className="lead">One last check</p>
                                        <div className="checkbox c-checkbox">
                                            <label>
                                                <input type="checkbox" required="required" name="terms" /><span className="ion-checkmark-round"></span> I agree with the Terms and Conditions.
                                            </label>
                                        </div>
                                    </fieldset>
                                </div>
                            </form>
                        </div>
                    </div>
                    <div className="panel panel-default">
                        <div className="panel-heading">Basic Vertical Example</div>
                        <div className="panel-body">
                            <form id="example-vertical" className="hidden">
                                <h4>Keyboard</h4>
                                <section>
                                    <p>Try the keyboard navigation by clicking arrow left or right!</p>
                                </section>
                                <h4>Effects</h4>
                                <section>
                                    <p>Wonderful transition effects.</p>
                                </section>
                                <h4>Pager</h4>
                                <section>
                                    <p>The next and previous buttons help you to navigate through your content.</p>
                                </section>
                            </form>
                        </div>
                    </div>
                </div>
            </section>
        );
    }
}

export default Wizard;

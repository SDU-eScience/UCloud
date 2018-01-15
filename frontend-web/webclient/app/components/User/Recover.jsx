import React from 'react';
import { Link } from 'react-router'

import RecoverRun from './Recover.run';

class Recover extends React.Component {

    componentDidMount() {
        RecoverRun();
    }

    render() {
        return (
            <div className="container-full">
                <div className="container container-xs">
                    <div className="text-center mv-lg"><img src="img/user/01.jpg" className="img-circle mb block-center img-responsive thumb64" />
                        <p>Brian Walker</p>
                    </div>
                    <div id="confirmation" className="card bg-success ng-fadeIn hidden">
                        <div className="card-heading"><em className="ion-checkmark-round pull-right icon-lg"></em>
                            <h4 className="mt0">Mail Sent!</h4>
                        </div>
                        <div className="card-body">
                            <p>Please check your inbox and follow instructions to reset your password.</p>
                        </div>
                        <div className="card-footer text-right">
                            <Link to="login" className="btn btn-success btn-flat text-white">
                                Login
                                <em className="ion-ios-arrow-thin-right icon-fw"></em>
                            </Link>
                        </div>
                    </div>
                    <form id="user-recover" action="" name="loginForm" noValidate className="card form-validate">
                        <div className="card-heading">
                            <p className="text-center text-inherit">Fill with your mail to receive instructions on how to reset your password.</p>
                        </div>
                        <div className="card-offset pb0">
                            <div className="card-offset-item text-right ng-fadeInLeftShort">
                                <button type="submit" className="btn btn-success btn-circle btn-lg"><em className="ion-checkmark-round"></em></button>
                            </div>
                        </div>
                        <div className="card-body">
                            <div className="mda-form-group float-label mda-input-group">
                                <div className="mda-form-control">
                                    <input type="email" name="accountName" required="" className="form-control" />
                                    <div className="mda-form-control-line"></div>
                                    <label>Email address</label>
                                </div><span className="mda-input-group-addon"><em className="ion-ios-email-outline icon-lg"></em></span>
                            </div>
                        </div>
                    </form>
                </div>
            </div>
        );
    }
}

export default Recover;

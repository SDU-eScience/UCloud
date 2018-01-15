import React from 'react';
import { Link } from 'react-router'

import LockRun from './Lock.run';

class Lock extends React.Component {

    componentDidMount() {
        LockRun();
    }

    render() {
        return (
            <div className="container-full">
                <div className="container container-xs">
                    <div className="text-center mv-lg"><img src="img/user/01.jpg" className="img-circle mb block-center img-responsive thumb64" />
                        <p>Brian Walker</p>
                    </div>
                    <form id="user-lock" name="loginForm" noValidate className="card form-validate">
                        <div className="card-heading">
                            <p className="text-center">Type your password to unlock terminal</p>
                        </div>
                        <div className="card-offset pb0">
                            <div className="card-offset-item text-right ng-fadeInLeftShort">
                                <button type="submit" className="btn btn-success btn-circle btn-lg"><em className="ion-checkmark-round"></em></button>
                            </div>
                        </div>
                        <div className="card-body">
                            <div className="mda-form-group float-label mda-input-group">
                                <div className="mda-form-control">
                                    <input type="password" name="accountPassword" required="" className="form-control" />
                                    <div className="mda-form-control-line"></div>
                                    <label>Password</label>
                                </div>
                                <Link to="login" className="mda-form-msg right">Not you?</Link>
                                <span className="mda-input-group-addon">
                                    <em className="ion-ios-locked-outline icon-lg"></em>
                                </span>
                            </div>
                        </div>
                    </form>
                </div>
            </div>
        );
    }
}

export default Lock;

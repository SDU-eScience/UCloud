import React from 'react'

export default function (props) {
    if (props.loading) {
        return (
            <div className="row loader-primary">
                <div className="loader-demo">
                    <div className="loader-inner pacman">
                        <div/>
                        <div/>
                        <div/>
                        <div/>
                        <div/>
                    </div>
                </div>
            </div>)
    } else {
        return null;
    }
}
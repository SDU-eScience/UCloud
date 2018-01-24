import React from "react";

function TimePicker(props) {
    return <div/>
}

function DatePicker(props) {
    return (
        <div className="mda-form-control">
            <input type="text" name="start" value={props.defaultValue}
                   className="form-control"/>
            <div className="mda-form-control-line"/>
        </div>)
}

function DateRangePicker(props) {
    let now = new Date();
    let yesterday = now.setDate(now.getDate() - 1);
    return (
        <div className="rel-wrapper ui-datepicker ui-datepicker-popup dp-theme-success">
            <div className="input-daterange input-group mda-input-group">
                <DatePicker defaultValue={yesterday}/>
                <span className="input-group-addon">to</span>
                <DatePicker defaultValue={now}/>
            </div>
        </div>
    )
}


export {TimePicker, DatePicker, DateRangePicker}
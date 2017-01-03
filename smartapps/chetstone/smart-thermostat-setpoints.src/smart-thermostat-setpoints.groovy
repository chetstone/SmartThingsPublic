/**
 *  Smart Thermostat Setpoints
 *
 *  Copyright 2017 Chester Wood
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "Smart Thermostat Setpoints",
    namespace: "chetstone",
    author: "Chester Wood",
    description: "Queries the weather underground forecast to decide the thermostat setpoint",
    category: "Green Living",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png") {
    appSetting "apiKey"
}


preferences {
	section("Settings") {
        input "thermostat", "capability.thermostat", required: true, title: "Thermostat"
        input "cloudyset", "number", required: true, title: "Cloudy Day Setpoint"
        input "sunnyset", "number", required: true, title: "Sunny Day Setpoint" 
        input "threshold", "number", required: true, title: "Threshold (% sunny)" 
        input "runtime", "time", required: true, title: "Time to execute every day"
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"
	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def tomorrowHowSunny(def startHour, def endHour) {
    def sum = 0
    def count = 0
    Map sdata = getWeatherFeature('hourly', 'pws:KCOMOFFA6');
 
    if(sdata.response.containsKey('error') || sdata == null) {
    	sendNotificationEvent("Weather API error ${sdata?.response?.error}, setting setpoint assuming cloudy tomorrow")
        log.debug "Error ${sdata.response.error}"
        return 0
    }

    def fcst = sdata.hourly_forecast
    def today = fcst[0].FCTTIME.weekday_name

    for (int i=0; i < fcst.size(); i += 1) {
        if ( fcst[i].FCTTIME.weekday_name == today ) {
            continue
        }
        if ( fcst[i].FCTTIME.hour.toInteger() >= startHour) {
            if (fcst[i].FCTTIME.hour.toInteger() >= endHour) {
                break
            }
            count += 1
            sum += 100 - fcst[i].sky.toInteger()
            log.debug "hour: ${fcst[i].FCTTIME.hour} sunny: ${100 - fcst[i].sky.toInteger()}"
        }
    }
    if (count == 0 || sum == 0) {
        return 0
    }
    sum/count
}

def setSetpoint(howSunny) {
    def setpoint = howSunny > threshold ? sunnyset : cloudyset
    settings.thermostat.setHeatingSetpoint(setpoint)
    setpoint
}

def handler() {
    def sunniness = tomorrowHowSunny(9, 16)
    def setpoint = setSetpoint(sunniness)
    sendNotificationEvent(
        "Tomorrow will be sunny ${sunniness}% of the time. Setting setpoint to ${setpoint}℉")
    log.debug "handler called at ${new Date()}"
}

def initialize() {
    schedule(runtime, handler)
    log.debug tomorrowHowSunny(9, 16) 
}

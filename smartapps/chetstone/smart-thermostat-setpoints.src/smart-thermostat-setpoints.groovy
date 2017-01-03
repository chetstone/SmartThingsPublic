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
	section("Title") {
		// TODO: put inputs here
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

    def pickRange(def start, def end,  def fcst, block) {
        for (int i=0; i < fcst.size(); i += 1) {
            if ( fcst[i].FCTTIME.hour.toInteger() >= start) {
                if (fcst[i].FCTTIME.hour.toInteger() >= end) {
                    break
                }
                block([fcst[i].FCTTIME.hour,fcst[i].sky])
            }
        }
        
    }


def initialize() {
	// TODO: subscribe to attributes, devices, locations, etc.
    Map sdata = getWeatherFeature('hourly', 'pws:KCOMOFFA6');
 
    log.debug sdata.response    
    if(sdata.response.containsKey('error') || sdata == null) {
    	log.debug "Weather API error, skipping weather check"
        return false
    }
    def fcst =sdata.hourly_forecast


    pickRange(9, 15, fcst) {
        log.debug it
    }

}

// TODO: implement event handlers
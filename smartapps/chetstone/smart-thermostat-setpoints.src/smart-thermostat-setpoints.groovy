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
    appSetting "account0"
    appSetting "password0"
    appSetting "url0"
    appSetting "account1"
    appSetting "password1"
    appSetting "url1"


}


preferences {
	section("Settings") {
        input "thermostat", "capability.thermostat", required: true, title: "Thermostat"
        input "cloudyset", "number", required: true, title: "Cloudy Day Setpoint"
        input "sunnyset", "number", required: true, title: "Sunny Day Setpoint" 
        //input "testSunny", "number", required: true, title: "Fake predicted sunniness" 
        input "cloudy_threshold", "number", required: true, title: "Force cloudy setpoint (% sunny)" 
        input "sunny_threshold", "number", required: true, title: "Force sunny setpoint (% sunny)" 

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

def parseHttpResponse(response) {
	def code = response.getStatus();
    log.debug "HTTP Response: ${code}"
//            response.headers.each {
//            log.debug "${it.name} : ${it.value}"
//        }
	log.debug "HTTP Data: ${response.data}"
    if (code > 399) {
    	def dataStr = response.data;
    	sendPush("TemperatureLogger got HTTP response ${code}")
    }

}

def writeChannelData( value) {
    def now = new Date()
    def id = now.format("yyyy-MM-dd'T'HH:mm:ss'Z'") + ".Notes"
    log.debug(id )
	

	def uri = "https://${appSettings.account0}:${appSettings.password0}@${appSettings.url0}";
	def json = "{\"docs\":[{\"_id\":\"${id}\",\"predictions\":\"From groovy\",\"wxunderground\":${value}}]}";
	def headers = [
        "Content-Type" : "application/json"
    ];
	def params = [
        uri: uri,
        headers: headers,
        body: json,
        path: "_bulk_docs"
    ];
  
    log.debug(params)
    try {
        httpPostJson(params) {response -> parseHttpResponse(response)}
    }
    catch(e) {
        log.debug "httpPost threw ${e}"
        }
    // Also post to Digital Ocean
    uri = "https://${appSettings.account1}:${appSettings.password1}@${appSettings.url1}"
    
    params = [
        uri: uri,
        headers: headers,
        body: json,
        path: "_bulk_docs"
    ]
    log.debug(params)
    try {
        httpPostJson(params) {response -> parseHttpResponse(response)}
    }
    catch(e) {
          log.debug "httpPost threw ${e}"
    }
}

// 



def tomorrowHowSunny() {
	def startHour = 8
	def endHour = 16
    def sum = 0
    def count = 0
    def couch_count = 0
    def couch_sum = 0
    Map sdata = getWeatherFeature('hourly', 'pws:KCOCREST1');
 
    if(sdata == null || sdata.response == null || sdata.response.containsKey('error') ) {
    	sendNotificationEvent("Weather API error ${sdata?.response?.error}, setting setpoint assuming cloudy tomorrow")
        log.debug "Error ${sdata.response.error}"
        return 0
    }
	log.debug "Hourly Forecast: ${sdata.hourly_forecast[0].FCTTIME}"
    def fcst = sdata.hourly_forecast
    def today = fcst[0].FCTTIME.weekday_name
    // weight mornings higher
    def times = [startHour, 9, 9, 10, 10, 10, 11, 11, 12, 13, 14, 15, endHour]

    for (int i=0; i < fcst.size(); i += 1) {
        if ( fcst[i].FCTTIME.weekday_name == today ) {
            continue
        }
        if ( fcst[i].FCTTIME.hour.toInteger() >= startHour) {
            if (fcst[i].FCTTIME.hour.toInteger() >= endHour) {
                break
            }
            for (int j=0; j < times.size(); j += 1) {
            	if (fcst[i].FCTTIME.hour.toInteger() == times[j]) {
		            count += 1
        		    sum += 100 - fcst[i].sky.toInteger()
            //		log.debug "hour: ${fcst[i].FCTTIME.hour} sunny: ${100 - fcst[i].sky.toInteger()}"
                }
            }
            // get unscaled sum for pushing to couchdb
            couch_count += 1
            couch_sum += 100 - fcst[i].sky.toInteger()
           // log.debug "hour: ${fcst[i].FCTTIME.hour} sunny: ${100 - fcst[i].sky.toInteger()}"
        }
    }
    if (count == 0 || sum == 0) {
    log.debug "Returning 0 and not sending to couch"
        return 0
    }
    log.debug "Unscaled prediction: ${couch_sum/couch_count}"
    writeChannelData(couch_sum/couch_count)
    sum/count
}

def setSetpoint(howSunny) {
    def setpoint = howSunny >= sunny_threshold ? sunnyset :
    howSunny <= cloudy_threshold ? cloudyset : 
    	cloudyset + ((howSunny - cloudy_threshold)/(sunny_threshold - cloudy_threshold)*(sunnyset - cloudyset))
    setpoint = setpoint.toInteger()
    log.debug "Setpoint: ${setpoint}"
    settings.thermostat.setHeatingSetpoint(setpoint)
    setpoint
}

def handler() {
    float sunniness = tomorrowHowSunny()
    int setpoint = setSetpoint(sunniness)
    sendNotificationEvent(
        "Tomorrow will be sunny ${sunniness.round()}% of the time. Setting setpoint to ${setpoint}℉")
    log.debug "handler called at ${new Date()}"
}

def initialize() {
    schedule(runtime, handler)
    float sunny = tomorrowHowSunny()
    setSetpoint(sunny)
    log.debug "${sunny.round()}"
}

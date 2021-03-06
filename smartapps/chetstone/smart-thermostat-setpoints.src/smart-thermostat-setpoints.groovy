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
        input "vacationCloudySet", "number", required: true, title: "Vacation Cloudy Day Setpoint"
        input "vacationSunnySet", "number", required: true, title: "Vacation Sunny Day Setpoint" 

       
        //input "testSunny", "number", required: true, title: "Fake predicted sunniness" 
        input "cloudy_threshold", "number", required: true, title: "Force cloudy setpoint (% sunny)" 
        input "sunny_threshold", "number", required: true, title: "Force sunny setpoint (% sunny)" 

        input "runtime", "time", required: true, title: "Time to execute every day"
	}
    section("Start Date") {
      input name: "startDay", type: "number", title: "Day", required: false, range: "1..31"
      input name: "startMonth", type: "enum", title: "Month", required: false, 
          options: ["January", "February", "March","April","May","June","July","August","September","October","November","December"]
      input name: "startYear", type: "number", description: "Format(yyyy)", title: "Year", required: false, range: "2019..2049", defaultValue: "2019"
//      input name: "startTime", type: "time", title: "Start Time", description: null, required: false
    }
    section("End Date") {
      input name: "endDay", type: "number", title: "Day", required: false, range: "1..31"
      input name: "endMonth", type: "enum", title: "Month", required: false, 
          options: ["January", "February", "March","April","May","June","July","August","September","October","November","December"]
      input name: "endYear", type: "number", description: "Format(yyyy)", title: "Year", required: false, range: "2019..2049", defaultValue: "2019"
//      input name: "endTime", type: "time", title: "End Time", description: null, required: false 
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

public smartThingsDateFormat() { "yyyy-MM-dd'T'HH:mm:ss.SSSZ" }

def startDate() {
def month = startMonth 
  if (startDay && month && startYear) {
    return Date.parse("yyyy-MMMMM-dd", "${startYear}-${startMonth}-${startDay}")
  } else {
    // Start Date Time not set
    return false
  }
}

def endDate() {
def month = endMonth 
  if (endDay && month && endYear) {
     return Date.parse("yyyy-MMMMM-dd", "${endYear}-${endMonth}-${endDay}")
  } else {
    // End Date Time not set
    return false
  }
}
def todayDateOnly()  {
def today = timeToday("00:00", location.timeZone)
return today.clearTime()
}

def getSetPoints() {
    def cloudy = cloudyset
    def sunny = sunnyset
    def today = todayDateOnly()
    // We're going to be calling this smartapp the day before the thermostat setting takes effect,
    // so subtract one from the leaving or returning date
    if ( today > (startDate() -1) && today < (endDate() -1) ) {
        cloudy = vacationCloudySet
        sunny = vacationSunnySet
    } else if ( (startDate() - 1) == today || (endDate() - 1) == today ) {
        // leaving or returning tomorrow, so split the difference
        cloudy = (vacationCloudySet + cloudyset)/2
        sunny = (vacationSunnySet + sunnyset)/2
    }
    return ["cloudy": cloudy, "sunny": sunny]
}

def parseHttpResponse(response) {
	def code = response.getStatus();
    log.debug "HTTP Response: ${code}"
//            response.headers.each {
//            log.debug "${it.name} : ${it.value}"
//        }
//	log.debug "HTTP Data: ${response.data}"
    if (code > 399) {
    	def dataStr = response.data;
    	sendPush("TemperatureLogger got HTTP response ${code}")
    }

}

def writeChannelData( wxu, twc) {
    def now = new Date()
    def id = now.format("yyyy-MM-dd'T'HH:mm:ss'Z'") + ".Notes"
//    log.debug(id )
	

	def uri = "https://${appSettings.account0}:${appSettings.password0}@${appSettings.url0}";
    def twcString = (twc > 100) ? "": ",\"twcdaily\":${twc}"
    def wxuString = (wxu > 100) ? "": ",\"wxunderground\":${wxu}"
  
	def json = "{\"docs\":[{\"_id\":\"${id}\",\"predictions\":\"From groovy\"${wxuString}${twcString}}]}";
    
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
    // uri = "https://${appSettings.account1}:${appSettings.password1}@${appSettings.url1}"
    
    params = [
        uri: uri,
        headers: headers,
        body: json,
        path: "_bulk_docs"
    ]
    // log.debug(params)
    // try {
    //    httpPostJson(params) {response -> parseHttpResponse(response)}
    // }
    // catch(e) {
    //      log.debug "httpPost threw ${e}"
    // }
}

def tomorrowHowSunny() {
	def startHour = 8
	def endHour = 16
    def sum = 0
    def count = 0
    def couch_count = 0
    def couch_sum = 0
    def twcClouds = -1

    // Get TWC data
    Map twc = getTwcForecast()
    if (twc == null) {
        sendNotification("TWC API returned null")
    } else {
        def dayname = twc.daypart[0]['daypartName'][2]
        if (dayname == 'Tomorrow') {
            twcClouds = twc.daypart[0]['cloudCover'][2]
            //log.debug "Cloud Cover tomorrow is ${twcClouds}"
            sendNotification("TWC Sunniness tomorrow is ${100-twcClouds}")
        } else {
            sendNotification("Error in TWC data");
            log.debug "Unexpected data in twc.daypart[0][daypartName][2]. Got ${dayname}"
        }
    }
    //log.debug "Unscaled prediction: ${couch_count == 0 ? "Unavailable" :couch_sum/couch_count}"
    writeChannelData(couch_count == 0 ? 101 : couch_sum/couch_count, (100-twcClouds))
    
    if (count == 0 || sum == 0) {
        if (twcClouds == -1) {
            log.debug "Returning 0-- No TWC data"
            return 0
        } else {
            return 100 - twcClouds
        }
    }
    sum/count
}

def setSetpoint(howSunny) {
    def sunnySetPoint = getSetPoints().sunny
    def cloudySetPoint = getSetPoints().cloudy
    log.debug("sunnySetPoint is ${sunnySetPoint}, cloudySetPoint is ${cloudySetPoint}, getSetPoints is ${getSetPoints()}")
    def setpoint = howSunny >= sunny_threshold ? sunnySetPoint :
    howSunny <= cloudy_threshold ? cloudySetPoint : 
    	cloudySetPoint + ((howSunny - cloudy_threshold)/(sunny_threshold - cloudy_threshold)*(sunnySetPoint - cloudySetPoint))
    setpoint = setpoint.toInteger()
    //log.debug "Setpoint: ${setpoint}"
    settings.thermostat.setHeatingSetpoint(setpoint)
    setpoint
}

def handler() {
    float sunniness = tomorrowHowSunny()
    int setpoint = setSetpoint(sunniness)
    sendNotification(
        "Tomorrow will be sunny ${sunniness.round()}% of the time. Setting setpoint to ${setpoint}℉")
    log.debug "handler called at ${new Date()}"
}

def initialize() {
    schedule(runtime, handler)
//    log.debug "Now is ${todayDateOnly().format(smartThingsDateFormat())}, " +
//    "Start Time is ${startDate().format(smartThingsDateFormat())}, " +
//    "End Time is ${endDate().format(smartThingsDateFormat())}"
    float sunny = tomorrowHowSunny()
    setSetpoint(sunny)
    log.debug "${sunny.round()}"
}

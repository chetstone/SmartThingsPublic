/**
 *  TemperatureLogger. Writes temperature data to Xively datastream.
 *  See http://build.smartthings.com/projects/xively/ for more information.
 *
 *  Author: @kernelhack
 *  Date: 2014-01-01
 */
 


// Automatically generated. Make future change here.
definition(
    name: "TemperatureLogger",
    namespace: "chetstone",
    author: "chet@dewachen.org",
    description: "Log temps from Multi Sensor",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience%402x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience%402x.png",
    oauth: true)

preferences {
    section("Configure") {
        input "Thermostat", "capability.temperatureMeasurement", title: "Which Thermostat"
        //        input "InOffset", "text", title: "Thermostat Correction offset"
        input "OutTemp", "capability.temperatureMeasurement", title: "Which WineTemp"
        input "OutHumidity", "capability.relativeHumidityMeasurement", title: "Which WineHumidity"
        
	}
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    unsubscribe()
    unschedule()
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    // Subscribe to attributes, devices, locations, etc.
    //subscribe(OutTemp.temperature)
    //subscribe(OutHumidity.humidity)

    // schedule cron job to run every 10 minutes on the 4's
    runEvery10Minutes(cronJob)
}

def parseHttpResponse(response) {
    log.debug "HTTP Response: ${response}"
}

def writeChannelData( thermostat, outtemp, outhumid,  id) {

    def uri = "https://account0:password0@url0"
    def json = "{\"docs\":[{\"_id\":\"${id}\",\"Thermostat\":${thermostat},\"OutTemp\":${outtemp},\"OutHumidity\":${outhumid}}]}"

    def headers = [
        "Content-Type" : "application/json"
    ]

    def params = [
        uri: uri,
        headers: headers,
        body: json,
        path: "_bulk_docs"
    ]
    log.debug(params)

    httpPostJson(params) {response -> parseHttpResponse(response)}

    // Also post to Crystal
    uri = "http://account1:password1@url1"
    params = [
        uri: uri,
        headers: headers,
        body: json,
        path: "_bulk_docs"
    ]
    log.debug(params)

    httpPostJson(params) {response -> parseHttpResponse(response)}
}

// Post current temperature to the remote web service
def cronJob() {

    def now = new Date()
    def id = now.format("yyyy-MM-dd'T'HH:mm:ss'Z'") + ".ST"
    log.debug(id )
	
    //def thermostat = getThermostat()
    def thermostat = Thermostat.currentValue("temperature")
    def outTemp = OutTemp.currentValue("temperature")
    def outHumid = OutHumidity.currentValue("humidity")
    writeChannelData(thermostat, outTemp, outHumid, id)
}

// Handle temperature event
def temperature(evt) {

    log.debug "Temperature event: $evt.value"

}

// Handle humidity event
def humidity(evt) {

    log.debug "Humidity event: $evt.value"

}

def getThermostat() {
def t
  try { 
    t = (Thermostat.currentValue("temperature").toInteger() + InOffset.toInteger()).toString()
  }
  catch (e) {
    log.debug "getThermostat exception ${e}"
    t = Thermostat.currentValue("temperature")
  }
  return t
}


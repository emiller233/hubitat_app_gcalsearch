/**
 *  Copyright 2020 Michael Ritchie
 *  Thank you to Mike Nestor & Anthony Pastor for their contributions to the GCal Event Sensor which some code from this driver leverages
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

metadata {
	definition (name: "GCal Switch", namespace: "mlritchie", author: "Michael Ritchie") {
		capability "Sensor"
        capability "Polling"
		capability "Refresh"
        capability "Switch"
        
        attribute "eventTitle", "string"
        attribute "eventLocation", "string"
        attribute "eventStartTime", "string"
        attribute "eventEndTime", "string"
        attribute "eventAllDay", "bool"
	}
    
    preferences {
		input name: "isDebugEnabled", type: "bool", title: "Enable debug logging?", defaultValue: false, required: false
        input name: "switchValue", type: "enum", title: "Switch Default Value", required: true, options:["on","off"]
    }
}

def installed() {
    logDebug "GCal Switch: installed()"
    on()
    initialize()
}

def updated() {
    logDebug "GCal Switch: updated()"   
	initialize()
}

def initialize() {
    refresh()
}

def parse(String description) {

}

// refresh status
def refresh() {
    logDebug "GCal Switch: refresh()"
    poll() // and do one now
}

def poll() {
    logDebug "GCal Switch: poll()"
    def result = []
    def item = parent.getNextEvents()
    def isOn = device.currentValue("switch") == switchValue
    if (item && item.eventTitle) {
        logDebug "GCal Switch: event found, item: ${item}"
        result << sendEvent(name: "eventTitle", value: item.eventTitle )
        result << sendEvent(name: "eventLocation", value: item.eventLocation )
        result << sendEvent(name: "eventAllDay", value: item.eventAllDay )
        result << sendEvent(name: "eventStartTime", value: item.eventStartTime )
        result << sendEvent(name: "eventEndTime", value: item.eventEndTime )
        
        if (isOn) {
            off()
        }
    } else {
        logDebug "GCal Switch: no events found, isOn: ${isOn}"
        if (!isOn) {
            on()
        }
    }
    
    return result
}

def on() {
    logDebug "GCal Switch: on()"
    def result = []
    result << sendEvent(name: "switch", value: switchValue)
    result << sendEvent(name: "eventTitle", value: " ")
    result << sendEvent(name: "eventLocation", value: " ")
    result << sendEvent(name: "eventAllDay", value: " ")
    result << sendEvent(name: "eventStartTime", value: " ")
    result << sendEvent(name: "eventEndTime", value: " ")
    
    return result
}

def off() {
    def offValue = switchValue == "on" ? "off" : "on"
    logDebug "GCal Switch: off()"
    sendEvent(name: "switch", value: offValue)
}

private logDebug(msg) {
	if (isDebugEnabled) {
		log.debug "$msg"
	}
}

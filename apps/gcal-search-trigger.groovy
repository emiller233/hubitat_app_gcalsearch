/**
 *  Copyright 2017 Mike Nestor & Anthony Pastor & Michael Ritchie
 *  Hubitat conversion by cometfish.
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
    name: "GCal Search Trigger",
    namespace: "mnestor",
    author: "Mike Nestor and Anthony Pastor. Hubitat conversion by cometfish. Further edits by mlritchie",
    description: "Creates & Controls virtual contact (event) or presence sensors.",
    category: "My Apps",
    parent: "mnestor:GCal Search",
    iconUrl: "https://raw.githubusercontent.com/mnestor/GCal-Search/icons/icons/GCal.png",
    iconX2Url: "https://raw.githubusercontent.com/mnestor/GCal-Search/icons/icons/GCal%402x.png",
    iconX3Url: "https://raw.githubusercontent.com/mnestor/GCal-Search/icons/icons/GCal%402x.png",
) {}

preferences {
	page(name: "selectCalendars")
}

private version() {
	def text = "20200306.1"
}

def selectCalendars() {
	logDebug "selectCalendars()"
    
    def calendars = parent.getCalendarList()
    logDebug "Calendar list = ${calendars}"
    
    return dynamicPage(name: "selectCalendars", title: "Create new calendar search", install: true, uninstall: true, nextPage: "" ) {
    	section("Required Info") {                               
               //we can't do multiple calendars because the api doesn't support it and it could potentially cause a lot of traffic to happen
            input name: "watchCalendars", title:"", type: "enum", required:true, multiple:false, description: "Which calendar do you want to search?", options:calendars, submitOnChange: true
            input name: "search", type: "text", title: "Search String", required: true, submitOnChange: true  
        }
        
        if ( settings.search ) {
            section("Preferences") {
                input name: "timeToRun", type: "time", title: "Time to run", required: true
                def defName = settings.search - "\"" - "\"" //.replaceAll(" \" [^a-zA-Z0-9]+","")
                input name: "deviceName", type: "text", title: "Device Name", required: true, multiple: false, defaultValue: "${defName} Switch"
                input name: "switchValue", type: "enum", title: "Switch Default Value", required: true, defaultValue: "on", options:["on","off"]
                input name: "appName", type: "text", title: "Trigger Name", required: true, multiple: false, defaultValue: "${defName}", submitOnChange: true
                input name: "isDebugEnabled", type: "bool", title: "Enable debug logging?", defaultValue: false, required: false
            }
        }
            
        if ( state.installed ) {
	    	section ("Remove Trigger and Corresponding Device") {
            	paragraph "ATTENTION: The only way to uninstall this trigger and the corresponding device is by clicking the button below.\n" +                		
                		  "Trying to uninstall the corresponding device from within that device's preferences will NOT work."
            }
    	}   
	}       
}

def installed() {
	logDebug "Installed with settings: ${settings}"
	initialize()
    
    log.debug "switchValue: ${switchValue}"
}

def updated() {
	logDebug "Updated with settings: ${settings}"
	unschedule()
    
	initialize()
}

def initialize() {
    logDebug "initialize()"
    state.installed = true
   	
    // Sets Label of Trigger
    app.updateLabel(settings.appName)
    
    state.deviceID = "GCal_${app.id}"
    def childDevice = getChildDevice(state.deviceID)
    if (!childDevice) {
		logDebug("creating device: deviceID: ${state.deviceID}")
        childDevice = addChildDevice("mlritchie", "GCal Switch", "GCal_${app.id}", null, [name: "GCal Switch", label: deviceName])
		childDevice.updateSetting("isDebugEnabled",[value:"${isDebugEnabled}",type:"bool"])
        childDevice.updateSetting("switchValue",[value:"${switchValue}",type:"enum"])
        logDebug("created device: ${state.deviceID}")
    }

    schedule(timeToRun, poll)
}

def getNextEvents() {
    logDebug "GCalSearchTrigger: getNextEvents() child"
    def search = (!settings.search) ? "" : settings.search
    def items = parent.getNextEvents(settings.watchCalendars, search)
    def item = []
    
    if (items.size() > 0) {
        def searchTerms = search.toString().split(",")
        def foundMatch = false
        for (int s = 0; s < searchTerms.size(); s++) {
            def searchTerm = searchTerms[s]
            for (int i = 0; i < items.size(); i++) {
                def eventTitle = items[i].eventTitle
                
                if (searchTerm.indexOf("*") > -1) {
                    def searchList = searchTerm.toString().split("\\*")
                    for (int sL = 0; sL < searchList.size(); sL++) {
                        def searchItem = searchList[sL]
                        if (eventTitle.indexOf(searchItem) > -1) {
                            foundMatch = true
                        } else {
                            foundMatch = false
                            break
                        }
                    }
                    
                    if (foundMatch) {
                        item = items[i]
                        break
                    }
                } else {
                    if (eventTitle.startsWith(searchTerm)) {
                        foundMatch = true
                        item = items[i]
                        break
                    }
                }
            }
            
            if (foundMatch) {
                break
            }
        }
        
    }
    
    return item
}

def poll() {
	logDebug "GCalSearchTrigger::poll()"
    def childDevice = getChildDevice(state.deviceID)
    childDevice.poll()
}

private uninstalled() {
    logDebug "uninstalled():"
    
    logDebug "Delete all child devices."    
	deleteAllChildren()
}

private deleteAllChildren() {
    logDebug "deleteAllChildren():"
    
    getChildDevices().each {
    	logDebug "Delete $it.deviceNetworkId"
        try {
            deleteChildDevice(it.deviceNetworkId)
        } catch (Exception e) {
            log.error "Fatal exception? $e"
        }
    }
}

private childCreated() {
    def isChild = getChildDevice("GCal_${app.id}") != null
    logDebug "childCreated? ${isChild}"
    return isChild
}

private textVersion() {
    def text = "Trigger Version: ${ version() }"
}
private dVersion(){
	def text = "Device Version: ${getChildDevices()[0].version()}"
}

private logDebug(msg) {
	if (isDebugEnabled) {
		log.debug "$msg"
	}
}

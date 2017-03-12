/**
 *  Copyright 2017 Tomas Axerot
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */

definition(
    name: "Alarm Proxy",
    namespace: "tomasaxerot",
    author: "Tomas Axerot",
    description: "TODO",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

preferences {
    section("Select away sensors:") {
    	input "awayMotionIn", "capability.motionSensor", required:false, multiple: true, title: "Away motion sensors"
        input "awayContactIn", "capability.contactSensor", required:false, multiple: true, title: "Away contact sensors"
    }
    section("Select stay sensors:") {
    	input "stayMotionIn", "capability.motionSensor", required:false, multiple: true, title: "Stay motion sensors"
        input "stayContactIn", "capability.contactSensor", required:false, multiple: true, title: "Stay contact sensors"
    }        
    section("Select output sensor:") {
    	input "sensorOut", "device.virtualMultiSensor", required:true, title: "Output sensor"        
    }
    section("Entry Delay Configuration") {
    	input "entryDelay", "number", required: true, title: "Seconds", description: "Delay alarm entry by this many seconds", range: "0..120"        	
        input "entrySendPush", "bool", required: false, title: "Send Push Notification when entry delay starts?"
        input "entrySirens", "capability.alarm", required: false, title: "Beep with siren when entry delay starts?", multiple: true
    }   
}

def installed() {
    log.debug "installed: settings: ${settings}"    
    initialize()
}

def updated() {
    log.debug "updated: settings: ${settings}"
    unsubscribe()    
    initialize()
}

def initialize() {
	log.trace "initialize"
	atomicState.isEntryDelayStarted = false    
    atomicState.alarmSystemStatus = location.currentState("alarmSystemStatus")?.value
    subscribe(location, "alarmSystemStatus" , alarmSystemStatusHandler)
    
    syncSensorStates()          
    initializeSensors()	
}

/*******************************************************************************/

def initializeSensors() {	
	log.trace "initializeSensors"
    
    if (settings.awayMotionIn) {        
        subscribe(awayMotionIn, "motion.active", motionActiveHandler)
    	subscribe(awayMotionIn, "motion.inactive", motionInactiveHandler)
    }
    if (settings.stayMotionIn) {        
        subscribe(stayMotionIn, "motion.active", motionActiveHandler)
    	subscribe(stayMotionIn, "motion.inactive", motionInactiveHandler)
    }
    if (settings.awayContactIn) {        
        subscribe(awayContactIn, "contact.open", contactOpenHandler)
    	subscribe(awayContactIn, "contact.closed", contactClosedHandler)
    }
    if (settings.stayContactIn) {        
        subscribe(stayContactIn, "contact.open", contactOpenHandler)
    	subscribe(stayContactIn, "contact.closed", contactClosedHandler)
    }
}

def syncSensorStates() {
	log.trace "syncSensorStates"
    
    if(anyMotionActive())
    	activateVirtualSensor()
    else
    	inactivateVirtualSensor()
        
    if(anyContactOpen())
    	openVirtualSensor()
    else
    	closeVirtualSensor()
}

def anyMotionActive() {
	log.trace "anyMotionActive"
    
    if(awayMotionIn && isAway() &&awayMotionIn.any {it.currentMotion == "active" }) {
    	log.debug "anyMotionActive: awayMotionIn is active"
        return true
    }
        
    if(stayMotionIn && isStay() && stayMotionIn.any {it.currentMotion == "active" }) {
    	log.debug "anyMotionActive: stayMotionIn is active"
        return true
    }
    
    return false
}

def anyContactOpen() {
	log.trace "anyContactOpen"
    
	if(awayContactIn && isAway() && awayContactIn.any {it.currentContact == "open" }) {
    	log.debug "anyContactOpen: awayContactIn is open"
        return true
    }
        
    if(stayContactIn && isStay() && stayContactIn.any {it.currentContact == "open" }) {
    	log.debug "anyContactOpen: stayContactIn is open"
        return true
    }
    
    return false
}

def motionActiveHandler(evt) {
	log.trace "motionActiveHandler: $evt.device"
    
    if(trigger(evt.device)){
    	entry()
    	runIn(entryDelay, "activateVirtualSensor")
    }    
}

def motionInactiveHandler(evt) {
	log.trace "motionInactiveHandler: $evt.device"
    
    runIn(entryDelay, "inactivateVirtualSensor")    
}

def contactOpenHandler(evt) {
	log.trace "contactOpenHandler: $evt.device"	        
    
    if(trigger(evt.device)) {
    	entry()
        runIn(entryDelay, "openVirtualSensor")
    }    
}

def contactClosedHandler(evt) {
	log.trace "contactClosedHandler: $evt.device"  
    
    runIn(entryDelay, "closeVirtualSensor")    
}

def activateVirtualSensor() {
	log.trace "activateVirtualSensor"    
	
    if(sensorOut.currentMotion == "active")
    	return
    
    log.debug "activateVirtualSensor: set $sensorOut.displayName to active"
    sensorOut.active()
}

def inactivateVirtualSensor() {
	log.trace "inactivateVirtualSensor"    
    
    if(sensorOut.currentMotion == "inactive")
    	return
        
    if(anyMotionActive())
    	return        
    
    log.debug "inactivateVirtualSensor: set $sensorOut.displayName to inactive"
	sensorOut.inactive()
}

def openVirtualSensor() {
	log.trace "openVirtualSensor"
    
    if(sensorOut.currentContact == "open")
    	return
    
    log.debug "openVirtualSensor: set $sensorOut.displayName to open"
	sensorOut.open()
}

def closeVirtualSensor() {
	log.trace "closeVirtualSensor"   
    
    if(sensorOut.currentContact == "closed")
    	return
        
    if(anyContactOpen())
    	return       
    
    log.debug "closeVirtualSensor: set $sensorOut.displayName to closed"
	sensorOut.close()
}

/*******************************************************************************/

def alarmSystemStatusHandler(evt) {
	log.trace "alarmSystemStatusHandler: ${evt.value}"   
    atomicState.alarmSystemStatus = evt.value
    
    //TODO
    //if(state is off)
    	//cancel any runIns
        //syncStates
}

def isAway() {
	return atomicState.alarmSystemStatus == "away"
}

def isStay() {
	return atomicState.alarmSystemStatus == "stay"
}

def trigger(device) {
	if(isAway()) {    	
    	if(awayMotionIn && awayMotionIn.find { it.id == device.id }) {
    		log.debug "trigger: $device.displayName is triggering for away"
        	return true
    	}    
        if(awayContactIn && awayContactIn.find { it.id == device.id }) {
    		log.debug "trigger: $device.displayName is triggering for away"
        	return true
    	}    
    }
    if(isStay()) {
    	if(stayMotionIn && stayMotionIn.find { it.id == device.id }) {
    		log.debug "trigger: $device.displayName is triggering for stay"
        	return true
    	}
        
        if(stayContactIn && stayContactIn.find { it.id == device.id }) {
    		log.debug "trigger: $device.displayName is triggering for stay"
        	return true
    	}  
    }
    
    log.trace "trigger: $device.displayName is not triggering"
    return false 
    
}

def resetEntryDelay() {
	log.debug "resetEntryDelay"
    atomicState.isEntryDelayStarted = false
}

def entry() {
	log.debug "entry"    
	
	if(!atomicState.isEntryDelayStarted) {
        atomicState.isEntryDelayStarted = true
        atomicState.entryDelayStarted = now()
        
        beepEntrySiren()
        sendEntryNotification()
        
        runIn(entryDelay, "resetEntry")       
    }
}

def resetEntry() {
	log.debug "resetEntry"		
    atomicState.isEntryDelayStarted = false
}

def beepEntrySiren() {
	if(entrySirens) {
    	log.debug "beepEntrySiren"
    	entrySirens.siren()  	    	
        entrySirens.off()       
    }    
}

def sendEntryNotification() {	
	if (entrySendPush) {
    	log.debug "sendEntryNotification"
    	def message = "Entry delay started, disarm within $entryDelay seconds"
        sendPush(message)
    }
}
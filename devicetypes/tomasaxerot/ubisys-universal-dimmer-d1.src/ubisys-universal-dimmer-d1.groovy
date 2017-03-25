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
 *
 */
 
 /**
  * Warning
  * I would consider this DTH in alpha state atm
  * 
  * Version: 
  * 0.2 (2017-03-25): Added configuration and possibility to set phase control, also logging Dimmer setup(capabilities, status and mode) on refresh
  * 0.1 (2017-03-24): Initial version of DTH for Ubisys Universal Dimmer D1 (Rev. 1), Application: 1.12, Stack: 1.88, Version: 0x010C0158
  * 
  */

metadata {
    definition (name: "Ubisys Universal Dimmer D1", namespace: "tomasaxerot", author: "Tomas Axerot") {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        capability "Power Meter"
        capability "Sensor"
        capability "Switch"
        capability "Switch Level"
        capability "Health Check"
        capability "Light"

		//0000 = Basic
        //0003 = Identify
        //0004 = Groups
        //0005 = Scenes
        //0006 = On/off        
        //0008 = Level control
        //0301 = ballast
        //0702 = Metering
		//0B04 = Electrical Measurement
        //FC01 = Dimmer Setup, Manufacturer configuration

		//Raw
		//01 0104 0101 00 08 0000 0003 0004 0005 0006 0008 0301 FC01 00
        //model: D1 (5503), manufacturer: ubisys
        //Dimmable Light (0x0101)
        //Endpoint 1: in: 0000, 0003, 0004, 0005, 0006, 0008, 0301 FC01        
        //Endpoint 2: in: 0000, 0003, out: 0005, 0006, 0008 (input 1)
        //Endpoint 3: in: 0000, 0003, out: 0005, 0006, 0008 (input 2)
        //Endpoint 4: in: 0702, 0B04        
        
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0008, 0702", outClusters: "0005, 0006, 0008", manufacturer: "ubisys", model: "D1 (5503)", deviceJoinName: "Ubisys Universal Dimmer D1"
    }

    tiles(scale: 2) {
        multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#00A0DC", nextState:"turningOff"
                attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"turningOn"
                attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#00A0DC", nextState:"turningOff"
                attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"turningOn"
            }
            tileAttribute ("device.level", key: "SLIDER_CONTROL") {
                attributeState "level", action:"switch level.setLevel"
            }
            tileAttribute ("power", key: "SECONDARY_CONTROL") {
                attributeState "power", label:'${currentValue} W'
            }
        }
        
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        
        standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
		}
        
        main "switch"
        details(["switch", "refresh", "configure"])
    }
    
    preferences {
        //input name: "email", type: "email", title: "Email", description: "Enter Email Address", required: true, displayDuringSetup: true
        //input name: "text", type: "text", title: "Text", description: "Enter Text", required: true
        //input name: "number", type: "number", title: "Number", description: "Enter number", required: true
        //input name: "bool", type: "bool", title: "Bool", description: "Enter boolean", required: true
        //input name: "password", type: "password", title: "password", description: "Enter password", required: true
        //input name: "phone", type: "phone", title: "phone", description: "Enter phone", required: true
        //input name: "decimal", type: "decimal", title: "decimal", description: "Enter decimal", required: true
        //input name: "time", type: "time", title: "time", description: "Enter time", required: true
        input name: "phaseControl", type: "enum", title: "Phase Control", options: ["Auto", "Leading", "Trailing"], description: "Enter Phase Control, Auto is recommended", required: false
    }
}

// Parse incoming device messages to generate events
def parse(String description) {
    log.trace "parse: description is $description"	

	def event = zigbee.getEvent(description)
	if (event) {
    	log.trace "parse: event is $event.name"
		if (event.name == "power") {			
            def descriptionText = '{{ device.displayName }} power is {{ value }} Watts'
			return createEvent(name: event.name, value: event.value, descriptionText: descriptionText, translatable: true)
		} 
        
        return createEvent(event)        
	} 
    
    def cluster = zigbee.parse(description)
    if(cluster) {
    	log.trace "parse: cluster is $cluster"

		if (cluster && cluster.clusterId == 0x0006 && cluster.command == 0x07) {
			if (cluster.data[0] == 0x00) {
				log.debug "ON/OFF REPORTING CONFIG RESPONSE: " + cluster
				return createEvent(name: "checkInterval", value: 60 * 12, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
			} else {
				log.warn "ON/OFF REPORTING CONFIG FAILED- error code:${cluster.data[0]}"
				return null
			}
		} else if(cluster && !shouldProcessMessage(cluster)) {
        	log.trace "parse: cluster message ignored"
            return null        	
        } else {
			log.warn "parse: failed to process cluster message"
			log.debug "${cluster}"
            return null
		}
    }
    
    def map = zigbee.parseDescriptionAsMap(description)
    if(map) {
    	log.trace "parse: map is $map"
        
    	if (map.clusterInt == 0xFC01 && map.attrInt == 0x0000 && map.value) {        	
            def capabilitiesInt = Integer.parseInt(map.value, 16)
            /* 8-bit bitmap	BITMAP8	0x18
            Forward Phase Control #0 (0x01) When this bit is set, the dimmer supports AC forward phase control.
			Reverse Phase Control #1 (0x02) When this bit is set, the dimmer supports AC reverse phase control.
			RFU #2…#4 (0x1C) These bits are reserved for future use and must be written as 0 and ignored when read.
			Reactance Discriminator #5 (0x20) When this bit is set, the dimmer is capable of measuring the reactance of 
            	the attached ballast good enough to distinguish inductive and capacitive loads and select the appropriate dimming technique accordingly.
			Configurable Curve #6 (0x40) When this bit is set, the dimmer is capable of replacing the built-in, default
            	dimming curve, with a curve that better suits the attached ballast
			Overload detection #7 (0x80) When this bit is set, the dimmer is capable of detecting an output overload
            	and shutting the output off to prevent damage to the dimmer.
            */            
            if(capabilitiesInt & 0x01)
            	log.trace "parse: Forward Phase Control supported"                
            if(capabilitiesInt & 0x02)
            	log.trace "parse: Reverse Phase Control supported"
        }
        else if (map.clusterInt == 0xFC01 && map.attrInt == 0x0001 && map.value) {        	
            def statusInt = Integer.parseInt(map.value, 16)
            /* 8-bit bitmap	BITMAP8	0x18
            Forward Phase Control #0 (0x01) When this bit is set, the dimmer is currently operating in AC forward phase control mode.
            Reverse Phase Control #1 (0x02) When this bit is set, the dimmer is currently operating in AC reverse phase control.
            Operational #2 (0x04) These bits are reserved for future use and must be written as 0 and ignored when read.
            Overload #3 (0x08) The output is currently turned off, because the dimmer has detected an overload.
            RFU #4…#5 (0x30) Reserved for future use. Set to 0 when writing, ignore when reading.
            Capacitive Load #6 (0x40) When this bit is set, the dimmer’s reactance discriminator has detected a capacitive load.
            Inductive Load #7 (0x80) When this bit is set, the dimmer’s reactance discriminator has detected an inductive load.
            */            
            if(statusInt & 0x01)
            	log.trace "parse: operating in forward phase (Leading) control mode"                
            if(statusInt & 0x02)
            	log.trace "parse: operating in reverse phase (Trailing) control mode"                
            if(statusInt & 0x08)
            	log.trace "parse: overload"
            if(statusInt & 0x40)
            	log.trace "parse: capacitive load detected"
            if(statusInt & 0x80)
            	log.trace "parse: inductive load detected"            
        }
        else if (map.clusterInt == 0xFC01 && map.attrInt == 0x0002 && map.value) {
        	/*
            Phase Control #0…#1 (0x02) Specifies the mode of operation:
			00b: Automatically select the appropriate dimming technique
			01b: Always use forward phase control (leading edge, L)
			02b: Always use reverse phase control (trailing edge, C/R)
			11b: Reserved. Do not use.
            */
            log.trace "parse: mode is: $map.value"
        }    
    }
    
    log.warn "parse: failed to process message"	
    return null
}

private boolean shouldProcessMessage(cluster) {
    // 0x0B is default response indicating message got through
    // 0x07 is bind message
    boolean ignoredMessage = cluster.profileId != 0x0104 ||
        cluster.command == 0x0B ||
        cluster.command == 0x07 ||
        (cluster.data.size() > 0 && cluster.data.first() == 0x3e)
    return !ignoredMessage
}

def off() {
	log.trace "off"
    zigbee.off()
}

def on() {
	log.trace "on"
    zigbee.on()
}

def setLevel(value) {
	log.trace "setLevel: $value"
    zigbee.setLevel(value) + (value?.toInteger() > 0 ? zigbee.on() : [])
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
	log.trace "ping"
    return zigbee.onOffRefresh()
}

def refresh() {
	log.trace "refresh"
    //zigbee.onOffRefresh() + zigbee.levelRefresh() + zigbee.simpleMeteringPowerRefresh() + zigbee.electricMeasurementPowerRefresh() + zigbee.onOffConfig(0, 300) + zigbee.levelConfig() + zigbee.simpleMeteringPowerConfig() + zigbee.electricMeasurementPowerConfig()
    //Removed: zigbee.electricMeasurementPowerRefresh(), zigbee.electricMeasurementPowerConfig()
    //zigbee.readAttribute(0x0702, 0x0400, [destEndpoint: 0x04]) /*+ zigbee.readAttribute(0x0B04, 0x050B, [destEndpoint: 0x04])*/
    
    def refreshCmds = []
    refreshCmds += zigbee.onOffRefresh() +
    			   zigbee.levelRefresh() +
                   zigbee.readAttribute(0x0702, 0x0400, [destEndpoint: 0x04]) +
                   zigbee.readAttribute(0xFC01, 0x0000) + 
                   zigbee.readAttribute(0xFC01, 0x0001) +
                   zigbee.readAttribute(0xFC01, 0x0002)
                
    return refreshCmds
}

def configure() {
    //log.debug "Configuring Reporting and Bindings."
    log.trace "configure"

    // Device-Watch allows 2 check-in misses from device + ping (plus 1 min lag time)
    // enrolls with default periodic reporting until newer 5 min interval is confirmed
    sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 1 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
    
    //zigbee.configureReporting(0x0702, 0x0400, 0x2A, 1, 600, 0x05, [destEndpoint: 0x04]) /*+ zigbee.configureReporting(0x0B04, 0x050B, 0x29, 1, 600, 0x05, [destEndpoint: 0x04])*/
    
    
    def configCmds = []
    
    //Configure phase control
    if(phaseControl) {
    	log.debug "configure: set phase control to $phaseControl"
        if(phaseControl == "Auto") {        	
            configCmds += zigbee.writeAttribute(0xFC01, 0x0002, 0x18, 0x00) //Auto
        } else if(phaseControl == "Leading") {        	
        	configCmds += zigbee.writeAttribute(0xFC01, 0x0002, 0x18, 0x01) //Leading        	
        } else if(phaseControl == "Trailing") {        	
            configCmds += zigbee.writeAttribute(0xFC01, 0x0002, 0x18, 0x02) //Trailing
        }    	
    }
    
    //Reporting
    configCmds += zigbee.onOffConfig(0, 300) + 
    			  zigbee.levelConfig() + 
                  zigbee.configureReporting(0x0702, 0x0400, 0x2A, 1, 600, 0x05, [destEndpoint: 0x04]) 
    
     return refresh() + configCmds
}
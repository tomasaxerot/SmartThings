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
  * 0.3 (2017-03-26): Added possibility to set device mode: Push/Bi-Stable
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
            //tileAttribute ("power", key: "SECONDARY_CONTROL") {
            //    attributeState "power", label:'${currentValue} W'
            //}
        }        
        
        valueTile("power", "Power", decoration: "flat", width: 2, height: 2) {
			state "default", label:'${currentValue} W'
		}
        
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        
        standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
		}
        
        main "switch"
        details(["switch", "power", "refresh", "configure"])
    }
    
    preferences {        
        input name: "deviceSetup", type: "enum", title: "Device Setup", options: ["Push", "Bi-Stable"], description: "Enter Device Setup, Push button is default", required: false
        input name: "phaseControl", type: "enum", title: "Phase Control", options: ["Auto", "Leading", "Trailing"], description: "Enter Phase Control, Auto is recommended", required: false
        input name: "readConfiguration", type: "bool", title: "Read Advanced Configuration", description: "Enter Read Advanced Configuration", required: false
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
                       
            log.debug "parse: AC Forward Phase Control ${capabilitiesInt & 0x01 ? "supported" : "not supported"}"            
            log.debug "parse: AC Reverse Phase Control ${capabilitiesInt & 0x02 ? "supported" : "not supported"}"
            log.debug "parse: Reactance Discriminator/Auto mode ${capabilitiesInt & 0x20 ? "supported" : "not supported"}"            
            log.debug "parse: Configurable Curve ${capabilitiesInt & 0x40 ? "supported" : "not supported"}"            
            log.debug "parse: Overload Detection ${capabilitiesInt & 0x80 ? "supported" : "not supported"}"                
            
            return null
        }
        else if (map.clusterInt == 0xFC01 && map.attrInt == 0x0001 && map.value) {        	
            def statusInt = Integer.parseInt(map.value, 16)
                 
            if(statusInt & 0x01)
            	log.debug "parse: operating in forward phase (Leading) control mode"                
            if(statusInt & 0x02)
            	log.debug "parse: operating in reverse phase (Trailing) control mode"                
            if(statusInt & 0x08)
            	log.debug "parse: overload"
            if(statusInt & 0x40)
            	log.debug "parse: capacitive load detected"
            if(statusInt & 0x80)
            	log.debug "parse: inductive load detected"                 
            
            return null
        }
        else if (map.clusterInt == 0xFC01 && map.attrInt == 0x0002 && map.value) {
        	def modeInt = Integer.parseInt(map.value, 16)        	
            
            if((modeInt & 0x03) == 0)
            	log.debug "parse: Phase control is Auto"                
            if((modeInt & 0x03) == 1)
            	log.debug "parse: Phase control is force forward phase control (leading edge, L)"                
            if((modeInt & 0x03) == 2)
            	log.debug "parse: Phase control is force reverse phase control (trailing edge, C/R)"            
            
            return null
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
    
    //zigbee.readAttribute(0x0B04, 0x050B, [destEndpoint: 0x04])
    //zigbee.readAttribute(0xFC00, 0x0001, [destEndpoint: 0xE8])
    
    def refreshCmds = []
    refreshCmds += zigbee.onOffRefresh() +
    			   zigbee.levelRefresh() +
                   zigbee.readAttribute(0x0702, 0x0400, [destEndpoint: 0x04])
                   
    if(readConfiguration) {
    	refreshCmds += zigbee.readAttribute(0xFC01, 0x0000) + 
                   	   zigbee.readAttribute(0xFC01, 0x0001) +
                       zigbee.readAttribute(0xFC01, 0x0002)
    }
                   
                
    return refreshCmds
}

def configure() {
    log.trace "configure"

    // Device-Watch allows 2 check-in misses from device + ping (plus 1 min lag time)
    // enrolls with default periodic reporting until newer 5 min interval is confirmed
    sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 1 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
    
    def configCmds = []
    
    if(deviceSetup) {
    	log.debug "configure: set device setup to $deviceSetup"
    	        
        //Push(Default)	: 070008030b0106320105000803c6010832000500080386010802000603070106070008020b0006320105000802c6000832000500080286000802000602070006000841
        //Bi-Stable		: 02000602030006020006020D0006000241
        
        if(deviceSetup == "Push") {                
        	configCmds += "st wattr 0x${device.deviceNetworkId} 0xE8 0xFC00 0x0001 0x48 {070008030b0106320105000803c6010832000500080386010802000603070106070008020b0006320105000802c6000832000500080286000802000602070006000841}"            
        }
        else if(deviceSetup == "Bi-Stable") {                
        	configCmds += "st wattr 0x${device.deviceNetworkId} 0xE8 0xFC00 0x0001 0x48 {02000602030006020006020D0006000241}"            
        }    
    }
    
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
    //TODO: zigbee.configureReporting(0x0B04, 0x050B, 0x29, 1, 600, 0x05, [destEndpoint: 0x04])
    configCmds += zigbee.onOffConfig(0, 300) + 
    			  zigbee.levelConfig() + 
                  zigbee.configureReporting(0x0702, 0x0400, 0x2A, 1, 600, 0x05, [destEndpoint: 0x04]) 
    
     return refresh() + configCmds
}
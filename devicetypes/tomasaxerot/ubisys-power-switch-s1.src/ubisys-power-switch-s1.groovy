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
  * Version:
  * 0.3 (2017-03-28): Cleaning up 
  * 0.2 (2017-03-19): Removed power divisor, added configuration tile
  * 0.1 (2017-03-15): Initial version of DTH for Ubisys Power switch S1 (Rev. 3), Application: 1.09, Stack: 1.88  
  */

metadata {
    definition (name: "Ubisys Power switch S1", namespace: "tomasaxerot", author: "Tomas Axerot") {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        capability "Switch"
        capability "Power Meter"

		//0000 = Basic, 0003 = Identify, 0004 = Groups, 0005 = Scenes, 0006 = On/off, 0x0702 Metering, 0x0B04 Electrical Measurement, FC00 = Device Setup

		//S1
        //Raw description: 01 0104 0009 00 05 0000 0003 0004 0005 0006 00
        //EndPoint 01: in: 0000 0003 0004 0005 0006
        //EndPoint 02: 
        //EndPoint 03: in: 0702 0B04
        //EndPoint E8: in: 0xFC00
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0702", manufacturer: "ubisys", model: "S1 (5501)", deviceJoinName: "Ubisys Power switch S1"
    }        
    
	tiles(scale: 2) {
		multiAttributeTile(name: "switch", type: "lighting", width: 6, height: 4, canChangeIcon: true) {
			tileAttribute("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label: 'On', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00A0DC", nextState: "turningOff"
				attributeState "off", label: 'Off', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff", nextState: "turningOn"
				attributeState "turningOn", label: 'Turning On', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00A0DC", nextState: "turningOff"
				attributeState "turningOff", label: 'Turning Off', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff", nextState: "turningOn"
			}			
		}
        
        valueTile("power", "device.power", width: 2, height: 2) {
			state("default", label:'${currentValue} W',
				backgroundColors:[
					[value: 0, color: "#ffffff"],
					[value: 1, color: "#00A0DC"]					
				]
			)
		}

		standardTile("refresh", "device.power", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label: '', action: "refresh.refresh", icon: "st.secondary.refresh"
		}
        
        standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
		}

		main "switch"
		details(["switch", "power", "refresh", "configure"])
	}   
    
    preferences {        
        input name: "deviceSetup", type: "enum", title: "Device Setup", options: ["Bi-Stable", "Push"], description: "Enter Device Setup, Bi-Stable button is default", required: false        
        input name: "readConfiguration", type: "bool", title: "Read Advanced Configuration", description: "Enter Read Advanced Configuration", required: false
    }
}

def parse(String description) {
    log.trace "parse: description is $description"	

	def event = zigbee.getEvent(description)
	if (event) {
    	log.trace "parse: event is $event"        
        return createEvent(event)        
	}
    
    def map = zigbee.parseDescriptionAsMap(description)
    if(map) {
    	log.trace "parse: map is $map"        
        
        if (map.clusterInt == 0x0006 && map.commandInt == 0x07) {
			if (Integer.parseInt(map.data[0], 16) == 0x00) {
				log.debug "On/Off reporting successful"
				return createEvent(name: "checkInterval", value: 60 * 12, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
			} else {
				log.warn "On/Off reporting failed with code: ${map.data[0]}"				
			}
        } else if (map.clusterInt == 0x0702 && map.commandInt == 0x07) {
			if (Integer.parseInt(map.data[0], 16) == 0x00) {
				log.debug "Metering reporting successful"				
			} else {
				log.warn "Metering reporting failed with code: ${map.data[0]}"				
			}
		} else if (map.clusterInt == 0xFC00 && map.commandInt == 0x04) {
			if (Integer.parseInt(map.data[0], 16) == 0x00) {
				log.debug "Device Setup successful"				
			} else {
				log.warn "Device Setup failed with code: ${map.data[0]}"                				
			}            		         
        } else if(!shouldProcessMessage(map)) {
        	log.trace "parse: map message ignored"            
        } else {
        	log.warn "parse: failed to process map message"
        }
        return null
    }
    
    log.warn "parse: failed to process message"	
    return null
}

private boolean shouldProcessMessage(map) {
    // 0x0B is default response
    // 0x07 is configure reporting response
    boolean ignoredMessage = map.profileId != 0x0104 ||
        map.commandInt == 0x0B ||
        map.commandInt == 0x07 ||
        (map.data.size() > 0 && Integer.parseInt(map.data[0], 16) == 0x3e)
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

def ping() {
	log.trace "ping"
	return zigbee.onOffRefresh()
}

def refresh() {
	log.trace "refresh"	
    
    def refreshCmds = []
    refreshCmds += zigbee.onOffRefresh() +    			   
                   zigbee.readAttribute(0x0702, 0x0400, [destEndpoint: 0x03])
                   
    if(readConfiguration) {
    	refreshCmds += zigbee.readAttribute(0xFC00, 0x0000, [destEndpoint: 0xE8]) + 
                   	   zigbee.readAttribute(0xFC00, 0x0001, [destEndpoint: 0xE8])
                       
    }                   
                
    return refreshCmds
}

//Device Setup. Bi-Stable is default
private getDEVICESETUP_BISTABLE() { "02000602030006020006020D0006000241" }
private getDEVICESETUP_PUSH() { "020006020d0106020006020d0006000241" }

def configure() {
	log.trace "configure"

    // Device-Watch allows 2 check-in misses from device + ping (plus 1 min lag time)
    // enrolls with default periodic reporting until newer 5 min interval is confirmed
    sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 1 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
    
    def configCmds = []
    
    //Configure device setup
    if(deviceSetup) {
    	log.debug "configure: set device setup to $deviceSetup"        
        if(deviceSetup == "Push") {                        	
            configCmds += "st wattr 0x${device.deviceNetworkId} 0xE8 0xFC00 0x0001 0x48 {${DEVICESETUP_PUSH}}"            
        } else if(deviceSetup == "Bi-Stable") {                
        	configCmds += "st wattr 0x${device.deviceNetworkId} 0xE8 0xFC00 0x0001 0x48 {${DEVICESETUP_BISTABLE}}"            
        }    
    }
    
    configCmds += zigbee.onOffConfig(0, 300) +     			  
                  zigbee.configureReporting(0x0702, 0x0400, 0x2A, 1, 600, 0x05, [destEndpoint: 0x03]) 
    
     return refresh() + configCmds    
}
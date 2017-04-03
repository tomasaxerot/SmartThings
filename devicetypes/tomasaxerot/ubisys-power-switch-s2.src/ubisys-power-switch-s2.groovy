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

metadata {
	//DTH for Ubisys Power switch S2 (Rev. 2), Application: 1.08, Stack: 1.88, Version: 0x01080158
    definition (name: "Ubisys Power Switch S2", namespace: "tomasaxerot", author: "Tomas Axerot") {
        capability "Actuator"
        capability "Switch"
        capability "Power Meter"
        capability "Configuration"
        capability "Refresh"
        capability "Sensor"
        capability "Health Check"
        capability "Light"
		
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0702", manufacturer: "ubisys", model: "S2 (5502)", deviceJoinName: "Ubisys Power Switch S2"        
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
    	//S2: Button (Level), Switch (toggle), Switch (on/off), Button (on/off), Pair of Buttons (toggle)....select input 1/2
        input name: "deviceSetup", type: "enum", title: "Device Setup", options: ["Push", "Bi-Stable"], description: "Enter Device Setup, Push button is default", required: false        
        input name: "readConfiguration", type: "bool", title: "Read Advanced Configuration", description: "Enter Read Advanced Configuration", required: false
    }
}

def parse(String description) {
    log.trace "parse: description is $description"	
    
    if (description?.startsWith("on/off")) {
    	//Trigger read of cluster 6 ep 1/2, need to poll due to missing endpoint in attribute report
        def cmds = zigbee.readAttribute(0x0006, 0x0000, [destEndpoint: 0x01]) + zigbee.readAttribute(0x0006, 0x0000, [destEndpoint: 0x02])		
		return cmds.collect { new physicalgraph.device.HubAction(it) }        
    }
    
    def map = zigbee.parseDescriptionAsMap(description)
    if(map) {
    	if (map.clusterInt == 0x0006 && map.attrInt == 0x00 && map.commandInt == 0x01) {
			log.debug "parse: switch read from $map.sourceEndpoint"
            if(map.sourceEndpoint == "01") {            	
            	return createEvent(name: "switch", value: map.value == "01" ? "on" : "off")
            } else if(map.sourceEndpoint == "02") {            	
            	return childDevices[0].sendEvent(name: "switch", value: map.value == "01" ? "on" : "off")
            }            
        } else if (map.clusterInt == 0x0006 && map.commandInt == 0x0B) {
			log.debug "parse: switch set for $map.sourceEndpoint"
            if(map.sourceEndpoint == "01") {            	
            	return createEvent(name: "switch", value: map.data[0] == "01" ? "on" : "off")
            } else if(map.sourceEndpoint == "02") {            	
            	return childDevices[0].sendEvent(name: "switch", value: map.data[0] == "01" ? "on" : "off")
            }            
        } else if (map.clusterInt == 0x0006 && map.commandInt == 0x07) {
			if (Integer.parseInt(map.data[0], 16) == 0x00) {
				log.debug "On/Off reporting successful"
				return createEvent(name: "checkInterval", value: 60 * 22, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
			} else {
				log.warn "On/Off reporting failed with code: ${map.data[0]}"				
			}
        } else if (map.clusterInt == 0x0702 && map.commandInt == 0x07) {
			if (Integer.parseInt(map.data[0], 16) == 0x00) {
				log.debug "Metering reporting successful"				
			} else {
				log.warn "Metering reporting failed with code: ${map.data[0]}"				
			}
		} else if (map.clusterInt == 0x0702 && map.attrInt == 0x0400) {
			log.debug "parse: power report"
            return createEvent(name: "power", value: Integer.parseInt(map.value, 16))            
        } else if (map.clusterInt == 0xFC00 && map.commandInt == 0x04) {
			if (Integer.parseInt(map.data[0], 16) == 0x00) {
				log.debug "Device Setup successful"				
			} else {
				log.warn "Device Setup failed with code: ${map.data[0]}"                				
			}            		         
        } else if(!shouldProcessMessage(map)) {
        	log.trace "parse: map message ignored"            
        } else {
        	log.warn "parse: failed to process map $map"
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

void off2() {	
	log.trace "off2"        
    
    def actions = [new physicalgraph.device.HubAction("st cmd 0x${device.deviceNetworkId} 0x02 0x0006 0x00 {}")]    
    sendHubCommand(actions)
}

void on2() {	
	log.trace "on2"
    
    def actions = [new physicalgraph.device.HubAction("st cmd 0x${device.deviceNetworkId} 0x02 0x0006 0x01 {}")]    
    sendHubCommand(actions)    
}

def installed() {
	log.trace "installed"
    createChildDevices()    
}

private void createChildDevices() {
    log.trace "createChildDevices"
    
    // Save the device label for updates by updated()
    state.oldLabel = device.label
    
    addChildDevice("tomasaxerot", "Ubisys Component", "${device.deviceNetworkId}-2", null,[componentLabel: "${device.displayName} (CH2)", isComponent: false, componentName: "ch2"])
}

def ping() {
	log.trace "ping"
	return zigbee.onOffRefresh()
}

def refresh() {
	log.trace "refresh"    
    
    def refreshCmds = zigbee.readAttribute(0x0006, 0x0000, [destEndpoint: 0x01]) +
    				  zigbee.readAttribute(0x0006, 0x0000, [destEndpoint: 0x02]) + 
                      zigbee.readAttribute(0x0702, 0x0400, [destEndpoint: 0x05])
                   
    if(readConfiguration) {
    	refreshCmds += zigbee.readAttribute(0xFC00, 0x0000, [destEndpoint: 0xE8]) + 
                   	   zigbee.readAttribute(0xFC00, 0x0001, [destEndpoint: 0xE8])                       
    }                   
                
    return refreshCmds
}

//Device Setup S2. Push is default
private getDEVICESETUP_PUSH() { "020006040d0106020006030d0006000241" }
//private getDEVICESETUP_BISTABLE() { "02000602030006020006020D0006000241" }

def configure() {
	log.trace "configure"

    // Device-Watch allows 2 check-in misses from device + ping (plus 1 min lag time)
    // enrolls with default periodic reporting until newer 10 min interval is confirmed
    sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 1 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
    
    def configCmds = zigbee.configureReporting(0x0006, 0x0000, 0x10, 0, 600, null, [destEndpoint: 0x01]) +
    				 zigbee.configureReporting(0x0006, 0x0000, 0x10, 0, 600, null, [destEndpoint: 0x02]) +
                     zigbee.configureReporting(0x0702, 0x0400, 0x2A, 1, 600, 0x01, [destEndpoint: 0x05])
    
    //Configure device setup
    if(deviceSetup) {
    	log.debug "configure: set device setup to $deviceSetup"        
        if(deviceSetup == "Push") {                        	
            configCmds += "st wattr 0x${device.deviceNetworkId} 0xE8 0xFC00 0x0001 0x48 {${DEVICESETUP_PUSH}}"            
        } //else if(deviceSetup == "Bi-Stable") {                
        //	configCmds += "st wattr 0x${device.deviceNetworkId} 0xE8 0xFC00 0x0001 0x48 {${DEVICESETUP_BISTABLE}}"            
        //}    
    }
    
    return refresh() + configCmds    
}
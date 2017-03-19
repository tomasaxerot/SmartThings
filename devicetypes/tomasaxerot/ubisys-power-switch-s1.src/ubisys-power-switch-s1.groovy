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
  * 0.2 (2017-03-19): Removed power divisor, added configuration tile
  * 0.1 (2017-03-15): Initial version of DTH for Ubisys Power switch S1 (Rev. 3), Application: 1.09, Stack: 1.88
  * 
  */

metadata {
    definition (name: "Ubisys Power switch S1", namespace: "tomasaxerot", author: "Tomas Axerot") {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        capability "Switch"
        capability "Power Meter"

		//0000 = Basic
        //0003 = Identify
        //0004 = Groups
        //0005 = Scenes
        //0006 = On/off        
        //0x0702 Metering
		//0x0B04 Electrical Measurement

		//S1
        //Raw description: 01 0104 0009 00 05 0000 0003 0004 0005 0006 00
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0702", manufacturer: "ubisys", model: "S1 (5501)", deviceJoinName: "Ubisys Power switch S1"                
        //fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0702, 0x0B04", manufacturer: "ubisys", model: "S1 (5501)", deviceJoinName: "Ubisys Power switch S1"                

		//S2
		//Raw description: 01 0104 0002 00 05 0000 0003 0004 0005 0006 00
        //fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0702", manufacturer: "ubisys", model: "S2 (5502)", deviceJoinName: "Ubisys Power switch S2"
        //fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0702, 0x0B04", manufacturer: "ubisys", model: "S2 (5502)", deviceJoinName: "Ubisys Power switch S2"
    }

    // simulator metadata
    simulator {
        // status messages
        status "on": "on/off: 1"
        status "off": "on/off: 0"

        // reply messages
        reply "zcl on-off on": "on/off: 1"
        reply "zcl on-off off": "on/off: 0"
    }
    
    // UI tile definitions
	tiles(scale: 2) {
		multiAttributeTile(name: "switch", type: "lighting", width: 6, height: 4, canChangeIcon: true) {
			tileAttribute("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label: 'On', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00A0DC", nextState: "turningOff"
				attributeState "off", label: 'Off', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff", nextState: "turningOn"
				attributeState "turningOn", label: 'Turning On', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00A0DC", nextState: "turningOff"
				attributeState "turningOff", label: 'Turning Off', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff", nextState: "turningOn"
			}
			tileAttribute("power", key: "SECONDARY_CONTROL") {
				attributeState "power", label: '${currentValue} W'
			}
		}

		standardTile("refresh", "device.power", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label: '', action: "refresh.refresh", icon: "st.secondary.refresh"
		}
        
        standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
		}

		main "switch"
		details(["switch", "refresh", "configure"])
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
			event = createEvent(name: event.name, value: event.value, descriptionText: descriptionText, translatable: true)
		} else if (event.name == "switch") {
			def descriptionText = event.value == "on" ? '{{ device.displayName }} is On' : '{{ device.displayName }} is Off'
			event = createEvent(name: event.name, value: event.value, descriptionText: descriptionText, translatable: true)
		}
	} else {
		def cluster = zigbee.parse(description)

		if (cluster && cluster.clusterId == 0x0006 && cluster.command == 0x07) {
			if (cluster.data[0] == 0x00) {
				log.debug "ON/OFF REPORTING CONFIG RESPONSE: " + cluster
				event = createEvent(name: "checkInterval", value: 60 * 12, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
			} else {
				log.warn "ON/OFF REPORTING CONFIG FAILED- error code:${cluster.data[0]}"
				event = null
			}
		} else if(!shouldProcessMessage(cluster)) {
        	log.trace "Message ignored"
            event = null        	
        } else {
			log.warn "Failed to process message: $description"
			log.debug "${cluster}"
		}        
	}
	return event ? createEvent(event) : event   
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

def ping() {
	log.trace "ping"
	return zigbee.onOffRefresh()
}

def refresh() {
	log.trace "refresh"	
    zigbee.onOffRefresh() + zigbee.readAttribute(0x0702, 0x0400, [destEndpoint: 0x03]) /*+ zigbee.readAttribute(0x0B04, 0x050B, [destEndpoint: 0x03])*/
}

def configure() {
	log.trace "configure"
	// Device-Watch allows 2 check-in misses from device + ping (plus 1 min lag time)
	// enrolls with default periodic reporting until newer 5 min interval is confirmed
	sendEvent(name: "checkInterval", value: 2 * 10 * 60 + 1 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
    
    // OnOff minReportTime 0 seconds, maxReportTime 5 min. Reporting interval if no activity
    refresh() + zigbee.onOffConfig(0, 300) + zigbee.configureReporting(0x0702, 0x0400, 0x2A, 1, 600, 0x05, [destEndpoint: 0x03]) /*+ zigbee.configureReporting(0x0B04, 0x050B, 0x29, 1, 600, 0x05, [destEndpoint: 0x03])*/
}
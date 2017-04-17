/**
 *  Nibe F750
 *
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
 *  Based on work by Petter Arnqvist Eriksson
 */
 
def tempColors = [[value: 31, color: "#153591"], [value: 44, color: "#1e9cbb"], [value: 59, color: "#90d2a7"], [value: 74, color: "#44b621"], 
                  [value: 84, color: "#f1d801"], [value: 95, color: "#d04e00"], [value: 96, color: "#bc2323"]]
                  
def stateColors = [[value: 0, color: "#cccccc"], [value: 1, color: "#00a0dc"]]
 
metadata {
	definition (name: "Nibe F750", namespace: "tomasaxerot", author: "Tomas Axerot") {
		capability "Polling"
		capability "Refresh"
        capability "Temperature Measurement"
        capability "Power Meter"
        attribute "fan_speed", "number"
       	attribute "water_temp", "number"
        attribute "outdoor_temp", "number"
        attribute "compressor", "number"
	}
    
tiles(scale: 2) {
		multiAttributeTile(name: "temperature", type: "thermostat", width: 6, height: 4, canChangeIcon: true) {
  			tileAttribute("device.temperature", key: "PRIMARY_CONTROL") {
    			attributeState "temperature", label: '${currentValue}°', unit: "F", backgroundColors: tempColors
  			}
  			tileAttribute("device.water_temp", key: "SECONDARY_CONTROL") {
    			attributeState "temperature", icon: "http://cdn.device-icons.smartthings.com/Bath/bath6-icn@2x.png", label: '${currentValue}°', unit: "C"
  			}
        }        
        
        valueTile("outdoor_temp", "device.outdoor_temp", width: 2, height: 2) {
            state "temperature", label: 'Outdoor\n ${currentValue}°', unit: "F", defaultState: true, backgroundColors: tempColors
        }         
        
        valueTile("fan_speed", "device.fan_speed", width: 2, height: 2) {
            state "default", label: 'Fan\n ${currentValue}%', unit: "%", defaultState: true, backgroundColors: stateColors
        }
        
        valueTile("power", "device.power", width: 2, height: 2) {
            state "default", label: 'Addition\n ${currentValue}kW', unit: "kW", defaultState: true, backgroundColors: stateColors
        }
        
        valueTile("compressor", "device.compressor", width: 2, height: 2) {
            state "default", label: 'Comp.\n ${currentValue}Hz', unit: "Hz", defaultState: true, backgroundColors: stateColors
        }

		standardTile("refresh", "device.weather", inactiveLabel: false, width: 2, height: 2, decoration: "flat", wordWrap: true) {
			state "default", label: "", action: "refresh", icon: "st.secondary.refresh"
		}

		main(["temperature"])
		details(["temperature", "water_temp", "outdoor_temp", "fan_speed", "power", "compressor", "refresh"])}
}

def parse(String description) {}

def poll() {
	getIndoorTemp()
    getOutdoorTemp()
    getWaterTemp()
	getFanSpeed()
	getAddition()
    getCompressorFrequency()
}

def getParameter(String parmeterCode, Closure rawToDisplay, String eventName) {
	def rawValue = parent.getParameter(parmeterCode)
    if(rawValue != null) {
    	def displayValue = rawToDisplay(rawValue)
        log.debug "$parmeterCode: ${displayValue}"
        sendEvent("name": eventName, "value": displayValue)
    } else {
        log.debug "No data available"
    }    
}

def getIndoorTemp() {
	getParameter("indoor_temperature", { a -> a.toInteger() / 10}, "temperature")
}

def getOutdoorTemp() {
	getParameter("outdoor_temperature", { a -> a.toInteger() / 10}, "outdoor_temp")
}

def getWaterTemp() {
	getParameter("hot_water_temperature", { a -> a.toInteger() / 10}, "water_temp")
}

def getFanSpeed() {
	getParameter("fan_speed", { a -> a.toInteger() }, "fan_speed")
}

def getAddition() {
	getParameter("43084", { a -> a.toInteger() / 100 }, "power")
}

def getCompressorFrequency() {
	getParameter("43136", { a -> Math.round(a.toInteger() / 10) }, "compressor")	
}

def refresh() {
    poll()
}
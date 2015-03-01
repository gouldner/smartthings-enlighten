/**
 *  Enlighten Solar System
 *
 *  Copyright 2015 Umesh Sirsiwal with contribution from Ronald Gouldner
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
preferences {
    input("user_id", "text", title: "Enphase Dev Account User ID")
    input("system_id", "text", title: "Enphase System ID")
    input("key", "text", title: "Enphase Dev Account Key")

}
metadata {
         definition (name: "Enlighten Solar System", namespace: "usirsiwal", author: "Umesh Sirsiwal") {
                    capability "Power Meter"
                    capability "Refresh"
                    capability "Polling"
                    

                    attribute "energy_today", "STRING"
                    attribute "energy_life", "STRING"
                    
                    }

         simulator {
                      // TODO: define status and reply messages here
                   }

         tiles {
                   valueTile("energy_today", "device.energy_today", width: 1, height: 1, canChangeIcon: true) {
                         state("energy_today", label: '${currentValue}KWh', unit:"KWh", 
                          //icon: "https://raw.githubusercontent.com/usirsiwal/smartthings-enlighten/master/enphase.jpg",
                          backgroundColors: [
                           [value: 2, color: "#bc2323"],
                           [value: 5, color: "#d04e00"],
                           [value: 10, color: "#f1d801"],
                           [value: 20, color: "#90d2a7"],
                           [value: 30, color: "#44b621"],
                           [value: 40, color: "#1e9cbb"],
                           [value: 50, color: "#153591"]
                          ],

                      )
                   }
                   valueTile("energy_life", "device.energy_life", width: 1, height: 1, canChangeIcon: true) {
                         state("energy_life", label: '${currentValue}MWh', unit:"MWh", backgroundColors: [
                           [value: 2, color: "#bc2323"],
                           [value: 5, color: "#d04e00"],
                           [value: 10, color: "#f1d801"],
                           [value: 20, color: "#90d2a7"],
                           [value: 30, color: "#44b621"],
                           [value: 40, color: "#1e9cbb"],
                           [value: 50, color: "#153591"],
                          ]
                        )
                   }
                   valueTile("power", "device.power", width: 1, height: 1) {
                     state("power", label: '${currentValue}W', unit:"W", backgroundColors: [
                       [value: 100, color: "#bc2323"],
                       [value: 200, color: "#d04e00"],
                       [value: 400, color: "#1e9cbb"],
                       [value: 600, color: "#153591"]
                      ]
                     )
                   }

                   chartTile(name: "powerChart", attribute: "power")
                   
                   standardTile("refresh", "device.energy_today", inactiveLabel: false, decoration: "flat") {
                      state "default", action:"polling.poll", icon:"st.secondary.refresh"
                   }

                   main "energy_today"
                   details(["powerChart","power", "energy_today",  "energy_life", "refresh"])
        }
}


// parse events into attributes
def parse(String description) {
    log.debug "Parsing '${description}'"

}

def poll() {
    refresh()
}

def refresh() {
  log.debug "Executing 'refresh'"
  energyRefresh()
}


def energyRefresh() {
  log.debug "Executing 'energyToday'"

  def cmd = "https://api.enphaseenergy.com/api/v2/systems/${settings.system_id}/summary?key=${settings.key}&user_id=${settings.user_id}";
  log.debug "Sending request cmd[${cmd}]"

  httpGet(cmd) {resp ->
        if (resp.data) {
            log.debug "${resp.data}"
            def energyToday = resp.data.energy_today
            def energyLife = resp.data.energy_lifetime
            def currentPower = resp.data.current_power

            log.debug "Energy today ${energyToday}"
            log.debug "Energy life ${energyLife}"
            log.debug "Current Power ${currentPower}"
            
            sendEvent(name: 'energy_today', value: (energyToday/1000))
            sendEvent(name: 'energy_life', value: (energyLife/1000000))
            sendEvent(name: 'power', value: (currentPower))
            
            storeData('power', currentPower)

        }
        if(resp.status == 200) {
           log.debug "poll results returned"
        }
        else {
           log.error "polling children & got http status ${resp.status}"
        }
    }
}

def getVisualizationData(attribute) {	
	log.debug "getChartData for $attribute"
	def keyBase = "measure.${attribute}"
    log.debug "getChartData state = $state"
	
	def dateBuckets = state[keyBase]
	
	//convert to the right format
	def results = dateBuckets?.sort{it.key}.collect {[
		date: Date.parse("yyyy-MM-dd", it.key),
		average: it.value.average,
		min: it.value.min,
		max: it.value.max
		]}
	
	log.debug "getChartData results = $results"
	results
}

private getKeyFromDate(date = new Date()){
	date.format("yyyy-MM-dd")
}

private storeData(attribute, value) {
	log.debug "storeData initial state: $state"
	def keyBase = "measure.${attribute} ${value}"
	def numberValue = value.toBigDecimal()
	
	// create bucket if it doesn't exist
	if(!state[keyBase]) {
		state[keyBase] = [:]
		log.debug "storeData - attribute not found. New state: $state"
	}
	
	def dateString = getKeyFromDate()
	if(!state[keyBase][dateString]) {
		//no date bucket yet, fill with initial values
		state[keyBase][dateString] = [:]
		state[keyBase][dateString].average = numberValue
		state[keyBase][dateString].runningSum = numberValue
		state[keyBase][dateString].runningCount = 1
		state[keyBase][dateString].min = numberValue
		state[keyBase][dateString].max = numberValue
		
		log.debug "storeData date bucket not found. New state: $state"
		
		// remove old buckets
		def old = getKeyFromDate(new Date() - 7)
		state[keyBase].findAll { it.key < old }.collect { it.key }.each { state[keyBase].remove(it) }
	} else {
		//re-calculate average/min/max for this bucket
		state[keyBase][dateString].runningSum = (state[keyBase][dateString].runningSum.toBigDecimal()) + numberValue
		state[keyBase][dateString].runningCount = state[keyBase][dateString].runningCount.toInteger() + 1
		state[keyBase][dateString].average = state[keyBase][dateString].runningSum.toBigDecimal() / state[keyBase][dateString].runningCount.toInteger()
		
		log.debug "storeData after average calculations. New state: $state"
		
		if(state[keyBase][dateString].min == null) { 
			state[keyBase][dateString].min = numberValue
		} else if (numberValue < state[keyBase][dateString].min.toBigDecimal()) {
			state[keyBase][dateString].min = numberValue
		}
		if(state[keyBase][dateString].max == null) {
			state[keyBase][dateString].max = numberValue
		} else if (numberValue > state[keyBase][dateString].max.toBigDecimal()) {
			state[keyBase][dateString].max = numberValue
		}
	}
	log.debug "storeData after min/max calculations. New state: $state"
}


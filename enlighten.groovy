/**
 *  Enlighten Solar System
 *
 *  Copyright 2015 Ronald Gouldner based on original version by Umesh Sirsiwal
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
	definition (name: "Enlighten Solar System", namespace: "gouldner", author: "Ronald Gouldner") {
	capability "Power Meter" 
        capability "Refresh"
	capability "Polling"
        
        attribute "energy_today", "STRING"
        attribute "energy_life", "STRING"
        
        fingerprint deviceId: "RRGEnlightenPV"
	fingerprint inClusters: "0x20,0x27,0x31,0x40,0x43,0x44,0x70,0x72,0x80,0x86"
	}

	simulator {
		// TODO: define status and reply messages here
	}

	tiles {
            standardTile("PoweredBy", "device.poweredBy") {
                state "default", label: "Powered by Enphase Energy"
                //icon:"https://s3.amazonaws.com/enterprise-multitenant.3scale.net.3scale.net/enphase-energy/2014/05/06/ENPH_logo_scr_RGB_API_sm-4155f33125cda43a.png?AWSAccessKeyId=AKIAIRYLTWBQ37ZNGBZA&Expires=1425085235&Signature=QWZgPM0keTYDoJ%2BmJ4Ds56rr%2Buo%3D"
            }
            valueTile("energy_today", "device.energy_today") {
   	         state("energy_today", label: '${currentValue}KWh T', unit:"KWh", backgroundColors: [
                    [value: 2, color: "#bc2323"],
                    [value: 5, color: "#d04e00"],
                    [value: 10, color: "#f1d801"],
                    [value: 20, color: "#90d2a7"],
		            [value: 30, color: "#44b621"],
                    [value: 40, color: "#1e9cbb"],
                    [value: 50, color: "#153591"]
    	            ]
            	)
        	}
            valueTile("power", "device.power") {
   	         state("Power", label: '${currentValue}KWh P', unit:"KWh", backgroundColor: "#000000")
        	}
            valueTile("energy_life", "device.energy_life", width: 1, height: 1, canChangeIcon: true) {
   	         state("energy_life", label: '${currentValue}MWh L', unit:"MWh", backgroundColors: [
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

            standardTile("refresh", "device.energy_today", inactiveLabel: false, decoration: "flat") {
                state "default", action:"polling.poll", icon:"st.secondary.refresh"
            }

        
        main (["power","energy_today"])
        details(["PoweredBy","power","energy_today", "energy_life", "refresh"])

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
            def energyToday = resp.data.energy_today/1000
            def energyLife = resp.data.energy_lifetime/1000000
            def currentPower = resp.data.current_power/1000
            
            log.debug "Energy today ${energyToday}"
            log.debug "Energy life ${energyLife}"
            log.debug "Current Power Level ${currentPower}"
            
            delayBetween([sendEvent(name: 'energy_today', value: (energyToday))
                          ,sendEvent(name: 'energy_life', value: (energyLife))
                          ,sendEvent(name: 'power', value: (currentPower))
	                     ])

        }
        if(resp.status == 200) {
            	log.debug "poll results returned"
        }
         else {
            log.error "polling children & got http status ${resp.status}"
        }
    }
}

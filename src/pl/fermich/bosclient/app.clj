(ns pl.fermich.bosclient
  (:gen-class))

(use 'pl.fermich.bosclient.broker-client)

(def brokerClient (create-broker-client))

;(println (.login brokerClient "" ""))

;(println "----------- SECURITY LIST ----------------")

;(println (.get-sec-list brokerClient))

;(println "----------- LOGOUT ----------------")

;(println (.logout brokerClient))
